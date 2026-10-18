#!/usr/bin/env python3
"""Export one Figma page's store screenshots into fastlane's folders.

  FIGMA_TOKEN=... python3 scripts/export_store_screenshots.py 1.12.0 [--platform ios|android|all]

The store-screenshots skill builds each version's sets on a Figma page titled
with the version, one frame per scene named `<set id>/NN-<scene>`. This finds
those frames on any version's page (a release can reuse an earlier version's
screenshots), renders them at 1x through Figma's REST API (so each PNG is the
set's exact store size), flattens them (App Store Connect rejects alpha) and
writes them where deliver and supply read them:

  ios-iphone-6.9, ios-iphone-6.3,
  ios-ipad-13                 -> ios/fastlane/screenshots/en-GB/
  ios-header, ios-search      -> ios/fastlane/creative/en-GB/ (Asset Library, by hand)
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
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
SCENES = ROOT / ".claude/skills/store-screenshots/scenes.json"
FILE_KEY = "CphnOVwvLUEKxSDfplU37i"
IOS_DIR = ROOT / "ios/fastlane/screenshots/en-GB"
# App Store creative assets: deliver can't upload them (only App Store
# Connect's Asset Library can), so they get a folder deliver doesn't read.
CREATIVE_DIR = ROOT / "ios/fastlane/creative/en-GB"
PLAY_DIR = ROOT / "android/fastlane/metadata/android/en-US/images"
# Where each set lands; iOS sets share a folder, deliver tells them apart by size.
OUT = {
    "ios-iphone-6.9": IOS_DIR,
    "ios-iphone-6.3": IOS_DIR,
    "ios-ipad-13": IOS_DIR,
    "ios-header": CREATIVE_DIR,
    "ios-search": CREATIVE_DIR,
    "android-phone": PLAY_DIR / "phoneScreenshots",
    "android-tablet-7": PLAY_DIR / "sevenInchScreenshots",
    "android-tablet-10": PLAY_DIR / "tenInchScreenshots",
}
MAX_PER_SET = {"ios": 10, "android": 8}
# Play rejects screenshots over 8 MB; past that a high-quality JPEG goes instead.
MAX_BYTES = 8 * 1024 * 1024
# A 429 is waited out this many times, for at most this many seconds each.
MAX_RATE_LIMIT_RETRIES = 3
MAX_RATE_LIMIT_WAIT = 300


def figma(path, **params):
    url = f"https://api.figma.com/v1/{path}?{urllib.parse.urlencode(params)}"
    req = urllib.request.Request(url, headers={"X-Figma-Token": os.environ["FIGMA_TOKEN"]})
    for attempt in range(MAX_RATE_LIMIT_RETRIES + 1):
        try:
            with urllib.request.urlopen(req, timeout=120) as r:
                return json.load(r)
        except urllib.error.HTTPError as e:
            if e.code != 429:
                raise
            # Figma says how long to wait; short waits are worth sitting out,
            # long ones (a plan's monthly render budget) are not.
            wait = int(e.headers.get("Retry-After") or 60)
            limit = e.headers.get("X-Figma-Rate-Limit-Type", "unknown")
            if wait > MAX_RATE_LIMIT_WAIT or attempt == MAX_RATE_LIMIT_RETRIES:
                sys.exit(f"Figma rate limit ({limit}) on {path}: retry after {wait}s "
                         f"(~{wait / 3600:.1f} h). Rerun once that has passed.")
            print(f"Figma rate limit ({limit}); retrying {path} in {wait}s", flush=True)
            time.sleep(wait)


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
    ap.add_argument("version", help="the Figma page title: the version whose screenshots to use, e.g. 1.12.0")
    ap.add_argument("--platform", choices=["ios", "android", "all"], default="all")
    args = ap.parse_args()
    if not os.environ.get("FIGMA_TOKEN"):
        sys.exit("FIGMA_TOKEN is not set")

    sets = [s for s in json.loads(SCENES.read_text())["sets"]
            if args.platform in ("all", s["platform"])]
    for s in [s for s in sets if s.get("pending")]:
        print(f"{s['id']}: skipped, {s['pending']}")
    sets = [s for s in sets if not s.get("pending")]

    pages = figma(f"files/{FILE_KEY}", depth=1)["document"]["children"]
    page_id = next((p["id"] for p in pages if p["name"] == args.version), None)
    if not page_id:
        sys.exit(f"No Figma page titled {args.version}: run the store-screenshots skill first")
    page = figma(f"files/{FILE_KEY}/nodes", ids=page_id, depth=3)["nodes"][page_id]["document"]
    frames = frames_on(page)

    for d in {OUT[s["id"]] for s in sets}:
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir(parents=True)

    wanted = []  # (set, frame name, frame) for every frame to render
    for s in sets:
        prefix = f"{s['id']}/"
        mine = sorted((name, f) for name, f in frames.items() if name.startswith(prefix))
        if not mine and s.get("kind") == "creative":
            # Optional, and uploaded by hand: an older page may predate them.
            print(f"{s['id']}: skipped, page {args.version} has none")
            continue
        if not mine:
            sys.exit(f"Page {args.version} has no frames for {s['id']}")
        if s.get("kind") != "creative" and len(mine) > MAX_PER_SET[s["platform"]]:
            sys.exit(f"{s['id']}: {len(mine)} frames, the store takes {MAX_PER_SET[s['platform']]}")
        wanted += [(s, name, f) for name, f in mine]

    # One render request for every frame: Figma's image endpoint is the
    # rate-limited one, and its budget can run out for days.
    urls = figma(f"images/{FILE_KEY}", ids=",".join(f["id"] for _, _, f in wanted),
                 format="png", scale=1)["images"]
    for s, name, f in wanted:
        # 01-globe -> 01-globe.png (Play) or 01-globe-ios-ipad-13.png (iOS,
        # where the sets share a folder and deliver orders by file name).
        stem = name[len(s["id"]) + 1:] + (f"-{s['id']}" if s["platform"] == "ios" else "")
        with urllib.request.urlopen(urls[f["id"]], timeout=120) as r:
            dest = save(r.read(), OUT[s["id"]] / f"{stem}.png", (s["width"], s["height"]))
        print(f"{s['id']}: {dest.relative_to(ROOT)}")

if __name__ == "__main__":
    main()
