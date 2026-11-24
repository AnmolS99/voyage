begin;
select plan(16);

select table_privs_are('public', 'country_neighbors', 'anon', array['SELECT'],
  'anon can only read country_neighbors');
select table_privs_are('public', 'country_neighbors', 'authenticated', array['SELECT'],
  'authenticated can only read country_neighbors');
select table_privs_are('public', 'country_border_distances', 'anon', array['SELECT'],
  'anon can only read country_border_distances');
select table_privs_are('public', 'country_border_distances', 'authenticated', array['SELECT'],
  'authenticated can only read country_border_distances');

select function_privs_are('public', 'shortest_routes', array['text', 'text'], 'anon', array['EXECUTE'],
  'anon can call shortest_routes');
select function_privs_are('public', 'border_distance_km', array['text', 'text'], 'anon', array['EXECUTE'],
  'anon can call border_distance_km');

select is_empty(
  $$ select a, b from public.country_neighbors n
     where not exists (select from public.country_neighbors m where m.a = n.b and m.b = n.a) $$,
  'the border graph is symmetric'
);

select is(
  (select count(*) from public.country_border_distances),
  (select count(distinct code) * (count(distinct code) - 1) / 2
     from (select a as code from public.country_border_distances
           union select b from public.country_border_distances) codes),
  'there is a distance for every pair of countries'
);

select is_empty(
  $$ select d.a, d.b, d.km from public.country_border_distances d
     where (d.km = 0) <> exists (select from public.country_neighbors n where n.a = d.a and n.b = d.b) $$,
  'distances are 0 exactly for neighbours'
);

select is(public.border_distance_km('DE', 'FR'), public.border_distance_km('FR', 'DE'),
  'border_distance_km takes either order');
select is(public.border_distance_km('FR', 'FR'), 0, 'a country is 0 km from itself');
select is(public.border_distance_km('FR', 'XX'), null, 'an unknown code has no distance');
select is_empty($$ select * from public.shortest_routes('FR', 'XX') $$, 'no route to an unknown code');
select is_empty($$ select * from public.shortest_routes('XX', 'XX') $$, 'no route from an unknown code to itself');

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
