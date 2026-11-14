#!/usr/bin/env python3
"""Draws the ThinkOfMe app icon as SVG and renders every size the apps and stores need.

A cream gift box with a gold ribbon whose bow is a heart-shaped loop, on the accent red.
Outputs (committed, so builds need no tools):
  design/icon/icon.svg, icon-dark.svg, glyph.svg            sources
  ios/App/Resources/Assets.xcassets/AppIcon.appiconset/     1024 light, dark, tinted
  android/app/src/main/res/mipmap-*/ic_launcher_*.png       adaptive layers + monochrome
  store/google-play/icon-512.png                            Play listing icon

Run: pip install cairosvg pillow && python3 design/icon/generate-icons.py
"""
import io
import json
from pathlib import Path

import cairosvg
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT_IOS = ROOT / "ios/App/Resources/Assets.xcassets/AppIcon.appiconset"
OUT_ANDROID = ROOT / "android/app/src/main/res"
OUT_PLAY = ROOT / "store/google-play"

DEFS = """
<defs>
  <radialGradient id="bg" cx="50%" cy="36%" r="78%">
    <stop offset="0" stop-color="#E5605F"/>
    <stop offset="0.55" stop-color="#C23A3F"/>
    <stop offset="1" stop-color="#951F35"/>
  </radialGradient>
  <radialGradient id="bgDark" cx="50%" cy="36%" r="80%">
    <stop offset="0" stop-color="#3A2230"/>
    <stop offset="1" stop-color="#140A12"/>
  </radialGradient>
  <linearGradient id="body" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFF7EE"/>
    <stop offset="1" stop-color="#EFD8C2"/>
  </linearGradient>
  <linearGradient id="lid" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#FFFDF9"/>
    <stop offset="1" stop-color="#F4E2CF"/>
  </linearGradient>
  <linearGradient id="lidShade" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#7A3B2A" stop-opacity="0.22"/>
    <stop offset="1" stop-color="#7A3B2A" stop-opacity="0"/>
  </linearGradient>
  <linearGradient id="gold" x1="0" y1="0" x2="1" y2="0">
    <stop offset="0" stop-color="#F9DA93"/>
    <stop offset="0.5" stop-color="#EDB656"/>
    <stop offset="1" stop-color="#C98E2F"/>
  </linearGradient>
  <linearGradient id="goldBow" x1="0" y1="0" x2="1" y2="1">
    <stop offset="0" stop-color="#FBE2A6"/>
    <stop offset="0.55" stop-color="#EDB656"/>
    <stop offset="1" stop-color="#C48A2C"/>
  </linearGradient>
  <radialGradient id="shadow" cx="50%" cy="50%" r="50%">
    <stop offset="0" stop-color="#3B0A14" stop-opacity="0.45"/>
    <stop offset="1" stop-color="#3B0A14" stop-opacity="0"/>
  </radialGradient>
</defs>
"""

HEART = "M 512 392 C 410 330 318 268 326 188 C 333 118 430 96 512 176 C 594 96 691 118 698 188 C 706 268 614 330 512 392 Z"
SPARKLE = "M 0 -34 C 6 -8 8 -6 34 0 C 8 6 6 8 0 34 C -6 8 -8 6 -34 0 C -8 -6 -6 -8 0 -34 Z"


def glyph(shadow: bool = True, sparkles: bool = True) -> str:
    parts = []
    if shadow:
        parts.append('<ellipse cx="512" cy="812" rx="318" ry="50" fill="url(#shadow)"/>')
    parts += [
        # Box body, ribbon and the lid's shadow on it.
        '<rect x="252" y="470" width="520" height="326" rx="46" fill="url(#body)"/>',
        '<rect x="252" y="486" width="520" height="40" fill="url(#lidShade)"/>',
        '<rect x="471" y="486" width="82" height="310" fill="url(#gold)"/>',
        # Lid.
        '<rect x="220" y="384" width="584" height="112" rx="40" fill="url(#lid)"/>',
        '<rect x="246" y="396" width="532" height="8" rx="4" fill="#FFFFFF" opacity="0.8"/>',
        '<rect x="465" y="384" width="94" height="112" fill="url(#gold)"/>',
        # Ribbon tails falling over the lid from the knot.
        '<path d="M 500 392 C 470 420 440 452 420 492 L 452 500 C 468 466 492 436 516 408 Z" fill="#C98E2F"/>',
        '<path d="M 524 392 C 554 420 584 452 604 492 L 572 500 C 556 466 532 436 508 408 Z" fill="#B87F26"/>',
        # The bow: a ribbon loop shaped like a heart, sitting on the lid, with a knot.
        '<g transform="translate(512 412) scale(1.04) translate(-512 -392)">',
        f'<path d="{HEART}" fill="none" stroke="#7A4A12" stroke-opacity="0.3" stroke-width="58" stroke-linejoin="round" transform="translate(0 10)"/>',
        f'<path d="{HEART}" fill="none" stroke="url(#goldBow)" stroke-width="54" stroke-linejoin="round"/>',
        '<path d="M 400 196 C 420 160 456 150 482 168" fill="none" stroke="#FFF3D6" stroke-width="12" stroke-linecap="round" opacity="0.85"/>',
        '</g>',
        '<rect x="474" y="378" width="76" height="60" rx="24" fill="url(#gold)"/>',
        '<rect x="488" y="386" width="30" height="10" rx="5" fill="#FFF3D6" opacity="0.7"/>',
    ]
    if sparkles:
        parts += [
            f'<path d="{SPARKLE}" fill="#FFE7B0" transform="translate(222 236) scale(1.1)" opacity="0.95"/>',
            f'<path d="{SPARKLE}" fill="#FFE7B0" transform="translate(818 300) scale(0.7)" opacity="0.85"/>',
        ]
    return "\n".join(parts)


