"""Culprit's logo (the mod page's square avatar): an original pixel-art magnifying glass holding one of Culprit's boxes,
the costliest kind, glowing in the cost scale's hottest colours, on a dark ground with the scale as a tape behind it,
the way Ground Burst's logo lays its glowing missile over a hazard tape (Ground Burst's tools/branding/logos.py).

- tape:  the glass over the cost-scale tape on the dark ground: the mod's logo (docs/branding/logo.png);
- plain: the glass on the dark ground alone;
- badge: the plain logo in a ring, round, for places that crop avatars to a circle;
- icon:  the glass alone on transparency (docs/branding/icon.png), for the banner and anything else.

The glass is drawn on a 48 x 48 grid, the way Minecraft's own pictures are pixels, and enlarged without smoothing; only
the glow round the box and the ground are smooth.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\logos.py [--out <folder>]
Writes logo_<name>.png (400 x 400) and logo_<name>_1024.png into the folder (default build/branding/logos), a sheet of
them at 400, 128 and 48 px (build/branding/logos_sheet.png), the chosen one as docs/branding/logo.png and the glass as
docs/branding/icon.png.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

import style as S

N = 1024                                 # every logo is made at 1024 and scaled down
G = 48                                   # the pixel grid of the glass
CHOSEN = "scale"
LOGO = S.ROOT / "docs" / "branding" / "logo.png"
ICON = S.ROOT / "docs" / "branding" / "icon.png"

OUTLINE = (20, 22, 28)
STEEL = [(236, 240, 246), (196, 202, 214), (146, 152, 168), (96, 102, 118)]     # lit to shaded
STEEL_EDGE = (62, 66, 80)
GLASS = (16, 24, 38)
GRIP = [(118, 88, 70), (86, 62, 50), (58, 40, 34), (38, 26, 24)]                  # a leather grip, lit to shaded
LENS_C, R_OUT, R_IN = (29.0, 19.0), 14.0, 10.6
HANDLE_DIR = (-math.sqrt(0.5), math.sqrt(0.5))                                   # toward the bottom left
LIGHT = (-math.sqrt(0.5), -math.sqrt(0.5))                                       # light from the top left
TAPE_AT = 0.77                                                                   # the tape's middle, clear of the lens
BOX, BOX_YAW, BOX_PITCH = 4.8, 40.0, 28.0                                        # the box's half size and turn
EDGE = (255, 120, 150)                                                           # its lines: a hot pink, lit


def noise(n: int, amount: float, seed: int) -> np.ndarray:
    """A little fine noise so gradients do not band once saved."""
    return np.random.default_rng(seed).normal(0, amount, (n, n, 1))


def from_array(a: np.ndarray) -> Image.Image:
    return Image.fromarray(np.uint8(np.clip(a, 0, 255) + 0.5), "RGB").convert("RGBA")


def ground(light: float = 1.0, lit=(36, 36, 58)) -> Image.Image:
    """The dark, lit a little behind the glass (Ground Burst's night, cooler, so the scale's warm colours stand out)."""
    xs, ys = S.grid(N, N)
    r = np.hypot((xs - N * 0.54) / N, (ys - N * 0.44) / N)
    glow = np.exp(-(r / 0.38) ** 2)[..., None] * light
    base = np.array(S.NIGHT, float)
    return from_array(base + (np.array(lit, float) - base) * glow + noise(N, 0.9, 2))


# ---- the glass, in pixels ----------------------------------------------------------------------------------------

def put(img: np.ndarray, x: int, y: int, colour, alpha: float = 1.0):
    if 0 <= x < G and 0 <= y < G:
        img[y, x, :3] = colour
        img[y, x, 3] = alpha * 255


def cube(a: float, yaw: float, pitch: float):
    """The box's corners turned and seen from a little above: screen x, screen y (down), depth (toward the eye)."""
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    pts = []
    for i in range(8):
        x, y, z = (a if i & 4 else -a), (a if i & 2 else -a), (a if i & 1 else -a)
        x, z = x * cy + z * sy, -x * sy + z * cy
        y, z = y * cp - z * sp, y * sp + z * cp
        pts.append((LENS_C[0] + x, LENS_C[1] - y + 0.6, z))
    edges = [(i, j) for i in range(8) for j in range(i + 1, 8) if bin(i ^ j).count("1") == 1]
    return pts, edges


