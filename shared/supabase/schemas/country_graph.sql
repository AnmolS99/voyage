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
