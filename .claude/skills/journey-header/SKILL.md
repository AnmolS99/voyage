---
name: journey-header
description: >-
  Make the painted Journey header art (day + night) for one country of the
  voyage app from a photo the user sends, with the Gemini script
  scripts/journey_art.py: two day attempts, the user picks one, two nights from
  it, the user picks one, then export, commit, push and add the country to the
  PR. Use whenever the user sends a country (and usually a photo) for Journey
  art, header art or "journey headers", or asks to add, redo or regenerate a
  country's header painting — even if they only write a country name and attach
  an image.
---

# Journey Header

Paint one country's header for the country sheet: a **day** painting (light
mode) and a **night** painting (dark mode) made from the chosen day, so both
share one composition. Everything goes through `scripts/journey_art.py`
(Gemini). CLAUDE.md's "Journey Art" section explains the art, the shared
prompts (`STYLE`, `SKY_BAND`, `MOON`, `KEEP_EVERYTHING`) and the export; this
skill is the loop around it.

**The hard rule: the top third of the image is open sky.** The flag and
country name sit there, from the left edge to about three-quarters across, with
the Done button top-right. Nothing — spire, minaret, peak, tree, moon — may
cross it. Check every attempt against it before the user sees it.

## Before starting

- `GEMINI_API_KEY` must be set (paid tier; the free tier allows no images).
  If it's missing, the user may need `source ~/.zshenv`.
- Work on the branch for the current batch of countries (for example
  `feat/asian-journey-art`), never on `master`. If there is none yet, create
  one with a conventional name and open its PR after the first country (see
  [The PR](#the-pr)).
- Never look for photos yourself: the user supplies them. They may say where a
  photo was taken; if not, identify the landmark and city from the photo and
  say what you found.

## 1. Two days

Look at the photo first and estimate how high the landmark's tallest point
sits. The model keeps a photo's framing over the prompt's sky rule, so if that
point would land in the top third, pass `--shrink`: the photo is set at that
fraction of a 16:9 grey canvas's width, along its bottom, leaving sky above.
Where its top lands, as a fraction of the height from the top:

    top = (1 − h/H) + f · h/H

where `f` is the tallest point's height within the photo (0 = top edge), and
`h/H` is the photo's height on the canvas: `shrink × (16/9) / (photo w/h)`, so
just `shrink` for a 16:9 photo. The tallest point may sit right at the line:
aim for `top` ≈ 0.34–0.36, just under a third, so the landmark stays as large
as it can. A photo whose landmark is already below a third needs no shrink.

```bash
python3 scripts/journey_art.py day AF ~/path/photo.jpg -n 2 --shrink 0.7 \
  --hint "This is the Kart-e Sakhi shrine in Kabul ... the minaret tops stay below the top third."
```

The `--hint` names the place and says what to keep or leave out ("keep the two
minarets", "leave out the people closest to the camera", "paint it in
daylight" for a night photo, "under a clear sky" for an overcast one). Days are
numbered on from what's in `journey-art/<ISO>/`, so a second round makes
day-3 and day-4.

Then build the review sheet, read it, and send it to the user:

```bash
python3 .claude/skills/journey-header/scripts/sheet.py AF day-1 day-2
```

Say which attempt breaks the top third, if any, and anything else off
(cropped landmark, invented towers, lettering). Ask which day to use. If both
fail, fix it before asking (see [When an attempt is wrong](#when-an-attempt-is-wrong)).

## 2. Two nights

```bash
python3 scripts/journey_art.py night AF 2 -n 2          # nights from day-2: night-2-1, night-2-2
python3 .claude/skills/journey-header/scripts/sheet.py AF night-2-1 night-2-2
```

Send the sheet and ask which night. Nights drift from their day, so point out:
- a moon in the top third, or touching the landmark;
- snow on peaks, or towers and buildings that weren't there;
- a zoomed or shifted frame.

The user decides whether a drift matters.

## 3. Export, commit, push

```bash
python3 scripts/journey_art.py export AF 2 1 --place "The Kart-e Sakhi Shrine, Kabul"
```

This writes `shared/data/journey/AF_day.webp` and `AF_night.webp` (1170×720)
and adds the place to `places.json`. `JourneyArtTest` and
`testJourneyArtNamesItsPlace` fail without that entry. The place is short and
specific: the landmark and the city, or what the view shows ("Patan Durbar
Square below the Himalaya").

Commit only those three files, one commit per country:

```bash
git add shared/data/journey/AF_day.webp shared/data/journey/AF_night.webp shared/data/journey/places.json
git commit -m "feat: add journey art for Afghanistan"
git push
```

Redoing a country that's already in: `fix: lower <Country>'s journey art below the title`
or similar.

## The PR

One PR per batch, modelled on AnmolS99/voyage#147, updated after every
country. Its body:

```markdown
Adds Journey header art (day + night) for <region> countries in the app, one commit per country.

| Country | Day | Night | Caption | Happy |
| --- | --- | --- | --- | :---: |
| 🇦🇫 Afghanistan | <img src="https://github.com/AnmolS99/voyage/blob/<SHA>/shared/data/journey/AF_day.webp?raw=true" width="260"> | <img src="https://github.com/AnmolS99/voyage/blob/<SHA>/shared/data/journey/AF_night.webp?raw=true" width="260"> | The Kart-e Sakhi Shrine, Kabul | ✅ |

Each day was painted from a real photo with `scripts/journey_art.py`, ... ; each night was made from the chosen day.

A few needed extra direction: ...

`JourneyArtTest` / `testJourneyArtNamesItsPlace` are covered: every new pair has an entry in `places.json`.

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

- Rows are alphabetical by country name. `<SHA>` is the full SHA of the
  commit that added (or last changed) that country's art, so the image shows
  what that commit holds.
- When a country needed more than the default (a shrink, a cropped photo, a
  hint that changed the scene, a reframe, a second photo), add one clause for
  it to the "A few needed extra direction" sentence.
- Update it by fetching the body (`gh pr view <N> --json body -q .body`),
  inserting the row in order and editing the note, writing it to a file in the
  scratchpad, then `gh pr edit <N> --body-file <file>`.

Then tell the user the country is in, with the commit.

## When an attempt is wrong

- **Landmark in the top third:** run `day` again with a smaller `--shrink`
  and a firmer hint. If an attempt is otherwise the one the user wants, move it
  down instead: `python3 scripts/journey_art.py reframe AF 2 [1] --scale 0.8`
  shrinks the day (and its night, if given) to the bottom and paints new sky
  above it, so the pair still lines up.
- **A small fix** (remove a sign, drop scaffolding):
  `python3 scripts/journey_art.py edit AF day-2 "remove the scaffolding on the left"`.
- **Gemini blocks the request** (`image_safety`): drop whatever in the hint or
  photo may have triggered it. For example, crop a watermark off the photo
  with Pillow instead of asking the model to remove it.
- **Portrait or tall photos:** crop to 16:9 first (the bottom of the photo can
  go), or widen with a hint ("widen it into a landscape") and a small shrink.
- **Gemini 5xx:** the script retries by itself; run it again if it still fails.

The user picks every day and every night: never export an attempt they haven't
chosen.
