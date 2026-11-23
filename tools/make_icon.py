"""Draws the Regain icon: an eclipse giving the light back. A dark disc covers most of an amber sun, leaving a crescent of light returning.

Regain = taking your attention back; the crescent is light returning after the blackout.
Writes branding/regain-icon-1024.png and branding/regain-icon.svg. The Android adaptive icon in
android/app/src/main/res uses the same geometry as a vector drawable (evenodd crescent path).
Run: python tools/make_icon.py
"""
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

OUT = Path(__file__).resolve().parent.parent / "branding"
OUT.mkdir(exist_ok=True)
for old in OUT.glob("*-icon*"):
    old.unlink()

SIZE = 1024
SS = 4  # supersampling for smooth edges
S = SIZE * SS


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(size, top, bottom):
    img = Image.new("RGB", (size, size))
    px = img.load()
    for y in range(size):
        c = lerp(top, bottom, y / (size - 1))
        for x in range(size):
            px[x, y] = c
    return img


def disc(cx, cy, r):
    m = Image.new("L", (S, S), 0)
    k = S / 1024
    ImageDraw.Draw(m).ellipse(((cx - r) * k, (cy - r) * k, (cx + r) * k, (cy + r) * k), fill=255)
    return m


# Background: rounded square, deep ink
canvas = vertical_gradient(S, (22, 24, 32), (8, 9, 13))
tile = Image.new("L", (S, S), 0)
ImageDraw.Draw(tile).rounded_rectangle((0, 0, S - 1, S - 1), radius=int(S * 0.22), fill=255)

# Soft glow behind the eclipse
glow = disc(512, 512, 300).filter(ImageFilter.GaussianBlur(S * 0.05))
glow = glow.point(lambda v: int(v * 0.35))
canvas.paste(Image.new("RGB", (S, S), (245, 160, 50)), (0, 0), glow)

# Solid dark core, so the glow does not tint the eclipse disc
canvas.paste(Image.new("RGB", (S, S), (11, 12, 17)), (0, 0), disc(542, 482, 232))

# Crescent: amber disc minus a slightly smaller, offset dark disc
outer = disc(512, 512, 280)
inner = disc(542, 482, 232)
crescent = Image.new("L", (S, S), 0)
crescent.paste(outer, (0, 0))
crescent.paste(0, (0, 0), inner)
amber = vertical_gradient(S, (255, 214, 120), (240, 140, 36))
canvas.paste(amber, (0, 0), crescent)

canvas.putalpha(tile)
canvas = canvas.resize((SIZE, SIZE), Image.LANCZOS)
canvas.save(OUT / "regain-icon-1024.png")

svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" width="1024" height="1024">
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#161820"/><stop offset="1" stop-color="#08090D"/></linearGradient>
    <linearGradient id="amber" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FFD678"/><stop offset="1" stop-color="#F08C24"/></linearGradient>
  </defs>
  <rect width="1024" height="1024" rx="225" fill="url(#bg)"/>
  <path fill="url(#amber)" fill-rule="evenodd"
        d="M232 512 a280 280 0 1 0 560 0 a280 280 0 1 0 -560 0 Z M310 482 a232 232 0 1 0 464 0 a232 232 0 1 0 -464 0 Z"/>
</svg>
"""
(OUT / "regain-icon.svg").write_text(svg, encoding="utf-8")
print("wrote", OUT)
