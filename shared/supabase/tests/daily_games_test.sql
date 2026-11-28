begin;
select plan(52);

-- Fake scorers, so these tests check the platform without the games' real rules:
-- - trivia: solved when the last guess is the answer; score = number of guesses.
-- - shortest_route: the score encodes the graph_version it received, so a test can
--   see that submit_result passed it through.
-- - hot_or_cold: no scorer and no daily_games row, to test a game that isn't live yet.
create or replace function public.score_trivia(puzzle jsonb, guesses text[], hints_used int, graph_version text)
returns public.daily_score language plpgsql as $$
begin
  if cardinality(guesses) = 0 then
    raise exception 'No guesses' using errcode = '22023';
  end if;
  return row(cardinality(guesses), guesses[cardinality(guesses)] = puzzle ->> 'answer')::public.daily_score;
end;
$$;

create or replace function public.score_shortest_route(puzzle jsonb, guesses text[], hints_used int, graph_version text)
returns public.daily_score language sql as $$
  select row(case graph_version when 'current' then 1 when 'older' then 2 else 3 end, true)::public.daily_score
$$;

insert into public.daily_games (game, bucket_floors) values
  ('trivia', '{1,2,3,4,5}'),
  ('shortest_route', '{0,1,2,3,4,5}')
on conflict (game) do update set bucket_floors = excluded.bucket_floors;
delete from public.daily_games where game = 'hot_or_cold';

truncate public.daily_puzzles, public.daily_results, public.daily_stats;

insert into public.daily_puzzles (date, game, payload)
select public.utc_today() + d, 'trivia', '{"answer": "FR"}'
from generate_series(-3, 3) d;
insert into public.daily_puzzles (date, game, payload) values
  (public.utc_today(), 'shortest_route', '{"start": "PT", "end": "PL", "shortest": 3}'),
  (public.utc_today(), 'hot_or_cold', '{"answer": "PE"}');

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111@device.voyage.invalid'),
  ('22222222-2222-4222-8222-222222222222', '22222222-2222-4222-8222-222222222222@device.voyage.invalid');

create function pg_temp.sign_in(uid text) returns void language sql as $$
  select set_config('role', 'authenticated', true),
         set_config('request.jwt.claims', json_build_object('sub', uid, 'role', 'authenticated')::text, true)
$$;
grant execute on function pg_temp.sign_in(text) to authenticated;

-- Privileges

select table_privs_are('public', 'daily_puzzles', 'anon', array['SELECT']);
select table_privs_are('public', 'daily_puzzles', 'authenticated', array['SELECT']);
select table_privs_are('public', 'daily_games', 'anon', array['SELECT']);
select table_privs_are('public', 'daily_games', 'authenticated', array['SELECT']);
select table_privs_are('public', 'daily_results', 'anon', array[]::text[]);
select table_privs_are('public', 'daily_results', 'authenticated', array['SELECT']);
select table_privs_are('public', 'daily_stats', 'anon', array[]::text[]);
select table_privs_are('public', 'daily_stats', 'authenticated', array['SELECT']);

select function_privs_are('public', 'submit_result', array['date', 'daily_game', 'jsonb', 'integer', 'text'],
  'authenticated', array['EXECUTE']);
select function_privs_are('public', 'submit_result', array['date', 'daily_game', 'jsonb', 'integer', 'text'],
  'anon', array[]::text[]);
select function_privs_are('public', 'get_daily_stats', array['date', 'daily_game'], 'authenticated', array['EXECUTE']);
select function_privs_are('public', 'get_daily_stats', array['date', 'daily_game'], 'anon', array[]::text[]);
select function_privs_are('public', 'score_daily', array['daily_game', 'jsonb', 'jsonb', 'integer', 'text'],
  'authenticated', array[]::text[]);
select function_privs_are('public', 'daily_guess_codes', array['jsonb'], 'authenticated', array[]::text[]);

-- Puzzles

set local role anon;
select results_eq(
  $$ select date - public.utc_today() from public.daily_puzzles where game = 'trivia' order by date $$,
  array[-3, -2, -1, 0, 1],
  'puzzles are readable up to tomorrow (UTC), and no further'
);
select throws_ok($$ insert into public.daily_puzzles values (public.utc_today(), 'hot_or_cold', '{}') $$,
  '42501', null, 'clients cannot write puzzles');
select throws_ok($$ select public.submit_result(public.utc_today(), 'trivia', '["FR"]') $$,
  '42501', null, 'anon cannot submit a result');
reset role;

-- Guess validation

select is(public.daily_guess_codes('["FR", "DE", "AU"]'), array['FR', 'DE', 'AU'], 'guesses come back as codes, in order');
select is(public.daily_guess_codes('["ZZ"]'), array['ZZ'], 'an unknown code is left to the scorer');
select is(public.daily_guess_codes('[]'), array[]::text[], 'no guesses is an empty list');
select throws_ok($$ select public.daily_guess_codes('{"guess": "FR"}') $$, '22023', 'Guesses must be an array');
select throws_ok($$ select public.daily_guess_codes('["FR", 7]') $$, '22023', 'Guesses must be ISO codes');
select throws_ok($$ select public.daily_guess_codes('["FRA"]') $$, '22023', 'Guesses must be ISO codes',
  'codes have two letters');
