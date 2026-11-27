"""Preview renders: isometric views of volumes or canvas regions, and a top-down city map."""
import os
import numpy as np
from PIL import Image, ImageDraw
from world import P, KEEP, split

HERE = os.path.dirname(os.path.abspath(__file__))
TEX = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "beyonder", "textures", "block")

VANILLA = {
    "stone": (125, 125, 125), "dirt": (134, 96, 67), "grass_block": (95, 140, 60), "bedrock": (60, 60, 60),
    "water": (50, 80, 140), "gravel": (130, 124, 122), "sand": (219, 207, 163), "mud": (60, 57, 61),
    "spruce_planks": (114, 84, 48), "oak_planks": (162, 130, 78), "dark_oak_planks": (66, 43, 20),
    "birch_planks": (196, 179, 123), "mangrove_planks": (117, 54, 48), "spruce": (114, 84, 48), "oak": (162, 130, 78),
    "dark_oak": (66, 43, 20), "mangrove": (117, 54, 48), "birch": (196, 179, 123),
    "glass": (190, 220, 230), "glass_pane": (190, 220, 230), "iron_bars": (110, 110, 110),
    "lantern": (220, 170, 80), "flower_pot": (124, 68, 52), "chain": (60, 60, 70),
    "bricks": (150, 97, 83), "deepslate_bricks": (70, 70, 72), "deepslate_tiles": (54, 54, 56),
    "polished_deepslate": (72, 72, 74), "polished_blackstone_bricks": (40, 35, 42), "polished_blackstone": (48, 42, 50),
    "blackstone": (42, 36, 42), "smooth_quartz": (236, 230, 222), "quartz_bricks": (234, 229, 221), "calcite": (223, 224, 220),
    "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (115, 121, 105), "mossy_cobblestone": (110, 118, 94),
    "cobblestone": (127, 127, 127), "smooth_stone": (158, 158, 158), "polished_andesite": (132, 134, 133),
    "andesite": (136, 136, 136), "waxed_oxidized_cut_copper": (80, 160, 130), "oxidized_cut_copper": (80, 160, 130),
    "green_terracotta": (76, 83, 42), "black_terracotta": (37, 23, 16), "white_terracotta": (210, 178, 161),
    "oak_leaves": (60, 110, 40), "spruce_leaves": (50, 80, 50), "birch_leaves": (100, 140, 60), "dark_oak_leaves": (50, 90, 30),
    "oak_log": (110, 85, 50), "spruce_log": (58, 37, 16), "birch_log": (216, 215, 210), "dark_oak_log": (60, 46, 26),
    "hay_block": (166, 136, 38), "barrel": (110, 80, 45), "bookshelf": (117, 94, 59), "chest": (160, 110, 40),
    "rail": (110, 100, 90), "coal_block": (16, 16, 16), "iron_block": (220, 220, 220), "tuff": (108, 109, 102),
    "white_wool": (233, 236, 236), "red_wool": (160, 39, 34), "black_wool": (20, 21, 25), "hanging_roots": (160, 110, 90),
    "moss_carpet": (89, 109, 45), "lily_pad": (32, 128, 48), "dirt_path": (148, 122, 65), "coarse_dirt": (119, 85, 59),
    "packed_mud": (142, 106, 79), "mud_bricks": (137, 103, 79), "podzol": (92, 63, 24), "rooted_dirt": (144, 103, 76),
    "light_gray_concrete": (125, 125, 115), "gray_concrete": (55, 58, 62), "smooth_basalt": (72, 72, 78),
    "red_nether_bricks": (69, 7, 9), "nether_bricks": (44, 21, 26), "terracotta": (152, 94, 67),
    "red_terracotta": (143, 61, 46), "brown_terracotta": (77, 51, 35), "light_gray_terracotta": (135, 106, 97),
    "gray_terracotta": (57, 42, 35), "cyan_terracotta": (86, 91, 91), "blue_stained_glass_pane": (50, 70, 170),
    "red_stained_glass_pane": (150, 40, 40), "blue_stained_glass": (50, 70, 170), "red_stained_glass": (150, 40, 40),
    "yellow_stained_glass_pane": (200, 180, 50), "purple_stained_glass_pane": (120, 50, 160),
    "bell": (200, 160, 40), "campfire": (100, 60, 30), "torch": (240, 200, 90), "wall_torch": (240, 200, 90),
    "ladder": (130, 100, 60), "scaffolding": (170, 140, 80), "composter": (100, 70, 40), "cauldron": (60, 60, 60),
}


def _avg(path):
    im = Image.open(path).convert("RGBA").crop((0, 0, 16, 16))
    a = np.asarray(im).reshape(-1, 4).astype(float)
    m = a[:, 3] > 10
    if not m.any():
        return (128, 128, 128)
    return tuple(int(c) for c in a[m, :3].mean(0))


_COL = {}


def base_name(name):
    n = name.split(":", 1)[1]
    for suf in ("_stairs", "_slab", "_wall_hanging_sign", "_hanging_sign", "_wall_sign", "_sign", "_wall", "_fence_gate",
                "_fence", "_door", "_trapdoor", "_pressure_plate", "_button", "_carpet", "_pane"):
        if n.endswith(suf):
            n = n[: -len(suf)]
            break
    return name.split(":", 1)[0], n


