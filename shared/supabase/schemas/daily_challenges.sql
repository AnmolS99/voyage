create table public.daily_challenges (
  "id" uuid primary key default gen_random_uuid(),
  "date" date not null unique,
  "is_guess_country" boolean not null default false,
  "is_guess_capital" boolean not null default false,
  "is_guess_flag" boolean not null default false,
  "answer" text not null,
  "created_at" timestamptz not null default now(),
  "updated_at" timestamptz not null default now(),
  constraint daily_challenges_one_question_type check (
    is_guess_country::int + is_guess_capital::int + is_guess_flag::int = 1
  )
);

alter table public.daily_challenges enable row level security;

revoke all on table public.daily_challenges from anon, authenticated;
grant select on table public.daily_challenges to anon, authenticated;

create policy "Challenges are readable up to tomorrow (UTC)"
  on public.daily_challenges for select
  to anon, authenticated
  using (date <= (now() at time zone 'utc')::date + 1);
