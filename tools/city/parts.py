"""Shared building parts: material families, windows, doors, roofs, chimneys, simple interiors.

Every helper draws into a north-facing Vol: the street is on the low-z side, y=0 is the pavement's air layer.
"""
from world import S, I, AIR, Vol, P

# ---------------------------------------------------------------- material families


class Fam:
    """A block with its stairs, slab and wall variants (any may be None)."""

    def __init__(self, block, stairs=None, slab=None, wall=None):
        self.block, self._stairs, self._slab, self._wall = S(block), stairs, slab, wall

    def stairs(self, facing, half="bottom"):
        return S(self._stairs, facing=facing, half=half) if self._stairs else self.block

    def slab(self, kind="bottom"):
        return S(self._slab, type=kind) if self._slab else self.block

    def wall(self):
        return S(self._wall) if self._wall else self.block


def fam(base, single=None, wall=True, ns="b:"):
    single = single or base
    return Fam(ns + base, ns + single + "_stairs", ns + single + "_slab", ns + single + "_wall" if wall else None)


STOCK = fam("stock_bricks", "stock_brick")
RED = fam("red_bricks", "red_brick")
SOOT = fam("soot_bricks", "soot_brick")
ASHLAR = fam("ashlar")
COBBLE = fam("tingen_cobbles", "tingen_cobble")
PLASTER = Fam("b:whitewashed_plaster", "b:ashlar_stairs", "b:ashlar_slab", "b:ashlar_wall")
TIMBERPL = Fam("b:timber_plaster", "b:weathered_plank_stairs", "b:weathered_plank_slab", None)
PLANKS = Fam("b:weathered_planks", "b:weathered_plank_stairs", "b:weathered_plank_slab", None)
VANILLA_BRICK = Fam("bricks", "brick_stairs", "brick_slab", "brick_wall")
DEEP_BRICK = Fam("deepslate_bricks", "deepslate_brick_stairs", "deepslate_brick_slab", "deepslate_brick_wall")
POL_DEEP = Fam("polished_deepslate", "polished_deepslate_stairs", "polished_deepslate_slab", "polished_deepslate_wall")
BLACK = Fam("polished_blackstone_bricks", "polished_blackstone_brick_stairs", "polished_blackstone_brick_slab",
            "polished_blackstone_brick_wall")
QUARTZ = Fam("smooth_quartz", "smooth_quartz_stairs", "smooth_quartz_slab", None)
STONEBRICK = Fam("stone_bricks", "stone_brick_stairs", "stone_brick_slab", "stone_brick_wall")
MOSSY = Fam("mossy_stone_bricks", "mossy_stone_brick_stairs", "mossy_stone_brick_slab", "mossy_stone_brick_wall")
MOSSY_COBBLE = Fam("mossy_cobblestone", "mossy_cobblestone_stairs", "mossy_cobblestone_slab", "mossy_cobblestone_wall")
COBBLESTONE = Fam("cobblestone", "cobblestone_stairs", "cobblestone_slab", "cobblestone_wall")
SMOOTH = Fam("smooth_stone", None, "smooth_stone_slab", None)

SLATE = Fam("b:slate_roof_tiles", "b:slate_roof_stairs", "b:slate_roof_slab")
CLAY = Fam("b:clay_roof_tiles", "b:clay_roof_stairs", "b:clay_roof_slab")
DEEPTILE = Fam("deepslate_tiles", "deepslate_tile_stairs", "deepslate_tile_slab", "deepslate_tile_wall")
COPPER = Fam("waxed_oxidized_cut_copper", "waxed_oxidized_cut_copper_stairs", "waxed_oxidized_cut_copper_slab")
DARKPLANK = Fam("dark_oak_planks", "dark_oak_stairs", "dark_oak_slab")
SPRUCE = Fam("spruce_planks", "spruce_stairs", "spruce_slab")
OAK = Fam("oak_planks", "oak_stairs", "oak_slab")

WINDOW = S("b:sash_window")
LIT = S("b:lit_sash_window")
SHOPWIN = S("b:shop_window")
LAMP = S("b:wall_gas_lamp", facing="north")

WOODS = ("spruce", "dark_oak", "oak", "mangrove")


def door(v, x, y, z, wood="spruce", facing="north", hinge="left"):
    v.set(x, y, z, S(f"{wood}_door", facing=facing, half="lower", hinge=hinge, open="false"))
    v.set(x, y + 1, z, S(f"{wood}_door", facing=facing, half="upper", hinge=hinge, open="false"))


def double_door(v, x, y, z, wood="dark_oak", facing="north"):
    door(v, x, y, z, wood, facing, "left")
    door(v, x + 1, y, z, wood, facing, "right")


def window(v, x, y, z, rng, lit_chance=0.3, h=2, kind=None):
    """A sash window h tall at the facade plane z; some are lit."""
    b = kind or (LIT if rng.random() < lit_chance else WINDOW)
    v.fill(x, y, z, x, y + h - 1, z, b)


