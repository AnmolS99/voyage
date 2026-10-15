#!/usr/bin/env python3
"""Generate shared/data/textures/relief.png, the globe's shaded-relief layer.

The source is Natural Earth's 1:10m "Shaded Relief" raster (SR_LR, public
domain): a grayscale hillshade lit from the northwest, in which flat ground and
the whole ocean are one gray (206). This script re-centres that so flat ground
is exactly neutral (128) and resamples it to an equirectangular 4096 x 2048,
top-left at (180°W, 90°N) — the layout of the Earth textures beside it.

Neutral 128 is what the renderers' blend expects: both apply the relief as a
*hard light* over whatever is painted underneath (a country's palette color or
the Earth texture). Hard light leaves a color unchanged at 0.5, darkens it
proportionally below and lightens it toward white above, so slopes facing away
from the light shade, slopes facing it catch light, and flat land and the ocean
keep their palette colors to within one 8-bit step.

Usage:  python3 scripts/generate_relief_texture.py [--source SR_LR.tif] [--contrast K]

  --source    a local SR_LR.tif; downloaded from naciscdn.org when omitted
  --contrast  multiplier on the hillshade's deviation from flat (default 1.0)
"""

from __future__ import annotations

import argparse
import io
import tempfile
import urllib.request
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

REPO_ROOT = Path(__file__).resolve().parents[1]
OUTPUT = REPO_ROOT / "shared/data/textures/relief.png"
SOURCE_URL = "https://naciscdn.org/naturalearth/10m/raster/SR_LR.zip"

# The gray Natural Earth paints flat ground and the ocean with.
SOURCE_FLAT = 206
NEUTRAL = 128
WIDTH, HEIGHT = 4096, 2048


def load_source(path: Path | None) -> np.ndarray:
    Image.MAX_IMAGE_PIXELS = None
    if path is None:
        with tempfile.TemporaryDirectory() as tmp:
            print(f"downloading {SOURCE_URL}")
            data = urllib.request.urlopen(SOURCE_URL).read()
            zipfile.ZipFile(io.BytesIO(data)).extract("SR_LR.tif", tmp)
            return np.asarray(Image.open(Path(tmp) / "SR_LR.tif").convert("L"), dtype=np.float32)
    return np.asarray(Image.open(path).convert("L"), dtype=np.float32)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", type=Path)
    parser.add_argument("--contrast", type=float, default=1.0)
    args = parser.parse_args()

    shade = load_source(args.source)
    flat = np.median(shade)
    if flat != SOURCE_FLAT:
        raise SystemExit(f"expected the source's flat gray to be {SOURCE_FLAT}, found {flat}")

    relief = NEUTRAL + args.contrast * (shade - SOURCE_FLAT)
    # Area-averaged (BOX) rather than Lanczos: no ringing, so open ocean stays
    # exactly neutral right up to the coast.
    resized = Image.fromarray(relief, mode="F").resize((WIDTH, HEIGHT), Image.Resampling.BOX)
    out = np.clip(np.rint(np.asarray(resized)), 0, 255).astype(np.uint8)

    Image.fromarray(out, mode="L").save(OUTPUT, optimize=True)
    print(f"wrote {OUTPUT.relative_to(REPO_ROOT)} ({OUTPUT.stat().st_size / 1e6:.1f} MB)")


if __name__ == "__main__":
    main()