def mono_glyph() -> str:
    """One-colour silhouette for themed icons: white shapes, the ribbon cut out in black."""
    return "\n".join([
        '<rect x="252" y="470" width="520" height="326" rx="46" fill="#FFFFFF"/>',
        '<rect x="220" y="384" width="584" height="112" rx="40" fill="#FFFFFF"/>',
        '<rect x="244" y="492" width="536" height="16" fill="#000000"/>',
        '<rect x="471" y="384" width="82" height="412" fill="#000000"/>',
        '<g transform="translate(512 412) scale(1.04) translate(-512 -392)">',
        f'<path d="{HEART}" fill="none" stroke="#FFFFFF" stroke-width="58" stroke-linejoin="round"/>',
        '</g>',
        '<rect x="474" y="372" width="76" height="66" rx="24" fill="#FFFFFF"/>',
    ])


def svg(background: str | None, content: str, transform: str = "") -> str:
    bg = f'<rect width="1024" height="1024" fill="{background}"/>' if background else ""
    return (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'
        f"{DEFS}{bg}<g transform=\"{transform}\">{content}</g></svg>"
    )


def render(source: str, size: int) -> Image.Image:
    png = cairosvg.svg2png(bytestring=source.encode(), output_width=size, output_height=size)
    return Image.open(io.BytesIO(png)).convert("RGBA")


def main() -> None:
    here = Path(__file__).resolve().parent
    centered = "translate(0 26)"
    light = svg("url(#bg)", glyph(), centered)
    dark = svg("url(#bgDark)", glyph(), centered)
    (here / "icon.svg").write_text(light)
    (here / "icon-dark.svg").write_text(dark)
    (here / "glyph.svg").write_text(svg(None, glyph(shadow=False)))

    # iOS: light (no transparency allowed), dark, and a grayscale glyph for tinted icons.
    OUT_IOS.mkdir(parents=True, exist_ok=True)
    render(light, 1024).convert("RGB").save(OUT_IOS / "icon-1024.png")
    render(dark, 1024).convert("RGB").save(OUT_IOS / "icon-1024-dark.png")
    plain = render(svg(None, glyph(shadow=False, sparkles=False), centered), 1024)
    tinted = Image.new("RGB", (1024, 1024), "black")
    tinted.paste(plain.convert("L").convert("RGB"), mask=plain.split()[3])
    tinted.save(OUT_IOS / "icon-1024-tinted.png")
    (OUT_IOS / "Contents.json").write_text(json.dumps({
        "images": [
            {"filename": "icon-1024.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {"appearances": [{"appearance": "luminosity", "value": "dark"}],
             "filename": "icon-1024-dark.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
            {"appearances": [{"appearance": "luminosity", "value": "tinted"}],
             "filename": "icon-1024-tinted.png", "idiom": "universal", "platform": "ios", "size": "1024x1024"},
        ],
        "info": {"author": "xcode", "version": 1},
    }, indent=2) + "\n")

    # Android adaptive icon: 108dp layers, the glyph kept inside the 66dp safe circle.
    fit = "translate(512 512) scale(0.68) translate(-512 -480)"
    foreground = svg(None, glyph(shadow=False), fit)
    background = svg("url(#bg)", "")
    densities = {"mdpi": 108, "hdpi": 162, "xhdpi": 216, "xxhdpi": 324, "xxxhdpi": 432}
    for name, size in densities.items():
        folder = OUT_ANDROID / f"mipmap-{name}"
        folder.mkdir(parents=True, exist_ok=True)
        render(foreground, size).save(folder / "ic_launcher_foreground.png")
        render(background, size).convert("RGB").save(folder / "ic_launcher_background.png")
        silhouette = render(svg("#000000", mono_glyph(), fit), size).convert("L")
        mono = Image.new("RGBA", (size, size), (255, 255, 255, 0))
        mono.putalpha(silhouette)
        mono.save(folder / "ic_launcher_monochrome.png")

    OUT_PLAY.mkdir(parents=True, exist_ok=True)
    render(light, 512).convert("RGB").save(OUT_PLAY / "icon-512.png")
    print("Icons written.")


if __name__ == "__main__":
    main()
