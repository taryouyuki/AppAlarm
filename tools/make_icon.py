"""Draws the AppAlarm launcher icon: a dreamy pastel calendar with a play button.

Transparent background; rendered at 2048px and downsampled to the mipmap densities.
"""
from __future__ import annotations

import math
import os

from PIL import Image, ImageDraw, ImageFilter

OUT_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
SIZE = 2048
DENSITIES = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

PINK_TOP = (255, 214, 230, 255)
PINK_BOTTOM = (216, 198, 246, 255)
BAND_TOP = (255, 190, 214, 255)
BAND_BOTTOM = (214, 180, 240, 255)
RING = (255, 250, 252, 255)
PLAY_A = (255, 122, 162, 255)
PLAY_B = (166, 126, 240, 255)


def vertical_gradient(size: tuple[int, int], top, bottom) -> Image.Image:
    w, h = size
    grad = Image.new("RGBA", (1, h))
    for y in range(h):
        t = y / max(1, h - 1)
        grad.putpixel((0, y), tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(4)))
    return grad.resize((w, h), Image.BILINEAR)


def rounded_mask(size: tuple[int, int], radius: int) -> Image.Image:
    mask = Image.new("L", size, 0)
    d = ImageDraw.Draw(mask)
    d.rounded_rectangle((0, 0, size[0] - 1, size[1] - 1), radius=radius, fill=255)
    return mask


def star(draw: ImageDraw.ImageDraw, cx: float, cy: float, r: float, color) -> None:
    """Four-point sparkle."""
    pts = []
    for i in range(8):
        ang = math.pi / 2 * (i / 2.0)
        rr = r if i % 2 == 0 else r * 0.28
        pts.append((cx + rr * math.cos(ang), cy + rr * math.sin(ang)))
    draw.polygon(pts, fill=color)


def build() -> Image.Image:
    s = SIZE
    canvas = Image.new("RGBA", (s, s), (0, 0, 0, 0))

    # Calendar geometry
    left, top, right, bottom = int(s * 0.14), int(s * 0.20), int(s * 0.86), int(s * 0.84)
    w, h = right - left, bottom - top
    radius = int(w * 0.20)

    # 1) dreamy glow behind the calendar
    glow = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.rounded_rectangle((left - 40, top - 40, right + 40, bottom + 40),
                         radius=radius + 40, fill=(255, 190, 220, 150))
    glow = glow.filter(ImageFilter.GaussianBlur(70))
    canvas.alpha_composite(glow)

    # 2) calendar body
    body = vertical_gradient((w, h), PINK_TOP, PINK_BOTTOM)
    body.putalpha(rounded_mask((w, h), radius))
    canvas.alpha_composite(body, (left, top))

    # 3) header band
    band_h = int(h * 0.30)
    band = vertical_gradient((w, band_h), BAND_TOP, BAND_BOTTOM)
    band.putalpha(rounded_mask((w, band_h), radius))
    # square off the bottom corners of the band
    square = Image.new("L", (w, band_h), 0)
    ImageDraw.Draw(square).rectangle((0, band_h - radius, w, band_h), fill=255)
    band.putalpha(Image.composite(band.getchannel("A"), band.getchannel("A"), square))
    canvas.alpha_composite(band, (left, top))

    draw = ImageDraw.Draw(canvas)

    # 4) binding rings
    ring_r = int(w * 0.055)
    for cx in (left + int(w * 0.30), left + int(w * 0.70)):
        cy = top + int(band_h * 0.30)
        draw.rounded_rectangle((cx - ring_r // 2, cy - ring_r, cx + ring_r // 2, cy + ring_r),
                               radius=ring_r // 2, fill=RING)

    # 5) soft plate + play triangle
    cx, cy = left + w // 2, top + band_h + (h - band_h) // 2
    plate_r = int(w * 0.30)
    plate = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(plate).ellipse((cx - plate_r, cy - plate_r, cx + plate_r, cy + plate_r),
                                  fill=(255, 255, 255, 235))
    canvas.alpha_composite(plate.filter(ImageFilter.GaussianBlur(2)))

    tri = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    td = ImageDraw.Draw(tri)
    tw = plate_r * 1.15
    th = plate_r * 1.35
    td.polygon([(cx - tw * 0.32, cy - th * 0.45),
                (cx - tw * 0.32, cy + th * 0.45),
                (cx + tw * 0.52, cy)], fill=PLAY_A)
    # lilac gradient on the triangle
    lil = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(lil).polygon([(cx - tw * 0.32, cy - th * 0.45),
                                 (cx - tw * 0.32, cy + th * 0.45),
                                 (cx + tw * 0.52, cy)], fill=PLAY_B)
    mask = tri.getchannel("A").point(lambda a: a // 2)
    tri = Image.composite(lil, tri, mask)
    canvas.alpha_composite(tri)

    # 6) sparkles
    star(draw, left + int(w * 0.86), top + int(h * 0.10), int(w * 0.07), (255, 255, 255, 235))
    star(draw, left + int(w * 0.10), top + int(h * 0.62), int(w * 0.05), (255, 255, 255, 200))
    star(draw, left + int(w * 0.88), top + int(h * 0.72), int(w * 0.04), (255, 240, 250, 190))

    return canvas


def main() -> None:
    master = build()
    for density, px in DENSITIES.items():
        folder = os.path.join(OUT_ROOT, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)
        master.resize((px, px), Image.LANCZOS).save(os.path.join(folder, "ic_launcher.png"))
    master.resize((512, 512), Image.LANCZOS).save(
        os.path.join(os.path.dirname(os.path.abspath(__file__)), "icon_preview.png"))
    print("icons written")


if __name__ == "__main__":
    main()
