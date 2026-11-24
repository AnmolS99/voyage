begin;
select plan(7);

select table_privs_are('public', 'country_neighbors', 'anon', array['SELECT'],
  'anon can only read country_neighbors');
select table_privs_are('public', 'country_neighbors', 'authenticated', array['SELECT'],
  'authenticated can only read country_neighbors');

select function_privs_are('public', 'country_graph_version', array[]::text[], 'anon', array['EXECUTE'],
  'anon can read the graph version');

select is_empty(
  $$ select a, b from public.country_neighbors n
     where not exists (select from public.country_neighbors m where m.a = n.b and m.b = n.a) $$,
  'the border graph is symmetric'
);

select is_empty($$ select from public.country_neighbors where a = 'AU' $$, 'an island has no neighbours');

set local role anon;
select throws_ok($$ insert into public.country_neighbors (a, b) values ('FR', 'AU') $$,
  '42501', null, 'anon cannot add a border');
select ok(
  exists (select from public.country_neighbors where a = 'PT' and b = 'ES'),
  'anon can read borders'
);
reset role;

select * from finish();
rollback;
