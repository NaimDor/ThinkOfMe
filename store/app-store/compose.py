#!/usr/bin/env python3
"""Frames raw simulator screenshots into App Store images (1290 x 2796, 6.9" iPhone).

Each image: a warm brand gradient, a two-line headline in the language's own font, and
the screenshot in a rounded "device" card with a soft shadow, bleeding off the bottom.

  python3 store/app-store/compose.py RAW_DIR OUT_DIR
RAW_DIR holds <lang>/<NN-name>.png from the UI test (lang: en, he, ar, ru).
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
FONTS = HERE.parents[1] / "design" / "fonts"
WIDTH, HEIGHT = 1290, 2796
LOCALES = {"en": "en-US", "he": "he", "ar": "ar-SA", "ru": "ru"}
FONT = {
    "en-US": "fraunces_semibold.ttf",
    "he": "frankruhllibre_bold.ttf",
    "ar-SA": "notonaskharabic_semibold.ttf",
    "ru": "lora_semibold.ttf",
}
INK = (36, 22, 40)
MUTED = (111, 98, 114)
TOP = (251, 247, 242)
BOTTOM = (251, 231, 225)


def gradient() -> Image.Image:
    image = Image.new("RGB", (WIDTH, HEIGHT), TOP)
    draw = ImageDraw.Draw(image)
    for y in range(HEIGHT):
        t = y / HEIGHT
        draw.line([(0, y), (WIDTH, y)], fill=tuple(int(TOP[i] + (BOTTOM[i] - TOP[i]) * t) for i in range(3)))
    # A soft accent glow behind the device.
    glow = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-200, 1100, WIDTH + 200, HEIGHT + 600), fill=(194, 58, 63, 38))
    return Image.alpha_composite(image.convert("RGBA"), glow.filter(ImageFilter.GaussianBlur(160)))


def fitted_font(path: Path, lines: list[str], max_width: int, size: int) -> ImageFont.FreeTypeFont:
    while size > 60:
        font = ImageFont.truetype(str(path), size, layout_engine=ImageFont.Layout.RAQM)
        if all(font.getlength(line) <= max_width for line in lines):
            return font
        size -= 4
    return ImageFont.truetype(str(path), size, layout_engine=ImageFont.Layout.RAQM)


def compose(raw: Path, locale: str, lines: list[str]) -> Image.Image:
    canvas = gradient()
    draw = ImageDraw.Draw(canvas)
    font_path = FONTS / FONT[locale]
    rtl = locale in ("he", "ar-SA")
    direction = "rtl" if rtl else "ltr"
    language = {"en-US": "en", "he": "he", "ar-SA": "ar", "ru": "ru"}[locale]

    title = fitted_font(font_path, lines[:1], WIDTH - 160, 118)
    subtitle = fitted_font(font_path, lines[1:2], WIDTH - 160, 96)
    y = 210
    for line, font, color in ((lines[0], title, INK), (lines[1], subtitle, MUTED if len(lines[1]) > 18 else INK)):
        width = font.getlength(line, direction=direction, language=language)
        draw.text(((WIDTH - width) / 2, y), line, font=font, fill=color, direction=direction, language=language)
        y += int(font.size * 1.32)

    # The screenshot as a rounded device card.
    shot = Image.open(raw).convert("RGB")
    card_width = 1060
    card_height = int(shot.height * card_width / shot.width)
    shot = shot.resize((card_width, card_height), Image.LANCZOS)
    radius = 96
    mask = Image.new("L", shot.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, card_width, card_height), radius, fill=255)
    left = (WIDTH - card_width) // 2
    top = y + 90

    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((left, top + 30, left + card_width, top + card_height + 30), radius, fill=(60, 20, 30, 70))
    canvas = Image.alpha_composite(canvas, shadow.filter(ImageFilter.GaussianBlur(40)))
    frame = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle((left - 14, top - 14, left + card_width + 14, top + card_height + 14), radius + 14, fill=(36, 22, 40, 255))
    canvas = Image.alpha_composite(canvas, frame)
    canvas.paste(shot, (left, top), mask)
    return canvas.convert("RGB")


def main() -> int:
    raw_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    captions = json.loads((HERE / "captions.json").read_text())
    count = 0
    for lang, locale in LOCALES.items():
        for raw in sorted((raw_dir / lang).glob("*.png")):
            lines = captions[locale].get(raw.stem)
            if not lines:
                continue
            target = out_dir / locale / f"{raw.stem}.png"
            target.parent.mkdir(parents=True, exist_ok=True)
            compose(raw, locale, lines).save(target, optimize=True)
            count += 1
    print(f"Composed {count} screenshots into {out_dir}")
    return 0 if count else 1


if __name__ == "__main__":
    sys.exit(main())
