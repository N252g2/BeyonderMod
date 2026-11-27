"""Tingen street buildings. Each function returns a north-facing Vol: width w along the street, depth d.
Row z=0 is the overhang row over the pavement (steps, sills, oriels, cornices); the facade is at z=1.
y=0 is the pavement's air layer; ground floors are raised one step (floor block at y=0).
"""
from world import S, I, AIR, Vol, KEEP
from parts import *  # noqa: F401,F403
import parts as pt

SHOP_NAMES = [
    "Hale & Sons, Tailors", "Brindle's Bakery", "Ashworth Apothecary", "The Lamplighter's Supply",
    "Cobb's Ironmongery", "Mercer Bros. Drapers", "Wexley Tobacconist", "Pell's Pawnbroker",
    "Ravel & Co. Booksellers", "Thorne's Butchery", "Gilt Clockmaker", "Moss Grocers",
    "Fennick Hatters", "Orrin's Chemist", "The Brass Kettle Tea Room", "Sable Fishmonger",
    "Corwin Printing Works", "Lindell Haberdashery", "Quill & Ink Stationers", "Harrow Bootmakers",
    "Dunmore Fine Wines", "Ellery Photographic Studio", "Varne Confectioner", "Kestrel Gunsmith",
    "Morrow Undertakers", "Pike's Pie Shop", "Copperfield Cutlery", "Weller's Coffee House",
    "Ottley Spectacles", "Bram's Barber", "Nightingale Florist", "Selwyn Antiques",
]
PUB_NAMES = ["The Crooked Lantern", "The Drowned Bell", "The Three Ravens", "The Brass Moon", "The Old Weir",
             "The Grey Gull", "The Rook & Pawn", "The Lantern Arms", "The Tussock Tavern", "The Iron Gate",
             "The Black Barge", "The Ferryman's Rest", "The Sleeping Hound", "The Clockwork Fox"]


class Style:
    def __init__(self, wall, trim, band, roof, wood="spruce", floor="spruce_planks", lit=0.3, plinth=None):
        self.wall, self.trim, self.band, self.roof = wall, trim, band, roof
        self.wood, self.floor, self.lit = wood, S(floor), lit
        self.plinth = plinth or trim


STYLES = {
    "daffodil": lambda r: Style(STOCK, ASHLAR, RED, SLATE, r.choice(("spruce", "dark_oak")), "spruce_planks", 0.35),
    "red": lambda r: Style(RED, ASHLAR, STOCK, CLAY, r.choice(("spruce", "oak", "dark_oak")), "oak_planks", 0.3),
    "soot": lambda r: Style(SOOT, COBBLE, SOOT, DEEPTILE, r.choice(("spruce", "dark_oak")), "b:weathered_planks", 0.2, COBBLE),
    "stucco": lambda r: Style(PLASTER, ASHLAR, PLASTER, SLATE, "dark_oak", "dark_oak_planks", 0.4),
    "stock": lambda r: Style(STOCK, ASHLAR, STOCK, DEEPTILE, r.choice(("spruce", "oak")), "spruce_planks", 0.3),
    "redslate": lambda r: Style(RED, ASHLAR, RED, SLATE, r.choice(("spruce", "dark_oak")), "spruce_planks", 0.3),
    "vbrick": lambda r: Style(VANILLA_BRICK, ASHLAR, VANILLA_BRICK, SLATE, "spruce", "spruce_planks", 0.3),
}


def style(rng, name):
    return STYLES[name](rng)


# ---------------------------------------------------------------- shared body


def body(v, w, d, storeys, st, rng, top=None, shopfront=False):
    """Walls, floors, facade grid lines. Returns the y of the top plate."""
    top = 4 * storeys if top is None else top
    v.fill(0, 0, 1, w - 1, top, d - 1, st.wall.block)
    v.fill(1, 1, 2, w - 2, top - 1, d - 2, AIR)
    v.fill(1, 0, 2, w - 2, 0, d - 2, st.floor)
    for k in range(1, storeys):
        v.fill(1, 4 * k, 2, w - 2, 4 * k, d - 2, st.floor)
        # string course on the facade at each floor line
        v.fill(0, 4 * k, 1, w - 1, 4 * k, 1, st.band.block)
    if not shopfront:
        v.fill(0, 0, 1, w - 1, 0, 1, st.plinth.block)
    return top


