begin;
select plan(12);

select table_privs_are('public', 'daily_challenges', 'anon', array['SELECT']);
select table_privs_are('public', 'daily_challenges', 'authenticated', array['SELECT']);

create temp table utc as select (now() at time zone 'utc')::date as today;
grant select on utc to anon;

delete from public.daily_challenges where date between (select today from utc) and (select today from utc) + 2;
insert into public.daily_challenges (date, is_guess_country, answer)
select today + offset_days, true, 'NO' from utc, generate_series(0, 2) as offset_days;

select throws_ok(
  $$ insert into public.daily_challenges (date, is_guess_country, is_guess_flag, answer)
     values ('2000-01-01', true, true, 'NO') $$,
  '23514', null, 'exactly one question type per challenge');
select throws_ok(
  $$ insert into public.daily_challenges (date, is_guess_country, answer)
     values ((select today from utc), true, 'SE') $$,
  '23505', null, 'one challenge per date');
select throws_ok(
  $$ insert into public.daily_challenges (date, is_guess_country, is_guess_capital, is_guess_flag, answer)
     values ('2000-01-01', null, true, false, 'NO') $$,
  '23502', null, 'question type flags are not null');

set local role anon;

select results_eq(
  $$ select date from public.daily_challenges where date >= (select today from utc) order by date $$,
  $$ select today + offset_days from utc, generate_series(0, 1) as offset_days $$,
  'anon reads up to UTC tomorrow and nothing later');
select ok(
  (select count(*) from public.daily_challenges where date < (select today from utc)) > 0,
  'anon reads past challenges');

select throws_ok(
  $$ insert into public.daily_challenges (date, is_guess_country, answer) values ('2000-01-01', true, 'NO') $$,
  '42501', null, 'anon cannot insert');
select throws_ok(
  $$ update public.daily_challenges set answer = 'SE' $$,
  '42501', null, 'anon cannot update');
select throws_ok(
  $$ delete from public.daily_challenges $$,
  '42501', null, 'anon cannot delete');
select throws_ok(
  $$ truncate public.daily_challenges $$,
  '42501', null, 'anon cannot truncate');

reset role;
set local role authenticated;
select throws_ok(
  $$ truncate public.daily_challenges $$,
  '42501', null, 'authenticated cannot truncate');

select * from finish();
rollback;
