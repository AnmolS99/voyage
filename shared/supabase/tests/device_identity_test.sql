begin;
select plan(19);

create function pg_temp.sign_up_event(email text, provider text default 'email') returns jsonb
language sql as $$
  select jsonb_build_object('user', jsonb_build_object(
    'email', email,
    'app_metadata', jsonb_build_object('provider', provider)
  ))
$$;

select is(public.hook_before_user_created(pg_temp.sign_up_event('3f1c2a9e-8b4d-4e6f-9a1b-2c3d4e5f6a7b@device.voyage.invalid')),
  '{}'::jsonb, 'hook accepts a device credential');
select is(public.hook_before_user_created(pg_temp.sign_up_event('3F1C2A9E-8B4D-4E6F-9A1B-2C3D4E5F6A7B@DEVICE.VOYAGE.INVALID')),
  '{}'::jsonb, 'hook ignores case');
select is(public.hook_before_user_created(pg_temp.sign_up_event('someone@example.com')) -> 'error' ->> 'http_code',
  '403', 'hook rejects a real email');
select is(public.hook_before_user_created(pg_temp.sign_up_event('x3f1c2a9e-8b4d-4e6f-9a1b-2c3d4e5f6a7b@device.voyage.invalid.com')) -> 'error' ->> 'http_code',
  '403', 'hook rejects look-alike addresses');
select is(public.hook_before_user_created(pg_temp.sign_up_event('3f1c2a9e-8b4d-4e6f-9a1b-2c3d4e5f6a7b@device.voyage.invalid', 'apple')) -> 'error' ->> 'http_code',
  '403', 'hook rejects other providers');
select is(public.hook_before_user_created(pg_temp.sign_up_event(null)) -> 'error' ->> 'http_code',
  '403', 'hook rejects sign-ups without an email');

select function_privs_are('public', 'hook_before_user_created', array['jsonb'], 'supabase_auth_admin', array['EXECUTE']);
select function_privs_are('public', 'hook_before_user_created', array['jsonb'], 'authenticated', array[]::text[]);
select function_privs_are('public', 'delete_my_data', array[]::text[], 'authenticated', array['EXECUTE']);
select function_privs_are('public', 'delete_my_data', array[]::text[], 'anon', array[]::text[]);
select table_privs_are('public', 'profiles', 'anon', array[]::text[]);
select table_privs_are('public', 'profiles', 'authenticated', array['SELECT']);

insert into auth.users (id, email) values
  ('11111111-1111-4111-8111-111111111111', '11111111-1111-4111-8111-111111111111@device.voyage.invalid'),
  ('22222222-2222-4222-8222-222222222222', '22222222-2222-4222-8222-222222222222@device.voyage.invalid');

select results_eq(
  $$ select count(*)::int from public.profiles where id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222') $$,
  array[2], 'a profile is created for each new user');

set local role authenticated;
set local request.jwt.claims = '{"sub": "11111111-1111-4111-8111-111111111111", "role": "authenticated"}';

select results_eq($$ select id from public.profiles $$,
  array['11111111-1111-4111-8111-111111111111'::uuid], 'a user sees only their own profile');
select throws_ok($$ insert into public.profiles (id) values (gen_random_uuid()) $$,
  '42501', null, 'users cannot write profiles');

select lives_ok($$ select public.delete_my_data() $$, 'a user can delete their data');

reset role;
select results_eq(
  $$ select id from auth.users where id in ('11111111-1111-4111-8111-111111111111', '22222222-2222-4222-8222-222222222222') $$,
  array['22222222-2222-4222-8222-222222222222'::uuid], 'only the caller''s user is deleted');
select is_empty(
  $$ select 1 from public.profiles where id = '11111111-1111-4111-8111-111111111111' $$,
  'their profile is deleted with them');

set local role authenticated;
set local request.jwt.claims = '{"role": "authenticated"}';
select throws_ok($$ select public.delete_my_data() $$, '42501', 'Not signed in',
  'delete_my_data needs a signed-in user');

select * from finish();
rollback;