def interior(v, w, d, storeys, st, rng, kind="home", stair_x=None):
    sx = w - 2 if stair_x is None else stair_x
    z0 = max(2, d - 7)
    for k in range(storeys - 1):
        x = sx if k % 2 == 0 else max(1, sx - 1)
        pt.stair_run(v, x, z0, 4 * k + 1, 4, st.wood if st.wood != "mangrove" else "spruce")
    for k in range(storeys):
        y = 4 * k + 1
        pt.furnish(v, 1, w - 2, 2, d - 2, y, rng, kind if k == 0 else "home")
        pt.ceiling_lamp(v, (w - 1) // 2, 4 * k + 3, (d + 1) // 2)


def cornice(v, w, y, st):
    v.fill(0, y, 0, w - 1, y, 0, st.trim.stairs("south", "top"))


def back_wall(v, w, d, storeys, st, rng):
    for k in range(storeys):
        y = 4 * k + 1
        for x in range(2, w - 2, 2):
            pt.window(v, x, y, d - 1, rng, st.lit * 0.6)
    pt.door(v, w - 2, 1, d - 1, st.wood, "south")


def roof_and_chimneys(v, w, d, top, st, rng, chimney_left=True, chimney_right=False, dormers=0):
    ridge = pt.gable_x(v, 0, w - 1, 0, d - 1, top, st.roof, st.wall)
    zr = (d - 1) // 2
    if chimney_left:
        pt.chimney_stack(v, 0, zr, top, ridge + 1, st.wall, smoke=rng.random() < 0.6, wide=1)
        v.fill(0, top, zr + 1, 0, ridge + 1, zr + 1, st.wall.block)
        v.set(0, ridge + 2, zr + 1, S("flower_pot"))
    if chimney_right:
        pt.chimney_stack(v, w - 1, zr, top, ridge + 1, st.wall, smoke=rng.random() < 0.6, wide=1)
        v.fill(w - 1, top, zr + 1, w - 1, ridge + 1, zr + 1, st.wall.block)
    for i in range(dormers):
        x = (w - 1) // 2 if dormers == 1 else 2 + i * (w - 4) // max(1, dormers - 1)
        pt.dormer(v, x, 1, top + 1, st.roof, st.wall, rng, st.lit)
    return ridge


def end_windows(v, w, d, storeys, st, rng, side):
    x = 0 if side == "left" else w - 1
    for k in range(storeys):
        for z in range(3, d - 2, 3):
            v.fill(x, 4 * k + 1, z, x, 4 * k + 2, z, pt.LIT if rng.random() < st.lit else pt.WINDOW)
    # quoins: alternating trim blocks on the corner
    for y in range(0, 4 * storeys, 2):
        v.set(x, y, 1, st.trim.block)


# ---------------------------------------------------------------- houses


def terrace_house(rng, w, d, st, storeys=3, oriel=False, door_left=True, end=None, lamp=False, dormer=False, bay=False):
    h = 4 * storeys + d // 2 + 6
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng)
    xd = 1 if door_left else w - 2
    # recessed door with a step and a fanlight
    v.fill(xd, 1, 1, xd, 2, 1, AIR)
    v.set(xd, 0, 0, st.trim.stairs("south"))
    pt.door(v, xd, 1, 2, st.wood, "north", "left" if door_left else "right")
    v.set(xd, 3, 2, S("glass_pane"))
    v.set(xd, 3, 1, st.trim.block)
    win_xs = [x for x in range(1, w - 1) if x != xd]
    # ground floor: a paired window away from the door, or a canted bay under its own little roof
    if bay and w >= 6:
        bx0, bx1 = (xd + 1, w - 2) if door_left else (1, xd - 1)
        v.fill(bx0, 0, 0, bx1, 0, 0, st.trim.block)
        v.fill(bx0, 1, 0, bx1, 2, 0, pt.LIT if rng.random() < st.lit else pt.WINDOW)
        v.fill(bx0, 3, 0, bx1, 3, 0, st.roof.slab("bottom"))
        v.fill(bx0, 1, 1, bx1, 2, 1, AIR)
        v.fill(bx0, 3, 1, bx1, 3, 1, st.trim.block)
    else:
        for x in win_xs:
            if abs(x - xd) >= 2 or w <= 5:
                pt.window(v, x, 1, 1, rng, st.lit)
                v.set(x, 3, 1, st.trim.block)
    # upper floors
    for k in range(1, storeys):
        y = 4 * k + 1
        if oriel and k == 1:
            ox0, ox1 = (2, w - 2) if door_left else (1, w - 3)
            v.fill(ox0, 4 * k, 0, ox1, 4 * k, 0, st.wall.stairs("south", "top"))
            v.fill(ox0, y, 0, ox1, y + 1, 0, pt.LIT if rng.random() < st.lit else pt.WINDOW)
            v.fill(ox0, y + 2, 0, ox1, y + 2, 0, st.trim.slab("bottom"))
            v.fill(ox0, y, 1, ox1, y + 1, 1, AIR)
            continue
        for x in range(1, w - 1):
            if w >= 6 and x == (xd + (1 if door_left else -1)):
                continue        # leave a pier between the windows
            pt.dressed_window(v, x, y, 1, rng, st.trim, st.lit)
    cornice(v, w, top - 1, st)
    back_wall(v, w, d, storeys, st, rng)
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    if lamp:
        v.set(xd + (1 if door_left else -1), 3, 0, pt.LAMP)
    interior(v, w, d, storeys, st, rng)
    roof_and_chimneys(v, w, d, top, st, rng, True, end == "right", 1 if dormer else 0)
    return v


