-- New objects in public start with no access for client roles; each one grants what it needs.
alter default privileges for role postgres in schema public
  revoke all on tables from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke all on sequences from anon, authenticated;
alter default privileges for role postgres in schema public
  revoke execute on functions from anon, authenticated;
-- Schema-scoped defaults can't remove Postgres's global EXECUTE-to-PUBLIC.
alter default privileges for role postgres
  revoke execute on functions from public;

alter table public.daily_challenges enable row level security;

revoke all on table public.daily_challenges from anon, authenticated;
grant select on table public.daily_challenges to anon, authenticated;

-- Created in the dashboard; replaced by the date-limited policy below.
drop policy if exists "Enable read access for all users" on public.daily_challenges;

-- `+ 1` keeps players ahead of UTC on their local today.
create policy "Challenges are readable up to tomorrow (UTC)"
  on public.daily_challenges for select
  to anon, authenticated
  using (date <= (now() at time zone 'utc')::date + 1);

alter table public.daily_challenges
  alter column is_guess_country set not null,
  alter column is_guess_capital set not null,
  alter column is_guess_flag set not null,
  add constraint daily_challenges_date_key unique (date),
  add constraint daily_challenges_one_question_type check (
    is_guess_country::int + is_guess_capital::int + is_guess_flag::int = 1
  );
