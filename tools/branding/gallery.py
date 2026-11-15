"""The mod page's gallery (CurseForge's Images tab), laid out as Ground Burst's is (hrbs_'s design there): the picture
blurred and darkened across the whole image, the picture itself framed in white on the right, and on the left a title
in Russo One, the stripe under it and a few words in Rajdhani Bold. Culprit's own colours (style.py): the title white
(the first image's running hot, as the banner's), the stripe the cost scale, the words cream.

The pictures are docs/screenshots/gallery (1920 x 1080, from a normally generated world: `runShowcase`); the layout is
Ground Burst's 1280 x 720 one measured and scaled to 1920 x 1080.

Usage: <Ground Burst's tools/.venv>\\Scripts\\python.exe tools\\branding\\gallery.py [--out <folder>]
Writes NN_<name>.jpg for each, captions.md (each image's title and description to type into CurseForge, in upload order:
the first five are the carousel at the top of the description page) and a sheet to look over, into the folder (default
build/gallery).
"""
import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageEnhance, ImageFilter

import style as S

SHOTS = S.ROOT / "docs" / "screenshots" / "gallery"
W, H = 1920, 1080
K = W / 1280                                   # Ground Burst's gallery is 1280 x 720
FRAME = (round(533 * K), round(165 * K), round(1219 * K), round(555 * K))     # the framed picture, as measured there
BORDER = round(3 * K)
LEFT = round(70 * K)                           # the text's left edge
TITLE_TOP, TITLE_CAP = round(200 * K), 37 * K
TEXT_CAP, TEXT_STEP = 19 * K, 38 * K
TEXT_WIDTH = FRAME[0] - LEFT - round(40 * K)

# Upload order (the first five are the description page's carousel): the picture, its title on the image, its words on
# the image, and its caption on CurseForge (title, description). Cards of a screen take the village behind them (a
# screen blurred is a smear of its own bright parts).
SCREENS = {"05_results_tick", "06_results_things", "07_results_mods", "08_frames", "10_chunks_map", "11_scan_menu", "14_settings"}
BEHIND_SCREENS = "02_village"
GALLERY = [
    ("01_sunset", "CULPRIT", "Scan your server and see, on the world itself, what is lagging your game.",
     "Culprit", "A plains village at sunset under a scan's results: each villager, animal, bell and chunk labelled with what it costs the server a tick."),
    ("02_village", "ON THE WORLD", "Every mob, machine and chunk boxed where it stands, coloured by what it costs.",
     "The cost of everything, on the world", "After a scan, everything that ticked is boxed where it stands, from pale yellow for the cheap to deep magenta for the worst, and each chunk's total sits on its outline."),
    ("03_readout", "POINT AT IT", "Look at anything to read what it costs a tick, its worst tick and its mod.",
     "Point at the culprit", "Look at a villager, a hopper or a furnace and Culprit reads it out under your crosshair: its cost a tick, its share of the tick, its worst tick and the mod it comes from."),
    ("05_results_tick", "THE WHOLE TICK", "The whole server tick in parts, every part timed, adding up to the tick.",
     "Every millisecond accounted for", "Press F9 for the results: the server tick in parts (mobs, block entities, chunk loading, mob spawning, the autosave, Culprit's own timing and what was not measured), adding up to the tick."),
    ("09_chunks_world", "LOADED CHUNKS", "Every loaded chunk outlined in the colour of what holds it, and who forced it.",
     "What keeps the world loaded", "The chunk inspector outlines every loaded chunk in the colour of why it is loaded: players, /forceload and who ran it, portals, ender pearls, the spawn and other mods' chunk loaders."),
    ("06_results_things", "WORST FIRST", "Every thing ranked by what it costs, with where it stands. Click one to mark it.",
     "The culprits, ranked", "The Things tab ranks everything that ticked by its cost, with its place, its share of the tick and its worst tick. Search it, click a line to mark it on the world, or, as an operator, teleport there."),
    ("07_results_mods", "BY MOD", "What each mod costs your server, beside the game's own work.",
     "By mod", "The Mods tab adds it all up by mod, the game's own work included, so the list comes to the whole tick."),
    ("08_frames", "FRAMES TOO", "What each mob, chest and particle costs to draw, on any server.",
     "Your frame rate, too", "The frame scan does the same for your frames: what each mob, chest, sign and kind of particle costs to draw, and every part of the frame. It needs nothing from the server."),
    ("04_marked", "MARKED", "Pick a thing in the results and a beam marks it on the world.",
     "Marked on the world", "Click a line in the results and a beam rises over that thing, so you can find it from far off."),
    ("10_chunks_map", "CHUNK MAP", "The loaded chunks round you on a map, and what keeps each loaded.",
     "The chunk map", "The Loaded chunks section maps the chunks round you, coloured by what keeps each loaded, with every loader listed by the chunks it holds."),
    ("11_scan_menu", "SCAN MENU", "Press F8 and scan the server or your frames for 10, 30 or 60 seconds.",
     "The scan menu", "F8 opens the scan menu: the server's ticks or your frames, for 10, 30 or 60 seconds. Both keys can be changed in Controls."),
    ("12_chat", "IN THE CHAT", "The chat says when a scan starts and ends, with the costliest things.",
     "In the chat", "A scan's start and end in the chat: the tick's average and worst, its lag spikes and the costliest things."),
    ("13_hud_line", "HUD LINE", "The server's TPS and MSPT and your FPS in a corner, off until you turn it on.",
     "The HUD line", "An optional line in the corner you choose: the server's ticks a second and time a tick, and your frames a second."),
    ("14_settings", "SETTINGS", "How far things are boxed, how many are labelled, when the results hide.",
     "Your settings", "How far things are boxed, how many are labelled, when the results hide themselves, and the HUD line, from the results screen's Settings button."),
    ("15_night", "DAY OR NIGHT", "The results stay on the world until you hide them.",
     "Day or night", "The village after dark, its results still on the world: they stay until you hide them, or after the minutes you choose."),
]


