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

create function public.create_profile_for_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  insert into public.profiles (id) values (new.id);
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.create_profile_for_new_user();

-- The only accounts are the apps' generated device credentials, so any other sign-up is refused.
create function public.hook_before_user_created(event jsonb)
returns jsonb
language plpgsql
immutable
set search_path = ''
as $$
begin
  if event -> 'user' -> 'app_metadata' ->> 'provider' = 'email'
    and event -> 'user' ->> 'email'
      ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}@device\.voyage\.invalid$'
  then
    return '{}'::jsonb;
  end if;

  return jsonb_build_object(
    'error', jsonb_build_object(
      'message', 'Sign-ups are only available in the voyage app.',
      'http_code', 403
    )
  );
end;
$$;

grant execute on function public.hook_before_user_created(jsonb) to supabase_auth_admin;

create function public.delete_my_data()
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if auth.uid() is null then
    raise exception 'Not signed in' using errcode = '42501';
  end if;

  delete from auth.users where id = auth.uid();
end;
$$;

grant execute on function public.delete_my_data() to authenticated;
