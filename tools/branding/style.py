"""Culprit's branding style: the colours, type and heat stripe of the banner and logo.

Ground Burst's layout and type (its tools/branding/style.py): the title in Russo One, a stripe under it, the tagline in
Rajdhani Bold, on a dark ground. Culprit's own theme is its world view's: the cost scale it colours things by (pale
yellow, orange, red, deep magenta: client/Heat.java's stops), on the dark slate of its screens, with the cyan of the
beam that marks a thing picked from the results. The stripe is the cost scale itself, in steps, as the scale's legend.
Both fonts are under the SIL Open Font License (their licences beside them in fonts/).
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent.parent
FONTS = HERE / "fonts"
TITLE_FONT = FONTS / "RussoOne-Regular.ttf"
TAG_FONT = FONTS / "Rajdhani-Bold.ttf"

# The cost scale (client/Heat.java), cheap to costly.
HEAT = [(0xFF, 0xF1, 0xA8), (0xFF, 0xD1, 0x66), (0xFF, 0xA0, 0x40), (0xFF, 0x6B, 0x3D), (0xF0, 0x30, 0x4A), (0xC8, 0x17, 0x8A)]
WHITE = (255, 255, 255)
CREAM = (232, 226, 210)          # the tagline
SLATE = (16, 18, 22)             # the screens' panel
NIGHT = (11, 12, 17)             # the dark the banner falls to
BEAM = (77, 224, 232)            # the marked thing's beam
TAGLINE = "Find what is lagging your game."

# Ground Burst's measured proportions, 400 px high: capitals 84 px, the stripe 240 x 10 px 53 px under the title's
# baseline, the tagline's capitals 31 px, 29 px under the stripe.
STRIPE_W, STRIPE_H = 240, 10


def heat(t: float) -> tuple:
    """The scale's colour at t (0 to 1), as Heat.rgb."""
    x = min(max(t, 0.0), 1.0) * (len(HEAT) - 1)
    i = min(int(x), len(HEAT) - 2)
    f = x - i
    a, b = HEAT[i], HEAT[i + 1]
    return tuple(round(a[k] * (1 - f) + b[k] * f) for k in range(3))


def title_font(cap: float) -> ImageFont.FreeTypeFont:
    """Russo One at the size whose capitals are `cap` pixels tall (its H is 0.70 of the size)."""
    return ImageFont.truetype(str(TITLE_FONT), round(cap / 0.70))


def tag_font(cap: float) -> ImageFont.FreeTypeFont:
    """Rajdhani Bold at the size whose capitals are `cap` pixels tall (its A is 0.65 of the size)."""
    return ImageFont.truetype(str(TAG_FONT), round(cap / 0.65))


def title_width(cap: float, word: str = "CULPRIT") -> float:
    return title_font(cap).getlength(word)


def draw_title(layer: Image.Image, x: float, cap_top: float, cap: float, word: str = "CULPRIT", style: str = "heat") -> tuple[float, float]:
    """The title with its capitals' top at cap_top: white, or ("heat") white with its last letters running hot, as the
    costliest thing glows. Returns its right edge and its baseline."""
    f = title_font(cap)
    top_offset = f.getbbox("H")[1]
    y = cap_top - top_offset
    w = f.getlength(word)
    text = Image.new("L", layer.size, 0)
    ImageDraw.Draw(text).text((x, y), word, font=f, fill=255)
    if style == "white":
        fill = Image.new("RGBA", layer.size, WHITE + (255,))
    else:
        # White over the first letters, then up the scale to deep magenta over the last: the scale read left to right.
        xs = (np.arange(layer.width) + 0.5 - x) / max(w, 1)
        t = np.clip((xs - 0.38) / 0.62, 0, 1)
        cols = np.array([WHITE if v <= 0 else heat(0.15 + 0.85 * v) for v in t], dtype=np.float64)
        white = np.array(WHITE, dtype=np.float64)
        blend = np.clip((xs - 0.30) / 0.12, 0, 1)[:, None]
        row = white * (1 - blend) + cols * blend
        arr = np.broadcast_to(row[None, :, :], (layer.height, layer.width, 3))
        fill = Image.fromarray(np.uint8(arr + 0.5), "RGB").convert("RGBA")
    fill.putalpha(text)
    layer.alpha_composite(fill)
    return x + w, cap_top + cap


