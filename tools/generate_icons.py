#!/usr/bin/env python3
"""Generates the legacy (API < 26) AMS Keyboard launcher icon rasters.

Android 8.0+ (API 26) uses the adaptive icon in ``mipmap-anydpi-v26`` built from
``drawable/ic_launcher_background.xml`` and ``drawable/ic_launcher_foreground.xml``.
Older devices fall back to the PNGs produced here. Every generated image is fully
opaque: no transparency anywhere, so the launcher can never show a blank icon.

Requires Pillow (``pip install Pillow``) and a bold TTF. Run from the repo root:

    python3 tools/generate_icons.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app" / "src" / "main" / "res"

S = 512
FONT_CANDIDATES = (
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
    "/System/Library/Fonts/Supplemental/Arial Bold.ttf",
    "C:/Windows/Fonts/arialbd.ttf",
)

TOP = (0x26, 0x34, 0x3A)
BOTTOM = (0x1E, 0x2A, 0x30)
ACCENT = (0x80, 0xCB, 0xC4)
WHITE = (0xF5, 0xF7, 0xF8)

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def load_font(size):
    for candidate in FONT_CANDIDATES:
        if Path(candidate).exists():
            return ImageFont.truetype(candidate, size)
    raise SystemExit("No bold TTF found; set FONT_CANDIDATES to a local bold font.")


def make_icon():
    image = Image.new("RGB", (S, S), TOP)
    draw = ImageDraw.Draw(image)

    # Vertical gradient plate.
    for y in range(S):
        t = y / (S - 1)
        draw.line(
            [(0, y), (S, y)],
            fill=tuple(round(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)),
        )

    # Wordmark, optically centred in the upper half.
    font = load_font(168)
    text = "AMS"
    spacing = 10
    widths = [draw.textlength(char, font=font) for char in text]
    total = sum(widths) + spacing * (len(text) - 1)
    box = font.getbbox("AMS")
    cap_height = box[3] - box[1]
    x = (S - total) / 2.0
    y = S / 2.0 - cap_height / 2.0 - box[1] - 48
    for index, char in enumerate(text):
        draw.text((x, y), char, font=font, fill=WHITE)
        x += widths[index] + spacing

    # Teal space bar: the keyboard cue.
    bar_w, bar_h = 196, 22
    bar_x = (S - bar_w) / 2.0
    bar_y = y + cap_height + 74
    draw.rounded_rectangle(
        [bar_x, bar_y, bar_x + bar_w, bar_y + bar_h], radius=11, fill=ACCENT
    )

    # Small key hints either side of the bar.
    key, offset = 28, 52
    for cx in (bar_x - offset - key, bar_x + bar_w + offset):
        draw.rounded_rectangle(
            [cx, bar_y - 3, cx + key, bar_y + key - 3], radius=7, fill=(0x46, 0x59, 0x60)
        )

    return image


def main():
    icon = make_icon()
    for folder, size in DENSITIES.items():
        target = RES / folder
        target.mkdir(parents=True, exist_ok=True)
        resized = icon.resize((size, size), Image.LANCZOS)
        resized.save(target / "ic_launcher.png")
        resized.save(target / "ic_launcher_round.png")
        print("wrote", target / "ic_launcher.png")


if __name__ == "__main__":
    main()
