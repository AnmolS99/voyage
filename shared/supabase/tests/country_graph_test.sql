begin;
select plan(8);

select table_privs_are('public', 'country_neighbors', 'anon', array['SELECT'],
  'anon can only read country_neighbors');
select table_privs_are('public', 'country_neighbors', 'authenticated', array['SELECT'],
  'authenticated can only read country_neighbors');

select function_privs_are('public', 'shortest_routes', array['text', 'text'], 'anon', array['EXECUTE'],
  'anon can call shortest_routes');

select is_empty(
  $$ select a, b from public.country_neighbors n
     where not exists (select from public.country_neighbors m where m.a = n.b and m.b = n.a) $$,
  'the border graph is symmetric'
);

select is_empty($$ select * from public.shortest_routes('FR', 'AU') $$, 'no route to an island');
select is_empty($$ select * from public.shortest_routes('FR', 'XX') $$, 'no route to an unknown code');

set local role anon;
select throws_ok($$ insert into public.country_neighbors (a, b) values ('FR', 'AU') $$,
  '42501', null, 'anon cannot add a border');
select is(
  (select count(*) from public.shortest_routes('PT', 'DE'))::int, 1,
  'anon can find routes'
);
reset role;

select * from finish();
rollback;