def draw_tagline(layer: Image.Image, x: float, cap_top: float, cap: float, text: str = TAGLINE, colour=CREAM) -> float:
    f = tag_font(cap)
    ImageDraw.Draw(layer).text((x, cap_top - f.getbbox("A")[1]), text, font=f, fill=colour + (255,))
    return x + f.getlength(text)


def tagline_width(cap: float, text: str = TAGLINE) -> float:
    return tag_font(cap).getlength(text)


def heat_stripe(width: int, height: int, steps: int = 6, gap: float = 0.0, slant: float = 1.0, supersample: int = 4) -> Image.Image:
    """The cost scale as a stripe: `steps` blocks from pale yellow to deep magenta, each cut on a slant (as Ground
    Burst's hazard bands lean) with a thin dark gap between them; the stripe's own ends cut the same way."""
    s = supersample
    w, h = width * s, height * s
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    lean = h * slant                                    # how far a cut leans over the stripe's height
    g = gap * s
    step = (w - lean) / steps
    for i in range(steps):
        x0 = i * step + (g / 2 if i else 0)
        x1 = (i + 1) * step - (g / 2 if i < steps - 1 else 0)
        col = HEAT[round(i * (len(HEAT) - 1) / max(steps - 1, 1))] if steps == len(HEAT) else heat(i / max(steps - 1, 1))
        d.polygon([(x0 + lean, 0), (x1 + lean, 0), (x1, h), (x0, h)], fill=col + (255,))
    return img.resize((width, height), Image.LANCZOS)


def heat_tape(length: int, height: int, period: float, dark: float, supersample: int = 3) -> Image.Image:
    """A long tape for the logo: the cost scale along its length, crossed by dark bands leaning as / does (Ground
    Burst's hazard tape, in the scale's colours)."""
    s = supersample
    w, h = length * s, height * s
    xs = (np.arange(w) + 0.5) / w
    row = np.array([heat(0.04 + 0.96 * v) for v in xs], dtype=np.float64)
    arr = np.broadcast_to(row[None, :, :], (h, w, 3)).copy()
    img = Image.fromarray(np.uint8(arr + 0.5), "RGB").convert("RGBA")
    d = ImageDraw.Draw(img)
    p, dk = period * s, dark * s
    k = -h
    while k < w + h:
        d.polygon([(k, 0), (k + dk, 0), (k + dk - h, h), (k - h, h)], fill=SLATE + (255,))
        k += p
    return img.resize((length, height), Image.LANCZOS)


def shadow_of(layer: Image.Image, radius: float, opacity: float, offset=(0, 0)) -> Image.Image:
    """A soft dark shadow of a layer's opaque parts."""
    alpha = layer.getchannel("A").point(lambda a: int(a * opacity))
    sh = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    sh.putalpha(alpha)
    sh = sh.filter(ImageFilter.GaussianBlur(radius))
    out = Image.new("RGBA", layer.size, (0, 0, 0, 0))
    out.alpha_composite(sh, offset)
    return out


def glow_of(layer: Image.Image, radius: float, strength: float) -> Image.Image:
    """A soft glow in a layer's own colours (its opaque parts blurred and brightened)."""
    g = layer.filter(ImageFilter.GaussianBlur(radius))
    a = np.asarray(g, dtype=np.float64)
    a[..., 3] = np.clip(a[..., 3] * strength, 0, 255)
    return Image.fromarray(np.uint8(a + 0.5), "RGBA")


def smoothstep(e0, e1, x):
    """0 below e0, 1 above e1, a smooth S between (numbers or numpy arrays)."""
    t = np.clip((np.asarray(x, dtype=np.float64) - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def mask(a) -> Image.Image:
    """A greyscale mask from an array of 0..1."""
    return Image.fromarray(np.uint8(np.clip(a, 0, 1) * 255 + 0.5), "L")


def grid(w: int, h: int):
    """Pixel centres' coordinates: (xs, ys), each h x w."""
    return np.meshgrid(np.arange(w) + 0.5, np.arange(h) + 0.5)


def tint(img: Image.Image, colour, amount: float) -> Image.Image:
    """Mixes a picture toward a flat colour."""
    flat = Image.new(img.mode, img.size, colour + ((255,) if img.mode == "RGBA" else ()))
    return Image.blend(img, flat, amount)


def darken(img: Image.Image, colour, alpha: Image.Image) -> Image.Image:
    """Lays a colour over a picture through a mask (255: all colour)."""
    over = Image.new("RGBA", img.size, colour + (255,))
    over.putalpha(alpha)
    out = img.convert("RGBA")
    out.alpha_composite(over)
    return out