def line(x0: float, y0: float, x1: float, y1: float):
    """The pixels of a line (Bresenham), its ends rounded to pixels."""
    x0, y0, x1, y1 = int(math.floor(x0)), int(math.floor(y0)), int(math.floor(x1)), int(math.floor(y1))
    dx, dy = abs(x1 - x0), -abs(y1 - y0)
    sx, sy = (1 if x0 < x1 else -1), (1 if y0 < y1 else -1)
    err = dx + dy
    while True:
        yield x0, y0
        if x0 == x1 and y0 == y1:
            return
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def hull(points):
    pts = sorted(set(points))
    if len(pts) < 3:
        return pts

    def cross(o, a, b):
        return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0])
    lower, upper = [], []
    for p in pts:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], p) <= 0:
            lower.pop()
        lower.append(p)
    for p in reversed(pts):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], p) <= 0:
            upper.pop()
        upper.append(p)
    return lower[:-1] + upper[:-1]


def glass_layers():
    """The glass as pixel layers (G x G RGBA arrays): body (rim, handle, outline), lens, box fill, box lines, shine."""
    body = np.zeros((G, G, 4), np.float64)
    lens = np.zeros((G, G, 4), np.float64)
    box_fill = np.zeros((G, G, 4), np.float64)
    box_lines = np.zeros((G, G, 4), np.float64)
    shine = np.zeros((G, G, 4), np.float64)
    hx, hy = HANDLE_DIR
    start = (LENS_C[0] + hx * (R_OUT - 0.8), LENS_C[1] + hy * (R_OUT - 0.8))
    for y in range(G):
        for x in range(G):
            px, py = x + 0.5, y + 0.5
            dx, dy = px - LENS_C[0], py - LENS_C[1]
            r = math.hypot(dx, dy)
            if r <= R_IN:
                lens[y, x] = (*GLASS, 255)
            elif r <= R_OUT:
                shade = (dx * LIGHT[0] + dy * LIGHT[1]) / r                 # +1 facing the light
                if r <= R_IN + 1.0:
                    col = STEEL_EDGE if shade > -0.2 else STEEL[3]          # the rim's inner lip, in shadow at the top
                else:
                    col = STEEL[0] if shade > 0.55 else STEEL[1] if shade > 0.12 else STEEL[2] if shade > -0.4 else STEEL[3]
                body[y, x] = (*col, 255)
            else:
                # The handle: a steel collar, then the grip, rounded at its end.
                vx, vy = px - start[0], py - start[1]
                along = vx * hx + vy * hy
                across = vx * (-hy) + vy * hx                               # across the handle, + toward the top left
                lit = across
                if 0 <= along < 3.4 and abs(across) <= 3.2:
                    col = STEEL[0] if lit > 1.4 else STEEL[1] if lit > -0.2 else STEEL[2] if lit > -1.8 else STEEL[3]
                    body[y, x] = (*col, 255)
                elif 3.4 <= along <= 19.5 and abs(across) <= 2.7 and (along <= 17.3 or (along - 17.3) ** 2 + across ** 2 <= 2.7 ** 2):
                    col = GRIP[0] if lit > 1.3 else GRIP[1] if lit > -0.1 else GRIP[2] if lit > -1.6 else GRIP[3]
                    # Stitched rings round the grip every few pixels.
                    if int(along) in (8, 13):
                        col = tuple(max(0, c - 22) for c in col)
                    body[y, x] = (*col, 255)
    # The box: Culprit's box round the costliest thing, seen through the glass: its three faces toward the eye tinted in
    # the scale's hottest colours (the top lightest), its nine edges in sight drawn (the three meeting at the far corner
    # hidden, as on a solid box).
    pts, edges = cube(BOX, math.radians(BOX_YAW), math.radians(BOX_PITCH))
    far = min(range(8), key=lambda i: pts[i][2])
    faces = [[i for i in range(8) if (i >> bit) & 1 == side] for bit in (2, 1, 0) for side in (0, 1)]
    for face in faces:
        if far in face:
            continue                                                      # a face away from the eye
        top = all(i & 2 for i in face)
        left = sum(pts[i][0] for i in face) / 4 < LENS_C[0]               # the side face toward the light
        col, alpha = (S.heat(0.66), 165) if top else (S.heat(0.90), 135) if left else (S.heat(1.0), 105)
        poly = hull([(p[0], p[1]) for p in (pts[i] for i in face)])
        fill = Image.new("L", (G, G), 0)
        ImageDraw.Draw(fill).polygon(poly, fill=255)
        fm = np.asarray(fill) > 0
        box_fill[fm] = (*col, alpha)
    for i, j in edges:
        if far in (i, j):
            continue
        for x, y in line(pts[i][0], pts[i][1], pts[j][0], pts[j][1]):
            put(box_lines, x, y, EDGE)
    # The shine on the glass, top left.
    for y in range(G):
        for x in range(G):
            dx, dy = x + 0.5 - LENS_C[0], y + 0.5 - LENS_C[1]
            r, ang = math.hypot(dx, dy), math.degrees(math.atan2(dy, dx))
            if R_IN - 3.1 <= r <= R_IN - 1.6 and -168 <= ang <= -108:
                shine[y, x] = (255, 255, 255, 120)
            elif R_IN - 2.6 <= r <= R_IN - 1.6 and 150 <= ang <= 162:
                shine[y, x] = (255, 255, 255, 70)
    # A dark outline round the whole glass (rim, lens and handle), a pixel wide.
    solid = (body[..., 3] > 0) | (lens[..., 3] > 0)
    grown = solid.copy()
    for oy in (-1, 0, 1):
        for ox in (-1, 0, 1):
            grown |= np.roll(np.roll(solid, oy, 0), ox, 1)
    edge = grown & ~solid
    body[edge] = (*OUTLINE, 255)
    return body, lens, box_fill, box_lines, shine


