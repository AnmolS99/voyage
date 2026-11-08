#!/usr/bin/env python3
"""Turn a real photo of a country into the Journey card's painted header art.

The country sheet's header is a 390 x 240 pt banner (13:8). This script asks a
Gemini image model to repaint a photo the user supplies in the look of an
expert painter's gouache-and-oil landscape — visible brushwork, slightly
imperfect edges, with the mood and palette of Norway's day art
(`shared/data/journey/NO_day.webp`: a luminous gradient sky and hazy depth) —
once for the day (light mode) and once for the night (dark mode). The night version is made *from a chosen day
version*, not from the photo, so both share one composition and switching
themes doesn't move the scene.

Everything it writes goes under `journey-art/<ISO>/` (gitignored): the photo
(`source.jpg`), each attempt (`day-N.png`, `night-N-M.png` — night M made from
day N) with the prompt that made it beside it (`.txt`), an `index.html` that
shows every attempt as the banner in both themes. Attempts are numbered on from
what's there, so nothing is overwritten. `export` writes the picked pair to
`shared/data/journey/<ISO>_day.webp` and `<ISO>_night.webp`, where both apps
read them (iOS `CountryExploreView`, Android `CountryDetailSheet`), and names
the place the painting shows in `places.json` beside them — the apps show it
behind a small pin on the art.

Usage:
  python3 scripts/journey_art.py day EG ~/Photos/giza.jpg [-n 2] [--hint "..."] [--shrink 0.66]
  python3 scripts/journey_art.py night EG 2 [-n 2] [--hint "..."]
  python3 scripts/journey_art.py export EG 2 1 --place "Pyramids of Giza"  # day-2 + night-2-1
  python3 scripts/journey_art.py edit EG day-2 "add a felucca on the Nile" [-n 2]  # small change, same picture
  python3 scripts/journey_art.py reframe EG 2 [1] [--scale 0.8]  # day-2 (+ night-2-1), lower in the frame
  python3 scripts/journey_art.py preview EG           # rebuild index.html only

  -n       attempts to make in parallel (default 2)
  --hint   extra direction for this country ("keep the three pyramids", ...)
  --scale  reframe: how much of the frame the painting keeps (default 0.78)
  --shrink day: paint from the photo shrunk to this fraction of a 16:9 grey
           canvas, along its bottom — for a photo whose landmark sits near
           the top edge, which the prompt alone does not move down
  --place  what the painting shows (export; needed the first time)
  --model  Gemini image model (default gemini-nano-banana-2.1;
           gemini-3-pro-image for the premium model)

Needs GEMINI_API_KEY in the environment and Pillow (`pip install pillow`),
so it runs on macOS and Linux alike — a cloud session included. iPhone HEIC
photos also need `pip install pillow-heif`.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import os
import re
import io
import sys
import time
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

try:
    from PIL import Image, ImageOps
except ImportError:
    sys.exit("journey_art: needs Pillow (pip install pillow)")
try:
    from pillow_heif import register_heif_opener  # iPhone photos are HEIC
    register_heif_opener()
except ImportError:
    pass

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / "journey-art"
APP_ART = ROOT / "shared" / "data" / "journey"
# Norway's own day art, so the reference ships with the repo and new countries
# match the set already painted.
STYLE_REFERENCE = APP_ART / "NO_day.webp"
PLACES = APP_ART / "places.json"

API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
DEFAULT_MODEL = "gemini-nano-banana-2.1"

# The banner is 390 x 240 pt; exported at 3x. Models offer 16:9, which is
# centre-cropped to 13:8 — the same crop the preview's object-fit shows.
BANNER_W, BANNER_H = 390, 240
EXPORT_SCALE = 3
ASPECT_RATIO = "16:9"
IMAGE_SIZE = "2K"
MAX_UPLOAD_PX = 2048
RETRIES = 3

STYLE = """\
Rendering style: a hand-painted landscape by an expert painter in gouache and \
oil — not anime, not cel shading, not digital airbrush. Visible, confident \
brushstrokes that vary in size; edges that are sometimes crisp and sometimes \
lost; slightly uneven colour and a little canvas or paper texture showing \
through; foliage and blossoms suggested with dabs of paint rather than drawn \
leaf by leaf; small imperfections a human hand leaves. From the style reference \
take only the mood and palette: a luminous gradient sky, rich harmonious colour \
and atmospheric depth with distant layers fading into haze. A few small \
drifting particles that fit the place (petals, leaves, dust or sand). No \
photographic detail, no outlines around everything, no text, no lettering, no \
watermark, no frame, no people in close-up."""

# Where the banner's overlays sit, said once and given to every prompt: the flag
# and name run from the left edge to about 3/4 across (a long name like "United
# Kingdom" reaches that far) and the sheet's Done button sits top-right. Every
# country so far had to be reframed or edited until its landmark, trees or moon
# started below the top quarter.
SKY_BAND = """\
The top quarter of the image, edge to edge, is open, calm sky: a country's flag \
and name are laid across it from the left edge to about three-quarters of the \
way across, and a round button sits in its top-right corner. Nothing reaches \
into that band — no peak, tower, spire, roof, gable, tree canopy, moon or other \
eye-catching shape. This is a hard rule, not a preference: the tallest point of \
the scene (the tip of the highest spire, tower, peak or tree) sits BELOW 30% of \
the image height, measured from the top, so the top quarter stays clear with a \
margin. If the landmark would reach higher, pull the view back so the whole \
scene sits lower and smaller — never crop the landmark to make room. Keep the \
band soft and low in contrast so text reads over it."""

