create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  created_at timestamptz not null default now()
);

alter table public.profiles enable row level security;

grant select on table public.profiles to authenticated;

create policy "Users read their own profile"
  on public.profiles for select
  to authenticated
  using ((select auth.uid()) = id);
