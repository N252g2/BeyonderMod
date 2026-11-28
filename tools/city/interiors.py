"""Planned interiors. Every building gets a stair core that nothing is ever put in front of, a hallway kept
clear from its front door, and rooms furnished by what they are for: parlour and kitchen downstairs, bedrooms
above, a counter and shelves in a shop, a bar in a pub, offices, stores.

Coordinates are the building's own (north-facing Vol): interior x0..x1, z0..z1; floor k has its floor block at
y = h*k and its first air layer at y = h*k + 1.
"""
from world import S, AIR, KEEP

DIRS = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
BED_COLOURS = ("red", "brown", "blue", "gray", "green", "white", "light_gray", "cyan")


class Floor:
    """One storey: which cells are kept clear, and a helper to place things on the others."""

    def __init__(self, v, y, x0, x1, z0, z1):
        self.v, self.y, self.x0, self.x1, self.z0, self.z1 = v, y, x0, x1, z0, z1
        self.keep = set()

    def free(self, x, z):
        if not (self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1) or (x, z) in self.keep:
            return False
        return self.v.get(x, self.y, z) in (0, KEEP) and self.v.get(x, self.y + 1, z) in (0, KEEP)

    def put(self, x, z, state, keep_clear=True):
        if not self.free(x, z):
            return False
        self.v.set(x, self.y, z, state)
        if keep_clear:
            self.keep.add((x, z))
        return True

    def wall_side(self, x, z):
        """Which way the nearest wall is, from a cell next to one (for chairs and chests to face away from)."""
        if x == self.x0:
            return "west"
        if x == self.x1:
            return "east"
        if z == self.z0:
            return "north"
        if z == self.z1:
            return "south"
        return None

    def against_walls(self):
        cells = []
        for x in range(self.x0, self.x1 + 1):
            for z in range(self.z0, self.z1 + 1):
                if self.wall_side(x, z) and self.free(x, z):
                    cells.append((x, z))
        return cells


def opposite(d):
    return {"north": "south", "south": "north", "east": "west", "west": "east"}[d]


def stair_core(v, xa, xb, zlo, storeys, wood, h=4):
    """Switchback stairs in two columns, h+1 cells deep (h steps a flight, the last at the next floor's level).
    Even flights go up column xa toward +z, odd flights up column xb toward -z, so each flight starts beside
    the landing of the last. Returns the cells to keep clear."""
    for k in range(storeys - 1):
        y = h * k + 1
        if k % 2 == 0:
            steps = [(xa, zlo + i, y + i, "south") for i in range(h)]
        else:
            steps = [(xb, zlo + h - i, y + i, "north") for i in range(h)]
        for x, z, yy, f in steps:
            v.set(x, yy, z, S(f"{wood}_stairs", facing=f, half="bottom"))
        for x, z, yy, f in steps[:-1]:
            v.fill(x, yy + 1, z, x, yy + h - 1, z, AIR)
    cells = {(x, z) for x in (xa, xb) for z in range(zlo, zlo + h + 1)}
    cells.add((xa, zlo - 1))          # the foot of the first flight
    return cells


def bed(fl, x, z, facing, colour):
    """A bed whose head is at (x, z), lying back from ``facing``."""
    dx, dz = DIRS[facing]
    fx, fz = x - dx, z - dz
    if not (fl.free(x, z) and fl.free(fx, fz)):
        return False
    fl.v.set(x, fl.y, z, S(f"{colour}_bed", facing=facing, part="head"))
    fl.v.set(fx, fl.y, fz, S(f"{colour}_bed", facing=facing, part="foot"))
    fl.keep |= {(x, z), (fx, fz)}
    return True


def table_with_chairs(fl, x, z, wood, rng):
    if not fl.put(x, z, S(f"{wood}_fence")):
        return False
    fl.v.set(x, fl.y + 1, z, S(f"{wood}_pressure_plate"))
    for d, (dx, dz) in DIRS.items():
        if rng.random() < 0.55:
            # a chair's back is away from the table
            fl.put(x + dx, z + dz, S(f"{wood}_stairs", facing=d, half="bottom"))
    return True


def lamp(fl, x, z, h=4):
    y = fl.y + h - 2
    if fl.v.get(x, y, z) in (0, KEEP) and fl.v.get(x, y + 1, z) not in (0, KEEP):
        fl.v.set(x, y, z, S("lantern", hanging="true"))


def _state(s):
    name, props = s.split("[", 1)
    kv = dict(p.split("=") for p in props[:-1].split(","))
    return S(name, **kv)