def background(pic: Image.Image) -> Image.Image:
    """The picture across the whole image, blurred to its colours and taken down into the dark."""
    back = pic.resize((W, H), Image.LANCZOS).filter(ImageFilter.GaussianBlur(42 * K / 1.5))
    back = ImageEnhance.Color(back).enhance(0.85).convert("RGBA")
    back = S.tint(back, S.NIGHT, 0.72)
    xs, ys = S.grid(W, H)
    edge = S.smoothstep(0.55, 1.25, np.hypot((xs - W * 0.5) / (W * 0.5), (ys - H * 0.5) / (H * 0.5))) * 0.55
    return S.darken(back, S.NIGHT, S.mask(edge))


def framed(img: Image.Image, pic: Image.Image) -> Image.Image:
    """The picture in a white frame on the right, with a soft shadow under it."""
    x0, y0, x1, y1 = FRAME
    inner = pic.resize((x1 - x0 - 2 * BORDER, y1 - y0 - 2 * BORDER), Image.LANCZOS)
    card = Image.new("RGBA", (x1 - x0, y1 - y0), (236, 236, 236, 255))
    card.paste(inner, (BORDER, BORDER))
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    layer.alpha_composite(card, (x0, y0))
    img.alpha_composite(S.shadow_of(layer, 16 * K / 1.5, 0.75, (0, round(5 * K))))
    img.alpha_composite(layer)
    return img


def balanced(text: str, cap: float, width: float) -> list[str]:
    """The text in as few lines as fit the width, the lines then made as even as they can be in that many."""
    lines = wrap(text, cap, width)
    w = width
    while w > width * 0.5:
        trial = wrap(text, cap, w - 8)
        if len(trial) > len(lines):
            break
        lines, w = trial, w - 8
    return lines


def wrap(text: str, cap: float, width: float) -> list[str]:
    f = S.tag_font(cap)
    lines, line = [], ""
    for word in text.split():
        trial = (line + " " + word).strip()
        if f.getlength(trial) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = word
    return lines + ([line] if line else [])


def words(img: Image.Image, title: str, text: str, hot: bool) -> Image.Image:
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    cap = min(TITLE_CAP, TITLE_CAP * TEXT_WIDTH / S.title_width(TITLE_CAP, title))
    S.draw_title(layer, LEFT, TITLE_TOP + (TITLE_CAP - cap), cap, word=title, style="heat" if hot else "white")
    stripe_top = round(TITLE_TOP + TITLE_CAP + 24 * K)
    sw, sh = round(S.STRIPE_W * K), round(S.STRIPE_H * K)
    layer.alpha_composite(S.heat_stripe(sw, sh, steps=6, gap=sh * 0.45, slant=1.0), (LEFT, stripe_top))
    y = stripe_top + sh + 27 * K
    for line in balanced(text, TEXT_CAP, TEXT_WIDTH):
        S.draw_tagline(layer, LEFT, y, TEXT_CAP, text=line)
        y += TEXT_STEP
    img.alpha_composite(S.shadow_of(layer, 5, 0.55, (0, 2)))
    img.alpha_composite(layer)
    return img


def make(name: str, title: str, text: str, hot: bool) -> Image.Image:
    pic = Image.open(SHOTS / f"{name}.jpg").convert("RGB")
    img = background(Image.open(SHOTS / f"{BEHIND_SCREENS}.jpg").convert("RGB") if name in SCREENS else pic)
    img = framed(img, pic)
    img = words(img, title, text, hot)
    return img.convert("RGB")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=str(S.ROOT / "build" / "gallery"))
    args = ap.parse_args()
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    captions = ["# Gallery: upload in this order (the first five are the carousel on the description page)", ""]
    made = []
    for i, (name, title, text, cap_title, cap_text) in enumerate(GALLERY, start=1):
        slug = name.split("_", 1)[1]
        file = out / f"{i:02d}_{slug}.jpg"
        img = make(name, title, text, hot=(i == 1))
        img.save(file, quality=92, subsampling=0, optimize=True)
        made.append(img)
        captions += [f"## {i:02d}. {cap_title}", "", f"File: `build/gallery/{file.name}`", "", cap_text, ""]
        print(file.name, img.size)
    (out / "captions.md").write_text("\n".join(captions), encoding="utf-8")
    cols = 3
    rows = (len(made) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * 640, rows * 360), (30, 30, 30))
    for i, img in enumerate(made):
        sheet.paste(img.resize((640, 360), Image.LANCZOS), ((i % cols) * 640, (i // cols) * 360))
    sheet.save(out / "sheet.jpg", quality=88)
    print("captions and sheet in", out)


if __name__ == "__main__":
    main()
