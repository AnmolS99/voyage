# Daily games: the server

Shortest Route, Trivia and Hot or Cold share one server platform in Supabase: the day's
puzzles, each player's results, and the world statistics shown after a game.

> Platform: [#200](https://github.com/AnmolS99/voyage/issues/200). Epic:
> [#210](https://github.com/AnmolS99/voyage/issues/210). The player's identity is
> in [DEVICE_IDENTITY.md](DEVICE_IDENTITY.md).

## Tables

| Table | Holds | Clients can |
| --- | --- | --- |
| `daily_puzzles` | One payload per day and game, generated months ahead | read rows up to tomorrow (UTC), signed in or not |
| `daily_games` | The games that can be played, with their histogram buckets | read |
| `daily_results` | One result per player, day and game, scored by the server | read their own |
| `daily_stats` | Players, solves and the histogram per day and game | read, when signed in |

Clients write only through `submit_result`. The payload includes the answer,
so a game can give instant feedback offline. Only today's puzzle can be played,
so the most a curious player can spoil is today's puzzle, for themselves.

## Submitting a result

```
submit_result(day date, game daily_game, guesses jsonb, hints_used int = 0, graph_version text = null)
  → { score, solved }
```

- `guesses` is a JSON array of ISO codes, in the order they were made.
- `graph_version` is the app's `CountryGraph.version`. Shortest Route needs it, and
  the other games send `null`.
- The server scores the guesses again itself, stores the result, and adds it
  to `daily_stats` in the same transaction.
- A second submission for the same day and game changes nothing and returns
  the first result, so an outbox can retry safely.

Errors (PostgREST returns them as 4xx). Drop the entry for any of these, and
retry only network and 5xx failures:

| SQLSTATE | When |
| --- | --- |
| `42501` | Not signed in |
| `22023` | `day` isn't within a day of UTC today, the game isn't playable yet, or the guesses are malformed: not an array of distinct two-letter ISO codes, or broken by the game's own rules |
| `P0002` | No puzzle for that day and game |

`get_daily_stats(day, game)` returns `{ players, solved, histogram }`, with
zeros before anyone has played. The client works out the player's percentile
from the histogram.

## Histogram buckets

`daily_games.bucket_floors` lists the lowest score in each solved bucket. A
solved score goes in the last bucket whose floor it reaches. A failure goes in
one more bucket after them, so the histogram has `cardinality(bucket_floors) + 1`
entries. For example, `{1,2,3,5,8,13}` gives 1, 2, 3–4, 5–7, 8–12, 13+ and ✗.

## Adding a game

A game's PR adds, on both platforms and the server together:

1. A migration with its `daily_games` row and its scorer:
   ```sql
   public.score_<game>(puzzle jsonb, guesses text[], hints_used int, graph_version text)
     returns public.daily_score  -- (score int, solved boolean)
   ```
   `guesses` arrive already checked as distinct two-letter codes (an unknown
   code is simply a wrong guess). The scorer checks the game's own rules
   (limits, hints) and raises `22023` for anything malformed. `public.score_daily` already dispatches to it.
2. A generator plugin, `scripts/daily_puzzles/<game>.py` (the contract is in
   the docstring of `scripts/generate_daily_puzzles.py`).
3. Shared vectors, `shared/fixtures/daily/<game>.json`, read by the iOS and
   Android tests and turned into a pgTAP test by the generator.

## Generating puzzles

```bash
python3 scripts/generate_daily_puzzles.py --through 2027-06-30   # --check is what CI runs
```

Each run appends the days after every game's last puzzle as a new
`*_add_daily_puzzles.sql` migration, which deploys like any other. Puzzles are
never changed once written: a merged migration may already be live, CI refuses
edits to it, and a second row for a day fails the deploy. The first day in
`daily_puzzles` is the cutover from the old daily.

Every puzzle is validated: its answers are real countries, each game has a
puzzle every day from its first, and an answer doesn't come back within 60 days
in the same game. Different games may share an answer.