def dressed_window(v, x, y, z, rng, trim, lit_chance=0.3, h=2, sill=True, w=1):
    """Window with a stone lintel above and a projecting sill below (sill sits in the overhang row z-1)."""
    b = LIT if rng.random() < lit_chance else WINDOW
    v.fill(x, y, z, x + w - 1, y + h - 1, z, b)
    v.fill(x, y + h, z, x + w - 1, y + h, z, trim.block)
    if sill and z - 1 >= 0:
        v.fill(x, y - 1, z - 1, x + w - 1, y - 1, z - 1, trim.slab("top"))


# ---------------------------------------------------------------- roofs


def gable_x(v, x0, x1, z0, z1, y, roof, gable=None, overhang_ends=0):
    """Pitched roof with the ridge along x, covering z0..z1. Gable ends (x0, x1) are walled in ``gable``.
    Returns the ridge height."""
    zf, zb = z0, z1
    yy = y
    while zf <= zb:
        if zf == zb:
            v.fill(x0 - overhang_ends, yy, zf, x1 + overhang_ends, yy, zf, roof.slab("bottom"))
            if gable:
                v.set(x0, yy, zf, gable.block); v.set(x1, yy, zf, gable.block)
            break
        v.fill(x0 - overhang_ends, yy, zf, x1 + overhang_ends, yy, zf, roof.stairs("south"))
        v.fill(x0 - overhang_ends, yy, zb, x1 + overhang_ends, yy, zb, roof.stairs("north"))
        if gable and zb - zf > 1:
            v.fill(x0, yy, zf + 1, x0, yy, zb - 1, gable.block)
            v.fill(x1, yy, zf + 1, x1, yy, zb - 1, gable.block)
        if zb - zf == 1:
            # two stairs meet back to back: cap the ridge with slabs one higher
            v.fill(x0 - overhang_ends, yy + 1, zf, x1 + overhang_ends, yy + 1, zb, roof.slab("bottom"))
            return yy + 1
        zf += 1; zb -= 1; yy += 1
    return yy


def gable_z(v, x0, x1, z0, z1, y, roof, gable=None):
    """Pitched roof with the ridge along z (gable faces the street)."""
    xl, xr = x0, x1
    yy = y
    while xl <= xr:
        if xl == xr:
            v.fill(xl, yy, z0, xl, yy, z1, roof.slab("bottom"))
            if gable:
                v.set(xl, yy, z0, gable.block); v.set(xl, yy, z1, gable.block)
            break
        v.fill(xl, yy, z0, xl, yy, z1, roof.stairs("east"))
        v.fill(xr, yy, z0, xr, yy, z1, roof.stairs("west"))
        if gable and xr - xl > 1:
            v.fill(xl + 1, yy, z0, xr - 1, yy, z0, gable.block)
            v.fill(xl + 1, yy, z1, xr - 1, yy, z1, gable.block)
        if xr - xl == 1:
            v.fill(xl, yy + 1, z0, xr, yy + 1, z1, roof.slab("bottom"))
            return yy + 1
        xl += 1; xr -= 1; yy += 1
    return yy


def hip(v, x0, x1, z0, z1, y, roof, flat_top=None):
    """Hipped roof over the rectangle; stairs on all four sides, corners resolve in-game into outer corners."""
    yy = y
    while x0 <= x1 and z0 <= z1:
        if x0 == x1 or z0 == z1:
            v.fill(x0, yy, z0, x1, yy, z1, roof.slab("bottom"))
            return yy
        if flat_top is not None and yy - y >= flat_top:
            v.fill(x0, yy, z0, x1, yy, z1, roof.slab("bottom"))
            return yy
        v.fill(x0, yy, z0, x1, yy, z0, roof.stairs("south"))
        v.fill(x0, yy, z1, x1, yy, z1, roof.stairs("north"))
        v.fill(x0, yy, z0 + 1, x0, yy, z1 - 1, roof.stairs("east"))
        v.fill(x1, yy, z0 + 1, x1, yy, z1 - 1, roof.stairs("west"))
        if x1 - x0 == 1 or z1 - z0 == 1:
            v.fill(x0, yy + 1, z0, x1, yy + 1, z1, roof.slab("bottom"))
            return yy + 1
        x0 += 1; x1 -= 1; z0 += 1; z1 -= 1; yy += 1
    return yy


def mansard(v, x0, x1, z0, z1, y, roof, steep=3, rng=None, dormer_xs=(), dormer_lit=0.3):
    """Mansard roof: a steep lower slope of roof blocks set back one, dormers, then a shallow hipped cap."""
    # steep lower slope: roof block wall leaning in by one at the top
    for d in range(steep):
        inset = 0 if d < steep - 1 else 1
        v.walls(x0 + inset, y + d, z0 + inset, x1 - inset, y + d, z1 - inset, roof.block)
    v.fill(x0, y, z0, x1, y, z0, roof.stairs("south", "bottom"))
    for dx in dormer_xs:
        v.set(dx, y + 1, z0, LIT if rng and rng.random() < dormer_lit else WINDOW)
        v.set(dx, y + 2, z0, roof.stairs("south"))
    top = y + steep
    return hip(v, x0 + 1, x1 - 1, z0 + 1, z1 - 1, top, roof, flat_top=1)