COMPOSITION = f"""\
Composition: a wide 16:9 landscape whose centre 13:8 crop must work on its own. \
{SKY_BAND} If the photo's landmark rises higher than that, set the whole scene \
lower and smaller in the frame and paint more sky above it — never crop the \
landmark. Keep it recognisable and in the same arrangement as in the photo."""

# Night, edit and reframe each repaint the whole picture, and have dropped a
# taxi and pigeons along the way.
KEEP_EVERYTHING = """\
Keep every element of the picture where it is — buildings, landmarks, trees, \
vehicles, boats, people and animals — none removed, moved or resized."""

DAY_PROMPT = f"""\
Repaint the FIRST image (a real photo) as a daytime illustration. Keep its \
subject, landmarks, architecture and layout faithful to the photo; simplify \
clutter (cars, signs, crowds, wires) away. The SECOND image is a STYLE reference \
only: take its mood and palette, never its content.

Light: bright, warm golden-hour daylight with a soft pastel sky.

{STYLE}

{COMPOSITION}"""

# The moon every night painting shares, modelled on Hungary's
# (`shared/data/journey/HU_night.webp`): silver, low and right of centre, in
# clear sky apart from the landmark. Attempts that tinted it gold or set it
# beside a spire read as part of the building and were turned down.
MOON = """\
The moon is small and silver — pale silver-white with a faint soft halo, \
never golden or orange — set LOW in clear open sky: just above the skyline, \
right of centre, about a third of the way down the image, never in the top \
quarter. Give it room: it never overlaps, touches or sits right beside the \
landmark, a spire, tower or tree — it floats in empty sky off to the side, a \
quiet accent rather than a focal point."""

NIGHT_PROMPT = f"""\
Turn this illustration into the same scene at night. Keep the composition, \
every shape and the painting style exactly; change only the light: a deep \
indigo-to-violet sky with faint stars, cool moonlit rim light on the landmark, \
warm glowing windows, lamps and lanterns where they would naturally be, and \
lit headlights on any vehicles. Calm and dreamy, not dark and muddy — the \
landmark must stay clearly readable.

{MOON}

{KEEP_EVERYTHING}

{SKY_BAND}

{STYLE}"""