PARLOUR = ["bookshelf", "{wood}_stairs[facing={wall}]", "potted_fern", "potted_spruce_sapling", "bookshelf",
           "jukebox", "chest[facing={away}]"]
KITCHEN = ["smoker[facing={away}]", "cauldron", "barrel[facing=up]", "crafting_table", "furnace[facing={away}]",
           "barrel[facing=up]"]
BEDROOM = ["chest[facing={away}]", "barrel[facing=up]", "bookshelf", "potted_fern", "loom[facing={away}]"]
OFFICE = ["bookshelf", "bookshelf", "lectern[facing={away}]", "chest[facing={away}]", "cartography_table", "potted_fern"]
STORE = ["b:crate", "barrel[facing=up]", "b:crate", "hay_block", "barrel[facing=up]"]


def furnish_room(fl, rng, kind, wood, n):
    items = {"parlour": PARLOUR, "kitchen": KITCHEN, "bedroom": BEDROOM, "office": OFFICE, "store": STORE}[kind]
    items = [it.replace("{wood}", wood) for it in items]
    cells = fl.against_walls()
    rng.shuffle(cells)
    placed = 0
    for x, z in cells:
        if placed >= n:
            break
        side = fl.wall_side(x, z)
        it = rng.choice(items).replace("{away}", opposite(side)).replace("{wall}", side)
        if fl.put(x, z, _state(it) if "[" in it else S(it)):
            placed += 1


# ---------------------------------------------------------------- whole buildings