def chimney_stack(v, x, z, y0, y1, mat, smoke=True, wide=2):
    v.fill(x, y0, z, x + wide - 1, y1, z, mat.block)
    v.fill(x, y1 + 1, z, x + wide - 1, y1 + 1, z, mat.slab("bottom") if not smoke else mat.block)
    if smoke:
        v.set(x, y1 + 1, z, S("b:chimney"))
        if wide > 1:
            v.set(x + 1, y1 + 1, z, S("flower_pot"))


def dormer(v, x, z, y, roof, wall, rng, lit=0.3):
    """A small dormer with one window on a north-facing slope; (x, z, y) is the window cell."""
    v.set(x - 1, y, z, wall.block); v.set(x + 1, y, z, wall.block)
    v.set(x, y, z, LIT if rng.random() < lit else WINDOW)
    v.fill(x - 1, y, z + 1, x + 1, y, z + 2, wall.block)
    v.set(x - 1, y + 1, z, roof.stairs("east")); v.set(x + 1, y + 1, z, roof.stairs("west"))
    v.set(x, y + 1, z, roof.slab("bottom"))
    v.fill(x - 1, y + 1, z + 1, x - 1, y + 1, z + 2, roof.stairs("east"))
    v.fill(x + 1, y + 1, z + 1, x + 1, y + 1, z + 2, roof.stairs("west"))
    v.fill(x, y + 1, z + 1, x, y + 1, z + 2, roof.slab("bottom"))


# ---------------------------------------------------------------- interiors


FLOORS = ("spruce_planks", "oak_planks", "dark_oak_planks", "b:weathered_planks")


def floor_plate(v, x0, x1, z0, z1, y, floor):
    v.fill(x0, y, z0, x1, y, z1, floor)


def stair_run(v, x, z, y, n, wood="spruce", along="z"):
    """A straight run of n stairs rising south (along z) or east (along x), clearing headroom above."""
    for i in range(n):
        if along == "z":
            v.set(x, y + i, z + i, S(f"{wood}_stairs", facing="south", half="bottom"))
            v.fill(x, y + i + 1, z + i, x, y + i + 3, z + i, AIR)
        else:
            v.set(x + i, y + i, z, S(f"{wood}_stairs", facing="east", half="bottom"))
            v.fill(x + i, y + i + 1, z, x + i, y + i + 3, z, AIR)


def furnish(v, x0, x1, z0, z1, y, rng, kind="home"):
    """Scatter a few furnishings along the walls of a room whose floor surface is y (first air layer)."""
    spots = [(x, z0) for x in range(x0, x1 + 1)] + [(x, z1) for x in range(x0, x1 + 1)] + \
            [(x0, z) for z in range(z0 + 1, z1)] + [(x1, z) for z in range(z0 + 1, z1)]
    rng.shuffle(spots)
    if kind == "home":
        items = ["bookshelf", "barrel[facing=up]", "smoker[facing=south]", "crafting_table", "chest[facing=south]",
                 "spruce_stairs[facing=east]", "potted_fern", "cauldron", "loom[facing=south]"]
    elif kind == "shop":
        items = ["barrel[facing=up]", "b:crate", "bookshelf", "chest[facing=south]", "spruce_slab[type=top]",
                 "spruce_slab[type=top]", "b:crate", "barrel[facing=up]"]
    elif kind == "pub":
        items = ["barrel[facing=north]", "barrel[facing=north]", "barrel[facing=up]", "dark_oak_stairs[facing=east]",
                 "dark_oak_stairs[facing=west]", "smoker[facing=south]", "brewing_stand", "spruce_slab[type=top]"]
    elif kind == "office":
        items = ["bookshelf", "bookshelf", "lectern[facing=south]", "chest[facing=south]", "spruce_stairs[facing=east]",
                 "cartography_table", "potted_spruce_sapling"]
    else:
        items = ["b:crate", "barrel[facing=up]", "b:crate", "hay_block"]
    n = min(len(spots), max(2, (x1 - x0 + z1 - z0) // 2))
    for (x, z), it in zip(spots[:n], (rng.choice(items) for _ in range(n))):
        if v.get(x, y, z) in (0,):
            name, props = (it.split("[", 1) + [""])[:2]
            st = S(name) if not props else S(name) + "[" + props
            v.set(x, y, z, st)
    # a lantern hanging from the ceiling in the middle of the room
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    return cx, cz


def ceiling_lamp(v, x, y, z):
    v.set(x, y, z, S("lantern", hanging="true"))


def ivy(v, rng, x0, x1, ytop, z=0, attach="south", density=0.35):
    """Streaks of ivy hanging on the facade behind row z (attached to the block on the ``attach`` side)."""
    from world import KEEP as _K
    for x in range(x0, x1 + 1):
        if rng.random() > density:
            continue
        top = rng.randint(max(1, ytop // 2), ytop)
        for y in range(0, top + 1):
            if v.get(x, y, z) in (_K, 0):
                v.set(x, y, z, S("vine", **{attach: "true"}))
