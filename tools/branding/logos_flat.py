"""Culprit's logo in the clean look of utility mods (the user: "a clean non pixelated look, as well as being more
simplistic in colouring/shaping, think like sodium"): flat shapes with smooth edges, few colours, no outlines, the
magnifying glass and its box drawn as shapes rather than pixels. The stripe (the cost scale across the bottom-right
corner, the user's "thing", as on Ground Burst's logo) stays as logos.py draws it.

Examples:
- flat:     a white glass, the box inside it in three of the scale's hot colours;
- line:     a white glass, the box inside it as white lines (one white symbol, the stripe the colour, as Sodium's);
- hot:      a white glass whose lens is the cost scale, the box in it as white lines;
- duotone:  a pale steel glass, the box in two magentas;
- scope:    the flat glass with sight marks round the lens, the box smaller in its middle;
- light:    the flat glass in dark slate on a light ground.

Each is drawn four times over and smoothed down to 1024 px, then 400.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\logos_flat.py [--out <folder>] [--choose <name>]
Writes logo_<name>.png (400) and logo_<name>_1024.png into the folder (default build/branding/flat) and a sheet of
them at 400, 128 and 48 px beside it; --choose <name> also writes that one as docs/branding/logo.png.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageDraw

import logos as L
import style as S

N = L.N                                   # 1024
SS = 4                                    # drawn at four times the size, then smoothed down
M = N * SS

WHITE = (242, 244, 248)
STEEL = (206, 212, 224)
STEEL_DARK = (150, 158, 176)
INK = (34, 38, 50)                        # the light logo's glass
LENS = (28, 32, 44)                       # the glass's inside on the dark
TOP, LEFT, RIGHT = S.HEAT[3], S.HEAT[4], S.HEAT[5]       # the box's faces: the scale's hottest three

# The glass: the lens up and right of the middle, the handle down to the left, clear of the stripe's corner.
C = (0.565, 0.405)                        # the lens's middle (of the logo's size)
R, T = 0.265, 0.064                       # its outer radius and the ring's thickness
HANDLE_LEN, HANDLE_W = 0.285, 0.098
BOX = 0.122                               # the box's size: its middle to a corner


def px(v: float) -> float:
    return v * M


def disc(mask_draw, cx, cy, r, fill=255):
    mask_draw.ellipse((px(cx - r), px(cy - r), px(cx + r), px(cy + r)), fill=fill)


def solid(colour, mask: Image.Image) -> Image.Image:
    layer = Image.new("RGBA", (M, M), colour + (255,))
    layer.putalpha(mask)
    return layer


def ring_mask(r_out: float, r_in: float) -> Image.Image:
    outer = Image.new("L", (M, M), 0)
    disc(ImageDraw.Draw(outer), *C, r_out)
    inner = Image.new("L", (M, M), 0)
    disc(ImageDraw.Draw(inner), *C, r_in)
    return ImageChops.subtract(outer, inner)


def handle_mask(width: float = HANDLE_W) -> Image.Image:
    """The handle from under the ring (its start hidden there, so no knob shows at the join) down to the bottom left,
    its far end rounded."""
    d = math.sqrt(0.5)
    sx, sy = C[0] - d * (R - T * 0.5), C[1] + d * (R - T * 0.5)
    ex, ey = sx - d * HANDLE_LEN, sy + d * HANDLE_LEN
    mask = Image.new("L", (M, M), 0)
    dr = ImageDraw.Draw(mask)
    dr.line((px(sx), px(sy), px(ex), px(ey)), fill=255, width=round(px(width)))
    disc(dr, ex, ey, width / 2)
    return mask


def cube_points(cx: float, cy: float, s: float):
    """An upright box seen from a corner: its six outer corners and its near corner (the middle)."""
    c30 = math.cos(math.radians(30))
    top, bottom = (cx, cy - s), (cx, cy + s)
    ur, lr = (cx + s * c30, cy - s / 2), (cx + s * c30, cy + s / 2)
    ul, ll = (cx - s * c30, cy - s / 2), (cx - s * c30, cy + s / 2)
    mid = (cx, cy)
    return top, ur, lr, bottom, ll, ul, mid


def cube_faces(cx, cy, s, colours, gap: float = 0.0) -> Image.Image:
    """The box's three faces in flat colours (top, left, right), each pulled in a little by `gap` for a clean seam."""
    top, ur, lr, bottom, ll, ul, mid = cube_points(cx, cy, s)
    layer = Image.new("RGBA", (M, M), (0, 0, 0, 0))
    dr = ImageDraw.Draw(layer)
    for face, colour in (((top, ur, mid, ul), colours[0]), ((ul, mid, bottom, ll), colours[1]), ((mid, ur, lr, bottom), colours[2])):
        fx = sum(p[0] for p in face) / 4
        fy = sum(p[1] for p in face) / 4
        pts = [(px(fx + (p[0] - fx) * (1 - gap)), px(fy + (p[1] - fy) * (1 - gap))) for p in face]
        dr.polygon(pts, fill=colour + (255,))
    return layer


def cube_lines(cx, cy, s, colour, width: float) -> Image.Image:
    """The box as clean lines with round joins: its outline and the three edges meeting at the near corner."""
    top, ur, lr, bottom, ll, ul, mid = cube_points(cx, cy, s)
    mask = Image.new("L", (M, M), 0)
    dr = ImageDraw.Draw(mask)
    w = round(px(width))
    for a, b in ((top, ur), (ur, lr), (lr, bottom), (bottom, ll), (ll, ul), (ul, top), (mid, ul), (mid, ur), (mid, bottom)):
        dr.line((px(a[0]), px(a[1]), px(b[0]), px(b[1])), fill=255, width=w)
    for p in (top, ur, lr, bottom, ll, ul, mid):
        disc(dr, p[0], p[1], width / 2)
    return solid(colour, mask)