def house(v, w, d, storeys, wood, rng, xd, back_door=True, kind="home", h=4):
    """A narrow terraced house: hallway from the front door (xd) to the back, switchback stairs along the
    far party wall at the back, parlour at the front, kitchen at the back, bedrooms above."""
    x0, x1, z0, z1 = 1, w - 2, 2, d - 2
    xa = x1 if xd == x0 else x0
    xb = xa - 1 if xa == x1 else xa + 1
    zlo = max(z0, z1 - h)
    core = stair_core(v, xa, xb, zlo, storeys, wood, h) if storeys > 1 else set()
    for k in range(storeys):
        fl = Floor(v, h * k + 1, x0, x1, z0, z1)
        fl.keep |= core
        if k == 0:
            fl.keep |= {(xd, z) for z in range(z0, z1 + 1)}            # the hallway
            if kind == "shop":
                shop_floor(fl, rng, wood, xd, xa)
            elif kind == "pub":
                pub_floor(fl, rng, wood, xd, xa)
            else:
                front = Floor(v, fl.y, x0, x1, z0, (z0 + z1) // 2)
                front.keep = fl.keep
                furnish_room(front, rng, "parlour", wood, 3)
                xm = [x for x in range(x0, x1 + 1) if x not in (xd, xa, xb)]
                if xm:
                    table_with_chairs(fl, xm[len(xm) // 2], z0 + 1, wood, rng)
                back = Floor(v, fl.y, x0, x1, (z0 + z1) // 2 + 1, z1)
                back.keep = fl.keep
                furnish_room(back, rng, "kitchen", wood, 3)
            lamp(fl, xd, z0 + 1, h)
        else:
            colour = rng.choice(BED_COLOURS)
            if not bed(fl, xd, z0, "north", colour):
                bed(fl, xd, z1, "south", colour)
            furnish_room(fl, rng, "office" if kind == "office" else "bedroom", wood, 3)
            lamp(fl, xd, (z0 + z1) // 2, h)
    if back_door:
        v.set(xd, 1, d - 1, S(f"{wood}_door", facing="south", half="lower", hinge="left", open="false"))
        v.set(xd, 2, d - 1, S(f"{wood}_door", facing="south", half="upper", hinge="left", open="false"))


def shop_floor(fl, rng, wood, xd, xa):
    """Customers in front of a counter; shelves of goods behind it; staff reach the stairs round its end."""
    zc = min(fl.z0 + 2, max(fl.z0 + 1, fl.z1 - 6))
    for x in range(fl.x0, fl.x1 + 1):
        if x == xa or (x, zc) in fl.keep and x != xd:
            continue
        if x == xd:
            continue
        fl.put(x, zc, S(f"{wood}_slab", type="top"))
        if rng.random() < 0.35:
            fl.v.set(x, fl.y + 1, zc, S(rng.choice(("flower_pot", "candle", "lantern"))))
    # the hallway runs only as far as the counter, which closes it off
    fl.keep -= {(xd, z) for z in range(zc, fl.z1 + 1)}
    fl.put(xd, zc, S(f"{wood}_slab", type="top"))
    goods = ["bookshelf", "barrel[facing=up]", "b:crate", "chiseled_bookshelf[facing=north]", "barrel[facing=up]"]
    for x in range(fl.x0, fl.x1 + 1):
        z = fl.z1
        it = rng.choice(goods)
        fl.put(x, z, _state(it) if "[" in it else S(it))


def pub_floor(fl, rng, wood, xd, xa):
    """A bar along one side with barrels behind it, tables and chairs in the room."""
    xbar = fl.x1 if xa == fl.x0 else fl.x0
    if xbar == xd:
        xbar = fl.x0 if xd == fl.x1 else fl.x1
    for z in range(fl.z0 + 1, fl.z1 - 1):
        if (xbar, z) not in fl.keep:
            fl.put(xbar, z, S("barrel", facing="up") if z % 2 else S("barrel", facing="north"))
    for z in range(fl.z0 + 1, fl.z1 - 1, 3):
        for x in range(fl.x0, fl.x1 + 1):
            if x not in (xd, xbar) and fl.free(x, z):
                table_with_chairs(fl, x, z, "dark_oak", rng)
                break
    lamp(fl, (fl.x0 + fl.x1) // 2, (fl.z0 + fl.z1) // 2)


def corridor_block(v, w, d, storeys, wood, rng, kind="home", h=4, back_door=True, front_door=True, z0=2, xd=None,
                   entry=1):
    """A wide building with a central passage front to back, the stair core beside it at the front, and rooms
    to either side: tenements, villas, offices, stores."""
    x0, x1, z1 = 1, w - 2, d - 2
    xd = w // 2 if xd is None else xd
    xa, xb = xd + 1, xd + 2
    if xb > x1:
        xa, xb = xd - 1, xd - 2
    zlo = z0 + 1
    if zlo + 4 > z1:
        zlo = max(z0, z1 - h)
    core = stair_core(v, xa, xb, zlo, storeys, wood, h) if storeys > 1 else set()
    for k in range(storeys):
        fl = Floor(v, h * k + 1, x0, x1, z0, z1)
        fl.keep |= core | {(xd, z) for z in range(z0, z1 + 1)}
        fl.keep |= {(x, z) for x in range(xd - entry + 1, xd + entry) for z in (z0, z0 + 1)}
        if kind == "store":
            furnish_room(fl, rng, "store", wood, (w + d) // 2)
        elif kind == "office":
            furnish_room(fl, rng, "office", wood, (w + d) // 3)
            for x in range(x0 + 1, x1, 4):
                table_with_chairs(fl, x, (z0 + z1) // 2, wood, rng)
        elif k == 0:
            furnish_room(fl, rng, "kitchen", wood, 3)
            furnish_room(fl, rng, "parlour", wood, 3)
            for x in (x0 + 1, x1 - 1):
                table_with_chairs(fl, x, (z0 + z1) // 2, wood, rng)
        else:
            for x in range(x0, x1 + 1, 3):
                if x in (xd, xa, xb):
                    continue
                bed(fl, x, z1, "south", rng.choice(BED_COLOURS)) or bed(fl, x, z0, "north", rng.choice(BED_COLOURS))
            furnish_room(fl, rng, "bedroom", wood, (w + d) // 4)
        for z in range(z0 + 1, z1, 4):
            lamp(fl, xd, z, h)
    if front_door:
        v.set(xd, 1, z0 - 1, S(f"{wood}_door", facing="north", half="lower", hinge="left", open="false"))
        v.set(xd, 2, z0 - 1, S(f"{wood}_door", facing="north", half="upper", hinge="left", open="false"))
    if back_door:
        v.set(xd, 1, d - 1, S(f"{wood}_door", facing="south", half="lower", hinge="left", open="false"))
        v.set(xd, 2, d - 1, S(f"{wood}_door", facing="south", half="upper", hinge="left", open="false"))
    return xd


def one_room(v, x0, x1, z0, z1, y, rng, kind, wood, xd=None):
    fl = Floor(v, y, x0, x1, z0, z1)
    if xd is not None:
        fl.keep |= {(xd, z0), (xd, z0 + 1)}
    furnish_room(fl, rng, kind, wood, max(2, (x1 - x0 + z1 - z0) // 2))
    lamp(fl, (x0 + x1) // 2, (z0 + z1) // 2)
