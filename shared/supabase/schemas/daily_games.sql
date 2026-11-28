create type public.daily_game as enum ('shortest_route', 'trivia', 'hot_or_cold');

create type public.daily_score as (score int, solved boolean);

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