FIX = {"stock_brick": "stock_bricks", "red_brick": "red_bricks", "soot_brick": "soot_bricks", "tingen_cobble": "tingen_cobbles",
       "slate_roof": "slate_roof_tiles", "clay_roof": "clay_roof_tiles", "weathered_plank": "weathered_planks",
       "flagstone": "flagstones", "deepslate_brick": "deepslate_bricks", "deepslate_tile": "deepslate_tiles",
       "polished_blackstone_brick": "polished_blackstone_bricks", "stone_brick": "stone_bricks",
       "mossy_stone_brick": "mossy_stone_bricks", "brick": "bricks", "lit_sash_window": "lit_sash_window",
       "waxed_oxidized_cut_copper": "waxed_oxidized_cut_copper", "red_awning": "red_awning", "nether_brick": "nether_bricks",
       "mud_brick": "mud_bricks"}


def color(i):
    if i in _COL:
        return _COL[i]
    name = P.name(i)
    ns, n = base_name(name)
    n = FIX.get(n, n)
    c = None
    if ns == "beyonder":
        for cand in (n, n + "s", n + "_tiles", n.replace("_lamp", "_lamp_iron"), "gas_lamp_iron" if "lamp" in n else n):
            p = os.path.join(TEX, cand + ".png")
            if os.path.exists(p):
                c = _avg(p)
                break
        if n == "lamp_post":
            c = (30, 30, 30)
    else:
        c = VANILLA.get(n)
        if c is None:
            for k, val in VANILLA.items():
                if k in n:
                    c = val
                    break
    if c is None:
        c = (200, 0, 200)
    if "lit_" in name or "lantern" in name or "gas_lamp" in name:
        c = tuple(min(255, int(x * 1.1 + 20)) for x in c)
    _COL[i] = c
    return c


def thin(i):
    n = P.name(i)
    return any(k in n for k in ("_pane", "iron_bars", "railing", "_fence", "lamp", "lantern", "_sign", "chain", "flower",
                                "torch", "door", "_wall", "awning", "rail", "ladder", "potted", "button", "carpet",
                                "lily", "fern", "grass", "poppy", "dandelion", "bush", "sapling"))


def iso(a, scale=6, path=None, bg=(32, 34, 38)):
    """Isometric render of a [y, z, x] id array seen from the north-west, so north (street) fronts show."""
    h, d, w = a.shape
    u = scale
    m = 20
    W = int((w + d) * u) + 2 * m
    oy = int((w + d) * u / 2 + h * u) + m
    H = oy + m + u
    ox = d * u + m
    img = Image.new("RGB", (W, H), bg)
    dr = ImageDraw.Draw(img)
    solid = (a != 0) & (a != KEEP)
    ys, zs, xs = np.nonzero(solid)
    keep = np.ones(len(ys), bool)
    inner = (ys < h - 1) & (zs > 0) & (xs > 0)
    yi, zi, xi = ys[inner], zs[inner], xs[inner]
    hidden = solid[yi + 1, zi, xi] & solid[yi, zi - 1, xi] & solid[yi, zi, xi - 1]
    keep[np.nonzero(inner)[0][hidden]] = False
    ys, zs, xs = ys[keep], zs[keep], xs[keep]
    order = np.lexsort((ys, -(xs + zs)))

    def P(x, y, z):
        return (ox + (x - z) * u, oy - (x + z) * u / 2 - y * u)

    for n in order:
        x, y, z = int(xs[n]), int(ys[n]), int(zs[n])
        i = int(a[y, z, x])
        c = color(i)
        k = 0.5 if thin(i) else 1.0
        y1 = y + 1
        y0 = y1 - k
        top = c
        north = tuple(int(v * 0.8) for v in c)
        west = tuple(int(v * 0.62) for v in c)
        dr.polygon([P(x, y1, z), P(x + 1, y1, z), P(x + 1, y1, z + 1), P(x, y1, z + 1)], fill=top)
        dr.polygon([P(x, y0, z), P(x + 1, y0, z), P(x + 1, y1, z), P(x, y1, z)], fill=north)
        dr.polygon([P(x, y0, z), P(x, y0, z + 1), P(x, y1, z + 1), P(x, y1, z)], fill=west)
    if path:
        img.save(path)
    return img


def topdown(canvas, path, scale=1):
    """Map of the top block in each column, shaded by height."""
    a = canvas.a
    h, d, w = a.shape
    solid = a != 0
    # index of the highest non-air block per column
    rev = solid[::-1]
    top = h - 1 - np.argmax(rev, axis=0)
    has = solid.any(axis=0)
    ids = np.take_along_axis(a, top[None], axis=0)[0]
    uniq = np.unique(ids)
    lut = np.zeros((int(uniq.max()) + 1, 3), np.float32)
    for i in uniq:
        lut[i] = color(int(i))
    rgb = lut[ids]
    shade = np.clip(0.75 + (top - canvas.street) / 120.0, 0.55, 1.25)[..., None]
    rgb = np.clip(rgb * shade, 0, 255).astype(np.uint8)
    rgb[~has] = 0
    im = Image.fromarray(rgb, "RGB")
    if scale != 1:
        im = im.resize((w * scale, d * scale), Image.NEAREST)
    im.save(path)
    return im


def elevation(a, scale=8, path=None, depth_shade=0.07):
    """Front (north) elevation: the first solid block seen from the street in each (x, y) cell."""
    h, d, w = a.shape
    solid = (a != 0) & (a != KEEP)
    first = np.argmax(solid, axis=1)          # [h, w] first z hit
    has = solid.any(axis=1)
    ids = np.take_along_axis(a, first[:, None, :], axis=1)[:, 0, :]
    img = np.zeros((h, w, 3), np.float32) + 40
    for y in range(h):
        for x in range(w):
            if has[y, x]:
                c = np.array(color(int(ids[y, x])), np.float32)
                img[y, x] = c * max(0.45, 1 - first[y, x] * depth_shade)
    im = Image.fromarray(img[::-1].astype(np.uint8), "RGB").resize((w * scale, h * scale), Image.NEAREST)
    if path:
        im.save(path)
    return im