def shop_house(rng, w, d, st, storeys=3, name=None, awning=None, end=None):
    h = 4 * storeys + d // 2 + 6
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng, shopfront=True)
    # storefront: pilasters, stall riser, plate glass, fascia
    wood = st.wood
    xd = w // 2 if w % 2 else (w // 2 if rng.random() < 0.5 else w // 2 - 1)
    v.fill(1, 1, 1, w - 2, 1, 1, S(f"{wood}_planks"))
    v.fill(1, 2, 1, w - 2, 3, 1, pt.SHOPWIN)
    v.fill(0, 0, 1, 0, 4, 1, st.trim.block); v.fill(w - 1, 0, 1, w - 1, 4, 1, st.trim.block)
    v.fill(1, 4, 1, w - 2, 4, 1, S("b:dark_timber", axis="x"))
    v.fill(1, 0, 1, w - 2, 0, 1, st.trim.block)
    v.fill(xd, 1, 1, xd, 3, 1, AIR)
    v.set(xd, 0, 0, st.trim.stairs("south"))
    pt.door(v, xd, 1, 2, wood, "north")
    v.set(xd, 3, 2, S("glass_pane"))
    # fascia sign and awning
    name = name or rng.choice(SHOP_NAMES)
    words = name.split(" ")
    lines = [""] * 4
    cur = 1
    for word in words:
        if len(lines[cur]) + len(word) + 1 > 15 and lines[cur]:
            cur = min(cur + 1, 3)
        lines[cur] = (lines[cur] + " " + word).strip()
    v.sign(xd, 4, 0, S(f"{'spruce' if wood == 'mangrove' else wood}_wall_sign", facing="north"), lines)
    if awning:
        v.fill(1, 3, 0, w - 2, 3, 0, S(f"b:{awning}_awning", facing="north"))
        v.fill(xd, 3, 0, xd, 3, 0, S(f"b:{awning}_awning", facing="north"))
    # upper floors
    for k in range(1, storeys):
        y = 4 * k + 1
        for x in range(1, w - 1):
            if w >= 7 and x == xd:
                continue
            pt.dressed_window(v, x, y, 1, rng, st.trim, st.lit)
    cornice(v, w, top - 1, st)
    back_wall(v, w, d, storeys, st, rng)
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    interior(v, w, d, storeys, st, rng, "shop")
    roof_and_chimneys(v, w, d, top, st, rng, True, end == "right", 0)
    return v


