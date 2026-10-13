#!/usr/bin/env python3
"""Export one version's store screenshots from Figma into fastlane's folders.

  FIGMA_TOKEN=... python3 scripts/export_store_screenshots.py 1.12.0 [--platform ios|android|all]

The store-screenshots skill builds each version's sets on a Figma page titled
with the version, one frame per scene named `<set id>/NN-<scene>`. This finds
those frames, renders them at 1x through Figma's REST API (so each PNG is the
set's exact store size), flattens them (App Store Connect rejects alpha) and
writes them where deliver and supply read them:

  ios-iphone-6.9, ios-ipad-13 -> ios/fastlane/screenshots/en-US/
  android-phone               -> android/fastlane/metadata/android/en-US/images/phoneScreenshots/
  android-tablet-7            -> .../sevenInchScreenshots/
  android-tablet-10           -> .../tenInchScreenshots/

Those folders are gitignored: the PNGs are rebuilt from Figma on every upload.
Needs Pillow and a Figma personal access token with file_content:read.
"""
import argparse
import io
import json
import os
import shutil
import sys
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SCENES = ROOT / ".claude/skills/store-screenshots/scenes.json"
FILE_KEY = "CphnOVwvLUEKxSDfplU37i"
IOS_DIR = ROOT / "ios/fastlane/screenshots/en-US"
PLAY_DIR = ROOT / "android/fastlane/metadata/android/en-US/images"
# Where each set lands; iOS sets share a folder, deliver tells them apart by size.
OUT = {
    "ios-iphone-6.9": IOS_DIR,
    "ios-ipad-13": IOS_DIR,
    "android-phone": PLAY_DIR / "phoneScreenshots",
    "android-tablet-7": PLAY_DIR / "sevenInchScreenshots",
    "android-tablet-10": PLAY_DIR / "tenInchScreenshots",
}
MAX_PER_SET = {"ios": 10, "android": 8}
# Play rejects screenshots over 8 MB; past that a high-quality JPEG goes instead.
MAX_BYTES = 8 * 1024 * 1024


def figma(path, **params):
    url = f"https://api.figma.com/v1/{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"X-Figma-Token": os.environ["FIGMA_TOKEN"]})
    with urllib.request.urlopen(req, timeout=120) as r:
        return json.load(r)


def frames_on(page):
    """Every FRAME under the page (inside its Sections), by name."""
    found, stack = {}, list(page.get("children", []))
    while stack:
        node = stack.pop()
        if node["type"] == "FRAME":
            found[node["name"]] = node
        else:
            stack.extend(node.get("children", []))
    return found


def save(png_bytes, dest, size):
    img = Image.open(io.BytesIO(png_bytes))
    if img.size != size:
        sys.exit(f"{dest.name}: Figma rendered {img.size}, the store size is {size}")
    img = img.convert("RGB")
    img.save(dest, "PNG", optimize=True)
    if dest.stat().st_size > MAX_BYTES:
        dest.unlink()
        dest = dest.with_suffix(".jpg")
        img.save(dest, "JPEG", quality=92)
    return dest


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("version", help="the Figma page title, e.g. 1.12.0")
    ap.add_argument("--platform", choices=["ios", "android", "all"], default="all")
    args = ap.parse_args()
    if not os.environ.get("FIGMA_TOKEN"):
        sys.exit("FIGMA_TOKEN is not set")

    sets = [s for s in json.loads(SCENES.read_text())["sets"]
            if args.platform in ("all", s["platform"])]

    pages = figma(f"files/{FILE_KEY}", depth=1)["document"]["children"]
    page_id = next((p["id"] for p in pages if p["name"] == args.version), None)
    if not page_id:
        sys.exit(f"No Figma page titled {args.version}: run the store-screenshots skill first")
    page = figma(f"files/{FILE_KEY}/nodes", ids=page_id, depth=3)["nodes"][page_id]["document"]
    frames = frames_on(page)

    for d in {OUT[s["id"]] for s in sets}:
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir(parents=True)

    for s in sets:
        prefix = f"{s['id']}/"
        mine = sorted((name, f) for name, f in frames.items() if name.startswith(prefix))
        if not mine:
            sys.exit(f"Page {args.version} has no frames for {s['id']}")
        if len(mine) > MAX_PER_SET[s["platform"]]:
            sys.exit(f"{s['id']}: {len(mine)} frames, the store takes {MAX_PER_SET[s['platform']]}")
        urls = figma(f"images/{FILE_KEY}", ids=",".join(f["id"] for _, f in mine),
                     format="png", scale=1)["images"]
        for name, f in mine:
            # 01-globe -> 01-globe.png (Play) or 01-globe-ios-ipad-13.png (iOS,
            # where both sets share a folder and deliver orders by file name).
            stem = name[len(prefix):] + (f"-{s['id']}" if s["platform"] == "ios" else "")
            with urllib.request.urlopen(urls[f["id"]], timeout=120) as r:
                dest = save(r.read(), OUT[s["id"]] / f"{stem}.png", (s["width"], s["height"]))
            print(f"{s['id']}: {dest.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