EDIT_PROMPT = f"""\
Keep this illustration exactly as it is — same composition, every shape, the \
light and the painting style — and exactly the same framing: the output has the \
same field of view as the input, edge for edge, with no zoom, crop or shift. Make only the change described below, painted in \
the same brushwork so it looks as if it was always there; change nothing else.

{KEEP_EVERYTHING} (Unless the change itself says otherwise.)

{SKY_BAND}

{STYLE}

Change: {{change}}"""

REFRAME_PAD_COLOR = "808080"

SHRINK_CANVAS = (1920, 1080)

SHRUNK_PHOTO = """\
The photo has been shrunk and set at the bottom of a flat grey frame; the grey \
is empty canvas, not part of the scene. Paint the grey above as open sky and \
carry the scenery on naturally at the sides. Keep the scene at exactly the \
size and position it has in the frame — do not enlarge it or move it up."""

REFRAME_PROMPT = f"""\
This painting has been shrunk and set at the bottom of the frame; the flat grey \
area above and beside it is empty canvas. Paint the grey area so the picture \
continues seamlessly: mostly more of the same sky above, and the scenery at the \
sides carried on naturally. Leave the existing painting itself exactly as it is \
— every shape, colour and light — and match its brushwork, so no seam or border \
shows. Paint nothing eye-catching in the new sky: no moon, sun or bold clouds.

{KEEP_EVERYTHING}

{STYLE}"""


def die(message: str) -> None:
    sys.exit(f"journey_art: {message}")


def country_dir(iso: str) -> Path:
    if not re.fullmatch(r"[A-Z]{2}", iso):
        die(f"expected an ISO 3166-1 alpha-2 code like EG, got {iso!r}")
    return WORK / iso


def next_index(folder: Path, pattern: str) -> int:
    taken = [int(m.group(1)) for p in folder.glob("*.png") if (m := re.fullmatch(pattern, p.name))]
    return max(taken, default=0) + 1


def open_rgb(path: Path) -> Image.Image:
    """The image upright (camera EXIF rotation applied) and in RGB, ready to save as JPEG."""
    with Image.open(path) as image:
        return ImageOps.exif_transpose(image).convert("RGB")


def image_part(path: Path) -> dict:
    """An inline image, downscaled to MAX_UPLOAD_PX and re-encoded as JPEG."""
    image = open_rgb(path)
    image.thumbnail((MAX_UPLOAD_PX, MAX_UPLOAD_PX), Image.LANCZOS)
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=90)
    data = base64.b64encode(buffer.getvalue()).decode()
    return {"type": "image", "mime_type": "image/jpeg", "data": data}


def find_image(node) -> bytes | None:
    """The first inline image anywhere in the response, whatever its nesting."""
    if isinstance(node, dict):
        mime = node.get("mime_type") or node.get("mimeType") or ""
        if isinstance(node.get("data"), str) and (node.get("type") == "image" or mime.startswith("image/")):
            return base64.b64decode(node["data"])
        node = list(node.values())
    if isinstance(node, list):
        for child in node:
            if (found := find_image(child)) is not None:
                return found
    return None


def generate(model: str, prompt: str, image_parts: list[dict]) -> bytes:
    key = os.environ.get("GEMINI_API_KEY") or die("GEMINI_API_KEY is not set")
    body = {
        "model": model,
        "input": [{"type": "text", "text": prompt}, *image_parts],
        "response_format": {"type": "image", "aspect_ratio": ASPECT_RATIO, "image_size": IMAGE_SIZE},
    }
    request = urllib.request.Request(
        API_URL, data=json.dumps(body).encode(), method="POST",
        headers={"x-goog-api-key": key, "Content-Type": "application/json"},
    )
    # Gemini often answers one request of a parallel batch with a 504 deadline
    # error; the same request a little later goes through.
    for attempt in range(1, RETRIES + 1):
        try:
            with urllib.request.urlopen(request, timeout=300) as response:
                payload = json.load(response)
            break
        except urllib.error.HTTPError as error:
            detail = f"{error.code} from Gemini: {error.read().decode(errors='replace')[:800]}"
            if error.code < 500 or attempt == RETRIES:
                die(detail)
        except (TimeoutError, urllib.error.URLError) as error:
            detail = f"no answer from Gemini: {error}"
            if attempt == RETRIES:
                die(detail)
        print(f"  {detail.splitlines()[0][:120]} — retrying ({attempt}/{RETRIES - 1})")
        time.sleep(10 * attempt)
    image = find_image(payload)
    if image is None:
        die(f"no image in the response: {json.dumps(payload)[:800]}")
    return image


