-- The shared country graph (#199): which countries border each other, for scoring Travel
-- routes. Rows come from scripts/generate_neighbors.py, which writes them as the
-- *_load_country_graph.sql migrations; clients may read them but never write. Border
-- distances stay in the apps: Heat is scored by counting taps, so the server needs none.
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

-- Every shortest route from start_code to goal_code over the border graph, both ends
-- included: one row per route. [start_code] alone when they are the same country, no
-- rows when no route exists. iOS and Android `shortestRoutes`; the generator's
-- shortest_routes is the reference, pinned by shared/fixtures/daily/graph.json. Unlike
-- the apps it has no country list, so callers validate codes (an island and an unknown
-- code both have no rows here, and an unknown code still reaches itself).
create function public.shortest_routes(start_code text, goal_code text)
returns table (route text[])
language sql
stable
set search_path = ''
as $$
  with recursive
    -- Breadth-first layers out from the start, until one holds the goal. Each step
    -- carries every country seen so far, so a layer only holds new countries.
    layers (depth, frontier, seen) as (
      select 0, array[start_code collate "C"], array[start_code collate "C"]
      union all
      select l.depth + 1, step.next, l.seen || step.next
      from layers l
      cross join lateral (
        select array_agg(distinct n.b) as next
        from public.country_neighbors n
        where n.a = any (l.frontier) and not n.b = any (l.seen)
      ) step
      where step.next is not null and not goal_code = any (l.frontier)
    ),
    depths (code, depth) as (
      select code, l.depth from layers l cross join lateral unnest(l.frontier) as code
    ),
    -- Back from the goal, one layer closer to the start at each step.
    routes (route, depth) as (
      select array[d.code], d.depth from depths d where d.code = goal_code
      union all
      select previous.code || r.route, previous.depth
      from routes r
      join public.country_neighbors n on n.a = r.route[1]
      join depths previous on previous.code = n.b and previous.depth = r.depth - 1
    )
  select route from routes where depth = 0 and route[1] = start_code
  order by route;
$$;

grant execute on function public.shortest_routes(text, text) to anon, authenticated;