def heat_fill(mask: Image.Image) -> Image.Image:
    """The cost scale across a shape, pale yellow at its bottom left to deep magenta at its top right."""
    xs, ys = np.meshgrid(np.linspace(0, 1, M // 4), np.linspace(0, 1, M // 4))
    t = np.clip(((xs - (C[0] - R)) + ((C[1] + R) - ys)) / (4 * R), 0, 1)
    rgb = np.zeros(t.shape + (3,))
    for i in range(3):
        stops = np.array([c[i] for c in S.HEAT], float)
        rgb[..., i] = np.interp(t * (len(S.HEAT) - 1), np.arange(len(S.HEAT)), stops)
    img = Image.fromarray(np.uint8(rgb + 0.5), "RGB").resize((M, M), Image.BICUBIC).convert("RGBA")
    img.putalpha(mask)
    return img


def glass(ring_colour, handle_colour, lens_colour=None, lens_layer=None, marks=False) -> Image.Image:
    layer = Image.new("RGBA", (M, M), (0, 0, 0, 0))
    inner = Image.new("L", (M, M), 0)
    disc(ImageDraw.Draw(inner), *C, R - T + 0.002)
    if lens_layer is not None:
        layer.alpha_composite(lens_layer)
    elif lens_colour is not None:
        layer.alpha_composite(solid(lens_colour, inner))
    layer.alpha_composite(solid(handle_colour, handle_mask()))
    layer.alpha_composite(solid(ring_colour, ring_mask(R, R - T)))
    if marks:
        # Sight marks: four short ticks inward from the ring, at the top, bottom, left and right.
        mask = Image.new("L", (M, M), 0)
        dr = ImageDraw.Draw(mask)
        for ang in (0, 90, 180, 270):
            a = math.radians(ang)
            r0, r1 = R - T, R - T - 0.05
            dr.line((px(C[0] + math.cos(a) * r0), px(C[1] + math.sin(a) * r0), px(C[0] + math.cos(a) * r1),
                     px(C[1] + math.sin(a) * r1)), fill=255, width=round(px(0.022)))
            disc(dr, C[0] + math.cos(a) * r1, C[1] + math.sin(a) * r1, 0.011)
        layer.alpha_composite(solid(ring_colour, mask))
    return layer


def down(layer: Image.Image) -> Image.Image:
    return layer.resize((N, N), Image.LANCZOS)


def ground_flat(top=(27, 30, 40), bottom=(14, 16, 22)) -> Image.Image:
    """A plain ground, a little lighter at the top: flatter than the pixel logo's lit middle."""
    t = np.linspace(0, 1, N)[:, None, None]
    a = np.array(top, float) * (1 - t) + np.array(bottom, float) * t
    return L.from_array(np.broadcast_to(a, (N, N, 3)) + L.noise(N, 0.6, 5))


def compose(ground: Image.Image, symbol: Image.Image, shadow: float = 0.45) -> Image.Image:
    img = L.corner_tape(ground, L.TAPE_AT)
    sym = down(symbol)
    img.alpha_composite(S.shadow_of(sym, 14, shadow, (0, 8)))
    img.alpha_composite(sym)
    return img


def examples() -> dict:
    hot = (TOP, LEFT, RIGHT)
    out = {}
    g = glass(WHITE, WHITE, LENS)
    g.alpha_composite(cube_faces(*C, BOX, hot, gap=0.035))
    out["flat"] = compose(ground_flat(), g)

    g = glass(WHITE, WHITE, LENS)
    g.alpha_composite(cube_lines(*C, BOX, WHITE, 0.024))
    out["line"] = compose(ground_flat(), g)

    inner = Image.new("L", (M, M), 0)
    disc(ImageDraw.Draw(inner), *C, R - T + 0.002)
    g = glass(WHITE, WHITE, lens_layer=heat_fill(inner))
    g.alpha_composite(cube_lines(*C, BOX, WHITE, 0.024))
    out["hot"] = compose(ground_flat(), g)

    g = glass(STEEL, STEEL_DARK, LENS)
    g.alpha_composite(cube_faces(*C, BOX, ((242, 80, 150), (200, 23, 138), (160, 16, 108)), gap=0.035))
    out["duotone"] = compose(ground_flat(), g)

    g = glass(WHITE, WHITE, LENS, marks=True)
    g.alpha_composite(cube_faces(*C, BOX * 0.78, hot, gap=0.035))
    out["scope"] = compose(ground_flat(), g)

    g = glass(INK, INK, (232, 235, 241))
    g.alpha_composite(cube_faces(*C, BOX, hot, gap=0.035))
    out["light"] = compose(ground_flat((246, 247, 250), (222, 225, 232)), g, shadow=0.18)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(S.ROOT / "build" / "branding" / "flat"))
    ap.add_argument("--choose", default=None)
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    logos = examples()
    for name, logo in logos.items():
        logo.save(out / f"logo_{name}_1024.png", optimize=True)
        logo.resize((400, 400), Image.LANCZOS).save(out / f"logo_{name}.png", optimize=True)
    L.sheet(logos, out.parent / "flat_sheet.png")
    if args.choose:
        logos[args.choose].resize((400, 400), Image.LANCZOS).convert("RGB").save(L.LOGO, optimize=True)
        print("chose", args.choose, "as", L.LOGO)
    print("wrote", ", ".join(logos), "to", out)


if __name__ == "__main__":
    main()
