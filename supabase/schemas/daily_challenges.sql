create table public.daily_challenges (
  "id" uuid primary key default gen_random_uuid(),
  "date" date not null,
  "is_guess_country" boolean default false,
  "is_guess_capital" boolean default false,
  "is_guess_flag" boolean default false,
  "answer" text not null,
  "created_at" timestamptz not null default now(),
  "updated_at" timestamptz not null default now()
);
