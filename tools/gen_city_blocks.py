"""Tingen's city blocks: textures and every JSON file they need.

Run from the repo root:  python3 tools/gen_city_blocks.py

Draws the 16x16 textures procedurally (original art, same palette as the rest of Beyonder) and
writes blockstates, block and item models, loot tables, tags and English names. The Java side
registers the same list in content/CityBlocks.java.
"""
import json
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ASSETS = os.path.join(ROOT, "assets", "beyonder")
DATA = os.path.join(ROOT, "data")
TEX = os.path.join(ASSETS, "textures", "block")


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(c, k=1.0, add=0):
    return tuple(clamp(x * k + add) for x in c)


def new(alpha=255):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0 if alpha == 0 else 255))
    return img, img.load()


def save(img, name):
    os.makedirs(TEX, exist_ok=True)
    img.save(os.path.join(TEX, name + ".png"))


# ------------------------------------------------------------------ masonry

def bricks(name, colours, mortar, seed, soot=0.0, course=4, length=8):
    """Stretcher bond: each course offset by half a brick; every brick its own shade."""
    img, px = new()
    rnd = random.Random(seed)
    for y in range(16):
        row = y // course
        off = (length // 2) if row % 2 else 0
        for x in range(16):
            if y % course == course - 1 or (x + off) % length == length - 1:
                px[x, y] = rgb(mortar, 1.0 + (rnd.random() - 0.5) * 0.08) + (255,)
                continue
            key = ((x + off) // length, row)
            r2 = random.Random(seed * 131 + key[0] * 17 + key[1] * 911)
            base = r2.choice(colours)
            k = 1.0 + (rnd.random() - 0.5) * 0.09
            if y % course == 0:
                k += 0.07                       # the top edge of each brick catches the light
            if soot:
                k -= soot * r2.random() * (y % course) / (course - 1)
            px[x, y] = rgb(base, k) + (255,)
    save(img, name)


def ashlar(name, seed, base=(196, 190, 176), joint=(146, 140, 128), streaks=True):
    """Big dressed limestone blocks: two courses, joints offset."""
    img, px = new()
    rnd = random.Random(seed)
    for y in range(16):
        row = y // 8
        off = 8 if row % 2 else 0
        for x in range(16):
            if y % 8 == 7 or (x + off) % 16 == 15:
                px[x, y] = rgb(joint, 1.0 + (rnd.random() - 0.5) * 0.06) + (255,)
                continue
            k = 1.0 + (rnd.random() - 0.5) * 0.06
            if y % 8 == 0:
                k += 0.06
            if y % 8 == 6:
                k -= 0.05
            if streaks and (x * 7 + row * 3) % 11 == 0:
                k -= 0.05 * (y % 8) / 7          # rain streaks from the joint above
            px[x, y] = rgb(base, k) + (255,)
    save(img, name)


def carved_ashlar():
    """A frieze block: moulded bands with a row of rosettes in relief."""
    img, px = new()
    rnd = random.Random(71)
    base = (196, 190, 176)
    for y in range(16):
        for x in range(16):
            k = 1.0 + (rnd.random() - 0.5) * 0.05
            if y in (0, 15):
                k = 0.78
            elif y in (1, 14):
                k = 1.12
            elif y in (2, 13):
                k = 0.9
            px[x, y] = rgb(base, k) + (255,)
    for cx in (3.5, 11.5):                      # two rosettes
        for y in range(3, 13):
            for x in range(16):
                d = math.hypot(x - cx, y - 7.5)
                if d < 3.6:
                    petal = math.cos(math.atan2(y - 7.5, x - cx) * 6) * 0.5 + 0.5
                    k = 0.86 + 0.24 * petal if d > 1.2 else 1.15
                    # light from the top left: the lower right of each bump is in shadow
                    k -= 0.12 * ((x - cx) + (y - 7.5)) / 4.0
                    px[x, y] = rgb(base, k) + (255,)
    for y in range(4, 12):                      # a dividing bead between them
        px[7, y] = rgb(base, 1.1) + (255,)
        px[8, y] = rgb(base, 0.82) + (255,)
    save(img, "carved_ashlar")


def pillar():
    side, px = new()
    rnd = random.Random(73)
    base = (200, 194, 180)
    for y in range(16):
        for x in range(16):
            f = x % 4
            k = {0: 0.80, 1: 1.08, 2: 1.0, 3: 0.92}[f] + (rnd.random() - 0.5) * 0.04
            px[x, y] = rgb(base, k) + (255,)
    save(side, "ashlar_pillar")
    top, px = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            k = 1.0 - 0.02 * (int(d) % 3) + (rnd.random() - 0.5) * 0.04
            if 6.5 < d < 7.6:
                k = 0.8
            px[x, y] = rgb(base, k) + (255,)
    save(top, "ashlar_pillar_top")


def roof_tiles(name, colours, seed):
    img, px = new()
    rnd = random.Random(seed)
    for row in range(4):
        y0 = row * 4
        offset = 0 if row % 2 == 0 else 2
        for x0 in range(-offset, 16, 4):
            base = rnd.choice(colours)
            for y in range(y0, y0 + 4):
                for x in range(x0, x0 + 4):
                    if not 0 <= x < 16:
                        continue
                    k = 1.0 + (rnd.random() - 0.5) * 0.1
                    if y == y0 + 3:
                        k = 0.62                 # the shadow under each tile's lip
                    elif y == y0 + 2 and x in (x0, x0 + 3):
                        k = 0.75                 # rounded tile ends
                    if x == x0:
                        k = min(k, 0.8)
                    if y == y0:
                        k += 0.08
                    px[x, y] = rgb(base, k) + (255,)
    save(img, name)


def flagstones():
    img, px = new()
    rnd = random.Random(81)
    stones = [(0, 0, 9, 7), (9, 0, 7, 7), (0, 7, 6, 9), (6, 7, 10, 9)]
    for y in range(16):
        for x in range(16):
            px[x, y] = (78, 76, 74, 255)
    for (sx, sy, w, h) in stones:
        base = rnd.choice([(132, 130, 126), (124, 122, 118), (140, 138, 132), (118, 118, 116)])
        for y in range(sy, sy + h - 1):
            for x in range(sx, sx + w - 1):
                k = 1.0 + (rnd.random() - 0.5) * 0.08
                if y == sy:
                    k += 0.08
                if x == sx + w - 2 or y == sy + h - 2:
                    k -= 0.08
                if rnd.random() < 0.03:
                    k -= 0.15                    # pits and wear
                px[x, y] = rgb(base, k) + (255,)
    save(img, "flagstones")


def kerbstone():
    img, px = new()
    rnd = random.Random(83)
    for y in range(16):
        for x in range(16):
            k = 1.0 + (rnd.random() - 0.5) * 0.14
            c = (112, 112, 118)
            r = rnd.random()
            if r < 0.06:
                c = (60, 60, 66)                 # black mica
            elif r < 0.1:
                c = (176, 172, 168)              # white quartz
            if y == 0:
                k += 0.12
            if x == 15:
                k -= 0.25                        # joint between kerb stones
            px[x, y] = rgb(c, k) + (255,)
    save(img, "kerbstone")


def planks(name, base, seed, weathered=True):
    img, px = new()
    rnd = random.Random(seed)
    for y in range(16):
        plank = y // 4
        r2 = random.Random(seed + plank * 37)
        tone = r2.choice([0.92, 1.0, 1.06, 0.97])
        seam = r2.randrange(16)
        for x in range(16):
            k = tone + math.sin(x * 0.7 + plank * 2.1 + math.sin(y * 1.3) * 0.6) * 0.05 + (rnd.random() - 0.5) * 0.07
            if y % 4 == 3:
                k = 0.6
            if x == seam and y % 4 != 3:
                k = 0.7                          # butt joint
            if weathered and rnd.random() < 0.05:
                k -= 0.12                        # grey checks in old wood
            if weathered and (x - seam) % 16 in (2, 13) and y % 4 == 1:
                k = 0.55                         # nail heads
            px[x, y] = rgb(base, k) + (255,)
    save(img, name)


def timber():
    side, px = new()
    rnd = random.Random(91)
    base = (62, 42, 30)
    for y in range(16):
        for x in range(16):
            k = 1.0 + math.sin(x * 1.9 + math.sin(y * 0.4) * 1.2) * 0.08 + (rnd.random() - 0.5) * 0.06
            if x in (0, 15):
                k -= 0.15
            px[x, y] = rgb(base, k) + (255,)
    save(side, "dark_timber")
    top, px = new()
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            k = 1.05 + 0.08 * math.sin(d * 1.8) + (rnd.random() - 0.5) * 0.05
            if x in (0, 15) or y in (0, 15):
                k = 0.75
            px[x, y] = rgb((92, 66, 46), k) + (255,)
    save(top, "dark_timber_top")


# ------------------------------------------------------------------ windows

FRAME, FRAME_D, FRAME_L = (212, 206, 192), (110, 104, 96), (232, 228, 216)


def sash(name, lit):
    """A Victorian sash window: two sashes, each two panes, in a painted frame."""
    img, px = new()
    rnd = random.Random(101 if lit else 103)
    for y in range(16):
        for x in range(16):
            edge = x in (0, 15) or y in (0, 15)
            bar = x == 7 or x == 8 or y == 7 or y == 8
            if edge:
                px[x, y] = (rgb(FRAME_D) if x == 15 or y == 15 else rgb(FRAME)) + (255,)
                continue
            if bar:
                c = FRAME if (x in (7, 8) and y not in (7, 8)) else (FRAME_L if y == 7 else FRAME_D if y == 8 else FRAME)
                px[x, y] = rgb(c) + (255,)
                continue
            if lit:
                glow = 0.72 + 0.28 * (y / 15) - 0.12 * abs(x - 7.5) / 7.5 + (rnd.random() - 0.5) * 0.05
                c = (255 * glow, 186 * glow, 96 * glow)
                if x in (1, 2) or x in (13, 14):
                    c = (150 * glow * 0.8, 46 * glow, 40 * glow)          # drawn curtains
                if y == 1 and 3 <= x <= 12:
                    c = (120 * glow, 40 * glow, 34 * glow)                # the pelmet
            else:
                # night glass: dark blue with a pale streak of reflected sky
                k = 1.0 + (rnd.random() - 0.5) * 0.06
                c = rgb((30, 36, 48), k)
                if (x + (15 - y)) % 13 in (4, 5) and y < 14:
                    c = rgb((70, 82, 104), k)
                if (x + (15 - y)) % 13 == 6 and y < 14:
                    c = rgb((52, 62, 80), k)
            px[x, y] = tuple(clamp(v) for v in c) + (255,)
    save(img, name)


def shop_window():
    """Plate glass in a painted shop-front frame (see-through)."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    green, green_d = (38, 66, 54), (24, 44, 36)
    for y in range(16):
        for x in range(16):
            if x in (0, 15) or y in (0, 15) or y == 3:
                px[x, y] = (green_d if x == 15 or y == 15 else green) + (255,)
            elif y < 3:
                px[x, y] = (196, 164, 92, 255) if x % 3 == 1 else (60, 52, 40, 255)  # gilded transom lettering
            else:
                a = 60
                c = (168, 196, 206)
                if (x - y) % 12 in (2, 3):
                    a, c = 120, (220, 236, 240)
                px[x, y] = c + (a,)
    save(img, "shop_window")


# ------------------------------------------------------------------ details

def clock_face():
    img, px = new()
    rnd = random.Random(111)
    for y in range(16):
        for x in range(16):
            px[x, y] = rgb((58, 50, 44), 1.0 + (rnd.random() - 0.5) * 0.06) + (255,)
    for y in range(16):
        for x in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d < 7.6:
                k = 1.0 - 0.06 * (d / 7.6)
                px[x, y] = rgb((232, 224, 202), k) + (255,)
            if 6.6 < d < 7.6:
                px[x, y] = (196, 156, 72, 255)                              # gilt rim
    for h in range(12):                                                    # hour marks
        a = h / 12 * math.tau
        x = int(round(7.5 + math.sin(a) * 5.6))
        y = int(round(7.5 - math.cos(a) * 5.6))
        px[x, y] = (30, 26, 24, 255)
    for t in range(0, 5):                                                  # hands at ten past ten
        for (a, length) in ((-1.05, 4.0), (1.05, 5.2)):
            x = int(round(7.5 + math.sin(a) * t * length / 4))
            y = int(round(7.5 - math.cos(a) * t * length / 4))
            px[x, y] = (24, 20, 20, 255)
    px[7, 7] = px[8, 8] = px[7, 8] = px[8, 7] = (24, 20, 20, 255)
    save(img, "clock_face")


def gravestone():
    img, px = new()
    rnd = random.Random(121)
    for y in range(16):
        for x in range(16):
            k = 1.0 + (rnd.random() - 0.5) * 0.12
            c = (126, 128, 124)
            if rnd.random() < 0.05 + 0.05 * (y / 15):
                c = (96, 116, 76)                                         # lichen
            px[x, y] = rgb(c, k) + (255,)
    for y in range(5, 11):                                                # an engraved cross and lines
        px[7, y] = px[8, y] = rgb((70, 72, 70)) + (255,)
    for x in range(5, 11):
        px[x, 6] = rgb((70, 72, 70)) + (255,)
    for x in range(4, 12):
        if x % 3:
            px[x, 12] = rgb((84, 86, 84)) + (255,)
    save(img, "gravestone")


def crate():
    img, px = new()
    rnd = random.Random(131)
    base = (150, 112, 70)
    for y in range(16):
        for x in range(16):
            k = 1.0 + math.sin(y * 0.8 + x * 0.1) * 0.05 + (rnd.random() - 0.5) * 0.06
            if y % 5 == 4:
                k = 0.72
            px[x, y] = rgb(base, k) + (255,)
    for i in range(16):                                                   # the frame and a brace
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            px[x, y] = rgb((96, 68, 42)) + (255,)
        for (x, y) in ((i, 1), (1, i)):
            px[x, y] = rgb((120, 88, 54)) + (255,)
        if 1 < i < 14:
            px[i, 15 - i] = rgb((110, 80, 50)) + (255,)
            px[i, 14 - i] = rgb((130, 96, 60)) + (255,)
    for (x, y) in ((2, 2), (13, 2), (2, 13), (13, 13)):
        px[x, y] = (60, 58, 60, 255)                                      # nails
    save(img, "crate")


def sewer_grate():
    img, px = new()
    rnd = random.Random(141)
    for y in range(16):
        for x in range(16):
            frame = x in (0, 1, 14, 15) or y in (0, 1, 14, 15)
            slot = (y - 2) % 3 == 2
            if frame or not slot:
                k = 1.0 + (rnd.random() - 0.5) * 0.12
                c = (52, 54, 60) if not frame else (64, 66, 72)
                if y in (0, 2) or (not frame and (y - 2) % 3 == 0):
                    k += 0.18
                px[x, y] = rgb(c, k) + (255,)
            else:
                px[x, y] = (8, 9, 10, 255)
    save(img, "sewer_grate")


def awning(name, a, b):
    img, px = new()
    rnd = random.Random(hash(name) & 0xFFFF)
    for y in range(16):
        for x in range(16):
            c = a if (x // 2) % 2 == 0 else b
            k = 1.0 + (rnd.random() - 0.5) * 0.05 - 0.15 * (y / 15)
            px[x, y] = rgb(c, k) + (255,)
    save(img, name)


def awnings():
    awning("red_awning", (150, 40, 38), (222, 210, 186))
    awning("green_awning", (40, 92, 64), (222, 210, 186))
    awning("blue_awning", (44, 60, 104), (222, 210, 186))


def bench_textures():
    """Slatted park-bench wood and its cast-iron ends."""
    img, px = new()
    rnd = random.Random(171)
    for y in range(16):
        for x in range(16):
            slat = (y // 3) % 2 == 0 and y % 3 != 2
            base = (122, 88, 56) if slat else (52, 40, 30)
            px[x, y] = rgb(base, 1.0 + (rnd.random() - 0.5) * 0.12 + (0.06 if y % 3 == 0 else 0)) + (255,)
    save(img, "bench_wood")
    img, px = new()
    for y in range(16):
        for x in range(16):
            k = 1.0 + (rnd.random() - 0.5) * 0.1 + (0.12 if (x + y) % 5 == 0 else 0)
            px[x, y] = rgb((44, 46, 50), k) + (255,)
    save(img, "bench_iron")


def post_box():
    """A red cast-iron pillar box: a black band, the letter slot, the royal-red paint worn at the edges."""
    img, px = new()
    rnd = random.Random(173)
    for y in range(16):
        for x in range(16):
            k = 1.0 + (rnd.random() - 0.5) * 0.1
            if x in (0, 15):
                k -= 0.15
            c = (168, 30, 28)
            if y in (3, 4) and 4 <= x <= 11:
                c, k = (20, 18, 18), 1.0             # the slot
            elif y == 2 and 4 <= x <= 11:
                c = (196, 52, 44)                    # slot hood
            elif y >= 14:
                c = (34, 30, 30)                     # black plinth band
            elif 7 <= y <= 9 and 5 <= x <= 10:
                c = (186, 150, 70) if (x + y) % 2 else (150, 116, 50)   # a small gilt plate
            px[x, y] = rgb(c, k) + (255,)
    save(img, "post_box")


def draw_all():
    bricks("stock_bricks", [(178, 152, 106), (164, 140, 98), (188, 162, 114), (150, 130, 94), (172, 148, 104)],
           (122, 116, 104), seed=151, soot=0.35)
    bricks("red_bricks", [(150, 62, 48), (138, 56, 44), (162, 72, 54), (128, 52, 42), (146, 66, 50)],
           (170, 162, 150), seed=153, soot=0.12)
    ashlar("ashlar", 155)
    carved_ashlar()
    pillar()
    roof_tiles("clay_roof_tiles", [(150, 70, 50), (140, 64, 46), (160, 80, 58), (128, 58, 42)], 157)
    flagstones()
    kerbstone()
    planks("weathered_planks", (104, 92, 76), 159)
    timber()
    sash("sash_window", False)
    sash("lit_sash_window", True)
    shop_window()
    clock_face()
    gravestone()
    crate()
    sewer_grate()
    awnings()
    bench_textures()
    post_box()


# ------------------------------------------------------------------ JSON

def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2)


def bs(name, obj):
    wjson(os.path.join(ASSETS, "blockstates", name + ".json"), obj)


def model(name, obj):
    wjson(os.path.join(ASSETS, "models", "block", name + ".json"), obj)


def item_model(name, parent=None, layer=None):
    obj = {"parent": parent or f"beyonder:block/{name}"}
    if layer:
        obj = {"parent": "minecraft:item/generated", "textures": {"layer0": layer}}
    wjson(os.path.join(ASSETS, "models", "item", name + ".json"), obj)


def loot(name, slab=False):
    entry = {"type": "minecraft:item", "name": f"beyonder:{name}"}
    if slab:
        entry["functions"] = [{"function": "minecraft:set_count", "add": False, "count": 2.0, "conditions": [
            {"condition": "minecraft:block_state_property", "block": f"beyonder:{name}", "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]
    wjson(os.path.join(DATA, "beyonder", "loot_table", "blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [entry], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
        "random_sequence": f"beyonder:blocks/{name}"})


def cube(name, tex=None):
    tex = tex or name
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"beyonder:block/{tex}"}})
    bs(name, {"variants": {"": {"model": f"beyonder:block/{name}"}}})
    item_model(name)


def stairs(name, tex):
    t = {"bottom": f"beyonder:block/{tex}", "top": f"beyonder:block/{tex}", "side": f"beyonder:block/{tex}"}
    model(name, {"parent": "minecraft:block/stairs", "textures": t})
    model(name + "_inner", {"parent": "minecraft:block/inner_stairs", "textures": t})
    model(name + "_outer", {"parent": "minecraft:block/outer_stairs", "textures": t})
    variants = {}
    yrot = {"east": 0, "south": 90, "west": 180, "north": 270}
    for facing in ("east", "north", "south", "west"):
        for half in ("bottom", "top"):
            for shape in ("inner_left", "inner_right", "outer_left", "outer_right", "straight"):
                m = name + ("_inner" if shape.startswith("inner") else "_outer" if shape.startswith("outer") else "")
                y = yrot[facing]
                if shape in ("inner_left", "outer_left"):
                    y = (y + 270) % 360
                x = 0
                if half == "top":
                    x = 180
                    if shape in ("inner_left", "outer_left"):
                        y = (y + 90) % 360
                    elif shape in ("inner_right", "outer_right"):
                        y = (y + 90) % 360
                v = {"model": f"beyonder:block/{m}"}
                if x:
                    v["x"] = x
                if y:
                    v["y"] = y
                if x or y:
                    v["uvlock"] = True
                variants[f"facing={facing},half={half},shape={shape}"] = v
    bs(name, {"variants": variants})
    item_model(name)


def slab(name, tex, full):
    t = {"bottom": f"beyonder:block/{tex}", "top": f"beyonder:block/{tex}", "side": f"beyonder:block/{tex}"}
    model(name, {"parent": "minecraft:block/slab", "textures": t})
    model(name + "_top", {"parent": "minecraft:block/slab_top", "textures": t})
    bs(name, {"variants": {"type=bottom": {"model": f"beyonder:block/{name}"},
                           "type=top": {"model": f"beyonder:block/{name}_top"},
                           "type=double": {"model": f"beyonder:block/{full}"}}})
    item_model(name)


def wall(name, tex):
    t = {"wall": f"beyonder:block/{tex}"}
    model(name + "_post", {"parent": "minecraft:block/template_wall_post", "textures": t})
    model(name + "_side", {"parent": "minecraft:block/template_wall_side", "textures": t})
    model(name + "_side_tall", {"parent": "minecraft:block/template_wall_side_tall", "textures": t})
    model(name + "_inventory", {"parent": "minecraft:block/wall_inventory", "textures": t})
    parts = [{"when": {"up": "true"}, "apply": {"model": f"beyonder:block/{name}_post"}}]
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        for h, m in (("low", "_side"), ("tall", "_side_tall")):
            a = {"model": f"beyonder:block/{name}{m}", "uvlock": True}
            if y:
                a["y"] = y
            parts.append({"when": {d: h}, "apply": a})
    bs(name, {"multipart": parts})
    item_model(name, parent=f"beyonder:block/{name}_inventory")


def pillar_block(name, side, end):
    model(name, {"parent": "minecraft:block/cube_column", "textures": {"end": f"beyonder:block/{end}", "side": f"beyonder:block/{side}"}})
    model(name + "_horizontal", {"parent": "minecraft:block/cube_column_horizontal",
                                 "textures": {"end": f"beyonder:block/{end}", "side": f"beyonder:block/{side}"}})
    bs(name, {"variants": {"axis=y": {"model": f"beyonder:block/{name}"},
                           "axis=z": {"model": f"beyonder:block/{name}_horizontal", "x": 90},
                           "axis=x": {"model": f"beyonder:block/{name}_horizontal", "x": 90, "y": 90}}})
    item_model(name)


def horizontal(name, model_name):
    bs(name, {"variants": {f"facing={f}": ({"model": f"beyonder:block/{model_name}"} | ({"y": y} if y else {}))
                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})


def element(frm, to, tex, rotation=None, faces=("north", "south", "east", "west", "up", "down")):
    e = {"from": frm, "to": to, "faces": {f: {"texture": tex} for f in faces}}
    if rotation:
        e["rotation"] = rotation
    return e


def write_all_json():
    names = {}

    def reg(name, title):
        names[name] = title

    # building stone families
    for fam, title in (("stock_bricks", "Stock Bricks"), ("red_bricks", "Red Bricks"), ("ashlar", "Ashlar")):
        single = fam[:-1] if fam.endswith("bricks") else fam   # stock_brick_stairs, ashlar_stairs
        cube(fam)
        loot(fam)
        reg(fam, title)
        for kind, fn in (("stairs", stairs), ("slab", None), ("wall", wall)):
            n = f"{single}_{kind}"
            if kind == "slab":
                slab(n, fam, fam)
                loot(n, slab=True)
            else:
                fn(n, fam)
                loot(n)
            reg(n, f"{title[:-1] if title.endswith('s') else title} {kind.capitalize()}")
    cube("carved_ashlar"); loot("carved_ashlar"); reg("carved_ashlar", "Carved Ashlar")
    pillar_block("ashlar_pillar", "ashlar_pillar", "ashlar_pillar_top"); loot("ashlar_pillar"); reg("ashlar_pillar", "Ashlar Pillar")
    cube("clay_roof_tiles"); loot("clay_roof_tiles"); reg("clay_roof_tiles", "Clay Roof Tiles")
    stairs("clay_roof_stairs", "clay_roof_tiles"); loot("clay_roof_stairs"); reg("clay_roof_stairs", "Clay Roof Stairs")
    slab("clay_roof_slab", "clay_roof_tiles", "clay_roof_tiles"); loot("clay_roof_slab", slab=True); reg("clay_roof_slab", "Clay Roof Slab")
    cube("flagstones"); loot("flagstones"); reg("flagstones", "Flagstones")
    slab("flagstone_slab", "flagstones", "flagstones"); loot("flagstone_slab", slab=True); reg("flagstone_slab", "Flagstone Slab")
    cube("kerbstone"); loot("kerbstone"); reg("kerbstone", "Kerbstone")
    cube("weathered_planks"); loot("weathered_planks"); reg("weathered_planks", "Weathered Planks")
    stairs("weathered_plank_stairs", "weathered_planks"); loot("weathered_plank_stairs"); reg("weathered_plank_stairs", "Weathered Plank Stairs")
    slab("weathered_plank_slab", "weathered_planks", "weathered_planks"); loot("weathered_plank_slab", slab=True); reg("weathered_plank_slab", "Weathered Plank Slab")
    pillar_block("dark_timber", "dark_timber", "dark_timber_top"); loot("dark_timber"); reg("dark_timber", "Dark Timber Beam")
    cube("sash_window"); loot("sash_window"); reg("sash_window", "Sash Window")
    cube("lit_sash_window"); loot("lit_sash_window"); reg("lit_sash_window", "Lit Sash Window")
    model("shop_window", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                          "textures": {"all": "beyonder:block/shop_window"}})
    bs("shop_window", {"variants": {"": {"model": "beyonder:block/shop_window"}}}); item_model("shop_window")
    loot("shop_window"); reg("shop_window", "Shop Window")
    model("clock_face", {"parent": "minecraft:block/orientable", "textures": {
        "top": "beyonder:block/ashlar", "front": "beyonder:block/clock_face", "side": "beyonder:block/ashlar"}})
    horizontal("clock_face", "clock_face"); item_model("clock_face"); loot("clock_face"); reg("clock_face", "Clock Face")
    cube("crate"); loot("crate"); reg("crate", "Crate")
    model("sewer_grate", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": "beyonder:block/sewer_grate", "bottom": "beyonder:block/tingen_cobbles", "side": "beyonder:block/tingen_cobbles"}})
    bs("sewer_grate", {"variants": {"": {"model": "beyonder:block/sewer_grate"}}}); item_model("sewer_grate")
    loot("sewer_grate"); reg("sewer_grate", "Sewer Grate")

    # gravestone: a stone on a plinth with a rounded top
    model("gravestone", {"parent": "minecraft:block/block", "textures": {"particle": "beyonder:block/gravestone", "s": "beyonder:block/gravestone"},
                         "elements": [element([2, 0, 4], [14, 2, 12], "#s"), element([3, 2, 7], [13, 12, 9], "#s"),
                                      element([4, 12, 7], [12, 13, 9], "#s"), element([5.5, 13, 7], [10.5, 14, 9], "#s")]})
    horizontal("gravestone", "gravestone"); item_model("gravestone"); loot("gravestone"); reg("gravestone", "Gravestone")

    # awnings: a striped canopy sloping out from the wall, with a valance at the front
    for colour in ("red", "green", "blue"):
        n = f"{colour}_awning"
        model(n, {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                  "textures": {"particle": f"beyonder:block/{n}", "a": f"beyonder:block/{n}"},
                  "elements": [element([0, 14, 0], [16, 15, 16], "#a", {"origin": [8, 15, 16], "axis": "x", "angle": -22.5}),
                               element([0, 6.5, 0.5], [16, 9.5, 1.5], "#a")]})
        horizontal(n, n); item_model(n); loot(n); reg(n, f"{colour.capitalize()} Awning")

    # wall gas lamp: a bracket off the wall with a glass lantern on its end
    model("wall_gas_lamp", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "ambientocclusion": False,
                            "textures": {"particle": "beyonder:block/gas_lamp_iron", "iron": "beyonder:block/gas_lamp_iron",
                                         "glass": "beyonder:block/gas_lamp_glass"},
                            "elements": [element([6.5, 2, 15], [9.5, 8, 16], "#iron"),
                                         element([7.5, 4, 8], [8.5, 5, 15], "#iron"),
                                         element([7.5, 5, 13], [8.5, 7, 14], "#iron"),
                                         element([5.5, 5, 5.5], [10.5, 6, 10.5], "#iron"),
                                         element([6, 6, 6], [10, 12, 10], "#glass"),
                                         element([5.5, 12, 5.5], [10.5, 13, 10.5], "#iron"),
                                         element([7, 13, 7], [9, 14.5, 9], "#iron")]})
    horizontal("wall_gas_lamp", "wall_gas_lamp"); item_model("wall_gas_lamp"); loot("wall_gas_lamp"); reg("wall_gas_lamp", "Wall Gas Lamp")

    # park bench: slatted seat and back between cast-iron ends; the sitter faces north
    model("park_bench", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout",
                         "textures": {"particle": "beyonder:block/bench_wood", "w": "beyonder:block/bench_wood", "i": "beyonder:block/bench_iron"},
                         "elements": [element([1, 6, 3], [15, 7.5, 12], "#w"),
                                      element([1, 8, 12], [15, 15, 13.5], "#w"),
                                      element([0, 0, 3], [1.5, 7, 4.5], "#i"), element([0, 0, 11], [1.5, 15, 13.5], "#i"),
                                      element([14.5, 0, 3], [16, 7, 4.5], "#i"), element([14.5, 0, 11], [16, 15, 13.5], "#i"),
                                      element([0, 6, 3], [1.5, 8.5, 12], "#i"), element([14.5, 6, 3], [16, 8.5, 12], "#i")]})
    horizontal("park_bench", "park_bench"); item_model("park_bench"); loot("park_bench"); reg("park_bench", "Park Bench")
    # pillar box: a red cylinder-ish post with a cap; the slot faces north
    model("post_box", {"parent": "minecraft:block/block",
                       "textures": {"particle": "beyonder:block/post_box", "p": "beyonder:block/post_box"},
                       "elements": [element([4, 0, 4], [12, 14, 12], "#p"),
                                    element([3.5, 0, 3.5], [12.5, 1.5, 12.5], "#p"),
                                    element([3, 14, 3], [13, 15.5, 13], "#p"),
                                    element([5, 15.5, 5], [11, 16, 11], "#p")]})
    horizontal("post_box", "post_box"); item_model("post_box"); loot("post_box"); reg("post_box", "Pillar Box")

    # tags
    stone = [n for n in names if not any(k in n for k in ("planks", "plank", "timber", "crate", "awning", "window"))]
    wood = [n for n in names if any(k in n for k in ("planks", "plank", "timber", "crate"))]
    def add_tag(path, values):
        full = os.path.join(DATA, path)
        old = json.load(open(full)) if os.path.exists(full) else {"replace": False, "values": []}
        for v in values:
            if v not in old["values"]:
                old["values"].append(v)
        wjson(full, old)
    add_tag("minecraft/tags/block/mineable/pickaxe.json", [f"beyonder:{n}" for n in stone])
    add_tag("minecraft/tags/block/mineable/axe.json", [f"beyonder:{n}" for n in wood])
    add_tag("minecraft/tags/block/stairs.json", [f"beyonder:{n}" for n in names if n.endswith("_stairs")])
    add_tag("minecraft/tags/block/slabs.json", [f"beyonder:{n}" for n in names if n.endswith("_slab")])
    add_tag("minecraft/tags/block/walls.json", [f"beyonder:{n}" for n in names if n.endswith("_wall")])
    add_tag("minecraft/tags/item/stairs.json", [f"beyonder:{n}" for n in names if n.endswith("_stairs")])
    add_tag("minecraft/tags/item/slabs.json", [f"beyonder:{n}" for n in names if n.endswith("_slab")])
    add_tag("minecraft/tags/item/walls.json", [f"beyonder:{n}" for n in names if n.endswith("_wall")])
    # names
    lp = os.path.join(ASSETS, "lang", "en_us.json")
    lang = json.load(open(lp))
    for n, t in names.items():
        lang[f"block.beyonder.{n}"] = t
    with open(lp, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
    return names


if __name__ == "__main__":
    draw_all()
    names = write_all_json()
    print(len(names), "blocks:", " ".join(names))