def big(layer: np.ndarray, scale: int) -> Image.Image:
    return Image.fromarray(np.uint8(layer + 0.5), "RGBA").resize((G * scale, G * scale), Image.NEAREST)


def glass(size: int = 960) -> Image.Image:
    """The glass at `size` px on transparency: pixels enlarged, the box's glow inside the lens, a warm glow round it."""
    k = size // G
    body, lens, box_fill, box_lines, shine = glass_layers()
    out = Image.new("RGBA", (G * k, G * k), (0, 0, 0, 0))
    lens_img = big(lens, k)
    # The lens lit from within by the box: its colours blurred, kept inside the lens.
    glow = S.glow_of(big(box_fill, k), k * 2.4, 1.3)
    inside = lens_img.copy()
    inside.alpha_composite(glow)
    inside.putalpha(lens_img.getchannel("A"))
    # A soft warm glow round the whole glass, as Ground Burst's missile glows.
    whole = big(body, k)
    whole.alpha_composite(inside)
    halo = Image.new("RGBA", whole.size, S.heat(0.82) + (0,))
    halo.putalpha(whole.getchannel("A"))
    halo = halo.filter(ImageFilter.GaussianBlur(k * 1.4))
    ha = np.asarray(halo, np.float64)
    ha[..., 3] *= 0.55
    out.alpha_composite(Image.fromarray(np.uint8(ha + 0.5), "RGBA"))
    out.alpha_composite(whole)
    out.alpha_composite(big(box_fill, k))
    out.alpha_composite(S.glow_of(big(box_lines, k), k * 0.7, 1.2))
    out.alpha_composite(big(box_lines, k))
    out.alpha_composite(big(shine, k))
    return out.resize((size, size), Image.NEAREST) if out.width != size else out


def place(back: Image.Image, icon: Image.Image) -> Image.Image:
    out = back.copy()
    out.alpha_composite(icon, (round((N - icon.width) / 2), round((N - icon.height) / 2)))
    return out