def run_batch(model: str, prompt: str, images: list[Path], outputs: list[Path]) -> None:
    # Encoded once, not per parallel attempt.
    image_parts = [image_part(p) for p in images]

    def one(out: Path) -> None:
        out.write_bytes(generate(model, prompt, image_parts))
        out.with_suffix(".txt").write_text(f"model: {model}\ninputs: {[p.name for p in images]}\n\n{prompt}\n")
        print(f"  wrote {out.relative_to(ROOT)}")

    with ThreadPoolExecutor(max_workers=len(outputs)) as pool:
        list(pool.map(one, outputs))


def with_hint(prompt: str, hint: str | None) -> str:
    return f"{prompt}\n\nFor this place specifically: {hint}" if hint else prompt


def cmd_day(args) -> None:
    folder = country_dir(args.iso)
    folder.mkdir(parents=True, exist_ok=True)
    if not STYLE_REFERENCE.exists():
        die(f"missing {STYLE_REFERENCE.relative_to(ROOT)}")
    source = folder / "source.jpg"
    if args.photo:
        if not Path(args.photo).expanduser().is_file():
            die(f"no such photo: {args.photo}")
        open_rgb(Path(args.photo).expanduser()).save(source, "JPEG", quality=95)
    elif not source.exists():
        die(f"no photo yet for {args.iso}: pass one")
    first = next_index(folder, r"day-(\d+)\.png")
    outputs = [folder / f"day-{i}.png" for i in range(first, first + args.n)]
    prompt, photo = DAY_PROMPT, source
    if args.shrink:
        photo = folder / "source-shrunk.jpg"
        shrink_onto_canvas(source, photo, args.shrink)
        prompt = f"{DAY_PROMPT}\n\n{SHRUNK_PHOTO}"
    run_batch(args.model, with_hint(prompt, args.hint), [photo, STYLE_REFERENCE], outputs)
    write_preview(args.iso)


def cmd_night(args) -> None:
    folder = country_dir(args.iso)
    day = folder / f"day-{args.day}.png"
    if not day.exists():
        die(f"no {day.relative_to(ROOT)}")
    first = next_index(folder, rf"night-{args.day}-(\d+)\.png")
    outputs = [folder / f"night-{args.day}-{i}.png" for i in range(first, first + args.n)]
    run_batch(args.model, with_hint(NIGHT_PROMPT, args.hint), [day], outputs)
    write_preview(args.iso)


