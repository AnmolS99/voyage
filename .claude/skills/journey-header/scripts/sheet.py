#!/usr/bin/env python3
"""Stack Journey art attempts into one review sheet, marking where the title goes.

Each attempt is drawn as the app's banner sees it, with a red line at a third
of its height: nothing (spire, peak, tree) may cross that line, since the
flag and country name sit above it. A moon may. Grey bars dim the sides the export's
centre 13:8 crop trims away, so whatever is under them doesn't matter.

Usage:
  python3 .claude/skills/journey-header/scripts/sheet.py AF day-4 day-5
  python3 .claude/skills/journey-header/scripts/sheet.py AF night-4-1 night-4-2

Writes journey-art/<ISO>/sheet-<attempts>.png and prints its path.
"""

import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[4]
WIDTH = 1000
HEIGHT = round(WIDTH * 9 / 16)
GAP = 10
# The export keeps the centre 13:8 of the 16:9 attempt.
CROP = round((WIDTH - HEIGHT * 13 / 8) / 2)


def main() -> None:
    if len(sys.argv) < 3:
        sys.exit(__doc__)
    iso, attempts = sys.argv[1].upper(), sys.argv[2:]
    folder = ROOT / "journey-art" / iso
    sheet = Image.new("RGB", (WIDTH, len(attempts) * (HEIGHT + GAP) - GAP), "white")
    for i, name in enumerate(attempts):
        path = folder / f"{name}.png"
        if not path.exists():
            sys.exit(f"sheet: no {path.relative_to(ROOT)}")
        image = Image.open(path).convert("RGB").resize((WIDTH, HEIGHT))
        shade = Image.new("RGBA", image.size)
        draw = ImageDraw.Draw(shade)
        draw.rectangle([(0, 0), (CROP, HEIGHT)], fill=(0, 0, 0, 90))
        draw.rectangle([(WIDTH - CROP, 0), (WIDTH, HEIGHT)], fill=(0, 0, 0, 90))
        draw.line([(0, HEIGHT // 3), (WIDTH, HEIGHT // 3)], fill="red", width=3)
        draw.rectangle([(CROP, HEIGHT - 34), (CROP + 150, HEIGHT)], fill=(0, 0, 0, 160))
        draw.text((CROP + 10, HEIGHT - 26), name, fill="white", font_size=20)
        image.paste(shade, mask=shade)
        sheet.paste(image, (0, i * (HEIGHT + GAP)))
    out = folder / f"sheet-{'-'.join(attempts)}.png"
    sheet.save(out)
    print(out)


if __name__ == "__main__":
    main()
