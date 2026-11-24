-- The shared country graph (#199): which countries border each other, so Travel scoring can
-- check that a player's guesses connect the start to the end. The puzzle stores its
-- shortest route length, so the server never searches for routes. Rows come from
-- scripts/generate_neighbors.py, which writes them as the *_load_country_graph.sql
-- migrations; clients may read them but never write. Border distances and crossings stay
-- in the apps: Heat is scored by counting taps, so the server needs neither.
-- ISO codes are compared bytewise (collate "C") so orderings match the generator's.

create table public.country_neighbors (
  a text collate "C" not null,
  b text collate "C" not null,
  primary key (a, b),
  constraint country_neighbors_not_self check (a <> b)
);

comment on table public.country_neighbors is
  'Border graph, both directions: a borders b. Generated (scripts/generate_neighbors.py).';

alter table public.country_neighbors enable row level security;

grant select on table public.country_neighbors to anon, authenticated;

create policy "Country neighbours are readable by everyone"
  on public.country_neighbors for select
  to anon, authenticated
  using (true);