def shrink_onto_canvas(source: Path, out: Path, scale: float) -> None:
    """The photo, `scale` of a 16:9 grey canvas's width, centred along its bottom edge."""
    photo = open_rgb(source)
    width, height = SHRINK_CANVAS
    small = photo.resize(
        (round(width * scale), round(width * scale * photo.height / photo.width)), Image.LANCZOS
    )
    canvas = Image.new("RGB", (width, height), f"#{REFRAME_PAD_COLOR}")
    canvas.paste(small, ((width - small.width) // 2, max(0, height - small.height)))
    canvas.save(out, "JPEG", quality=95)


def shrink_to_bottom(source: Path, out: Path, scale: float) -> None:
    """The painting scaled by `scale`, centred along the bottom edge of a grey frame its original size."""
    painting = open_rgb(source)
    width, height = painting.size
    small = painting.resize((round(width * scale), round(height * scale)), Image.LANCZOS)
    frame = Image.new("RGB", (width, height), f"#{REFRAME_PAD_COLOR}")
    frame.paste(small, ((width - small.width) // 2, height - small.height))
    frame.save(out)


def cmd_reframe(args) -> None:
    """Moves a chosen pair lower in the frame, painting in more sky above, for when the title covers the landmark.

    Day and night are shrunk to the same place and filled in separately, so the
    painting itself stays as picked and the two still line up. Written as a new
    day-N and its night-N-1, ready for `export ISO N 1`. Without a night, only
    the day is reframed, ready for `night ISO N`.
    """
    folder = country_dir(args.iso)
    pairs = [(folder / f"day-{args.day}.png", "day-{n}")]
    if args.night is not None:
        pairs.append((folder / f"night-{args.day}-{args.night}.png", "night-{n}-1"))
    for path, _ in pairs:
        if not path.exists():
            die(f"no {path.relative_to(ROOT)}")
    n = next_index(folder, r"day-(\d+)\.png")
    for source, out in ((source, folder / f"{name.format(n=n)}.png") for source, name in pairs):
        canvas = folder / f"{out.stem}.canvas.png"
        shrink_to_bottom(source, canvas, args.scale)
        run_batch(args.model, REFRAME_PROMPT, [canvas], [out])
        canvas.unlink()
    write_preview(args.iso)


def cmd_edit(args) -> None:
    """A small change to one attempt, written as the next attempt in its series.

    An edited day-N becomes a new day-M (so its nights are made from the edit);
    an edited night-N-M becomes another night of day-N.
    """
    folder = country_dir(args.iso)
    source = folder / f"{args.attempt}.png"
    if not source.exists():
        die(f"no {source.relative_to(ROOT)}")
    if m := re.fullmatch(r"day-\d+", args.attempt):
        first = next_index(folder, r"day-(\d+)\.png")
        outputs = [folder / f"day-{i}.png" for i in range(first, first + args.n)]
    elif m := re.fullmatch(r"night-(\d+)-\d+", args.attempt):
        first = next_index(folder, rf"night-{m.group(1)}-(\d+)\.png")
        outputs = [folder / f"night-{m.group(1)}-{i}.png" for i in range(first, first + args.n)]
    else:
        die(f"expected an attempt like day-3 or night-2-4, got {args.attempt!r}")
    run_batch(args.model, EDIT_PROMPT.format(change=args.change), [source], outputs)
    write_preview(args.iso)


def export_banner(source: Path, out: Path) -> None:
    """Centre-crop to 13:8, scale to the banner at EXPORT_SCALE, encode WebP."""
    image = open_rgb(source)
    width, height = image.size
    crop_w = min(width, round(height * BANNER_W / BANNER_H))
    crop_h = round(crop_w * BANNER_H / BANNER_W)
    left, top = (width - crop_w) // 2, (height - crop_h) // 2
    banner = image.resize((BANNER_W * EXPORT_SCALE, BANNER_H * EXPORT_SCALE), Image.LANCZOS,
                          box=(left, top, left + crop_w, top + crop_h))
    banner.save(out, "WEBP", quality=82, method=4)
    print(f"  wrote {out.relative_to(ROOT)} ({out.stat().st_size // 1024} KB)")


def cmd_export(args) -> None:
    folder = country_dir(args.iso)
    day = folder / f"day-{args.day}.png"
    night = folder / f"night-{args.day}-{args.night}.png"
    for path in (day, night):
        if not path.exists():
            die(f"no {path.relative_to(ROOT)}")
    places = json.loads(PLACES.read_text()) if PLACES.exists() else {}
    if args.place:
        places[args.iso] = args.place.strip()
    elif args.iso not in places:
        die('name the place the painting shows: --place "..."')
    APP_ART.mkdir(exist_ok=True)
    PLACES.write_text(json.dumps(dict(sorted(places.items())), indent=2, ensure_ascii=False) + "\n")
    export_banner(day, APP_ART / f"{args.iso}_day.webp")
    export_banner(night, APP_ART / f"{args.iso}_night.webp")


def write_preview(iso: str) -> None:
    """index.html: every attempt as the banner, light theme over day and dark over night."""
    folder = country_dir(iso)
    days = sorted(folder.glob("day-*.png"), key=lambda p: int(p.stem.split("-")[1]))

    def banner(image: Path, theme: str) -> str:
        return (f'<figure class="banner {theme}"><img src="{image.name}" alt="">'
                f'<span class="title">{iso}</span><figcaption>{image.stem}</figcaption></figure>')

    rows = []
    for day in days:
        n = day.stem.split("-")[1]
        nights = sorted(folder.glob(f"night-{n}-*.png"), key=lambda p: int(p.stem.split("-")[2]))
        cells = [banner(day, "light")] + [banner(night, "dark") for night in nights]
        rows.append(f'<section><div class="row">{"".join(cells)}</div></section>')

    (folder / "index.html").write_text(f"""<!doctype html>
<meta charset="utf-8"><title>Journey art · {html.escape(iso)}</title>
<style>
body {{ margin: 24px; background: #1b1b22; color: #ddd; font: 13px system-ui; }}
.row {{ display: flex; gap: 16px; flex-wrap: wrap; margin-bottom: 28px; }}
.banner {{ margin: 0; width: {BANNER_W}px; padding-bottom: 28px; position: relative; border-radius: 16px; overflow: hidden; }}
.banner.light {{ background: #f5f2ed; }} .banner.dark {{ background: #262633; }}
.banner img {{ display: block; width: {BANNER_W}px; height: {BANNER_H}px; object-fit: cover; }}
.title {{ position: absolute; left: 20px; top: 18px; font: 500 44px/1 Georgia, serif; letter-spacing: -0.8px; }}
.light .title {{ color: #33261a; }} .dark .title {{ color: #f5f2ed; }}
figcaption {{ position: absolute; left: 12px; bottom: 6px; }}
.light figcaption {{ color: #786b5e; }}
</style>
<h1>{html.escape(iso)} — day (light) then its nights (dark)</h1>
{"".join(rows) or "<p>No attempts yet.</p>"}
""")
    print(f"  preview {(folder / 'index.html').relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)

    def add(name: str, handler) -> argparse.ArgumentParser:
        command = sub.add_parser(name)
        command.add_argument("iso", type=str.upper)
        command.set_defaults(handler=handler)
        return command

    for name, handler in (("day", cmd_day), ("night", cmd_night)):
        command = add(name, handler)
        if name == "day":
            command.add_argument("photo", nargs="?", help="the photo; reuses source.jpg when omitted")
            command.add_argument("--shrink", type=float, help="paint from the photo shrunk onto a taller canvas, e.g. 0.66")
        else:
            command.add_argument("day", type=int, help="which day-N to make the night from")
        command.add_argument("-n", type=int, default=2)
        command.add_argument("--hint")
        command.add_argument("--model", default=DEFAULT_MODEL)

    export = add("export", cmd_export)
    export.add_argument("day", type=int)
    export.add_argument("night", type=int)
    export.add_argument("--place")
    edit = add("edit", cmd_edit)
    edit.add_argument("attempt", help="day-N or night-N-M")
    edit.add_argument("change")
    edit.add_argument("-n", type=int, default=2)
    edit.add_argument("--model", default=DEFAULT_MODEL)
    reframe = add("reframe", cmd_reframe)
    reframe.add_argument("day", type=int)
    reframe.add_argument("night", type=int, nargs="?", help="omit to reframe the day alone")
    reframe.add_argument("--scale", type=float, default=0.78)
    reframe.add_argument("--model", default=DEFAULT_MODEL)
    add("preview", lambda args: write_preview(args.iso))

    args = parser.parse_args()
    if args.command == "export" and not Image.registered_extensions().get(".webp"):
        die("this Pillow was built without WebP support")
    args.handler(args)


if __name__ == "__main__":
    main()