def tape_logo(icon: Image.Image) -> Image.Image:
    img = ground()
    h = round(N * 0.05)
    w = round((2 * N - 2 * N * TAPE_AT) * math.sqrt(2) + N * 0.07)
    tape = S.heat_tape(w, h, h * 1.6, h * 0.55).rotate(45, resample=Image.BICUBIC, expand=True)
    cx, cy = N * TAPE_AT, N * TAPE_AT                      # across the bottom-right corner, clear of the glass
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    layer.alpha_composite(tape, (round(cx - tape.width / 2), round(cy - tape.height / 2)))
    img.alpha_composite(S.shadow_of(layer, 8, 0.6, (0, 5)))
    img.alpha_composite(layer)
    return place(img, icon)


def scale_logo(icon: Image.Image) -> Image.Image:
    """The tape as the cost scale's legend: blocks from pale yellow to deep magenta, cut on the slant."""
    img = ground()
    return place(corner_tape(img, TAPE_AT), icon)


def corner_tape(img: Image.Image, at: float, steps: int = 8) -> Image.Image:
    """The cost scale's tape across the bottom-right corner, its middle at (at, at) of the logo, clear of the glass (the
    user: the glass had covered the stripe): from the bottom edge to the right edge, pale yellow to deep magenta, its
    two ends running off the logo's edges as Ground Burst's tape does."""
    img = img.copy()
    seen = (2 * N - 2 * N * at) * math.sqrt(2)          # its length from the bottom edge to the right edge
    w, h = round(seen + N * 0.07), round(N * 0.05)
    tape = S.heat_stripe(w, h, steps=steps, gap=h * 0.32, slant=1.0).rotate(45, resample=Image.BICUBIC, expand=True)
    c = N * at
    layer = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    layer.alpha_composite(tape, (round(c - tape.width / 2), round(c - tape.height / 2)))
    img.alpha_composite(S.shadow_of(layer, 8, 0.6, (0, 5)))
    img.alpha_composite(layer)
    return img


def badge(icon: Image.Image) -> Image.Image:
    img = ground(1.0)
    d = ImageDraw.Draw(img)
    m = N * 0.035
    d.ellipse((m, m, N - m, N - m), outline=S.heat(0.7) + (255,), width=round(N * 0.022))
    img = place(img, icon)
    round_mask = Image.new("L", (N * 4, N * 4), 0)
    ImageDraw.Draw(round_mask).ellipse((0, 0, N * 4 - 1, N * 4 - 1), fill=255)
    img.putalpha(round_mask.resize((N, N), Image.LANCZOS))
    return img


def sheet(logos: dict, path: Path):
    pad, label_h = 24, 40
    cell_w = 400 + 128 + 48 + pad * 4
    cols = 2
    rows = (len(logos) + cols - 1) // cols
    W = cols * cell_w + pad
    H = rows * (400 + label_h + pad) + pad
    img = Image.new("RGB", (W, H), (43, 43, 46))
    d = ImageDraw.Draw(img)
    f = ImageFont.truetype(str(S.TAG_FONT), 30)
    for i, (name, logo) in enumerate(logos.items()):
        x = pad + (i % cols) * cell_w
        y = pad + (i // cols) * (400 + label_h + pad)
        d.text((x, y), name, font=f, fill=(235, 235, 235))
        y += label_h
        for size, dx in ((400, 0), (128, 400 + pad), (48, 400 + pad * 2 + 128)):
            small = logo.resize((size, size), Image.LANCZOS)
            img.paste(small, (x + dx, y), small)
    img.save(path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(S.ROOT / "build" / "branding" / "logos"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    icon = glass(960)
    logos = {"tape": tape_logo(icon), "scale": scale_logo(icon), "plain": place(ground(), icon), "badge": badge(icon)}
    for name, logo in logos.items():
        logo.save(out / f"logo_{name}_1024.png", optimize=True)
        logo.resize((400, 400), Image.LANCZOS).save(out / f"logo_{name}.png", optimize=True)
    sheet(logos, out.parent / "logos_sheet.png")
    LOGO.parent.mkdir(parents=True, exist_ok=True)
    logos[CHOSEN].resize((400, 400), Image.LANCZOS).convert("RGB").save(LOGO, optimize=True)
    icon.resize((512, 512), Image.NEAREST).save(ICON, optimize=True)
    print("wrote", ", ".join(logos), "to", out, "and", CHOSEN, "as", LOGO)


if __name__ == "__main__":
    main()