def pub(rng, w, d, st, storeys=3, name=None, end=None):
    h = 4 * storeys + d // 2 + 6
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng, shopfront=True)
    # dark timber and green-glazed ground floor
    v.fill(0, 0, 1, w - 1, 4, 1, S("b:dark_timber", axis="y"))
    for x in range(1, w - 1):
        if x % 3 != 0:
            v.fill(x, 2, 1, x, 3, 1, pt.SHOPWIN)
            v.set(x, 1, 1, S("green_terracotta"))
    v.fill(1, 4, 1, w - 2, 4, 1, S("black_terracotta"))
    xd = w // 2
    v.fill(xd, 1, 1, xd, 3, 1, AIR)
    pt.door(v, xd, 1, 2, "dark_oak", "north")
    v.set(xd, 3, 2, S("glass_pane"))
    v.set(xd, 0, 0, S("b:ashlar_stairs", facing="south", half="bottom"))
    name = name or rng.choice(PUB_NAMES)
    words = name.split(" ")
    half = (len(words) + 1) // 2
    v.sign(xd, 5, 0, S("dark_oak_wall_hanging_sign", facing="east"),
           ["", " ".join(words[:half]), " ".join(words[half:]), ""])
    v.set(xd - 2, 3, 0, pt.LAMP); v.set(xd + 2, 3, 0, pt.LAMP)
    for k in range(1, storeys):
        y = 4 * k + 1
        for x in range(1, w - 1):
            if x != xd:
                pt.dressed_window(v, x, y, 1, rng, st.trim, 0.55)
    cornice(v, w, top - 1, st)
    back_wall(v, w, d, storeys, st, rng)
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    interior(v, w, d, storeys, st, rng, "pub")
    roof_and_chimneys(v, w, d, top, st, rng, True, True, 0)
    return v


