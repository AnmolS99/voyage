create table public.country_neighbors (
  a text collate "C" not null,
  b text collate "C" not null,
  primary key (a, b),
  constraint country_neighbors_not_self check (a <> b)
);

comment on table public.country_neighbors is
  'Border graph, both directions: a borders b. Generated (scripts/generate_neighbors.py).';

create table public.country_border_distances (
  a text collate "C" not null,
  b text collate "C" not null,
  km integer not null,
  primary key (a, b),
  constraint country_border_distances_ordered check (a < b),
  constraint country_border_distances_km check (km >= 0)
);

comment on table public.country_border_distances is
  'Great-circle km between the closest points of two outlines, one row per pair (a < b); 0 exactly for neighbours. Generated (scripts/generate_neighbors.py).';

alter table public.country_neighbors enable row level security;
alter table public.country_border_distances enable row level security;

grant select on table public.country_neighbors to anon, authenticated;
grant select on table public.country_border_distances to anon, authenticated;

create policy "Country neighbours are readable by everyone"
  on public.country_neighbors for select
  to anon, authenticated
  using (true);

create policy "Border distances are readable by everyone"
  on public.country_border_distances for select
  to anon, authenticated
  using (true);
