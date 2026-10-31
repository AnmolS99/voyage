#!/usr/bin/env python3
"""Turn a real photo of a country into the Journey card's painted header art.

The country sheet's header is a 390 x 240 pt banner (13:8). This script asks a
Gemini image model to repaint a photo the user supplies in the look of an
expert painter's gouache-and-oil landscape — visible brushwork, slightly
imperfect edges, with the mood and palette of `journey-art/style_reference.webp`
(a luminous gradient sky and hazy depth) — once for the day (light mode) and
once for the night (dark mode). The night version is made *from a chosen day
version*, not from the photo, so both share one composition and switching
themes doesn't move the scene.

Everything it writes goes under `journey-art/<ISO>/` (gitignored): the photo
(`source.jpg`), each attempt (`day-N.png`, `night-N-M.png` — night M made from
day N) with the prompt that made it beside it (`.txt`), an `index.html` that
shows every attempt as the banner in both themes. Attempts are numbered on from
what's there, so nothing is overwritten. `export` writes the picked pair to
`shared/data/journey/<ISO>_day.webp` and `<ISO>_night.webp`, where both apps
read them (iOS `CountryExploreView`, Android `CountryDetailSheet`).

Usage:
  python3 scripts/journey_art.py day EG ~/Photos/giza.jpg [-n 3] [--hint "..."]
  python3 scripts/journey_art.py night EG 2 [-n 2] [--hint "..."]
  python3 scripts/journey_art.py export EG 2 1        # day-2 + night-2-1
  python3 scripts/journey_art.py preview EG           # rebuild index.html only

  -n       attempts to make in parallel (default 2)
  --hint   extra direction for this country ("keep the three pyramids", ...)
  --model  Gemini image model (default gemini-nano-banana-2.1;
           gemini-3-pro-image for the premium model)

Needs GEMINI_API_KEY in the environment, and macOS `sips` plus `cwebp`.
"""

from __future__ import annotations

import argparse
import base64
import html
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.error
import urllib.request
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WORK = ROOT / "journey-art"
APP_ART = ROOT / "shared" / "data" / "journey"
STYLE_REFERENCE = WORK / "style_reference.webp"

API_URL = "https://generativelanguage.googleapis.com/v1beta/interactions"
DEFAULT_MODEL = "gemini-nano-banana-2.1"

# The banner is 390 x 240 pt; exported at 3x. Models offer 16:9, which is
# centre-cropped to 13:8 — the same crop the preview's object-fit shows.
BANNER_W, BANNER_H = 390, 240
EXPORT_SCALE = 3
ASPECT_RATIO = "16:9"
IMAGE_SIZE = "2K"
MAX_UPLOAD_PX = 2048

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

COMPOSITION = """\
Composition: a wide 16:9 landscape whose centre 13:8 crop must work on its own. \
Keep the upper-left quarter calm, simple sky — a title is laid over it. Keep the \
landmark recognisable and in the same place as in the photo."""

DAY_PROMPT = f"""\
Repaint the FIRST image (a real photo) as a daytime illustration. Keep its \
subject, landmarks, architecture and layout faithful to the photo; simplify \
clutter (cars, signs, crowds, wires) away. The SECOND image is a STYLE reference \
only: take its mood and palette, never its content.

Light: bright, warm golden-hour daylight with a soft pastel sky.

{STYLE}

{COMPOSITION}"""

NIGHT_PROMPT = f"""\
Turn this illustration into the same scene at night. Keep the composition, \
every shape and the painting style exactly; change only the light: a deep \
indigo-to-violet sky with stars and a soft moon placed in the upper RIGHT \
part of the sky (never the upper-left quarter, where a title is laid over the \
image), cool moonlit rim light on the landmark, and a few warm glowing \
windows or lanterns where they would naturally be. Calm and dreamy, not dark and muddy — the landmark must stay \
clearly readable.

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


def image_part(path: Path) -> dict:
    """An inline image, downscaled to MAX_UPLOAD_PX and re-encoded as JPEG."""
    tmp = path.with_suffix(".upload.jpg")
    subprocess.run(["sips", "-Z", str(MAX_UPLOAD_PX), "-s", "format", "jpeg", str(path), "--out", str(tmp)],
                   check=True, capture_output=True)
    data = base64.b64encode(tmp.read_bytes()).decode()
    tmp.unlink()
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
    try:
        with urllib.request.urlopen(request, timeout=300) as response:
            payload = json.load(response)
    except urllib.error.HTTPError as error:
        die(f"{error.code} from Gemini: {error.read().decode(errors='replace')[:800]}")
    image = find_image(payload)
    if image is None:
        die(f"no image in the response: {json.dumps(payload)[:800]}")
    return image


def run_batch(model: str, prompt: str, images: list[Path], outputs: list[Path]) -> None:
    # Encoded once, before the parallel attempts: they would race on image_part's temp file.
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
        subprocess.run(["sips", "-s", "format", "jpeg", str(Path(args.photo).expanduser()), "--out", str(source)],
                       check=True, capture_output=True)
    elif not source.exists():
        die(f"no photo yet for {args.iso}: pass one")
    first = next_index(folder, r"day-(\d+)\.png")
    outputs = [folder / f"day-{i}.png" for i in range(first, first + args.n)]
    run_batch(args.model, with_hint(DAY_PROMPT, args.hint), [source, STYLE_REFERENCE], outputs)
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


def export_banner(source: Path, out: Path) -> None:
    """Centre-crop to 13:8, scale to the banner at EXPORT_SCALE, encode WebP."""
    width, height = (int(v) for v in re.findall(
        r"pixel(?:Width|Height): (\d+)",
        subprocess.run(["sips", "-g", "pixelWidth", "-g", "pixelHeight", str(source)],
                       check=True, capture_output=True, text=True).stdout))
    crop_w = min(width, round(height * BANNER_W / BANNER_H))
    crop_h = round(crop_w * BANNER_H / BANNER_W)
    png = WORK / f"{out.stem}.tmp.png"
    # Two passes: sips applies -z before --cropToHeightWidth within one call.
    subprocess.run(["sips", "--cropToHeightWidth", str(crop_h), str(crop_w), str(source), "--out", str(png)],
                   check=True, capture_output=True)
    subprocess.run(["sips", "-z", str(BANNER_H * EXPORT_SCALE), str(BANNER_W * EXPORT_SCALE), str(png)],
                   check=True, capture_output=True)
    subprocess.run(["cwebp", "-quiet", "-q", "82", str(png), "-o", str(out)], check=True)
    png.unlink()
    print(f"  wrote {out.relative_to(ROOT)} ({out.stat().st_size // 1024} KB)")


def cmd_export(args) -> None:
    folder = country_dir(args.iso)
    day = folder / f"day-{args.day}.png"
    night = folder / f"night-{args.day}-{args.night}.png"
    for path in (day, night):
        if not path.exists():
            die(f"no {path.relative_to(ROOT)}")
    APP_ART.mkdir(exist_ok=True)
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
        else:
            command.add_argument("day", type=int, help="which day-N to make the night from")
        command.add_argument("-n", type=int, default=2)
        command.add_argument("--hint")
        command.add_argument("--model", default=DEFAULT_MODEL)

    export = add("export", cmd_export)
    export.add_argument("day", type=int)
    export.add_argument("night", type=int)
    add("preview", lambda args: write_preview(args.iso))

    args = parser.parse_args()
    if shutil.which("cwebp") is None and args.command == "export":
        die("cwebp not found (brew install webp)")
    args.handler(args)


if __name__ == "__main__":
    main()
