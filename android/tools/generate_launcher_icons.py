#!/usr/bin/env python3
"""Generate Android launcher icon assets from the iOS app icon artwork.

The iOS icon (`ios/voyage/Assets.xcassets/AppIcon.appiconset/voyage_icon.png`) is
a 1024x1024 globe on a solid orange field. Android needs:

  * `mipmap-*/ic_launcher_foreground.png` — the globe alone, transparent
    elsewhere, sized into the adaptive-icon safe zone. Also used as the Splash
    Screen API icon.
  * `mipmap-*/ic_launcher_monochrome.png` — the themed-icon layer: land masses
    and the globe's rim as an alpha mask the system tints.

The orange background comes from `@color/voyage_orange`, so it is not baked into
the foreground layer. minSdk is 26, so every device supports adaptive icons and
no legacy raster launcher icons are generated.

Run from anywhere:  python3 android/tools/generate_launcher_icons.py
"""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw

REPO_ROOT = Path(__file__).resolve().parents[2]
SOURCE = REPO_ROOT / "ios/voyage/Assets.xcassets/AppIcon.appiconset/voyage_icon.png"
RES_DIR = REPO_ROOT / "android/app/src/main/res"

# Adaptive icon layers are 108dp, of which a 72dp circle is visible after
# masking. 0.53 puts the globe at ~80% of that visible circle — the same
# globe-to-icon proportion as the iOS artwork.
LAYER_SIZES = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
GLOBE_FRACTION = 0.53


def globe_cutout(source: Image.Image) -> Image.Image:
    """Return the globe as an RGBA image, cropped square with the field removed."""
    rgb = source.convert("RGB")
    field = rgb.getpixel((0, 0))  # solid orange corner

    # Bounding box of everything that isn't the orange field.
    mask = Image.new("L", rgb.size, 0)
    mask_px = mask.load()
    rgb_px = rgb.load()
    for y in range(rgb.height):
        for x in range(rgb.width):
            r, g, b = rgb_px[x, y]
            if abs(r - field[0]) + abs(g - field[1]) + abs(b - field[2]) > 24:
                mask_px[x, y] = 255
    box = mask.getbbox()
    if box is None:
        raise SystemExit(f"no globe found in {SOURCE}")

    # The globe is a circle: square up the bbox around its center, then mask it
    # so the orange field's anti-aliased fringe doesn't survive the crop.
    left, top, right, bottom = box
    cx, cy = (left + right) / 2, (top + bottom) / 2
    radius = max(right - left, bottom - top) / 2
    size = int(round(radius * 2))
    crop = rgb.crop(
        (
            int(round(cx - radius)),
            int(round(cy - radius)),
            int(round(cx - radius)) + size,
            int(round(cy - radius)) + size,
        )
    ).convert("RGBA")

    circle = Image.new("L", (size, size), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, size - 1, size - 1), fill=255)
    crop.putalpha(circle)
    return crop


def monochrome_mask(globe: Image.Image) -> Image.Image:
    """Land masses plus the globe's rim, as a white-on-transparent alpha mask."""
    size = globe.width
    rgb = globe.convert("RGB")
    rgb_px = rgb.load()
    globe_alpha = globe.getchannel("A").load()

    mask = Image.new("L", (size, size), 0)
    mask_px = mask.load()
    for y in range(size):
        for x in range(size):
            if globe_alpha[x, y] < 128:
                continue
            r, g, b = rgb_px[x, y]
            # Everything that isn't ocean (blue-dominant) is land or a border.
            if not (b > g and b > r):
                mask_px[x, y] = 255

    # Rim, so the silhouette still reads as a globe rather than loose blobs.
    rim = max(2, int(round(size * 0.05)))
    ImageDraw.Draw(mask).ellipse(
        (rim // 2, rim // 2, size - 1 - rim // 2, size - 1 - rim // 2),
        outline=255,
        width=rim,
    )

    layer = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    layer.putalpha(mask)
    return layer


def write_layer(image: Image.Image, name: str) -> None:
    for density, layer in LAYER_SIZES.items():
        diameter = int(round(layer * GLOBE_FRACTION))
        canvas = Image.new("RGBA", (layer, layer), (0, 0, 0, 0))
        scaled = image.resize((diameter, diameter), Image.LANCZOS)
        offset = (layer - diameter) // 2
        canvas.paste(scaled, (offset, offset), scaled)
        out = RES_DIR / f"mipmap-{density}" / f"{name}.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        canvas.save(out)
        print(f"wrote {out.relative_to(REPO_ROOT)}")


def main() -> None:
    globe = globe_cutout(Image.open(SOURCE))
    write_layer(globe, "ic_launcher_foreground")
    write_layer(monochrome_mask(globe), "ic_launcher_monochrome")


if __name__ == "__main__":
    main()
