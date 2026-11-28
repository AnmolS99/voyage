-- The daily games (#210): puzzles, results and world statistics shared by Shortest Route, Trivia
-- and Hot or Cold. A game becomes playable when its own migration adds its daily_games row and
-- its scorer, public.score_<game>(puzzle jsonb, guesses text[], hints_used int,
-- graph_version text) returns daily_score. Puzzles come from
-- scripts/generate_daily_puzzles.py as *_add_daily_puzzles.sql migrations.

create type public.daily_game as enum ('shortest_route', 'trivia', 'hot_or_cold');

create type public.daily_score as (score int, solved boolean);

create function public.utc_today()
returns date
language sql
stable
set search_path = ''
as $$ select (now() at time zone 'utc')::date $$;

grant execute on function public.utc_today() to anon, authenticated;

-- A solved score falls in the last bucket whose floor it reaches; a failed one in one more
-- bucket after them. So the histogram has cardinality(bucket_floors) + 1 buckets.
create table public.daily_games (
  game public.daily_game primary key,
  bucket_floors int[] not null,
  constraint daily_games_has_buckets check (cardinality(bucket_floors) > 0)
);

alter table public.daily_games enable row level security;

grant select on table public.daily_games to anon, authenticated;

create policy "Daily games are readable by everyone"
  on public.daily_games for select
  to anon, authenticated
  using (true);

create table public.daily_puzzles (
  date date not null,
  game public.daily_game not null,
  payload jsonb not null,
  primary key (date, game),
  constraint daily_puzzles_payload_is_object check (jsonb_typeof(payload) = 'object')
);

alter table public.daily_puzzles enable row level security;

grant select on table public.daily_puzzles to anon, authenticated;

-- `+ 1` keeps players ahead of UTC on their local today.
create policy "Puzzles are readable up to tomorrow (UTC)"
  on public.daily_puzzles for select
  to anon, authenticated
  using (date <= (select public.utc_today()) + 1);

create table public.daily_results (
  user_id uuid not null references auth.users (id) on delete cascade,
  date date not null,
  game public.daily_game not null,
  guesses jsonb not null,
  hints_used int not null default 0,
  graph_version text,
  score int not null,
  solved boolean not null,
  created_at timestamptz not null default now(),
  primary key (user_id, date, game),
  foreign key (date, game) references public.daily_puzzles (date, game),
  constraint daily_results_hints_used_not_negative check (hints_used >= 0)
);

alter table public.daily_results enable row level security;

grant select on table public.daily_results to authenticated;

create policy "Users read their own daily results"
  on public.daily_results for select
  to authenticated
  using ((select auth.uid()) = user_id);

-- Counts only, so a deleted user's results stay counted here.
create table public.daily_stats (
  date date not null,
  game public.daily_game not null,
  players int not null default 0,
  solved int not null default 0,
  histogram int[] not null,
  primary key (date, game),
  foreign key (date, game) references public.daily_puzzles (date, game)
);

alter table public.daily_stats enable row level security;

grant select on table public.daily_stats to authenticated;

create policy "Daily stats are readable by signed-in users"
  on public.daily_stats for select
  to authenticated
  using (true);

-- The guesses as ISO codes, in order. Raises unless they are distinct two-letter codes, so
-- every scorer starts from a well-formed list. An unknown code is just a wrong guess.
create function public.daily_guess_codes(guesses jsonb)
returns text[]
language plpgsql
immutable
set search_path = ''
as $$
declare
  codes text[];
begin
  if jsonb_typeof(guesses) is distinct from 'array' then
    raise exception 'Guesses must be an array' using errcode = '22023';
  end if;

  if exists (
    select from jsonb_array_elements(guesses) g
    where jsonb_typeof(g) <> 'string' or g #>> '{}' !~ '^[A-Z]{2}$'
  ) then
    raise exception 'Guesses must be ISO codes' using errcode = '22023';
  end if;

  select coalesce(array_agg(g order by i), '{}') into codes
  from jsonb_array_elements_text(guesses) with ordinality as t(g, i);

  if (select count(distinct c) from unnest(codes) c) <> cardinality(codes) then
    raise exception 'Guesses must not repeat' using errcode = '22023';
  end if;

  return codes;
end;
$$;

-- One branch per game, so a game's scorer is looked up only once that game is played.
create function public.score_daily(
  game public.daily_game,
  puzzle jsonb,
  guesses jsonb,
  hints_used int,
  graph_version text
)
returns public.daily_score
language plpgsql
stable
set search_path = ''
as $$
declare
  codes text[] := public.daily_guess_codes(guesses);
begin
  if hints_used is null or hints_used < 0 then
    raise exception 'hints_used must not be negative' using errcode = '22023';
  end if;

  if game = 'shortest_route' then
    return public.score_shortest_route(puzzle, codes, hints_used, graph_version);
  elsif game = 'trivia' then
    return public.score_trivia(puzzle, codes, hints_used, graph_version);
  elsif game = 'hot_or_cold' then
    return public.score_hot_or_cold(puzzle, codes, hints_used, graph_version);
  end if;

  raise exception 'Unknown game %', game;
end;
$$;

create function public.submit_result(
  day date,
  game public.daily_game,
  guesses jsonb,
  hints_used int default 0,
  graph_version text default null
)
returns public.daily_score
language plpgsql
security definer
set search_path = ''
as $$
declare
  uid uuid := auth.uid();
  puzzle jsonb;
  floors int[];
  result public.daily_score;
  bucket int;
begin
  if uid is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;

  -- A day either side of UTC covers every time zone's today, and nothing older.
  if day is null or day not between public.utc_today() - 1 and public.utc_today() + 1 then
    raise exception 'Only today''s puzzle can be submitted' using errcode = '22023';
  end if;

  select r.score, r.solved into result
  from public.daily_results r
  where r.user_id = uid and r.date = day and r.game = submit_result.game;
  if found then
    return result;
  end if;

  select g.bucket_floors into floors from public.daily_games g where g.game = submit_result.game;
  if not found then
    raise exception '% is not playable yet', submit_result.game using errcode = '22023';
  end if;

  select p.payload into puzzle
  from public.daily_puzzles p
  where p.date = day and p.game = submit_result.game;
  if not found then
    raise exception 'No % puzzle on %', submit_result.game, day using errcode = 'P0002';
  end if;

  result := public.score_daily(submit_result.game, puzzle, guesses, hints_used, graph_version);
  if result.score is null or result.solved is null then
    raise exception 'The % scorer returned no score', submit_result.game;
  end if;

  bucket := case
    when result.solved then (select count(*)::int from unnest(floors) f where f <= result.score)
    else cardinality(floors) + 1
  end;
  if bucket = 0 then
    raise exception 'Score % is below every % bucket', result.score, submit_result.game;
  end if;

  insert into public.daily_results (user_id, date, game, guesses, hints_used, graph_version, score, solved)
  values (uid, day, submit_result.game, guesses, hints_used, graph_version, result.score, result.solved)
  on conflict on constraint daily_results_pkey do nothing;
  if not found then
    -- A submission from another device got in first.
    select r.score, r.solved into result
    from public.daily_results r
    where r.user_id = uid and r.date = day and r.game = submit_result.game;
    return result;
  end if;

  insert into public.daily_stats (date, game, histogram)
  values (day, submit_result.game, array_fill(0, array[cardinality(floors) + 1]))
  on conflict on constraint daily_stats_pkey do nothing;

  update public.daily_stats s
  set players = s.players + 1,
      solved = s.solved + result.solved::int,
      histogram[bucket] = coalesce(s.histogram[bucket], 0) + 1
  where s.date = day and s.game = submit_result.game;

  return result;
end;
$$;

grant execute on function public.submit_result(date, public.daily_game, jsonb, int, text) to authenticated;

-- Zeros before anyone has played, so the histogram always has the game's buckets.
create function public.get_daily_stats(day date, game public.daily_game)
returns table (players int, solved int, histogram int[])
language sql
stable
set search_path = ''
as $$
  select
    coalesce(s.players, 0),
    coalesce(s.solved, 0),
    coalesce(s.histogram, array_fill(0, array[cardinality(g.bucket_floors) + 1]))
  from public.daily_games g
  left join public.daily_stats s on s.date = get_daily_stats.day and s.game = g.game
  where g.game = get_daily_stats.game
$$;

grant execute on function public.get_daily_stats(date, public.daily_game) to authenticated;