def tenement(rng, w, d, st, storeys=5, end=None):
    """A plain block of flats: grid of small windows, flat roof behind a parapet, rows of chimney pots."""
    h = 4 * storeys + 6
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng)
    xd = w // 2
    v.fill(xd, 1, 1, xd, 2, 1, AIR)
    pt.door(v, xd, 1, 2, "spruce", "north")
    v.set(xd, 0, 0, st.trim.stairs("south"))
    v.set(xd, 3, 1, st.trim.block)
    for k in range(storeys):
        y = 4 * k + 1
        for x in range(1, w - 1, 2):
            if k == 0 and abs(x - xd) < 1:
                continue
            v.fill(x, y, 1, x, y + 1, 1, pt.LIT if rng.random() < st.lit else pt.WINDOW)
            v.set(x, y + 2, 1, st.trim.block)
    # flat roof, parapet, chimney rows
    v.fill(0, top, 0, w - 1, top, d - 1, st.wall.block)
    v.fill(1, top, 1, w - 2, top, d - 2, st.roof.slab("top"))
    v.fill(0, top + 1, 1, w - 1, top + 1, 1, st.wall.wall())
    v.fill(0, top + 1, d - 1, w - 1, top + 1, d - 1, st.wall.wall())
    v.fill(0, top + 1, 1, 0, top + 1, d - 1, st.wall.wall()); v.fill(w - 1, top + 1, 1, w - 1, top + 1, d - 1, st.wall.wall())
    v.fill(0, top - 1, 0, w - 1, top - 1, 0, st.wall.stairs("south", "top"))
    for x in range(1, w - 1, 4):
        pt.chimney_stack(v, x, d // 2, top + 1, top + 3, st.wall, rng.random() < 0.7, wide=2)
    # back: rows of windows and a yard door
    for k in range(storeys):
        for x in range(2, w - 2, 3):
            v.fill(x, 4 * k + 1, d - 1, x, 4 * k + 2, d - 1, pt.LIT if rng.random() < st.lit * 0.6 else pt.WINDOW)
    pt.door(v, w // 2, 1, d - 1, "spruce", "south")
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    # stairs up the middle and a few rooms
    for k in range(storeys - 1):
        pt.stair_run(v, 1 + (k % 2) * (w - 3), 2, 4 * k + 1, 4, "spruce")
    for k in range(storeys):
        pt.furnish(v, 2, w - 3, 3, d - 2, 4 * k + 1, rng, "home")
        pt.ceiling_lamp(v, w // 2, 4 * k + 3, d // 2)
    return v


def shack(rng, w, d, st=None):
    """A slum shack: weathered planks, a lean-to roof, a crooked stovepipe."""
    h = 9
    v = Vol(w, h, d)
    wall = rng.choice((pt.PLANKS, pt.TIMBERPL, pt.MOSSY_COBBLE, pt.PLANKS))
    v.fill(0, 0, 1, w - 1, 0, d - 1, S("mossy_cobblestone") if rng.random() < 0.5 else S("cobblestone"))
    v.fill(0, 1, 1, w - 1, 3, d - 1, wall.block)
    v.fill(1, 1, 2, w - 2, 3, d - 2, AIR)
    v.fill(1, 0, 2, w - 2, 0, d - 2, S("b:weathered_planks"))
    xd = rng.randint(1, w - 2)
    pt.door(v, xd, 1, 1, "spruce", "north")
    for x in range(1, w - 1):
        if x != xd and rng.random() < 0.5:
            v.set(x, 2, 1, S("iron_bars") if rng.random() < 0.3 else pt.WINDOW)
    # lean-to: high at the back
    roof = pt.PLANKS if rng.random() < 0.5 else pt.SPRUCE
    for i, z in enumerate(range(0, d)):
        y = 4 + i * 2 // d
        v.fill(0, y, z, w - 1, y, z, roof.stairs("south") if i % 2 == 0 else roof.slab("top" if False else "bottom"))
        v.fill(0, 4, z, w - 1, y - 1, z, wall.block) if y > 4 and z in (1, d - 1) else None
    v.fill(0, 4, 1, w - 1, 4, d - 1, roof.slab("bottom"))
    v.fill(0, 4, d - 1, w - 1, 5, d - 1, wall.block)
    v.fill(0, 6, d - 1, w - 1, 6, d - 1, roof.slab("bottom"))
    if rng.random() < 0.7:
        x = rng.randint(1, w - 2)
        v.fill(x, 5, d - 2, x, 7, d - 2, S("cobblestone_wall"))
        v.set(x, 8, d - 2, S("b:chimney"))
    pt.furnish(v, 1, w - 2, 2, d - 2, 1, rng, "store")
    return v


def townhouse(rng, w, d, st, storeys=4, end=None):
    """Golden Indus townhouse: stucco over a rusticated ground floor, portico, iron balcony, mansard with dormers."""
    h = 4 * storeys + 8
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng)
    v.fill(0, 0, 1, w - 1, 3, 1, S("b:ashlar"))
    for y in (1, 3):
        v.fill(0, y, 1, w - 1, y, 1, S("b:carved_ashlar")) if False else None
    xd = 1 if w <= 6 else 2
    # portico: two pillars, an entablature and a balcony over it
    v.fill(xd - 1, 0, 0, xd + 1, 0, 0, S("b:ashlar_slab", type="top"))
    v.set(xd, 0, 0, S("b:ashlar_stairs", facing="south"))
    v.fill(xd - 1, 1, 0, xd - 1, 3, 0, S("b:ashlar_pillar", axis="y"))
    v.fill(xd + 1, 1, 0, xd + 1, 3, 0, S("b:ashlar_pillar", axis="y"))
    v.fill(xd - 1, 4, 0, xd + 1, 4, 0, S("b:ashlar_slab", type="bottom"))
    v.fill(xd, 1, 1, xd, 3, 1, AIR)
    pt.door(v, xd, 1, 2, "dark_oak", "north")
    v.set(xd, 3, 2, S("glass_pane"))
    for x in range(1, w - 1):
        if abs(x - xd) > 1:
            pt.window(v, x, 1, 1, rng, st.lit)
            v.set(x, 3, 1, S("b:carved_ashlar"))
    # first floor: tall windows onto an iron balcony
    v.fill(0, 4, 0, w - 1, 4, 0, S("b:ashlar_slab", type="top"))
    v.fill(0, 5, 0, w - 1, 5, 0, S("b:iron_railing"))
    for k in range(1, storeys):
        y = 4 * k + 1
        for x in range(1, w - 1):
            if k == 1:
                v.fill(x, y, 1, x, y + 2, 1, pt.LIT if rng.random() < st.lit else pt.WINDOW)
            else:
                pt.dressed_window(v, x, y, 1, rng, st.trim, st.lit, sill=False)
    v.fill(0, top - 1, 0, w - 1, top - 1, 0, S("b:ashlar_stairs", facing="south", half="top"))
    back_wall(v, w, d, storeys, st, rng)
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    interior(v, w, d, storeys, st, rng, "office" if rng.random() < 0.3 else "home")
    pt.mansard(v, 0, w - 1, 1, d - 1, top, st.roof, 3, rng, [x for x in range(1, w - 1, 2)], st.lit)
    pt.chimney_stack(v, 0, d // 2, top, top + 6, st.wall if st.wall is not pt.PLASTER else pt.STOCK, rng.random() < 0.5, 1)
    return v


def villa(rng, w, d, st, storeys=2):
    """A detached house in its own garden: hipped roof, columned porch, bay windows, railings, a lawn and trees.
    The Vol covers the whole plot; the house sits back from the street."""
    h = 4 * storeys + 14
    v = Vol(w, h, d)
    v.ground_fill(0, 0, w - 1, d - 1, S("grass_block"))
    hw = min(w - 6, rng.choice((11, 13, 15)))
    hd = min(d - 9, rng.choice((10, 12)))
    hx0 = (w - hw) // 2
    hz0 = 6
    gx = hx0 + hw // 2
    # low wall with railings along the street, gate posts with lamps
    v.fill(0, 0, 0, w - 1, 0, 0, S("b:ashlar_wall"))
    v.fill(0, 1, 0, w - 1, 1, 0, S("b:iron_railing"))
    v.fill(gx - 1, 0, 0, gx + 1, 1, 0, AIR)
    for x in (gx - 2, gx + 2):
        v.fill(x, 0, 0, x, 1, 0, S("b:ashlar"))
        v.set(x, 2, 0, S("b:gas_lamp"))
    # garden walls at the sides and back
    v.fill(0, 0, 1, 0, 1, d - 1, S("b:stock_brick_wall")); v.fill(w - 1, 0, 1, w - 1, 1, d - 1, S("b:stock_brick_wall"))
    v.fill(0, 0, d - 1, w - 1, 1, d - 1, S("b:stock_brick_wall"))
    # the path to the door
    v.ground_fill(gx - 1, 0, gx + 1, hz0, S("b:flagstones"))
    # hedges along the side walls, a tree or two
    for z in range(2, d - 2):
        if rng.random() < 0.8:
            v.set(1, 0, z, S("spruce_leaves", persistent="true"))
            v.set(w - 2, 0, z, S("spruce_leaves", persistent="true"))
    hb = Vol(hw, h, hd + 1)
    top = body(hb, hw, hd + 1, storeys, st, rng)
    for y in range(0, top, 2):
        for x in (0, hw - 1):
            hb.set(x, y, 1, st.trim.block); hb.set(x, y, hd, st.trim.block)
    xd = hw // 2
    hb.fill(xd, 1, 1, xd, 2, 1, AIR)
    pt.door(hb, xd, 1, 2, "dark_oak", "north")
    hb.set(xd, 3, 2, S("glass_pane"))
    for bx in (2, hw - 3):
        if abs(bx - xd) >= 2:
            hb.fill(bx - 1, 0, 0, bx + 1, 0, 0, st.trim.block)
            hb.fill(bx - 1, 1, 0, bx + 1, 2, 0, pt.LIT if rng.random() < st.lit else pt.WINDOW)
            hb.fill(bx - 1, 3, 0, bx + 1, 3, 0, st.roof.slab("bottom"))
            hb.fill(bx - 1, 1, 1, bx + 1, 2, 1, AIR)
    for k in range(1, storeys):
        for x in range(1, hw - 1, 2):
            pt.dressed_window(hb, x, 4 * k + 1, 1, rng, st.trim, st.lit)
    for z in range(3, hd, 3):
        for k in range(storeys):
            for x in (0, hw - 1):
                hb.fill(x, 4 * k + 1, z, x, 4 * k + 2, z, pt.LIT if rng.random() < st.lit else pt.WINDOW)
    back_wall(hb, hw, hd + 1, storeys, st, rng)
    interior(hb, hw, hd + 1, storeys, st, rng)
    hb.fill(0, top, 1, hw - 1, top, hd, st.trim.block)
    pt.hip(hb, 0, hw - 1, 1, hd, top + 1, st.roof)
    chim = st.wall if st.wall is not pt.PLASTER else pt.RED
    pt.chimney_stack(hb, 1, (hd + 1) // 2, top, top + 2 + hw // 3, chim, True, 1)
    pt.chimney_stack(hb, hw - 2, (hd + 1) // 2, top, top + 2 + hw // 3, chim, rng.random() < 0.5, 1)
    v.paste(hb, hx0, 0, hz0)
    # porch on columns
    v.fill(gx - 1, 0, hz0 - 1, gx + 1, 0, hz0, st.trim.slab("top"))
    v.set(gx, 0, hz0 - 1, st.trim.stairs("south"))
    for x in (gx - 1, gx + 1):
        v.fill(x, 1, hz0 - 1, x, 3, hz0 - 1, S("b:ashlar_pillar", axis="y"))
    v.fill(gx - 1, 4, hz0 - 1, gx + 1, 4, hz0, st.trim.slab("bottom"))
    # garden trees
    from terrain import tree

    class _Adapter:
        def __init__(self, vol):
            self.v = vol

        def set(self, x, y, z, b):
            self.v.set(x, y, z, b)

        def get(self, x, y, z):
            g = self.v.get(x, y, z)
            return 0 if g == KEEP else g
    for tx, tz in ((2 + rng.randint(0, 2), d - 4), (w - 4 - rng.randint(0, 2), d - 4)):
        if rng.random() < 0.8:
            tree(_Adapter(v), tx, 0, tz, rng, rng.choice(("oak", "birch", "plane")))
    return v


def warehouse(rng, w, d, st, storeys=4, name=None, end=None):
    """Brick warehouse: gable to the street, stacked loading doors under a hoist beam, small barred windows."""
    h = 4 * storeys + w // 2 + 6
    v = Vol(w, h, d)
    top = body(v, w, d, storeys, st, rng)
    xc = w // 2
    for k in range(storeys):
        y = 4 * k + 1
        for x in range(1, w - 1):
            if abs(x - xc) <= 1:
                continue
            if x % 2 == 0:
                v.fill(x, y, 1, x, y + 1, 1, pt.LIT if rng.random() < 0.15 else pt.WINDOW)
                v.set(x, y + 2, 1, st.trim.block)
        # loading door column
        if k == 0:
            v.fill(xc - 1, 1, 1, xc + 1, 3, 1, AIR)
            v.fill(xc - 1, 1, 2, xc + 1, 3, 2, S("spruce_planks"))
            pt.door(v, xc, 1, 2, "spruce", "north")
        else:
            v.fill(xc, y, 1, xc, y + 1, 1, S("b:weathered_planks"))
            v.set(xc, y + 2, 1, st.trim.block)
    # hoist beam with a chain and a crate
    v.set(xc, top + 1, 0, S("b:dark_timber", axis="z"))
    v.set(xc, top + 1, 1, S("b:dark_timber", axis="z"))
    v.fill(xc, top - 1, 0, xc, top, 0, S("chain"))
    v.set(xc, top - 2, 0, S("b:crate"))
    # gable to the street
    v.fill(0, top, 1, w - 1, top, d - 1, st.wall.block)
    pt.gable_z(v, 0, w - 1, 0, d - 1, top + 1, st.roof, st.wall)
    v.set(xc, top + 1, 1, S("b:clock_face", facing="north") if rng.random() < 0.15 else st.wall.block)
    v.set(xc, top + 1, 0, S("b:dark_timber", axis="z"))
    name = name or rng.choice(("Brandt & Co.", "Kessel Bros.", "Tussock Wharf Co.", "Arden Shipping", "Holm & Vey",
                               "Loen Imperial Stores", "Morrow Freight", "Coldwater Tea Co."))
    v.sign(xc, 4, 0, S("spruce_wall_sign", facing="north"), ["", name, "Warehouse", ""])
    for k in range(storeys):
        pt.furnish(v, 1, w - 2, 2, d - 2, 4 * k + 1, rng, "store")
        pt.ceiling_lamp(v, w // 2, 4 * k + 3, d // 2)
    for k in range(storeys - 1):
        pt.stair_run(v, 1, 3, 4 * k + 1, 4, "spruce")
    if end:
        end_windows(v, w, d, storeys, st, rng, end)
    return v


def factory(rng, w, d, st, name=None):
    """A works: single tall storey of arched windows under a north-light sawtooth roof, and a tall chimney."""
    h = 45
    v = Vol(w, h, d)
    hh = 8
    v.fill(0, 0, 1, w - 1, hh, d - 1, st.wall.block)
    v.fill(1, 1, 2, w - 2, hh - 1, d - 2, AIR)
    v.fill(1, 0, 2, w - 2, 0, d - 2, S("b:tingen_cobbles"))
    for x in range(2, w - 2, 3):
        v.fill(x, 2, 1, x, 5, 1, pt.WINDOW)
        v.set(x, 6, 1, st.trim.stairs("north", "top"))
    for z in range(3, d - 2, 3):
        for x in (0, w - 1):
            v.fill(x, 2, z, x, 5, z, pt.WINDOW)
    xc = w // 2
    v.fill(xc - 1, 1, 1, xc + 1, 4, 1, AIR)
    v.fill(xc - 1, 1, 2, xc + 1, 4, 2, S("iron_bars"))
    v.fill(0, hh, 0, w - 1, hh, 0, st.wall.stairs("south", "top"))
    # sawtooth: ridges along x, the glazed faces looking north
    roof = pt.DEEPTILE if st.roof in (pt.CLAY,) else st.roof
    z = 1
    while z + 3 < d:
        v.fill(1, hh + 1, z, w - 2, hh + 3, z, S("glass"))
        v.fill(0, hh + 4, z, w - 1, hh + 4, z, roof.slab("bottom"))
        for i in range(1, 4):
            v.fill(0, hh + 4 - i, z + i, w - 1, hh + 4 - i, z + i, roof.stairs("north"))
            v.fill(1, hh + 1, z + i, w - 2, hh + 3 - i, z + i, AIR)
        v.fill(0, hh + 1, z, 0, hh + 3, z, st.wall.block); v.fill(w - 1, hh + 1, z, w - 1, hh + 3, z, st.wall.block)
        for i in range(1, 4):
            v.fill(0, hh + 1, z + i, 0, hh + 3 - i, z + i, st.wall.block)
            v.fill(w - 1, hh + 1, z + i, w - 1, hh + 3 - i, z + i, st.wall.block)
        z += 4
    v.fill(0, hh + 1, z, w - 1, hh + 1, d - 1, roof.slab("bottom"))
    # chimney: 3x3 brick shaft with a corbelled cap and smoke
    cx, cz = w - 4, d - 4
    ch = rng.randint(30, 38)
    v.fill(cx - 1, 0, cz - 1, cx + 1, ch, cz + 1, S("b:red_bricks"))
    v.fill(cx, 1, cz, cx, ch, cz, AIR)
    v.fill(cx - 1, 0, cz - 1, cx + 1, 3, cz + 1, S("b:soot_bricks"))
    v.fill(cx - 1, ch - 3, cz - 1, cx + 1, ch - 3, cz + 1, S("b:soot_bricks"))
    for dx, dz, f in ((-2, 0, "west"), (2, 0, "east"), (0, -2, "north"), (0, 2, "south")):
        v.fill(cx + dx if dx else cx - 1, ch, cz + dz if dz else cz - 1, cx + dx if dx else cx + 1, ch, cz + dz if dz else cz + 1,
               S("b:soot_brick_stairs", facing={"west": "east", "east": "west", "north": "south", "south": "north"}[f], half="top"))
    v.fill(cx - 1, ch + 1, cz - 1, cx + 1, ch + 1, cz + 1, S("b:soot_bricks"))
    v.set(cx, ch + 1, cz, S("b:chimney"))
    v.set(cx, ch, cz, S("b:soot_bricks"))
    name = name or rng.choice(("Tingen Iron Works", "Khoy Steam Mill", "Northgate Foundry", "Vey & Holm Engines",
                               "Ardent Textile Mill", "Lindon Brewery", "Coldwell Tannery", "Saltworth Soap Works"))
    v.sign(xc, hh - 1, 0, S("dark_oak_wall_sign", facing="north"), ["", name, "", ""])
    pt.furnish(v, 1, w - 2, 2, d - 2, 1, rng, "store")
    for x in range(3, w - 3, 6):
        v.fill(x, 1, d // 2, x + 1, 2, d // 2 + 1, S("b:crate") if rng.random() < 0.5 else S("anvil"))
    return v
