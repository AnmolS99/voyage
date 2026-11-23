begin;
select plan(5);

select is_empty(
  $$
    select c.relname
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind in ('r', 'p') and not c.relrowsecurity
  $$,
  'every table in public has row level security'
);

create table public.privilege_probe (id int);
select table_privs_are('public', 'privilege_probe', 'anon', array[]::text[],
  'new tables grant nothing to anon');
select table_privs_are('public', 'privilege_probe', 'authenticated', array[]::text[],
  'new tables grant nothing to authenticated');

create function public.privilege_probe_fn() returns int language sql as 'select 1';
select function_privs_are('public', 'privilege_probe_fn', array[]::text[], 'anon', array[]::text[],
  'new functions are not executable by anon');
select function_privs_are('public', 'privilege_probe_fn', array[]::text[], 'authenticated', array[]::text[],
  'new functions are not executable by authenticated');

select * from finish();
rollback;