select throws_ok($$ select public.daily_guess_codes('["fr"]') $$, '22023', 'Guesses must be ISO codes',
  'codes are upper case');
select throws_ok($$ select public.daily_guess_codes('["FR", "DE", "FR"]') $$, '22023', 'Guesses must not repeat');

-- Submitting

select pg_temp.sign_in('11111111-1111-4111-8111-111111111111');

select throws_ok($$ select public.submit_result(public.utc_today() - 2, 'trivia', '["FR"]') $$,
  '22023', 'Only today''s puzzle can be submitted', 'two days ago is too old');
select throws_ok($$ select public.submit_result(public.utc_today() + 2, 'trivia', '["FR"]') $$,
  '22023', 'Only today''s puzzle can be submitted', 'two days ahead is too early');
select throws_ok($$ select public.submit_result(null, 'trivia', '["FR"]') $$,
  '22023', 'Only today''s puzzle can be submitted', 'a day is required');
select throws_ok($$ select public.submit_result(public.utc_today(), 'hot_or_cold', '["PE"]') $$,
  '22023', 'hot_or_cold is not playable yet', 'a game without a scorer is refused');
select throws_ok($$ select public.submit_result(public.utc_today() + 1, 'shortest_route', '["ES"]') $$,
  'P0002', null, 'a day without a puzzle is refused');
select throws_ok($$ select public.submit_result(public.utc_today(), 'trivia', '["FR", "FR"]') $$,
  '22023', 'Guesses must not repeat', 'malformed guesses are refused');
select throws_ok($$ select public.submit_result(public.utc_today(), 'trivia', '[]') $$,
  '22023', 'No guesses', 'a scorer error is passed on');
select throws_ok($$ select public.submit_result(public.utc_today(), 'trivia', '["FR"]', -1) $$,
  '22023', 'hints_used must not be negative');

reset role;
select is_empty($$ select from public.daily_results $$, 'refused submissions store nothing');
select is_empty($$ select from public.daily_stats $$, 'refused submissions count nothing');
select pg_temp.sign_in('11111111-1111-4111-8111-111111111111');

select is(public.submit_result(public.utc_today() - 1, 'trivia', '["FR"]'), row(1, true)::public.daily_score,
  'yesterday (UTC) is still today somewhere');
select is(public.submit_result(public.utc_today() + 1, 'trivia', '["DE", "FR"]'), row(2, true)::public.daily_score,
  'tomorrow (UTC) is already today somewhere');
select is(public.submit_result(public.utc_today(), 'trivia', '["DE", "ES", "FR"]'), row(3, true)::public.daily_score,
  'the server scores the guesses');
select is(public.submit_result(public.utc_today(), 'trivia', '["IT"]'), row(3, true)::public.daily_score,
  'a second submission returns the first result');

select results_eq(
  $$ select guesses, score, solved from public.daily_results where date = public.utc_today() and game = 'trivia' $$,
  $$ values ('["DE", "ES", "FR"]'::jsonb, 3, true) $$,
  'a second submission changes nothing'
);
select throws_ok($$ insert into public.daily_results (user_id, date, game, guesses, score, solved)
                    values (auth.uid(), public.utc_today(), 'shortest_route', '[]', 0, true) $$,
  '42501', null, 'clients cannot write results directly');
select throws_ok($$ update public.daily_stats set players = 1000 $$,
  '42501', null, 'clients cannot write stats');

select pg_temp.sign_in('22222222-2222-4222-8222-222222222222');
select is(public.submit_result(public.utc_today(), 'trivia', '["DE", "ES", "IT", "PT", "BE"]'),
  row(5, false)::public.daily_score, 'another player fails');
select results_eq($$ select score from public.daily_results $$, array[5], 'players see only their own results');

select results_eq(
  $$ select * from public.get_daily_stats(public.utc_today(), 'trivia') $$,
  $$ values (2, 1, '{0,0,1,0,0,1}'::int[]) $$,
  'stats count players, solves and each bucket, with the failures last'
);
select results_eq(
  $$ select * from public.get_daily_stats(public.utc_today() - 3, 'trivia') $$,
  $$ values (0, 0, '{0,0,0,0,0,0}'::int[]) $$,
  'stats are zero before anyone plays'
);
select is_empty($$ select * from public.get_daily_stats(public.utc_today(), 'hot_or_cold') $$,
  'a game that is not playable has no stats');

-- The graph version

select is(public.submit_result(public.utc_today(), 'shortest_route', '["ES", "FR", "DE"]', 1, 'older'),
  row(2, true)::public.daily_score, 'the graph version reaches the scorer, and an older one is not an error');
select results_eq(
  $$ select graph_version, hints_used from public.daily_results where game = 'shortest_route' $$,
  $$ values ('older'::text, 1) $$,
  'the graph version and hints are recorded'
);
select is(
  (select histogram from public.get_daily_stats(public.utc_today(), 'shortest_route')),
  '{0,0,1,0,0,0,0}'::int[],
  'a score of 2 lands in the bucket with floor 2'
);

-- Deleting

select public.delete_my_data();
reset role;
select is_empty($$ select from public.daily_results where user_id = '22222222-2222-4222-8222-222222222222' $$,
  'deleting a user deletes their results');
select results_eq($$ select players from public.daily_stats where date = public.utc_today() and game = 'trivia' $$,
  array[2], 'but they stay counted');

select * from finish();
rollback;
