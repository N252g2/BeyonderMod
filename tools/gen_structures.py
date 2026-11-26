"""Designs Beyonder's Victorian buildings and writes them as structure files.

Run from the repo root:  python3 tools/gen_structures.py [--preview DIR]
Output: src/main/resources/data/beyonder/structure/tingen/*.nbt  (place with /beyonder build <name>)

Every building is built voxel by voxel here, so it can be re-tuned and re-generated. Fronts face north
(z = 0); the build command rotates them to face the player.
"""
import gzip
import io
import math
import os
import struct
import sys

ROOT = os.path.join(os.path.dirname(__file__), "..")
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "beyonder", "structure", "tingen")
DATA_VERSION = 3955  # Minecraft 1.21.1

AIR = ("minecraft:air", {})


def b(name, **props):
    if ":" not in name:
        name = "minecraft:" + name
    return (name, {k: str(v).lower() for k, v in props.items()})


# ------------------------------------------------------------------ building canvas

class Build:
    def __init__(self, sx, sy, sz):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.v = {}

    def set(self, x, y, z, block):
        if 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz:
            self.v[(x, y, z)] = block

    def get(self, x, y, z):
        return self.v.get((x, y, z), AIR)

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, block)

    def walls(self, x0, y0, z0, x1, y1, z1, block):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, block)
                self.set(x, y, z1, block)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, block)
                self.set(x1, y, z, block)

    def paste(self, other, ox, oy, oz, turn=0):
        """Copy another build in, rotated clockwise by turn * 90 degrees."""
        for (x, y, z), blk in other.v.items():
            nx, nz = x, z
            w, d = other.sx, other.sz
            for _ in range(turn % 4):
                nx, nz = d - 1 - nz, nx
                w, d = d, w
            self.set(ox + nx, oy + y, oz + nz, rotate_block(blk, turn))


CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


def rotate_block(blk, turn):
    name, props = blk
    if "facing" not in props or props["facing"] in ("up", "down"):
        return blk
    f = props["facing"]
    for _ in range(turn % 4):
        f = CW[f]
    p = dict(props)
    p["facing"] = f
    return (name, p)


# ------------------------------------------------------------------ connections (panes, bars, walls, fences)

PANES = ("glass_pane", "iron_bars")
NOT_SOLID = ("air", "carpet", "lantern", "ladder", "door", "trapdoor", "torch", "candle", "flower", "slab",
             "stairs", "gas_lamp", "lamp_post", "bell", "pressure_plate", "button", "rail")


def is_pane(name):
    return name.endswith(PANES)


def is_wall(name):
    return name.endswith("_wall") and "sign" not in name


def is_fence(name):
    return name.endswith("_fence")


def connects(name):
    return not any(k in name for k in NOT_SOLID)


def finish(build):
    """Sets pane, bar, wall and fence connections from their neighbours, as the game would."""
    dirs = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
    for (x, y, z), (name, props) in list(build.v.items()):
        if not (is_pane(name) or is_wall(name) or is_fence(name)):
            continue
        p = dict(props)
        linked = {}
        for d, (dx, dz) in dirs.items():
            n = build.get(x + dx, y, z + dz)[0]
            if is_pane(name):
                ok = is_pane(n) or connects(n)
            elif is_wall(name):
                ok = is_wall(n) or connects(n)
            else:
                ok = is_fence(n) or (connects(n) and not is_pane(n))
            linked[d] = ok
        if is_wall(name):
            for d in dirs:
                p[d] = "low" if linked[d] else "none"
            straight = (linked["north"] and linked["south"] and not linked["east"] and not linked["west"]) or \
                       (linked["east"] and linked["west"] and not linked["north"] and not linked["south"])
            p["up"] = "false" if straight and build.get(x, y + 1, z) == AIR else "true"
        else:
            for d in dirs:
                p[d] = "true" if linked[d] else "false"
        build.v[(x, y, z)] = (name, p)
    return build


# ------------------------------------------------------------------ palette

STONE_BASE = b("stone_bricks")
BRICK = b("beyonder:soot_bricks")
TRIM = b("polished_andesite")
FLOOR = b("spruce_planks")
DARK = b("dark_oak_planks")
ROOF = b("deepslate_tiles")
LIT = b("beyonder:lit_window")
PANE = b("glass_pane")
COBBLE = b("beyonder:tingen_cobbles")
WALK = b("smooth_stone")
CURB = b("stone_bricks")


def roof_stair(facing, half="bottom"):
    return b("deepslate_tile_stairs", facing=facing, half=half, shape="straight")


def door(x, y, z, build, facing="south", hinge="left", wood="dark_oak"):
    build.set(x, y, z, b(f"{wood}_door", facing=facing, half="lower", hinge=hinge, open="false", powered="false"))
    build.set(x, y + 1, z, b(f"{wood}_door", facing=facing, half="upper", hinge=hinge, open="false", powered="false"))


def gas_lamp(build, x, y, z):
    build.set(x, y, z, b("beyonder:lamp_post"))
    build.set(x, y + 1, z, b("beyonder:lamp_post"))
    build.set(x, y + 2, z, b("beyonder:gas_lamp"))


def gable_roof(build, x0, x1, z0, z1, y, block_fill=BRICK):
    """A pitched roof whose ridge runs along x; gables at x0 and x1 are filled with brick."""
    depth = z1 - z0 + 1
    for k in range(depth // 2 + 1):
        yy = y + k
        zf, zb = z0 + k, z1 - k
        if zf > zb:
            break
        for x in range(x0 - 1, x1 + 2):
            if zf == zb:
                build.set(x, yy, zf, b("deepslate_tile_slab", type="bottom"))
            else:
                build.set(x, yy, zf, roof_stair("south"))
                build.set(x, yy, zb, roof_stair("north"))
        # gable ends
        for z in range(zf + 1, zb):
            build.set(x0, yy, z, block_fill)
            build.set(x1, yy, z, block_fill)


def chimney(build, x, z, y0, y1):
    build.fill(x, y0, z, x, y1 - 1, z, BRICK)
    build.set(x, y1, z, b("beyonder:chimney"))


# ------------------------------------------------------------------ buildings

def terrace_house(variant=0):
    """A narrow two-storey brick row house with a lit front, iron railings and a smoking chimney."""
    W, D = 7, 13
    h = Build(W, 18, D)
    zf = 2  # house front wall (z 0-1 is the front area)
    # front area: railing and a step
    for x in range(W):
        if x != 3:
            h.set(x, 1, 0, b("iron_bars"))
        h.set(x, 0, 0, WALK)
        h.set(x, 0, 1, WALK)
    h.set(3, 1, 1, b("stone_brick_slab", type="bottom"))
    # foundation and floors
    h.fill(0, 0, zf, W - 1, 0, D - 1, STONE_BASE)
    h.fill(1, 0, zf + 1, W - 2, 0, D - 2, FLOOR)
    h.fill(1, 5, zf + 1, W - 2, 5, D - 2, FLOOR)
    h.fill(1, 10, zf + 1, W - 2, 10, D - 2, FLOOR)
    # walls
    h.walls(0, 1, zf, W - 1, 9, D - 1, BRICK)
    for x in range(W):  # stone bands at each floor
        h.set(x, 5, zf, TRIM)
        h.set(x, 10, zf, TRIM)
        h.set(x, 5, D - 1, TRIM)
    # front: door, windows (lit on some floors, dark glass on others for variety)
    door(3, 1, zf, h, facing="south")
    h.set(3, 3, zf, b("dark_oak_trapdoor", facing="north", half="top", open="false"))  # fanlight hood
    ground = LIT if variant % 2 == 0 else PANE
    upper = PANE if variant % 2 == 0 else LIT
    for x in (1, 5):
        h.fill(x, 2, zf, x, 3, zf, ground)
        h.set(x, 1, zf, TRIM)  # sill
    for x in (1, 3, 5):
        h.fill(x, 6, zf, x, 8, zf, upper)
    # window lintels
    for x in (1, 3, 5):
        h.set(x, 9, zf, TRIM)
    # back windows
    for x in (2, 4):
        h.fill(x, 2, D - 1, x, 3, D - 1, PANE)
        h.fill(x, 6, D - 1, x, 8, D - 1, LIT if (x + variant) % 2 else PANE)
    # roof and chimney
    gable_roof(h, 0, W - 1, zf, D - 1, 10)
    chimney(h, 1 if variant % 2 == 0 else W - 2, D - 3, 1, 16)
    # inside: ladder up, a lantern, a little furniture
    for y in range(1, 10):
        h.set(W - 2, y, D - 2, b("ladder", facing="north"))
    h.set(3, 4, zf + 5, b("lantern", hanging="true"))
    h.set(3, 9, zf + 5, b("lantern", hanging="true"))
    h.fill(1, 1, D - 2, 2, 2, D - 2, b("bookshelf"))
    h.set(1, 1, zf + 1, b("red_carpet"))
    h.set(2, 1, zf + 4, b("spruce_fence"))
    h.set(2, 2, zf + 4, b("spruce_pressure_plate"))
    h.fill(1, 6, zf + 3, 2, 6, zf + 3, b("brown_carpet"))
    return finish(h)


def corner_shop():
    """A shop with a big glass front under an awning, and a flat above it behind a parapet."""
    W, D = 9, 13
    s = Build(W, 14, D)
    zf = 2
    for x in range(W):
        s.set(x, 0, 0, WALK)
        s.set(x, 0, 1, WALK)
    s.fill(0, 0, zf, W - 1, 0, D - 1, STONE_BASE)
    s.fill(1, 0, zf + 1, W - 2, 0, D - 2, b("dark_oak_planks"))
    s.fill(1, 5, zf + 1, W - 2, 5, D - 2, FLOOR)
    s.walls(0, 1, zf, W - 1, 9, D - 1, BRICK)
    # shop front: dark timber pillars and big panes
    for x in range(W):
        s.set(x, 1, zf, DARK)
        s.set(x, 4, zf, DARK)
        s.set(x, 2, zf, PANE)
        s.set(x, 3, zf, PANE)
    for x in (0, W - 1):
        s.fill(x, 1, zf, x, 4, zf, DARK)
    door(4, 1, zf, s, facing="south")
    s.set(4, 3, zf, DARK)
    # awning: a row of slabs over the pavement
    for x in range(W):
        s.set(x, 4, zf - 1, b("spruce_slab", type="top"))
        s.set(x, 4, zf - 2, b("spruce_slab", type="top"))
    s.set(0, 3, 0, b("lantern", hanging="true"))
    s.set(W - 1, 3, 0, b("lantern", hanging="true"))
    # upper floor
    for x in range(W):
        s.set(x, 5, zf, TRIM)
    for x in (1, 4, 7):
        s.fill(x, 6, zf, x, 8, zf, LIT if x != 4 else PANE)
    # flat roof behind a brick parapet
    s.fill(0, 10, zf, W - 1, 10, D - 1, b("smooth_stone"))
    for x in range(W):
        s.set(x, 11, zf, b("beyonder:soot_brick_wall"))
        s.set(x, 11, D - 1, b("beyonder:soot_brick_wall"))
    for z in range(zf, D):
        s.set(0, 11, z, b("beyonder:soot_brick_wall"))
        s.set(W - 1, 11, z, b("beyonder:soot_brick_wall"))
    chimney(s, W - 2, D - 2, 11, 13)
    # inside: counter, shelves, a lamp
    for x in range(2, 7):
        s.set(x, 1, zf + 6, b("spruce_planks"))
    s.fill(1, 1, D - 2, W - 2, 3, D - 2, b("bookshelf"))
    s.set(4, 4, zf + 4, b("lantern", hanging="true"))
    for y in range(1, 10):
        s.set(1, y, D - 3, b("ladder", facing="east"))
    return finish(s)


def street(length=16):
    """A gas-lit street: cobbled road between kerbs and stone pavements, lamps on alternating sides."""
    W = 13
    s = Build(W, 4, length)
    for z in range(length):
        for x in range(W):
            if 3 <= x <= 9:
                s.set(x, 0, z, COBBLE)
            elif x in (2, 10):
                s.set(x, 0, z, CURB)
            else:
                s.set(x, 0, z, WALK)
    for z in range(2, length, 8):
        gas_lamp(s, 1, 1, z)
    for z in range(6, length, 8):
        gas_lamp(s, W - 2, 1, z)
    return finish(s)


def chapel():
    """A small chapel of the Evernight: tall purple windows, a steep roof, a bell tower and candles."""
    W, D = 11, 21
    c = Build(W, 30, D)
    zf = 4  # tower in front, z 0-3 forecourt
    stone = b("stone_bricks")
    dark_stone = b("polished_deepslate")
    # forecourt
    c.fill(0, 0, 0, W - 1, 0, zf - 1, WALK)
    # nave
    c.fill(0, 0, zf, W - 1, 0, D - 1, stone)
    c.fill(1, 0, zf + 1, W - 2, 0, D - 2, dark_stone)
    c.walls(0, 1, zf, W - 1, 9, D - 1, stone)
    # buttress columns
    for z in range(zf, D, 4):
        c.fill(0, 1, z, 0, 9, z, b("polished_deepslate"))
        c.fill(W - 1, 1, z, W - 1, 9, z, b("polished_deepslate"))
    # tall side windows
    for z in range(zf + 2, D - 1, 4):
        for x in (0, W - 1):
            c.fill(x, 3, z, x, 7, z, b("purple_stained_glass_pane"))
            c.fill(x, 3, z + 1, x, 7, z + 1, b("purple_stained_glass_pane"))
    # steep roof
    for k in range(W // 2 + 1):
        y = 10 + k
        for z in range(zf - 1, D + 1):
            if k == W // 2:
                c.set(k, y, z, b("deepslate_tile_slab", type="bottom"))
            else:
                c.set(k, y, z, roof_stair("east"))
                c.set(W - 1 - k, y, z, roof_stair("west"))
        for x in range(k + 1, W - 1 - k):
            c.set(x, y, zf, stone)
            c.set(x, y, D - 1, stone)
    # rose window at the back
    c.fill(4, 11, D - 1, 6, 13, D - 1, b("purple_stained_glass_pane"))
    # bell tower over the entrance
    tx0, tx1, tz0, tz1 = 3, 7, zf - 2, zf + 2
    c.walls(tx0, 1, tz0, tx1, 20, tz1, stone)
    c.fill(tx0 + 1, 1, tz0 + 1, tx1 - 1, 20, tz1 - 1, AIR)
    for y in (16, 17, 18):  # belfry openings
        for x in range(tx0 + 1, tx1):
            c.set(x, y, tz0, AIR)
            c.set(x, y, tz1, AIR)
        for z in range(tz0 + 1, tz1):
            c.set(tx0, y, z, AIR)
            c.set(tx1, y, z, AIR)
    c.fill(tx0 + 1, 15, tz0 + 1, tx1 - 1, 15, tz1 - 1, dark_stone)
    c.set(5, 16, zf, b("bell", attachment="floor", facing="north", powered="false"))
    # spire
    for k, y in enumerate(range(21, 27)):
        lo, hi = tx0 + min(k, 2), tx1 - min(k, 2)
        zlo, zhi = tz0 + min(k, 2), tz1 - min(k, 2)
        c.fill(lo, y, zlo, hi, y, zhi, ROOF)
    c.set(5, 27, zf, b("lightning_rod"))
    # entrance: double door and a lamp each side
    c.fill(4, 1, tz0, 6, 4, tz0, AIR)
    door(4, 1, tz0, c, facing="south", hinge="left")
    door(6, 1, tz0, c, facing="south", hinge="right")
    c.set(5, 1, tz0, AIR)
    c.set(5, 2, tz0, AIR)
    c.fill(4, 1, tz0 + 1, 6, 3, tz1 - 1, AIR)
    c.fill(4, 1, tz1, 6, 4, tz1, AIR)
    gas_lamp(c, 2, 1, 1)
    gas_lamp(c, 8, 1, 1)
    # pews, aisle and altar
    for z in range(zf + 4, D - 5, 2):
        for x in (2, 3, 7, 8):
            c.set(x, 1, z, b("spruce_stairs", facing="south", half="bottom", shape="straight"))
    for z in range(zf + 3, D - 3):
        c.set(5, 1, z, b("black_carpet"))
    c.fill(3, 1, D - 3, 7, 1, D - 3, b("polished_deepslate"))
    for x in (3, 5, 7):
        c.set(x, 2, D - 3, b("candle", candles="3", lit="true"))
    for z in range(zf + 4, D - 2, 5):
        c.set(5, 9, z, b("lantern", hanging="true"))
    return finish(c)


def tingen_street():
    """A ready-made Tingen street: row houses on both sides, a shop on each corner, gas lamps between."""
    house_w, house_d = 7, 13
    shop_w = 9
    houses = 4
    length = houses * house_w + shop_w
    W = house_d * 2 + 13
    t = Build(W, 18, length)
    road = street(length)
    t.paste(road, house_d, 0, 0)
    # west side faces east (turn 1), east side faces west (turn 3)
    z = 0
    for i in range(houses):
        t.paste(terrace_house(i), 0, 0, z, turn=1)
        t.paste(terrace_house(i + 1), house_d + 13, 0, z, turn=3)
        z += house_w
    t.paste(corner_shop(), 0, 0, z, turn=1)
    t.paste(corner_shop(), house_d + 13, 0, z, turn=3)
    return finish(t)


# ------------------------------------------------------------------ NBT writing

def _str(s):
    data = s.encode("utf-8")
    return struct.pack(">H", len(data)) + data


def _tag(tag_type, name, payload):
    return bytes([tag_type]) + _str(name) + payload


def _int(v):
    return struct.pack(">i", v)


def _list(elem_type, items):
    return bytes([elem_type]) + struct.pack(">i", len(items)) + b"".join(items)


def _compound(entries):
    return b"".join(entries) + b"\x00"


def to_nbt(build):
    palette, index = [], {}
    blocks = []
    for y in range(build.sy):
        for z in range(build.sz):
            for x in range(build.sx):
                blk = build.get(x, y, z)
                key = (blk[0], tuple(sorted(blk[1].items())))
                if key not in index:
                    index[key] = len(palette)
                    palette.append(blk)
                blocks.append(_compound([
                    _tag(9, "pos", _list(3, [_int(x), _int(y), _int(z)])),
                    _tag(3, "state", _int(index[key])),
                ]))
    pal = []
    for name, props in palette:
        entries = [_tag(8, "Name", _str(name))]
        if props:
            entries.append(_tag(10, "Properties", _compound([_tag(8, k, _str(v)) for k, v in sorted(props.items())])))
        pal.append(_compound(entries))
    root = _compound([
        _tag(3, "DataVersion", _int(DATA_VERSION)),
        _tag(9, "size", _list(3, [_int(build.sx), _int(build.sy), _int(build.sz)])),
        _tag(9, "palette", _list(10, pal)),
        _tag(9, "blocks", _list(10, blocks)),
        _tag(9, "entities", _list(10, [])),
    ])
    raw = _tag(10, "", root)
    buf = io.BytesIO()
    with gzip.GzipFile(fileobj=buf, mode="wb", mtime=0) as gz:
        gz.write(raw)
    return buf.getvalue()


# ------------------------------------------------------------------ preview

COLOURS = {
    "soot_bricks": (110, 62, 50), "stone_bricks": (125, 125, 125), "polished_andesite": (150, 152, 150),
    "spruce": (110, 80, 50), "dark_oak": (66, 44, 26), "deepslate_tile": (55, 55, 62), "lit_window": (240, 180, 90),
    "glass_pane": (170, 210, 220), "iron_bars": (90, 90, 95), "tingen_cobbles": (88, 92, 100),
    "smooth_stone": (160, 160, 160), "lamp_post": (40, 42, 48), "gas_lamp": (255, 200, 110), "chimney": (60, 30, 25),
    "purple_stained": (120, 60, 160), "polished_deepslate": (70, 70, 75), "bookshelf": (120, 90, 60),
    "carpet": (140, 30, 30), "lantern": (255, 190, 90), "ladder": (130, 100, 60), "bell": (230, 190, 60),
    "candle": (240, 230, 200), "lightning_rod": (200, 120, 80), "slab": (140, 140, 140), "door": (66, 44, 26),
}


def colour_of(name):
    for k, c in COLOURS.items():
        if k in name:
            return c
    return (160, 140, 120)


def preview(build, path):
    from PIL import Image, ImageDraw
    s = 6
    w = (build.sx + build.sz) * s * 2 + 40
    h = (build.sx + build.sz) * s + build.sy * s * 2 + 40
    img = Image.new("RGB", (w, h), (24, 22, 28))
    d = ImageDraw.Draw(img)
    ox, oy = build.sz * s * 2 + 20, build.sy * s * 2 + 20
    items = sorted((k for k, v in build.v.items() if v[0] != "minecraft:air"), key=lambda p: (p[0] + p[2], p[1]))
    for (x, y, z) in items:
        c = colour_of(build.v[(x, y, z)][0])
        px = ox + (x - z) * s * 2
        py = oy + (x + z) * s - y * s * 2
        top = [(px, py - s), (px + s * 2, py), (px, py + s), (px - s * 2, py)]
        left = [(px - s * 2, py), (px, py + s), (px, py + s * 3), (px - s * 2, py + s * 2)]
        right = [(px + s * 2, py), (px, py + s), (px, py + s * 3), (px + s * 2, py + s * 2)]
        d.polygon(top, fill=tuple(min(255, int(v * 1.15)) for v in c))
        d.polygon(left, fill=tuple(int(v * 0.8) for v in c))
        d.polygon(right, fill=tuple(int(v * 0.62) for v in c))
    img.save(path)


BUILDINGS = {
    "terrace_house": lambda: terrace_house(0),
    "terrace_house_b": lambda: terrace_house(1),
    "corner_shop": corner_shop,
    "street": lambda: street(16),
    "chapel": chapel,
    "tingen_street": tingen_street,
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    preview_dir = sys.argv[sys.argv.index("--preview") + 1] if "--preview" in sys.argv else None
    for name, make in BUILDINGS.items():
        build = make()
        data = to_nbt(build)
        with open(os.path.join(OUT, name + ".nbt"), "wb") as f:
            f.write(data)
        print(f"{name}: {build.sx}x{build.sy}x{build.sz}, {len(data)} bytes")
        if preview_dir:
            os.makedirs(preview_dir, exist_ok=True)
            preview(build, os.path.join(preview_dir, name + ".png"))
