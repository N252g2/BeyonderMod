"""Landmarks and the finishing passes (street name signs, leftover ground)."""
import numpy as np
from scipy import ndimage
from world import S, I, P, AIR, KEEP, Vol
from terrain import tree
import parts as pt
import buildings as B

LANDMARKS = []


def landmark(fn):
    LANDMARKS.append(fn)
    return fn


def reserve(g, x0, z0, x1, z1):
    g.free[z0:z1 + 1, x0:x1 + 1] = False
    g.built[z0:z1 + 1, x0:x1 + 1] = True


def ground(g, cv, x0, z0, x1, z1, b, clear=True):
    s = cv.street
    cv.a[s - 1, z0:z1 + 1, x0:x1 + 1] = I(b) if isinstance(b, str) else b
    if clear:
        cv.a[s:s + 4, z0:z1 + 1, x0:x1 + 1] = 0


def place_all(g, cv, rng, marks):
    for fn in LANDMARKS:
        fn(g, cv, rng, marks)


# ---------------------------------------------------------------- finishing


def street_signs(g, cv, rng, start=0):
    """Name plates on the corner buildings at every junction."""
    s = cv.street
    done = set()
    for sid, st in enumerate(g.streets):
        if st.kind == "lane" or sid < start:
            continue
        # walk the outer pavement edge on both sides and put a sign where a building corner meets a crossing
        for side in (-1, 1):
            c = st.c + side * (st.hw + 1)          # first cell beyond the street
            for t in range(st.a, st.b + 1, 1):
                x, z = (t, c) if st.axis == "x" else (c, t)
                if not (1 <= x < cv.w - 1 and 1 <= z < cv.d - 1):
                    continue
                if not g.built[z, x] or not g.only[z, x]:
                    continue
                # a corner: the previous or next cell along the street is not built (a crossing street)
                px, pz = (t - 1, c) if st.axis == "x" else (c, t - 1)
                nx, nz = (t + 1, c) if st.axis == "x" else (c, t + 1)
                corner = (not g.built[pz, px]) or (not g.built[nz, nx])
                if not corner or (sid, x // 24, z // 24) in done:
                    continue
                # the sign hangs on the face looking onto this street, one cell in from the corner
                ox, oz = (0, -side) if st.axis == "x" else (-side, 0)
                bx, bz = x, z
                sx, sz = bx + ox, bz + oz
                face = {(0, -1): "north", (0, 1): "south", (-1, 0): "west", (1, 0): "east"}[(ox, oz)]
                # find the facade: step into the lot until a solid block at sign height
                y = s + 3
                for k in range(3):
                    if cv.a[y, bz, bx] not in (0,) and cv.a[y, sz, sx] == 0:
                        cv.sign(sx, y, sz, S("dark_oak_wall_sign", facing=face), ["", st.name.upper(), "", ""])
                        cv.signs[-1] = cv.signs[-1] + ("white",)
                        done.add((sid, x // 24, z // 24))
                        break
                    sx, sz = bx, bz
                    bx, bz = bx - ox, bz - oz


def leftovers(g, cv, rng):
    """Anything still free becomes a little green: grass, a bench, a tree."""
    s = cv.street
    zs, xs = np.nonzero(g.free)
    for z, x in zip(zs, xs):
        if (x * 13 + z * 7) % 61 == 0 and g.free[max(z - 3, 0):z + 4, max(x - 3, 0):x + 4].all():
            tree(cv, x, s, z, rng, rng.choice(("plane", "oak", "birch")))
        elif (x * 5 + z * 11) % 37 == 0 and cv.a[s, z, x] == 0:
            cv.a[s, z, x] = I(rng.choice(("short_grass", "fern", "poppy", "dandelion")))


# ---------------------------------------------------------------- Gothic helpers


def steep_gable_z(v, x0, x1, z0, z1, y, roof, gable=None):
    """A Gothic roof, ridge along z: rises two for each step in."""
    i = 0
    while x0 + i <= x1 - i:
        xl, xr, yy = x0 + i, x1 - i, y + 2 * i
        if xl == xr:
            v.fill(xl, yy, z0, xl, yy, z1, roof.block)
            v.fill(xl, yy + 1, z0, xl, yy + 1, z1, roof.slab("bottom"))
            if gable:
                v.fill(xl, y, z0, xl, yy, z0, gable); v.fill(xl, y, z1, xl, yy, z1, gable)
            return yy + 1
        v.fill(xl, yy, z0, xl, yy, z1, roof.block)
        v.fill(xr, yy, z0, xr, yy, z1, roof.block)
        v.fill(xl, yy + 1, z0, xl, yy + 1, z1, roof.stairs("east"))
        v.fill(xr, yy + 1, z0, xr, yy + 1, z1, roof.stairs("west"))
        if gable:
            v.fill(xl + 1, yy, z0, xr - 1, yy + 1, z0, gable)
            v.fill(xl + 1, yy, z1, xr - 1, yy + 1, z1, gable)
        if xr - xl == 1:
            v.fill(xl, yy + 2, z0, xr, yy + 2, z1, roof.slab("bottom"))
            return yy + 2
        i += 1
    return y


def steep_gable_x(v, x0, x1, z0, z1, y, roof, gable=None):
    i = 0
    while z0 + i <= z1 - i:
        zf, zb, yy = z0 + i, z1 - i, y + 2 * i
        if zf == zb:
            v.fill(x0, yy, zf, x1, yy, zf, roof.block)
            v.fill(x0, yy + 1, zf, x1, yy + 1, zf, roof.slab("bottom"))
            if gable:
                v.fill(x0, y, zf, x0, yy, zf, gable); v.fill(x1, y, zf, x1, yy, zf, gable)
            return yy + 1
        v.fill(x0, yy, zf, x1, yy, zf, roof.block)
        v.fill(x0, yy, zb, x1, yy, zb, roof.block)
        v.fill(x0, yy + 1, zf, x1, yy + 1, zf, roof.stairs("south"))
        v.fill(x0, yy + 1, zb, x1, yy + 1, zb, roof.stairs("north"))
        if gable:
            v.fill(x0, yy, zf + 1, x0, yy + 1, zb - 1, gable)
            v.fill(x1, yy, zf + 1, x1, yy + 1, zb - 1, gable)
        if zb - zf == 1:
            v.fill(x0, yy + 2, zf, x1, yy + 2, zb, roof.slab("bottom"))
            return yy + 2
        i += 1
    return y


BLUE = S("blue_stained_glass_pane")
RED = S("red_stained_glass_pane")
BLUEB = S("blue_stained_glass")
REDB = S("red_stained_glass")


def checker(x, y, z=0):
    return BLUEB if (x + y + z) % 2 == 0 else REDB


def lancet_x(v, x0, x1, z, y0, y1, frame, mull=None):
    """A pointed window in the plane z (facing north/south), x0..x1, y0..y1 with checkered glass."""
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            v.set(x, y, z, checker(x, y))
    if mull and x1 - x0 >= 2:
        for x in range(x0 + 1, x1, 2):
            v.fill(x, y0, z, x, y1 - 1, z, mull)
    # pointed head: the top corners step in
    v.set(x0, y1, z, frame.stairs("east", "top")); v.set(x1, y1, z, frame.stairs("west", "top"))
    v.fill(x0 - 1, y0 - 1, z, x1 + 1, y0 - 1, z, frame.slab("top"))


def lancet_z(v, x, z0, z1, y0, y1, frame, mull=None):
    for z in range(z0, z1 + 1):
        for y in range(y0, y1 + 1):
            v.set(x, y, z, checker(z, y))
    if mull and z1 - z0 >= 2:
        for z in range(z0 + 1, z1, 2):
            v.fill(x, y0, z, x, y1 - 1, z, mull)
    v.set(x, y1, z0, frame.stairs("south", "top")); v.set(x, y1, z1, frame.stairs("north", "top"))


def dial(v, cx, cy, z, face_dir, r=4, axis="z"):
    """A big clock face of blocks: pale stone rim, white face, hour marks, hands at ten past ten.
    axis "z": the face lies in the plane z (facing north/south); axis "x": in the plane x=z argument."""
    def put(dx, dy, b):
        if axis == "z":
            v.set(cx + dx, cy + dy, z, b)
        else:
            # looking at an east/west face, "dx" runs along z
            v.set(z, cy + dy, cx + (dx if face_dir == "east" else -dx), b)
    rim, face, ink = S("b:ashlar"), S("calcite"), S("polished_blackstone")
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            d = (dx * dx + dy * dy) ** 0.5
            if d <= r + 0.35:
                put(dx, dy, face if d <= r - 0.65 else rim)
    for dx, dy in ((0, r - 1), (r - 1, 0), (0, -(r - 1)), (-(r - 1), 0)):
        put(dx, dy, ink)
    for k in range(1, r - 1):
        put(0, k, ink)                       # minute hand to twelve
    put(-1, 1, ink)                          # hour hand toward ten
    if r >= 4:
        put(-2, 1, ink)
    put(0, 0, S("b:clock_face", facing=face_dir))


def pinnacle(v, x, z, y, mat, h=3):
    v.fill(x, y, z, x, y + h - 1, z, mat.wall())
    v.set(x, y + h, z, S("lightning_rod"))


# ---------------------------------------------------------------- Saint Selena Cathedral


def cathedral(rng):
    W, D, H = 59, 52, 84
    v = Vol(W, H, D)
    cx = W // 2
    body, dark, black = pt.ASHLAR, pt.POL_DEEP, pt.BLACK
    roof = pt.SLATE
    floor_a, floor_b = S("polished_deepslate"), S("smooth_quartz")
    # ---- nave, aisles, transept and apse shells
    nz0, nz1 = 8, D - 10
    v.fill(cx - 16, 0, 5, cx + 16, 12, nz1, body.block)                   # aisles block
    v.fill(cx - 8, 0, nz0, cx + 8, 24, nz1, body.block)                    # nave
    v.fill(2, 0, 27, W - 3, 24, 37, body.block)                            # transept
    for z in range(nz1 + 1, D):                                            # apse (half disc)
        r = 8
        dz = z - nz1
        half = int((r * r - dz * dz) ** 0.5) if dz <= r else -1
        if half >= 0:
            v.fill(cx - half, 0, z, cx + half, 20, z, body.block)
    # hollow inside
    v.fill(cx - 15, 1, 6, cx + 15, 11, nz1 - 1, AIR)
    v.fill(cx - 7, 1, nz0 + 1, cx + 7, 23, nz1, AIR)
    v.fill(3, 1, 28, W - 4, 23, 36, AIR)
    for z in range(nz1 + 1, D - 1):
        dz = z - nz1
        half = int((49 - dz * dz) ** 0.5) if dz <= 7 else -1
        if half >= 0:
            v.fill(cx - half, 1, z, cx + half, 19, z, AIR)
    # floor: a checker of black and white
    for x in range(1, W - 1):
        for z in range(1, D - 1):
            if v.get(x, 1, z) == 0:
                v.set(x, 0, z, floor_a if (x + z) % 2 else floor_b)
    # arcades between nave and aisles: piers with pointed arches
    for z in range(nz0 + 1, nz1 + 1):
        for x in (cx - 8, cx + 8):
            if 27 <= z <= 37:
                continue
            if (z - nz0) % 4 == 0:
                v.fill(x, 1, z, x, 11, z, dark.block)
            else:
                v.fill(x, 1, z, x, 7, z, AIR)
                v.fill(x, 8, z, x, 11, z, body.block)
                k = (z - nz0) % 4
                if k == 1:
                    v.set(x, 8, z, body.stairs("north", "top"))
                elif k == 3:
                    v.set(x, 8, z, body.stairs("south", "top"))
    # crossing piers
    for x in (cx - 8, cx + 8):
        for z in (27, 37):
            v.fill(x, 1, z, x, 23, z, dark.block)
    # ---- windows: aisle walls, clerestory, transept roses, apse
    for z in range(8, nz1 - 1, 4):
        if 25 <= z <= 38:
            continue
        lancet_z(v, cx - 16, z, z + 1, 3, 9, dark)
        lancet_z(v, cx + 16, z, z + 1, 3, 9, dark)
        lancet_z(v, cx - 8, z, z + 1, 15, 21, dark)
        lancet_z(v, cx + 8, z, z + 1, 15, 21, dark)
        # buttresses between the aisle windows, flying buttresses over the aisle roof
        bz = z + 3
        if bz < nz1 and not (25 <= bz <= 38):
            for side in (-1, 1):
                bx = cx + side * 17
                v.fill(bx, 0, bz, bx, 13, bz, dark.block)
                v.fill(bx + side, 0, bz, bx + side, 9, bz, dark.block)
                v.set(bx + side, 10, bz, dark.stairs("east" if side < 0 else "west"))
                pinnacle(v, bx, bz, 14, dark, 3)
                for k in range(1, 8):
                    xx = bx - side * k
                    v.set(xx, 13 + k // 2 + (1 if k > 4 else 0), bz, dark.stairs("west" if side < 0 else "east", "top") if k % 2 else dark.slab("top"))
    # transept gable ends with a tall checkered window under a rose
    for x, face in ((2, "west"), (W - 3, "east")):
        lancet_z(v, x, 29, 35, 3, 15, dark, dark.wall())
        for dz in range(-3, 4):
            for dy in range(-3, 4):
                if dz * dz + dy * dy <= 10:
                    v.set(x, 20 + dy, 32 + dz, checker(dz, dy))
        v.set(x, 20, 32, S("purple_stained_glass"))
        v.fill(x, 0, 27, x, 0, 37, dark.block)
    # apse windows
    for z, x in ((nz1 + 3, cx - 7), (nz1 + 3, cx + 7), (nz1 + 6, cx - 4), (nz1 + 6, cx + 4)):
        v.fill(x, 4, z, x, 14, z, BLUEB if x < cx else REDB)
    v.fill(cx, 4, D - 2, cx, 15, D - 2, S("purple_stained_glass"))
    # ---- the front: tall checkered windows either side of the black tower
    for x0 in (cx - 15, cx + 8):
        lancet_x(v, x0 + 1, x0 + 6, 5, 3, 10, dark, dark.wall())
        v.fill(x0, 0, 4, x0 + 7, 0, 4, dark.block)
    for x0 in (cx - 7, cx + 3):
        lancet_x(v, x0 + 1, x0 + 3, nz0, 14, 22, dark, dark.wall())
    # ---- roofs
    v.fill(cx - 16, 12, 5, cx - 9, 12, nz1, roof.slab("top"))
    v.fill(cx + 9, 12, 5, cx + 16, 12, nz1, roof.slab("top"))
    for k, x in enumerate(range(cx - 16, cx - 8)):
        v.fill(x, 13 + k // 3, 5, x, 13 + k // 3, nz1, roof.stairs("east"))
    for k, x in enumerate(range(cx + 16, cx + 8, -1)):
        v.fill(x, 13 + k // 3, 5, x, 13 + k // 3, nz1, roof.stairs("west"))
    steep_gable_z(v, cx - 9, cx + 9, nz0, nz1 + 1, 25, roof, body.block)
    steep_gable_x(v, 1, W - 2, 26, 38, 25, roof, body.block)
    # apse roof: concentric half rings stepping up
    for k in range(0, 8):
        r = 9 - k
        for z in range(nz1 + 1, nz1 + r + 1):
            dz = z - nz1
            half = int((r * r - dz * dz) ** 0.5) if dz <= r else -1
            if half >= 0:
                y = 21 + k
                for x in range(cx - half, cx + half + 1):
                    if v.get(x, y, z) in (KEEP, 0):
                        v.set(x, y, z, roof.block)
    # ridge cresting and a fleche over the crossing
    v.fill(cx, 44, 30, cx, 52, 34, dark.wall())
    v.fill(cx - 1, 44, 31, cx + 1, 46, 33, dark.block)
    v.set(cx, 53, 32, S("lightning_rod"))
    # corner pinnacles on the transept and nave gables
    for x, z in ((2, 27), (2, 37), (W - 3, 27), (W - 3, 37), (cx - 16, 5), (cx + 16, 5)):
        v.fill(x, 0, z, x, 25, z, dark.block)
        pinnacle(v, x, z, 26, dark, 4)
    # ---- the black clock tower
    t0, t1 = cx - 5, cx + 5
    top = 50
    v.fill(t0, 0, 0, t1, top, 10, black.block)
    v.fill(t0 + 1, 1, 1, t1 - 1, top - 1, 9, AIR)
    for y in (12, 24, 36):
        v.fill(t0 + 1, y, 1, t1 - 1, y, 9, S("dark_oak_planks"))
        v.fill(t0, y, 0, t1, y, 0, black.stairs("south", "top"))      # string course
    # corner buttresses, stepping in as they rise
    for bx, bz in ((t0 - 1, 0), (t1 + 1, 0)):
        v.fill(bx, 0, bz, bx, 40, bz + 1, S("polished_blackstone"))
        v.fill(bx, 0, bz, bx, 20, bz + 2, S("polished_blackstone"))
        pinnacle(v, bx, bz, 41, black, 5)
    # great door with a pointed arch and a checkered window over it
    v.fill(cx - 2, 1, 0, cx + 2, 6, 0, AIR)
    v.set(cx - 2, 6, 0, black.stairs("east", "top")); v.set(cx + 2, 6, 0, black.stairs("west", "top"))
    v.fill(cx - 2, 1, 1, cx + 2, 6, 1, AIR)
    pt.double_door(v, cx - 1, 1, 2, "dark_oak", "north")
    v.set(cx, 1, 2, AIR); v.set(cx, 2, 2, AIR)
    v.fill(cx - 1, 3, 2, cx + 1, 6, 2, black.block)
    v.fill(cx - 3, 0, 0, cx + 3, 0, 0, S("polished_blackstone"))
    lancet_x(v, cx - 2, cx + 2, 0, 9, 17, black, black.wall())
    # clock dials on all four faces, belfry openings above
    dial(v, cx, 30, 0, "north", 4)
    dial(v, 5, 30, t0, "west", 4, axis="x")
    dial(v, 5, 30, t1, "east", 4, axis="x")
    dial(v, cx, 30, 10, "south", 4)
    for x in range(t0 + 2, t1 - 1, 3):
        v.fill(x, 39, 0, x + 1, 45, 0, S("iron_bars"))
        v.set(x, 46, 0, black.stairs("east", "top")); v.set(x + 1, 46, 0, black.stairs("west", "top"))
    for z in range(2, 9, 3):
        v.fill(t0, 39, z, t0, 45, z + 1, S("iron_bars"))
        v.fill(t1, 39, z, t1, 45, z + 1, S("iron_bars"))
    v.set(cx, 42, 5, S("bell", attachment="ceiling", facing="north"))
    v.fill(cx, 43, 5, cx, top - 1, 5, S("chain"))
    # parapet and the spire
    v.fill(t0 - 1, top + 1, -0, t1 + 1, top + 1, 10, black.wall())
    v.fill(t0, top + 1, 1, t1, top + 1, 9, AIR)
    v.fill(t0, top, 0, t1, top, 10, S("polished_blackstone"))
    for px, pz in ((t0 - 1, 0), (t1 + 1, 0), (t0 - 1, 10), (t1 + 1, 10)):
        pinnacle(v, max(0, px), pz, top + 1, black, 6)
    r = 4.6
    y = top + 1
    while r > 0.4:
        for x in range(t0, t1 + 1):
            for z in range(0, 11):
                d = ((x - cx) ** 2 + (z - 5) ** 2) ** 0.5
                if r - 1.1 < d <= r:
                    v.set(x, y, z, roof.block)
        y += 1
        r -= 0.17
    v.fill(cx, y, 5, cx, y + 2, 5, black.wall())
    v.set(cx, y + 3, 5, S("lightning_rod"))
    # ---- inside: pews, altar, candles and chandeliers
    for z in range(nz0 + 3, 25, 2):
        for x in list(range(cx - 6, cx - 1)) + list(range(cx + 2, cx + 7)):
            v.set(x, 1, z, S("dark_oak_stairs", facing="south"))
    for z in range(39, nz1 - 1, 2):
        for x in list(range(cx - 6, cx - 1)) + list(range(cx + 2, cx + 7)):
            v.set(x, 1, z, S("dark_oak_stairs", facing="south"))
    v.fill(cx - 3, 0, nz1 + 2, cx + 3, 1, nz1 + 4, S("polished_blackstone"))
    v.fill(cx - 1, 2, nz1 + 3, cx + 1, 2, nz1 + 3, S("smooth_quartz"))
    for x in (cx - 1, cx + 1):
        v.set(x, 3, nz1 + 3, S("candle", candles="3", lit="true"))
    v.set(cx, 3, nz1 + 3, S("lectern", facing="north"))
    for z in range(nz0 + 4, nz1, 8):
        v.fill(cx, 18, z, cx, 23, z, S("chain"))
        v.set(cx, 17, z, S("lantern", hanging="true"))
    for x in (cx - 12, cx + 12):
        for z in range(10, nz1, 6):
            v.set(x, 10, z, S("lantern", hanging="true"))
    return v


@landmark
def cathedral_plaza(g, cv, rng, marks):
    s = cv.street
    # the white plaza in front of the cathedral, the cathedral itself north of it facing south
    px0, pz0, px1, pz1 = 300, 268, 462, 306
    reserve(g, px0, pz0, px1, pz1)
    for z in range(pz0, pz1 + 1):
        for x in range(px0, px1 + 1):
            # white flags with a grey grid, a black star in the middle
            b = "smooth_quartz" if (x - 381) % 8 and (z - 287) % 8 else "polished_diorite"
            cv.a[s - 1, z, x] = I(b)
            cv.a[s:s + 4, z, x] = 0
    cxp, czp = 381, 287
    for x in range(cxp - 7, cxp + 8):
        for z in range(czp - 7, czp + 8):
            d = ((x - cxp) ** 2 + (z - czp) ** 2) ** 0.5
            if d <= 7:
                cv.a[s - 1, z, x] = I("calcite") if d > 5.5 else I("polished_blackstone") if d > 4.5 else I("smooth_quartz")
    # fountain: a basin with a black obelisk
    for x in range(cxp - 4, cxp + 5):
        for z in range(czp - 4, czp + 5):
            d = ((x - cxp) ** 2 + (z - czp) ** 2) ** 0.5
            if 3.5 < d <= 4.5:
                cv.a[s, z, x] = I("b:ashlar_slab", type="bottom") if False else I("smooth_quartz_slab", type="bottom")
            elif d <= 3.5:
                cv.a[s - 1, z, x] = I("water")
                cv.a[s - 2, z, x] = I("smooth_quartz")
    cv.a[s - 1:s + 5, czp, cxp] = I("polished_blackstone")
    cv.a[s + 5, czp, cxp] = I("polished_blackstone_wall")
    cv.a[s + 6, czp, cxp] = I("lantern")
    # lamps around the plaza and benches
    for x in range(px0 + 4, px1, 12):
        for z in (pz0 + 2, pz1 - 2):
            cv.a[s, z, x] = I("b:lamp_post"); cv.a[s + 1, z, x] = I("b:lamp_post"); cv.a[s + 2, z, x] = I("b:gas_lamp")
            for dx in (-2, 2):
                cv.a[s, z, x + dx] = I("b:park_bench", facing="south" if z == pz0 + 2 else "north")
    # flower beds in stone kerbs at the four corners of the plaza
    for bx, bz in ((px0 + 8, pz0 + 6), (px1 - 14, pz0 + 6), (px0 + 8, pz1 - 10), (px1 - 14, pz1 - 10)):
        for x in range(bx, bx + 7):
            for z in range(bz, bz + 5):
                edge = x in (bx, bx + 6) or z in (bz, bz + 4)
                cv.a[s - 1, z, x] = I("grass_block")
                cv.a[s, z, x] = I("b:ashlar_slab", type="bottom") if edge else I(rng.choice(
                    ("poppy", "red_tulip", "white_tulip", "lily_of_the_valley", "azure_bluet", "oxeye_daisy")))
    # trees in tubs along the west side
    for z in range(pz0 + 6, pz1 - 4, 8):
        cv.a[s - 1, z, px0 + 3] = I("grass_block")
        tree(cv, px0 + 3, s, z, rng, "plane")
    # the cathedral
    v = cathedral(rng)
    x0 = 352
    z1 = pz0 - 1
    reserve(g, x0 - 1, z1 - v.d, x0 + v.w, z1)
    ground(g, cv, x0 - 1, z1 - v.d, x0 + v.w, z1, "polished_diorite")
    cv.stamp(v, x0, z1 - v.d + 1, facing="south")
    marks["spawn"] = (cxp, s, czp + 10, 180)
    marks["cathedral"] = (cxp, s, pz0 + 2, 180)
    # City Hall east of the cathedral, also facing the plaza
    hv = city_hall(rng)
    hx = 420
    reserve(g, hx, z1 - hv.d + 1, hx + hv.w - 1, z1)
    ground(g, cv, hx, z1 - hv.d + 1, hx + hv.w - 1, z1, "b:flagstones")
    cv.stamp(hv, hx, z1 - hv.d + 1, facing="south")
    marks["city_hall"] = (hx + hv.w // 2, s, pz0 + 2, 180)


def city_hall(rng):
    """Classical: rusticated ground floor, a portico of six columns under a pediment, a copper dome."""
    w, d = 42, 38
    v = Vol(w, 60, d)
    st = B.Style(pt.ASHLAR, pt.ASHLAR, pt.ASHLAR, pt.COPPER, "dark_oak", "dark_oak_planks", 0.5)
    storeys = 3
    top = 4 * storeys + 2
    v.fill(0, 0, 3, w - 1, top, d - 1, S("b:ashlar"))
    v.fill(1, 1, 4, w - 2, top - 1, d - 2, AIR)
    v.fill(1, 0, 4, w - 2, 0, d - 2, S("polished_andesite"))
    for k in (1, 2):
        v.fill(1, 4 * k + 1, 4, w - 2, 4 * k + 1, d - 2, S("dark_oak_planks"))
    v.fill(0, 0, 3, w - 1, 4, 3, S("b:carved_ashlar"))
    # windows: tall on the piano nobile
    for x in range(2, w - 2, 3):
        if 14 <= x <= w - 15:
            continue
        v.fill(x, 1, 3, x, 3, 3, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
        v.fill(x, 6, 3, x, 8, 3, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
        v.set(x, 9, 3, S("b:ashlar_stairs", facing="south", half="top"))
        v.fill(x, 11, 3, x, 12, 3, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
    for z in range(6, d - 2, 3):
        for x in (0, w - 1):
            for y0 in (1, 6, 11):
                v.fill(x, y0, z, x, y0 + 1, z, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
    # portico
    px0, px1 = 13, w - 14
    v.fill(px0 - 1, 0, 0, px1 + 1, 0, 2, S("b:ashlar"))
    for k, z in enumerate((0, 1)):
        pass
    v.fill(px0 - 1, 0, 0, px1 + 1, 0, 0, S("b:ashlar_stairs", facing="south"))
    for x in range(px0, px1 + 1, 3):
        v.fill(x, 1, 1, x, top - 2, 1, S("b:ashlar_pillar", axis="y"))
        v.set(x, top - 1, 1, S("b:carved_ashlar"))
    v.fill(px0 - 1, top, 0, px1 + 1, top, 2, S("b:ashlar"))
    v.fill(px0 - 1, top - 1, 0, px1 + 1, top - 1, 0, S("b:ashlar_stairs", facing="south", half="top"))
    # pediment
    for i in range((px1 - px0) // 2 + 2):
        y = top + 1 + i // 2
        xl, xr = px0 - 1 + i, px1 + 1 - i
        if xl > xr:
            break
        v.set(xl, y, 0, S("b:ashlar_stairs", facing="east")); v.set(xr, y, 0, S("b:ashlar_stairs", facing="west"))
        v.fill(xl + 1, top + 1, 0, xr - 1, y, 2, S("b:ashlar"))
    v.fill(w // 2 - 1, top + 3, 0, w // 2 + 1, top + 3, 0, S("b:clock_face", facing="north"))
    v.set(w // 2 - 1, top + 3, 0, S("b:carved_ashlar")); v.set(w // 2 + 1, top + 3, 0, S("b:carved_ashlar"))
    xd = w // 2
    v.fill(xd - 1, 1, 3, xd + 1, 4, 3, AIR)
    pt.double_door(v, xd - 1, 1, 4, "dark_oak", "north")
    v.set(xd + 1, 1, 4, S("dark_oak_door", facing="north", half="lower", hinge="right"))
    v.set(xd + 1, 2, 4, S("dark_oak_door", facing="north", half="upper", hinge="right"))
    v.sign(xd, top - 1, 2, S("dark_oak_wall_sign", facing="north"), ["", "TINGEN", "CITY HALL", ""])
    # balustrade and roof
    v.fill(0, top + 1, 3, w - 1, top + 1, d - 1, S("b:ashlar_wall"))
    v.fill(1, top + 1, 4, w - 2, top + 1, d - 2, AIR)
    v.fill(1, top, 4, w - 2, top, d - 2, S("b:ashlar"))
    pt.hip(v, 2, w - 3, 5, d - 3, top + 1, pt.SLATE, flat_top=2)
    # drum and copper dome
    dcx, dcz, r = w // 2, d // 2 + 2, 7
    for y in range(top + 1, top + 8):
        for x in range(dcx - r, dcx + r + 1):
            for z in range(dcz - r, dcz + r + 1):
                dd = ((x - dcx) ** 2 + (z - dcz) ** 2) ** 0.5
                if r - 1 < dd <= r:
                    v.set(x, y, z, S("b:ashlar") if (x + z) % 3 else (pt.LIT if y in (top + 4, top + 5) else S("b:ashlar")))
                elif dd <= r - 1:
                    v.set(x, y, z, AIR)
    y = top + 8
    rr = r + 0.5
    while rr > 0.5:
        for x in range(dcx - r - 1, dcx + r + 2):
            for z in range(dcz - r - 1, dcz + r + 2):
                dd = ((x - dcx) ** 2 + (z - dcz) ** 2) ** 0.5
                if rr - 1.2 < dd <= rr:
                    v.set(x, y, z, S("waxed_oxidized_cut_copper"))
        y += 1
        rr = (max(0.0, (r + 0.5) ** 2 - (y - top - 8) ** 2 * 1.6)) ** 0.5
    v.fill(dcx, y, dcz, dcx, y + 2, dcz, S("b:ashlar_wall"))
    v.set(dcx, y + 3, dcz, S("lightning_rod"))
    for k in range(storeys):
        pt.furnish(v, 1, w - 2, 4, d - 2, [1, 6, 11][k], rng, "office")
    return v


# ---------------------------------------------------------------- Blackthorn Security, 36 Zouteland Street


@landmark
def blackthorn(g, cv, rng, marks):
    """A plain two-storey brick front on Zouteland Street: paired arched windows on each floor, steps up to a
    black door, a vertical signboard. The Nighthawks' Tingen office."""
    s = cv.street
    w, d = 9, 13
    v = Vol(w, 20, d)
    st = B.Style(pt.STOCK, pt.ASHLAR, pt.STOCK, pt.SLATE, "dark_oak", "dark_oak_planks", 0.5)
    v.fill(0, 0, 1, w - 1, 10, d - 1, S("b:stock_bricks"))
    v.fill(1, 1, 2, w - 2, 9, d - 2, AIR)
    v.fill(1, 1, 2, w - 2, 1, d - 2, S("dark_oak_planks"))
    v.fill(1, 5, 2, w - 2, 5, d - 2, S("dark_oak_planks"))
    # a smooth grey foundation, two steps up to a black door
    v.fill(0, 0, 1, w - 1, 1, 1, S("smooth_stone"))
    xd = 2
    v.set(xd, 0, 0, S("smooth_stone_slab", type="bottom"))
    v.fill(xd, 1, 1, xd, 3, 1, AIR)
    v.set(xd, 1, 1, S("smooth_stone_slab", type="bottom"))
    pt.door(v, xd, 2, 2, "dark_oak", "north")
    v.set(xd, 4, 1, S("b:ashlar"))
    # paired arched windows on each floor
    for y0 in (2, 6):
        for x in ((4, 5), (6, 7)) if y0 == 2 else ((1, 2), (5, 6)):
            v.fill(x[0], y0, 1, x[1], y0 + 1, 1, pt.LIT if y0 == 2 else pt.WINDOW)
            v.set(x[0], y0 + 2, 1, S("b:stock_brick_stairs", facing="east", half="top"))
            v.set(x[1], y0 + 2, 1, S("b:stock_brick_stairs", facing="west", half="top"))
            v.fill(x[0], y0 - 1, 0, x[1], y0 - 1, 0, S("b:ashlar_slab", type="top"))
    # the vertical signboard beside the door
    for i, word in enumerate(("BLACKTHORN", "SECURITY", "COMPANY")):
        v.sign(xd - 1 if xd > 1 else xd + 1, 4 - i + 1, 0, S("dark_oak_wall_sign", facing="north"), ["", word, "", ""])
    v.fill(0, 10, 0, w - 1, 10, 0, S("b:ashlar_stairs", facing="south", half="top"))
    pt.gable_x(v, 0, w - 1, 0, d - 1, 11, pt.SLATE, pt.STOCK)
    pt.chimney_stack(v, w - 1, d // 2, 11, 16, pt.STOCK, True, 1)
    pt.furnish(v, 1, w - 2, 2, d - 2, 2, rng, "office")
    pt.furnish(v, 1, w - 2, 2, d - 2, 6, rng, "office")
    pt.stair_run(v, w - 2, 3, 2, 4, "dark_oak")
    pt.ceiling_lamp(v, w // 2, 4, d // 2); pt.ceiling_lamp(v, w // 2, 9, d // 2)
    # 36 Zouteland Street: on the north side of the street, facing south toward the cathedral
    x = 404
    line = 209          # south edge of the Kingsway-Zouteland block
    reserve(g, x, line - d + 1, x + w - 1, line)
    cv.stamp(v, x, line - d + 1, facing="south")
    # its signs read white on black
    for i in range(len(cv.signs) - 3, len(cv.signs)):
        cv.signs[i] = cv.signs[i] + ("white",)
    marks["blackthorn"] = (x + w // 2, s, line + 3, 0)


# ---------------------------------------------------------------- shared landmark helpers


def enclose(cv, x0, z0, x1, z1, gates=(), wall="b:ashlar_wall", rail="b:iron_railing", post="b:ashlar"):
    """A low wall with railings round a rectangle; gates are (x, z) cells left open (3 wide)."""
    s = cv.street
    open_cells = set()
    for gx, gz in gates:
        for d in (-1, 0, 1):
            open_cells.add((gx + d, gz)); open_cells.add((gx, gz + d))
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            _fence_cell(cv, x, z, s, wall, rail, post, (x - x0) % 6 == 0, (x, z) in open_cells)
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            _fence_cell(cv, x, z, s, wall, rail, post, (z - z0) % 6 == 0, (x, z) in open_cells)
    for gx, gz in gates:
        for d in (-2, 2):
            for cx, cz in ((gx + d, gz), (gx, gz + d)):
                if (cx in (x0, x1) or cz in (z0, z1)) and x0 <= cx <= x1 and z0 <= cz <= z1:
                    cv.a[s:s + 2, cz, cx] = I(post)
                    cv.a[s + 2, cz, cx] = I("b:gas_lamp")


def _fence_cell(cv, x, z, s, wall, rail, post, is_post, is_open):
    if is_open:
        cv.a[s:s + 3, z, x] = 0
        return
    if is_post:
        cv.a[s:s + 2, z, x] = I(post)
        cv.a[s + 2, z, x] = I("b:ashlar_slab", type="bottom")
    else:
        cv.a[s, z, x] = I(wall)
        cv.a[s + 1, z, x] = I(rail)


def lamp(cv, x, z, y=None):
    s = cv.street if y is None else y
    cv.a[s:s + 2, z, x] = I("b:lamp_post")
    cv.a[s + 2, z, x] = I("b:gas_lamp")


def bench(cv, x, z, facing):
    cv.set(x, cv.street, z, I("b:park_bench", facing=facing))


def classical(rng, w, d, storeys, cols, name, wall="b:ashlar", roof=None, dome=False, lit=0.5, arched=False):
    """A classical public building facing north: plinth, rows of tall windows, a columned portico, cornice."""
    roof = roof or pt.SLATE
    sh = 5
    top = sh * storeys + 1
    v = Vol(w, top + 30, d)
    v.fill(0, 0, 2, w - 1, top, d - 1, S(wall))
    v.fill(1, 1, 3, w - 2, top - 1, d - 2, AIR)
    v.fill(1, 0, 3, w - 2, 0, d - 2, S("polished_andesite"))
    for k in range(1, storeys):
        v.fill(1, sh * k, 3, w - 2, sh * k, d - 2, S("dark_oak_planks"))
        v.fill(0, sh * k, 2, w - 1, sh * k, 2, S("b:carved_ashlar"))
    v.fill(0, 0, 2, w - 1, 1, 2, S("b:carved_ashlar"))
    pw = cols * 3 - 2
    px0 = (w - pw) // 2
    px1 = px0 + pw - 1
    for k in range(storeys):
        y = sh * k + 1
        for x in range(2, w - 2, 3):
            if px0 - 1 <= x <= px1 + 1:
                continue
            b = pt.LIT if rng.random() < lit else pt.WINDOW
            v.fill(x, y + 1, 2, x, y + 3, 2, b)
            if arched:
                v.set(x, y + 4, 2, S("b:ashlar_stairs", facing="south", half="top"))
        for z in range(5, d - 2, 3):
            for x in (0, w - 1):
                v.fill(x, y + 1, z, x, y + 3, z, pt.LIT if rng.random() < lit else pt.WINDOW)
    # portico
    v.fill(px0 - 1, 0, 0, px1 + 1, 0, 1, S("b:ashlar"))
    v.fill(px0 - 1, 0, 0, px1 + 1, 0, 0, S("b:ashlar_stairs", facing="south"))
    for x in range(px0, px1 + 1, 3):
        v.fill(x, 1, 1, x, top - 2, 1, S("b:ashlar_pillar", axis="y"))
        v.set(x, top - 1, 1, S("b:carved_ashlar"))
    v.fill(px0 - 1, top, 0, px1 + 1, top, 2, S("b:ashlar"))
    v.fill(px0 - 1, top - 1, 0, px1 + 1, top - 1, 0, S("b:ashlar_stairs", facing="south", half="top"))
    for i in range(pw // 2 + 2):
        y = top + 1 + i // 2
        xl, xr = px0 - 1 + i, px1 + 1 - i
        if xl > xr:
            break
        v.set(xl, y, 0, S("b:ashlar_stairs", facing="east")); v.set(xr, y, 0, S("b:ashlar_stairs", facing="west"))
        if xr - xl > 1:
            v.fill(xl + 1, top + 1, 0, xr - 1, y, 2, S("b:ashlar"))
    xd = w // 2
    v.fill(xd - 1, 1, 2, xd + 1, 4, 2, AIR)
    v.set(xd, 1, 3, AIR)
    pt.double_door(v, xd - 1, 1, 3, "dark_oak", "north")
    words = name.split(" ")
    half = (len(words) + 1) // 2
    v.sign(xd, top - 1, 1 + 1, S("dark_oak_wall_sign", facing="north"), ["", " ".join(words[:half]).upper(), " ".join(words[half:]).upper(), ""])
    # cornice and balustrade
    v.fill(0, top, 1, w - 1, top, 1, S("b:ashlar_stairs", facing="south", half="top"))
    v.fill(0, top + 1, 2, w - 1, top + 1, d - 1, S("b:ashlar_wall"))
    v.fill(1, top + 1, 3, w - 2, top + 1, d - 2, AIR)
    if dome:
        pt.hip(v, 1, w - 2, 3, d - 2, top + 1, roof, flat_top=2)
        dcx, dcz, r = w // 2, d // 2 + 1, min(w, d) // 4
        _dome(v, dcx, dcz, r, top + 2)
    else:
        pt.hip(v, 1, w - 2, 3, d - 2, top + 1, roof)
    for k in range(storeys):
        pt.furnish(v, 1, w - 2, 3, d - 2, sh * k + 1, rng, "office")
        pt.ceiling_lamp(v, w // 2, sh * k + sh - 1, d // 2)
    return v


def _dome(v, cx, cz, r, y0, mat="waxed_oxidized_cut_copper", drum=4):
    for y in range(y0, y0 + drum):
        for x in range(cx - r, cx + r + 1):
            for z in range(cz - r, cz + r + 1):
                dd = ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5
                if dd <= r:
                    on_edge = dd > r - 1
                    v.set(x, y, z, (S("b:ashlar") if (x + z) % 3 or y == y0 else pt.LIT) if on_edge else AIR)
    y = y0 + drum
    for k in range(r + 2):
        rr = (max(0.0, (r + 0.5) ** 2 - (k * 1.15) ** 2)) ** 0.5
        if rr < 0.6:
            break
        for x in range(cx - r - 1, cx + r + 2):
            for z in range(cz - r - 1, cz + r + 2):
                if ((x - cx) ** 2 + (z - cz) ** 2) ** 0.5 <= rr:
                    v.set(x, y + k, z, S(mat))
        top = y + k
    v.fill(cx, top + 1, cz, cx, top + 2, cz, S("b:ashlar_wall"))
    v.set(cx, top + 3, cz, S("lightning_rod"))


def college_range(rng, w, d, storeys, wall, trim, roof, lit=0.4, door=True):
    """Collegiate Gothic range facing north: pointed windows, a steep roof with dormers, a door to the quad."""
    top = 4 * storeys
    v = Vol(w, top + d + 8, d)
    v.fill(0, 0, 1, w - 1, top, d - 1, wall.block)
    v.fill(1, 1, 2, w - 2, top - 1, d - 2, AIR)
    v.fill(1, 0, 2, w - 2, 0, d - 2, S("oak_planks"))
    for k in range(1, storeys):
        v.fill(1, 4 * k, 2, w - 2, 4 * k, d - 2, S("oak_planks"))
    v.fill(0, 0, 1, w - 1, 0, 1, trim.block)
    for k in range(storeys):
        y = 4 * k + 1
        for x in range(2, w - 2, 3):
            for z in (1, d - 1):
                v.fill(x, y, z, x, y + 1, z, pt.LIT if rng.random() < lit else pt.WINDOW)
                v.set(x, y + 2, z, trim.stairs("east", "top")) if False else v.set(x, y + 2, z, trim.block)
        # buttress strips between the windows
        for x in range(0, w, 3):
            v.fill(x, 0, 0, x, top - 1, 0, wall.block) if False else None
    if door:
        xd = w // 2
        v.fill(xd, 1, 1, xd, 3, 1, AIR)
        v.set(xd, 3, 1, trim.stairs("east", "top")) if False else v.set(xd, 3, 1, AIR)
        pt.door(v, xd, 1, 2, "oak", "north")
        v.set(xd, 3, 2, S("glass_pane"))
        v.set(xd, 4, 1, trim.block)
        v.set(xd, 0, 0, trim.stairs("south"))
    v.fill(0, top, 0, w - 1, top, 0, trim.stairs("south", "top"))
    ridge = steep_gable_x(v, 0, w - 1, 0, d, top + 1, roof, wall.block)
    # wall dormers: the facade rises through the eaves in little gables
    for xd in range(4, w - 4, 7):
        v.fill(xd - 1, top + 1, 0, xd + 1, top + 6, 0, AIR)
        v.fill(xd - 1, top + 1, 1, xd + 1, top + 3, 3, wall.block)
        v.fill(xd, top + 1, 1, xd, top + 2, 1, pt.LIT if rng.random() < lit else pt.WINDOW)
        v.set(xd - 1, top + 4, 1, trim.stairs("east")); v.set(xd + 1, top + 4, 1, trim.stairs("west"))
        v.set(xd, top + 4, 1, wall.block); v.set(xd, top + 5, 1, trim.slab("bottom"))
        v.fill(xd - 1, top + 4, 2, xd - 1, top + 4, 3, roof.stairs("east")); v.fill(xd + 1, top + 4, 2, xd + 1, top + 4, 3, roof.stairs("west"))
        v.fill(xd, top + 4, 2, xd, top + 4, 3, roof.block); v.fill(xd, top + 5, 2, xd, top + 5, 3, roof.slab("bottom"))
        v.fill(xd - 1, top + 1, 0, xd + 1, top + 1, 0, trim.stairs("south", "top"))
    for x in range(2, w - 3, 9):
        pt.chimney_stack(v, x, d // 2, top, ridge + 1, wall, rng.random() < 0.4, 2)
    if wall is pt.RED or wall is pt.STOCK:
        pt.ivy(v, rng, 0, w - 1, top - 1, 0, "south", 0.18)
    for k in range(storeys - 1):
        pt.stair_run(v, w - 2, 3, 4 * k + 1, 4, "oak")
    for k in range(storeys):
        pt.furnish(v, 1, w - 2, 2, d - 2, 4 * k + 1, rng, "office")
        pt.ceiling_lamp(v, w // 2, 4 * k + 3, d // 2)
    return v


def gate_tower(rng, w, d, h, wall, trim, roof):
    v = Vol(w, h + 10, d)
    v.fill(0, 0, 0, w - 1, h, d - 1, wall.block)
    v.fill(1, 1, 1, w - 2, h - 1, d - 2, AIR)
    xc = w // 2
    v.fill(xc - 1, 0, 0, xc + 1, 4, d - 1, AIR)
    v.fill(xc - 1, 0, 0, xc + 1, 0, d - 1, S("b:flagstones")) if False else None
    v.set(xc - 1, 4, 0, trim.stairs("east", "top")); v.set(xc + 1, 4, 0, trim.stairs("west", "top"))
    v.set(xc - 1, 4, d - 1, trim.stairs("east", "top")); v.set(xc + 1, 4, d - 1, trim.stairs("west", "top"))
    for y in range(8, h - 2, 5):
        v.fill(xc, y, 0, xc, y + 2, 0, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
        v.fill(xc, y, d - 1, xc, y + 2, d - 1, pt.WINDOW)
    v.set(xc, h - 3, 0, S("b:clock_face", facing="north"))
    # battlements between square corner turrets, each with a little pyramid cap
    for x in range(w):
        for z in range(d):
            if x in (0, w - 1) or z in (0, d - 1):
                v.set(x, h + 1, z, wall.block if (x + z) % 2 == 0 else AIR)
    v.fill(0, h, 0, w - 1, h, 0, trim.stairs("south", "top"))
    for x, z in ((0, 0), (w - 2, 0), (0, d - 2), (w - 2, d - 2)):
        v.fill(x, h - 4, z, x + 1, h + 3, z + 1, wall.block)
        v.fill(x, h + 4, z, x + 1, h + 4, z + 1, trim.slab("bottom"))
        v.set(x, h + 5, z, roof.stairs("south")) if False else None
    return v


# ---------------------------------------------------------------- the station, its square and the railway


@landmark
def station(g, cv, rng, marks):
    s = cv.street
    # square in front
    sq = (322, 84, 518, 103)
    reserve(g, *sq)
    ground(g, cv, *sq, "b:flagstones")
    for x in range(sq[0] + 6, sq[2], 14):
        lamp(cv, x, sq[1] + 2); lamp(cv, x, sq[3] - 2)
    for x in range(sq[0] + 13, sq[2], 28):
        if abs(x - 420) < 8:
            continue
        cv.a[s - 1, 94, x] = I("grass_block")
        tree(cv, x, s, 94, rng, "plane")
    # a cab rank of hay and water troughs
    for x in range(sq[0] + 20, sq[0] + 40, 4):
        cv.a[s, sq[3] - 4, x] = I("hay_block")
        cv.a[s, sq[3] - 4, x + 1] = I("cauldron")
    # the station building (front to the south)
    bx0, bx1, bz0, bz1 = 336, 504, 62, 83
    reserve(g, bx0, bz0, bx1, bz1)
    st = B.Style(pt.RED, pt.ASHLAR, pt.STOCK, pt.SLATE, "dark_oak", "dark_oak_planks", 0.5)
    w, d = bx1 - bx0 + 1, bz1 - bz0 + 1
    v = Vol(w, 60, d)
    top = 12
    v.fill(0, 0, 1, w - 1, top, d - 1, st.wall.block)
    v.fill(1, 1, 2, w - 2, top - 1, d - 2, AIR)
    v.fill(1, 0, 2, w - 2, 0, d - 2, S("polished_andesite"))
    v.fill(1, 6, 2, w - 2, 6, d - 2, S("dark_oak_planks"))
    v.fill(0, 0, 1, w - 1, 0, 1, S("b:ashlar"))
    v.fill(0, 6, 1, w - 1, 6, 1, S("b:ashlar"))
    for x in range(2, w - 2, 3):
        v.fill(x, 1, 1, x, 3, 1, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
        v.set(x, 4, 1, S("b:ashlar_stairs", facing="south", half="top"))
        v.fill(x, 8, 1, x, 9, 1, pt.LIT if rng.random() < 0.4 else pt.WINDOW)
        v.set(x, 10, 1, S("b:ashlar"))
    # arched entrances under a glass canopy
    xc = w // 2
    for ex in range(xc - 12, xc + 13, 6):
        v.fill(ex - 1, 1, 1, ex + 1, 4, 1, AIR)
        v.set(ex - 1, 4, 1, S("b:ashlar_stairs", facing="east", half="top")); v.set(ex + 1, 4, 1, S("b:ashlar_stairs", facing="west", half="top"))
        pt.double_door(v, ex - 1, 1, 2, "dark_oak", "north")
        v.set(ex + 1, 1, 2, S("dark_oak_door", facing="north", half="lower", hinge="right"))
        v.set(ex + 1, 2, 2, S("dark_oak_door", facing="north", half="upper", hinge="right"))
    v.fill(xc - 15, 5, 0, xc + 15, 5, 0, S("glass"))
    for x in range(xc - 15, xc + 16, 5):
        v.fill(x, 1, 0, x, 4, 0, S("polished_blackstone_wall"))
    v.fill(0, top, 0, w - 1, top, 0, S("b:ashlar_stairs", facing="south", half="top"))
    pt.gable_x(v, 0, w - 1, 0, d - 1, top + 1, pt.SLATE, st.wall)
    # end pavilions with pyramid roofs and a central clock tower
    for px in (0, w - 9):
        v.fill(px, 0, 0, px + 8, top + 4, d - 1, st.wall.block)
        v.fill(px + 1, 1, 1, px + 7, top + 3, d - 2, AIR)
        for y0 in (2, 8, 13):
            for x in range(px + 2, px + 7, 2):
                v.fill(x, y0, 0, x, y0 + 1, 0, pt.LIT if rng.random() < 0.5 else pt.WINDOW)
        pt.hip(v, px, px + 8, 0, d - 1, top + 5, pt.SLATE)
    tx0 = xc - 4
    v.fill(tx0, 0, 0, tx0 + 8, 34, 8, st.wall.block)
    v.fill(tx0 + 1, 1, 1, tx0 + 7, 33, 7, AIR)
    v.fill(xc - 1, 1, 0, xc + 1, 4, 0, AIR)
    dial(v, xc, 27, 0, "north", 3)
    for y in range(8, 22, 4):
        v.fill(xc, y, 0, xc, y + 2, 0, pt.WINDOW)
    v.fill(tx0, 35, 0, tx0 + 8, 35, 8, S("b:ashlar"))
    pt.hip(v, tx0, tx0 + 8, 0, 8, 36, pt.SLATE)
    v.sign(xc, 5, 1, S("dark_oak_wall_sign", facing="north"), ["", "TINGEN", "CENTRAL STATION", ""])
    cv.stamp(v, bx0, bz0, facing="south")
    marks["station"] = (xc + bx0, s, sq[3] - 3, 180)
    # the train shed behind: platforms along x, tracks running east out of the city
    shx0, shx1, shz0, shz1 = 344, 496, 10, 61
    reserve(g, shx0, shz0, 639, shz1)
    ground(g, cv, shx0, shz0, 639, shz1, "gravel", clear=True)
    cv.a[s:s + 30, shz0:shz1 + 1, shx0:shx1 + 1] = 0
    tracks = [14, 22, 30, 38, 46, 54]
    for tz in tracks:
        for x in range(shx0 + 4, 640):
            cv.a[s, tz, x] = I("rail", shape="east_west")
            cv.a[s - 1, tz, x] = I("gravel")
        cv.a[s, tz, shx0 + 3] = I("b:ashlar")            # buffer stop
        cv.a[s + 1, tz, shx0 + 3] = I("red_concrete") if False else I("b:ashlar_slab", type="bottom")
    for pz in (17, 18, 19, 25, 26, 27, 33, 34, 35, 41, 42, 43, 49, 50, 51):
        cv.a[s, pz, shx0:shx1 - 2] = I("smooth_stone")
        cv.a[s - 1, pz, shx0:shx1 - 2] = I("stone")
    for pz in (18, 26, 34, 42, 50):
        for x in range(shx0 + 8, shx1 - 4, 12):
            cv.a[s + 1, pz, x] = I("b:park_bench", facing="north")
            cv.a[s + 1:s + 4, pz, x + 6] = I("polished_blackstone_wall")
            cv.a[s + 4, pz, x + 6] = I("lantern")
    # concourse across the buffer end
    cv.a[s, shz0:shz1 + 1, shx0:shx0 + 3] = I("smooth_stone")
    # the shed roof: two spans of iron and glass
    for span0, span1 in ((shz0, 35), (36, shz1)):
        mid = (span0 + span1) / 2
        half = (span1 - span0) / 2
        for z in range(span0, span1 + 1):
            t = (z - mid) / half
            yy = s + 9 + int(round(9 * (1 - t * t) ** 0.5))
            for x in range(shx0, shx1 + 1):
                rib = (x - shx0) % 8 == 0
                cv.a[yy, z, x] = I("polished_deepslate") if rib or z in (span0, span1) else I("light_gray_stained_glass")
            if z in (span0, span1):
                for x in range(shx0, shx1 + 1, 8):
                    cv.a[s + 1:yy, z, x] = I("polished_blackstone_wall")
        # gable screens at the open ends
        for x in (shx1,):
            for z in range(span0, span1 + 1):
                t = (z - mid) / half
                yy = s + 9 + int(round(9 * (1 - t * t) ** 0.5))
                for y in range(s + 9, yy):
                    cv.a[y, z, x] = I("light_gray_stained_glass") if (z + y) % 4 else I("polished_deepslate")
    # signal posts along the open line
    for x in range(shx1 + 20, 640, 40):
        for tz in (tracks[0] - 2, tracks[-1] + 2):
            cv.a[s:s + 4, tz, x] = I("polished_blackstone_wall")
            cv.a[s + 4, tz, x] = I("redstone_lamp", lit="true")
    # level crossing where Foundry Street meets the line: cobbles between the rails
    zs = range(shz0, shz1 + 1)
    for z in zs:
        for x in range(530, 541):
            if cv.a[s, z, x] != I("rail", shape="east_west"):
                cv.a[s - 1, z, x] = I("b:tingen_cobbles")
                cv.a[s, z, x] = 0
    enclose(cv, shx1 + 1, shz0 - 2, 639, shz0 - 2, (), "b:stock_brick_wall", "b:iron_railing", "b:stock_bricks")
    enclose(cv, shx1 + 1, shz1 + 2, 639, shz1 + 2, ((535, shz1 + 2),), "b:stock_brick_wall", "b:iron_railing", "b:stock_bricks")
    reserve(g, shx1, shz1 + 1, 639, shz1 + 3)


# ---------------------------------------------------------------- Khoy University


@landmark
def khoy_university(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 120, 4, 224, 164
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "grass_block")
    wall, trim, roof = pt.RED, pt.ASHLAR, pt.SLATE
    quads = [(124, 10, 214, 78), (124, 90, 214, 158)]
    for qi, (qx0, qz0, qx1, qz1) in enumerate(quads):
        dd = 11
        # four ranges facing into the quad
        n = college_range(rng, qx1 - qx0 + 1 - 2 * dd, dd, 3, wall, trim, roof)
        cv.stamp(n, qx0 + dd, qz0, facing="south")                         # north range, front to the quad
        sr = college_range(rng, qx1 - qx0 + 1 - 2 * dd, dd, 3, wall, trim, roof)
        cv.stamp(sr, qx0 + dd, qz1 - dd + 1, facing="north")
        wr = college_range(rng, qz1 - qz0 + 1, dd, 3, wall, trim, roof)
        cv.stamp(wr, qx0, qz0, facing="east")
        er = college_range(rng, qz1 - qz0 + 1, dd, 3, wall, trim, roof)
        cv.stamp(er, qx1 - dd + 1, qz0, facing="west")
        # gate tower in the east range, facing the river
        gt = gate_tower(rng, 9, 13, 22, wall, trim, roof)
        gz = (qz0 + qz1) // 2 - 4
        cv.stamp(gt, qx1 - 12, gz, facing="east")
        # the quad: lawn, crossing paths, a great tree and lamps
        lx0, lz0, lx1, lz1 = qx0 + dd, qz0 + dd, qx1 - dd, qz1 - dd
        cv.a[s - 1, lz0:lz1 + 1, lx0:lx1 + 1] = I("grass_block")
        mx, mz = (lx0 + lx1) // 2, (lz0 + lz1) // 2
        cv.a[s - 1, mz - 1:mz + 2, lx0:lx1 + 1] = I("b:flagstones")
        cv.a[s - 1, lz0:lz1 + 1, mx - 1:mx + 2] = I("b:flagstones")
        cv.a[s - 1, lz0:lz0 + 2, lx0:lx1 + 1] = I("b:flagstones")
        cv.a[s - 1, lz1 - 1:lz1 + 1, lx0:lx1 + 1] = I("b:flagstones")
        tree(cv, lx0 + 6, s, lz0 + 6, rng, "plane"); tree(cv, lx1 - 6, s, lz1 - 6, rng, "plane")
        cv.a[s:s + 3, mz, mx] = I("b:ashlar_pillar", axis="y")
        cv.a[s + 3, mz, mx] = I("b:gas_lamp")
        for x, z in ((lx0 + 2, lz0 + 2), (lx1 - 2, lz0 + 2), (lx0 + 2, lz1 - 2), (lx1 - 2, lz1 - 2)):
            lamp(cv, x, z)
    # a riverside lawn and the name on the gate
    cv.signs.append((214 + 2, s + 5, 44, ["", "KHOY", "UNIVERSITY", ""], "white"))
    cv.a[s + 5, 44, 216] = I("dark_oak_wall_sign", facing="east")
    marks["university"] = (219, s, 44, 90)
    for z in range(z0 + 4, z1, 14):
        lamp(cv, 220, z)


# ---------------------------------------------------------------- Iron Cross Market


@landmark
def market(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 480, 184, 528, 225
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "b:tingen_cobbles")
    roof_y = s + 9
    # cast-iron columns and an iron-and-glass roof with a raised lantern
    for x in range(x0, x1 + 1, 6):
        for z in range(z0, z1 + 1, 6):
            cv.a[s:roof_y, z, x] = I("polished_blackstone_wall")
            cv.a[roof_y - 1, z, x] = I("polished_blackstone")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            edge = x in (x0 - 1, x1 + 1) or z in (z0 - 1, z1 + 1)
            cv.a[roof_y, z, x] = I("polished_deepslate_slab", type="bottom") if edge else (
                I("polished_deepslate") if (x - x0) % 6 == 0 or (z - z0) % 6 == 0 else I("light_gray_stained_glass"))
    mz = (z0 + z1) // 2
    for k in range(1, 5):
        for x in range(x0 + 2, x1 - 1):
            for z in (mz - 6 + k, mz + 6 - k):
                cv.a[roof_y + k, z, x] = I("polished_deepslate_stairs", facing="south" if z < mz else "north")
        cv.a[roof_y + k, mz - 6 + k + 1:mz + 6 - k, x0 + 2:x1 - 1] = 0
    cv.a[roof_y + 5, mz - 1:mz + 2, x0 + 2:x1 - 1] = I("polished_deepslate_slab", type="bottom")
    for x in range(x0 + 2, x1 - 1):
        cv.a[roof_y + 1:roof_y + 3, mz - 5, x] = I("glass_pane") if False else cv.a[roof_y + 1:roof_y + 3, mz - 5, x]
    # stalls in rows: counter, awning, goods
    goods = ["b:crate", "barrel", "hay_block", "melon", "pumpkin", "b:crate", "barrel", "cake", "composter"]
    for z in range(z0 + 3, z1 - 1, 6):
        for x in range(x0 + 2, x1 - 1, 5):
            colour = rng.choice(("red", "green", "blue"))
            cv.a[s, z, x:x + 3] = I("spruce_slab", type="top")
            cv.a[s + 2, z - 1, x:x + 3] = I(f"b:{colour}_awning", facing="south")
            cv.a[s, z + 1, x] = I(rng.choice(goods)); cv.a[s, z + 1, x + 2] = I(rng.choice(goods))
            cv.a[s + 1, z, x + 1] = I(rng.choice(("flower_pot", "lantern", "candle")) if rng.random() < 0.5 else "air")
    cv.sign(x0 + 24, roof_y - 1, z1 + 1, S("dark_oak_wall_sign", facing="south"), ["", "IRON CROSS", "MARKET", ""])
    cv.signs[-1] = cv.signs[-1] + ("yellow",)
    cv.a[roof_y - 1, z1, x0 + 24] = I("polished_blackstone")
    marks["market"] = (x0 + 24, s, z1 + 6, 180)


# ---------------------------------------------------------------- the workhouse


@landmark
def workhouse(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 600, 291, 639, 336
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "b:tingen_cobbles")
    st = B.Style(pt.SOOT, pt.COBBLE, pt.SOOT, pt.DEEPTILE, "spruce", "b:weathered_planks", 0.15)
    front = B.tenement(rng, 40, 12, st, 3)
    cv.stamp(front, x0, z0, facing="west") if False else cv.stamp(front, x0, z0, facing="north")
    wing = B.tenement(rng, 34, 11, st, 3)
    cv.stamp(wing, x0, z0 + 12, facing="west")
    cv.stamp(B.tenement(rng, 34, 11, st, 3), x1 - 10, z0 + 12, facing="east")
    # high yard wall with a gate
    for x in range(x0, x1 + 1):
        cv.a[s:s + 4, z1, x] = I("b:soot_bricks")
    cv.a[s:s + 3, z1, x0 + 19:x0 + 22] = 0
    cv.a[s + 3, z1, x0 + 19:x0 + 22] = I("b:soot_bricks")
    cv.sign(x0 + 20, s + 4, z1 + 1, S("spruce_wall_sign", facing="south"), ["TINGEN UNION", "WORKHOUSE", "", "Labour Within"])


# ---------------------------------------------------------------- Raphael Cemetery


@landmark
def cemetery(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 480, 510, 581, 636
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "grass_block")
    gx = (x0 + x1) // 2
    enclose(cv, x0, z0, x1, z1, ((gx, z0),), "b:ashlar_wall", "b:iron_railing", "b:ashlar")
    # gravel walks
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if abs(x - gx) <= 1 or (z - z0) % 24 in (0, 1, 2):
                cv.a[s - 1, z, x] = I("gravel")
            elif (x - x0) % 30 in (14, 15, 16):
                cv.a[s - 1, z, x] = I("gravel")
    # rows of graves facing the walks, yews, the odd mausoleum
    for z in range(z0 + 4, z1 - 1, 3):
        for x in range(x0 + 2, x1 - 1, 2):
            if cv.a[s - 1, z, x] != I("grass_block") or cv.a[s - 1, z + 1, x] != I("grass_block"):
                continue
            r = rng.random()
            if r < 0.62:
                cv.a[s, z, x] = I("b:gravestone", facing=rng.choice(("north", "north", "south")))
                if rng.random() < 0.12:
                    cv.a[s, z + 1, x] = I(rng.choice(("poppy", "wither_rose", "lily_of_the_valley", "candle")))
            elif r < 0.66:
                cv.a[s:s + 2, z, x] = I("b:ashlar_wall")
                cv.a[s + 2, z, x] = I("b:ashlar_slab", type="bottom")
            elif r < 0.69:
                cv.a[s, z, x] = I("cobblestone_wall")
                cv.a[s + 1, z, x] = I("chain")
    for i in range(16):
        x = rng.randint(x0 + 3, x1 - 3); z = rng.randint(z0 + 6, z1 - 3)
        if cv.a[s - 1, z, x] == I("grass_block") and cv.a[s, z, x] == 0:
            tree(cv, x, s, z, rng, "yew")
    for mx, mz in ((x0 + 8, z0 + 40), (x1 - 12, z0 + 64), (x0 + 30, z0 + 90), (x1 - 30, z0 + 100)):
        m = Vol(7, 9, 7)
        m.fill(0, 0, 0, 6, 4, 6, S("b:ashlar")); m.fill(1, 1, 1, 5, 3, 5, AIR)
        m.fill(3, 1, 0, 3, 2, 0, S("iron_bars"))
        m.fill(0, 0, 0, 6, 0, 0, S("b:ashlar_stairs", facing="south")) if False else None
        m.set(0, 0, 0, S("b:ashlar_pillar", axis="y")); m.set(6, 0, 0, S("b:ashlar_pillar", axis="y"))
        pt.gable_z(m, 0, 6, 0, 6, 5, pt.ASHLAR)
        cv.stamp(m, mx, mz, facing="north")
    # the cemetery chapel by the gate
    ch = Vol(13, 22, 21)
    ch.fill(0, 0, 0, 12, 8, 20, S("b:ashlar")); ch.fill(1, 1, 1, 11, 7, 19, AIR)
    ch.fill(1, 0, 1, 11, 0, 19, S("polished_andesite"))
    for z in range(3, 19, 4):
        lancet_z(ch, 0, z, z, 2, 6, pt.POL_DEEP); lancet_z(ch, 12, z, z, 2, 6, pt.POL_DEEP)
    lancet_x(ch, 5, 7, 20, 2, 6, pt.POL_DEEP)
    ch.fill(5, 1, 0, 7, 4, 0, AIR); pt.double_door(ch, 5, 1, 1, "dark_oak", "north")
    steep_gable_z(ch, 0, 12, 0, 20, 9, pt.SLATE, S("b:ashlar"))
    ch.fill(6, 21 - 1, 0, 6, 21, 0, S("b:ashlar_wall"))
    for z in range(4, 18, 2):
        ch.fill(3, 1, z, 4, 1, z, S("dark_oak_stairs", facing="south")); ch.fill(8, 1, z, 9, 1, z, S("dark_oak_stairs", facing="south"))
    cv.stamp(ch, gx + 8, z0 + 6, facing="west")
    for z in range(z0 + 3, z1, 16):
        lamp(cv, gx + 2, z)
    cv.sign(gx + 2, s + 2, z0 - 1, S("spruce_wall_sign", facing="north"), ["", "RAPHAEL", "CEMETERY", ""])
    cv.a[s + 2, z0, gx + 2] = I("b:ashlar")
    marks["cemetery"] = (gx, s, z0 - 4, 0)


# ---------------------------------------------------------------- Tingen Gardens


@landmark
def gardens(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 182, 509, 363, 583
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "grass_block")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    enclose(cv, x0, z0, x1, z1, ((cx, z0), (cx, z1), (x0, cz), (x1, cz)), "b:ashlar_wall", "b:iron_railing", "b:ashlar")
    # an oval walk and two cross walks
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            ex = ((x - cx) / ((x1 - x0) / 2 - 8)) ** 2 + ((z - cz) / ((z1 - z0) / 2 - 8)) ** 2
            if 0.82 < ex < 1.0 or abs(z - cz) <= 1 or abs(x - cx) <= 1:
                cv.a[s - 1, z, x] = I("gravel")
    # the pond with an island
    for x in range(cx - 40, cx - 12):
        for z in range(cz - 16, cz + 17):
            e = ((x - (cx - 26)) / 13) ** 2 + ((z - cz) / 15) ** 2
            if e <= 1:
                cv.a[s - 1, z, x] = I("water"); cv.a[s - 2, z, x] = I("water"); cv.a[s - 3, z, x] = I("mud")
                if e > 0.8:
                    cv.a[s - 1, z, x] = I("water")
                if e < 0.06:
                    cv.a[s - 1, z, x] = I("grass_block"); cv.a[s - 2, z, x] = I("dirt")
                elif rng.random() < 0.03:
                    cv.a[s, z, x] = I("lily_pad")
    tree(cv, cx - 26, s, cz, rng, "birch")
    # bandstand: octagonal, copper roof
    bx, bz = cx + 24, cz
    for x in range(bx - 5, bx + 6):
        for z in range(bz - 5, bz + 6):
            d = max(abs(x - bx), abs(z - bz)) + 0.4 * min(abs(x - bx), abs(z - bz))
            if d <= 6:
                cv.a[s, z, x] = I("b:ashlar_slab", type="top") if d <= 5.5 else 0
    for x, z in ((bx - 4, bz - 2), (bx - 4, bz + 2), (bx + 4, bz - 2), (bx + 4, bz + 2), (bx - 2, bz - 4), (bx + 2, bz - 4), (bx - 2, bz + 4), (bx + 2, bz + 4)):
        cv.a[s + 1:s + 5, z, x] = I("polished_blackstone_wall")
    for k in range(5):
        r = 5.5 - k * 1.2
        for x in range(bx - 6, bx + 7):
            for z in range(bz - 6, bz + 7):
                d = max(abs(x - bx), abs(z - bz)) + 0.4 * min(abs(x - bx), abs(z - bz))
                if r - 1.3 < d <= r:
                    cv.a[s + 5 + k, z, x] = I("waxed_oxidized_cut_copper")
    cv.a[s + 10, bz, bx] = I("lightning_rod")
    cv.a[s + 4, bz, bx] = I("lantern", hanging="true")
    # trees, flower beds, benches and lamps
    for i in range(70):
        x = rng.randint(x0 + 3, x1 - 3); z = rng.randint(z0 + 3, z1 - 3)
        if cv.a[s - 1, z, x] == I("grass_block") and (cv.a[s - 1, z - 2:z + 3, x - 2:x + 3] == I("grass_block")).all() and cv.a[s, z, x] == 0:
            tree(cv, x, s, z, rng, rng.choice(("plane", "oak", "birch", "dark")))
    for i in range(160):
        x = rng.randint(x0 + 2, x1 - 2); z = rng.randint(z0 + 2, z1 - 2)
        if cv.a[s - 1, z, x] == I("grass_block") and cv.a[s, z, x] == 0:
            cv.a[s, z, x] = I(rng.choice(("poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium", "short_grass", "fern")))
    for x in range(x0 + 6, x1, 10):
        if cv.a[s - 1, cz + 2, x] == I("grass_block"):
            bench(cv, x, cz + 2, "south")
        if x % 20 < 10:
            lamp(cv, x, cz - 2) if cv.a[s - 1, cz - 2, x] != I("water") else None
    marks["gardens"] = (cx, s, z0 - 3, 0)


# ---------------------------------------------------------------- Golden Indus: the opera and the bank


@landmark
def golden_indus(g, cv, rng, marks):
    s = cv.street
    op = classical(rng, 50, 40, 3, 6, "Tingen Opera House", dome=True, lit=0.6, arched=True)
    cv.stamp(op, 302, 353, facing="north")
    reserve(g, 302, 353, 302 + op.w - 1, 353 + op.d - 1)
    ground(g, cv, 302, 353, 302 + op.w - 1, 353 + op.d - 1, "polished_andesite", clear=False)
    bk = classical(rng, 40, 34, 3, 5, "Loen Commercial Bank", roof=pt.COPPER, lit=0.4)
    cv.stamp(bk, 423, 353, facing="north")
    reserve(g, 423, 353, 423 + bk.w - 1, 353 + bk.d - 1)
    marks["golden"] = (400, s, 360, 180)
    marks["opera"] = (327, s, 352, 180)


# ---------------------------------------------------------------- Tingen University (south bank)


@landmark
def tingen_university(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 6, 512, 100, 584
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "grass_block")
    wall, trim, roof = pt.ASHLAR, pt.POL_DEEP, pt.COPPER
    dd = 11
    main = college_range(rng, x1 - x0 + 1, dd, 3, wall, trim, roof)
    cv.stamp(main, x0, z0, facing="north")
    cv.stamp(college_range(rng, z1 - z0 + 1 - dd, dd, 3, wall, trim, roof), x0, z0 + dd, facing="east")
    cv.stamp(college_range(rng, z1 - z0 + 1 - dd, dd, 3, wall, trim, roof), x1 - dd + 1, z0 + dd, facing="west")
    gt = gate_tower(rng, 11, 13, 26, wall, trim, roof)
    cv.stamp(gt, (x0 + x1) // 2 - 5, z0 - 1, facing="north")
    for x in range(x0 + dd + 3, x1 - dd - 2, 9):
        for z in range(z0 + dd + 4, z1 - 2, 9):
            tree(cv, x, s, z, rng, "plane")
    cv.sign((x0 + x1) // 2, s + 6, z0 - 2, S("dark_oak_wall_sign", facing="north"), ["", "TINGEN", "UNIVERSITY", ""])
    cv.signs[-1] = cv.signs[-1] + ("white",)
    marks["tingen_university"] = ((x0 + x1) // 2, s, z0 - 6, 0)


# ---------------------------------------------------------------- gasworks


@landmark
def gasworks(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 600, 510, 639, 584
    reserve(g, x0, z0, x1, z1)
    ground(g, cv, x0, z0, x1, z1, "b:tingen_cobbles")
    for gx, gz, r, h in ((619, 530, 11, 22), (619, 563, 10, 18)):
        for x in range(gx - r - 1, gx + r + 2):
            for z in range(gz - r - 1, gz + r + 2):
                d = ((x - gx) ** 2 + (z - gz) ** 2) ** 0.5
                if d <= r - 1:
                    cv.a[s:s + h - 3, z, x] = I("b:red_bricks") if False else 0
                    cv.a[s + h - 4, z, x] = I("polished_deepslate_slab", type="bottom")
                if r - 2 < d <= r - 1:
                    cv.a[s:s + h - 4, z, x] = I("gray_terracotta")
                    if (s + h) % 3 == 0:
                        pass
        # guide frame: columns round the tank tied by girders
        for k in range(12):
            a = k * 3.14159 * 2 / 12
            x = int(round(gx + r * 0.98 * __import__("math").cos(a))); z = int(round(gz + r * 0.98 * __import__("math").sin(a)))
            cv.a[s:s + h, z, x] = I("polished_blackstone_wall")
        for y in (s + 7, s + 14, s + h - 1):
            for k in range(72):
                a = k * 3.14159 * 2 / 72
                x = int(round(gx + r * __import__("math").cos(a))); z = int(round(gz + r * __import__("math").sin(a)))
                if cv.a[y, z, x] == 0:
                    cv.a[y, z, x] = I("polished_blackstone_slab", type="top")
    # coal heaps
    for i in range(6):
        hx, hz = rng.randint(x0 + 2, x0 + 8), rng.randint(z0 + 2, z1 - 2)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                hgt = 2 - max(abs(dx), abs(dz))
                if hgt >= 0 and 0 <= hz + dz < cv.d:
                    cv.a[s:s + hgt + 1, hz + dz, hx + dx] = I("coal_block")
    enclose(cv, x0, z0, x1, z1, ((x0, (z0 + z1) // 2),), "b:soot_brick_wall", "b:iron_railing", "b:soot_bricks")


# ---------------------------------------------------------------- docks on the Tussock


@landmark
def docks(g, cv, rng, marks):
    """Timber jetties out into the river below Lower Ward Street, with cranes and cargo."""
    s = cv.street
    from terrain import WATER_TOP
    for jx in range(500, 636, 18):
        # find the bank: first water cell going south
        zw = None
        for z in range(430, 500):
            if g.water[z, jx]:
                zw = z
                break
        if zw is None:
            continue
        for z in range(zw - 3, zw + 9):
            for x in range(jx, jx + 5):
                if g.wall[z, x] or g.water[z, x]:
                    cv.a[s - 1, z, x] = I("spruce_planks")
                    cv.a[s:s + 2, z, x] = 0
                    if (x - jx) in (0, 4) and (z - zw) % 4 == 0 and g.water[z, x]:
                        cv.a[s - 11:s - 1, z, x] = I("spruce_log", axis="y")
        # bollards, cargo and a crane at the head
        cv.a[s, zw + 8, jx] = I("b:ashlar_wall") if False else I("spruce_fence")
        cv.a[s, zw + 8, jx + 4] = I("spruce_fence")
        for z in range(zw, zw + 6, 2):
            cv.a[s, z, jx + rng.choice((0, 4))] = I(rng.choice(("b:crate", "barrel", "b:crate", "hay_block")))
        cv.a[s:s + 8, zw + 6, jx + 2] = I("b:dark_timber", axis="y")
        for k in range(1, 5):
            cv.a[s + 8, zw + 6 + k, jx + 2] = I("b:dark_timber", axis="z")
        cv.a[s + 5:s + 8, zw + 10, jx + 2] = I("chain")
        cv.a[s + 4, zw + 10, jx + 2] = I("b:crate")
    marks["docks"] = (560, s, 430, 180)


# ---------------------------------------------------------------- the works north of the canal


@landmark
def works(g, cv, rng, marks):
    """Mills and foundries with tall chimneys, facing Canal Street and Foundry Street."""
    lots = [(543, 86, 38, 30, "south"), (600, 86, 39, 30, "south"), (543, 63, 38, 20, "north"),
            (262, 60, 34, 26, "east"), (262, 8, 34, 30, "east")]
    for x, z, w, d, face in lots:
        st = B.style(rng, rng.choice(("red", "soot", "vbrick")))
        if face in ("north", "south"):
            v = B.factory(rng, w, d, st)
            fx, fz = x, z
            fw, fd = w, d
        else:
            v = B.factory(rng, d, w, st)
            fx, fz = x, z
            fw, fd = w, d
        if not g.free[fz:fz + fd, fx:fx + fw].all():
            continue
        cv.stamp(v, fx, fz, facing=face)
        reserve(g, fx, fz, fx + fw - 1, fz + fd - 1)


# ---------------------------------------------------------------- district churches


def church(rng, wall, trim, roof, glass_a, glass_b, spire=True, name=("", "", "", "")):
    """A parish church facing north: nave with lancets, a corner tower with a broach spire."""
    w, d = 15, 30
    v = Vol(w, 46, d)
    v.fill(1, 0, 4, w - 2, 9, d - 1, wall.block)
    v.fill(2, 1, 5, w - 3, 8, d - 2, AIR)
    for x in range(2, w - 2):
        for z in range(5, d - 1):
            v.set(x, 0, z, S("polished_andesite") if (x + z) % 2 else S("b:flagstones"))
    for z in range(7, d - 2, 4):
        for x in (1, w - 2):
            for y in range(2, 8):
                v.set(x, y, z, S(glass_a) if (y + z) % 2 else S(glass_b))
            v.set(x, 8, z, trim.block)
        for x in (0, w - 1):
            v.fill(x, 0, z + 2, x, 6, z + 2, trim.block)
            v.set(x, 7, z + 2, trim.stairs("east" if x == 0 else "west"))
    # west front: a tall window and the door
    for x in range(5, 10):
        for y in range(4, 9):
            v.set(x, y, 4, S(glass_a) if (x + y) % 2 else S(glass_b))
    v.set(5, 9, 4, trim.stairs("east", "top")); v.set(9, 9, 4, trim.stairs("west", "top"))
    v.fill(6, 1, 4, 8, 3, 4, AIR)
    pt.double_door(v, 6, 1, 5, "dark_oak", "north")
    v.set(8, 1, 5, S("dark_oak_door", facing="north", half="lower", hinge="right"))
    v.set(8, 2, 5, S("dark_oak_door", facing="north", half="upper", hinge="right"))
    v.set(7, 0, 3, trim.stairs("south"))
    steep_gable_z(v, 1, w - 2, 4, d - 1, 10, roof, wall.block)
    # tower at the north-east corner
    tx0, tz0 = w - 5, 0
    v.fill(tx0, 0, tz0, tx0 + 4, 20, tz0 + 4, wall.block)
    v.fill(tx0 + 1, 1, tz0 + 1, tx0 + 3, 19, tz0 + 3, AIR)
    for y in (6, 12):
        v.fill(tx0 + 2, y, tz0, tx0 + 2, y + 2, tz0, pt.WINDOW)
    v.fill(tx0 + 1, 16, tz0, tx0 + 3, 18, tz0, S("iron_bars")); v.fill(tx0, 16, tz0 + 1, tx0, 18, tz0 + 3, S("iron_bars"))
    v.fill(tx0 + 4, 16, tz0 + 1, tx0 + 4, 18, tz0 + 3, S("iron_bars"))
    v.set(tx0 + 2, 17, tz0 + 2, S("bell", attachment="ceiling", facing="north"))
    v.fill(tx0 + 2, 18, tz0 + 2, tx0 + 2, 19, tz0 + 2, S("chain"))
    if spire:
        for k in range(15):
            r = 2.6 - k * 0.175
            for x in range(tx0, tx0 + 5):
                for z in range(tz0, tz0 + 5):
                    if max(abs(x - tx0 - 2), abs(z - tz0 - 2)) <= r:
                        v.set(x, 21 + k, z, roof.block)
        v.fill(tx0 + 2, 36, tz0 + 2, tx0 + 2, 37, tz0 + 2, trim.wall())
        v.set(tx0 + 2, 38, tz0 + 2, S("lightning_rod"))
    else:
        for x in range(tx0, tx0 + 5):
            for z in range(tz0, tz0 + 5):
                if x in (tx0, tx0 + 4) or z in (tz0, tz0 + 4):
                    v.set(x, 21, z, trim.block if (x + z) % 2 == 0 else AIR)
    v.sign(7, 4, 3, S("dark_oak_wall_sign", facing="north"), list(name))
    for z in range(8, d - 4, 2):
        v.fill(2, 1, z, 5, 1, z, S("dark_oak_stairs", facing="south"))
        v.fill(9, 1, z, 12, 1, z, S("dark_oak_stairs", facing="south"))
    v.fill(5, 1, d - 3, 9, 1, d - 3, S("smooth_quartz"))
    v.set(7, 2, d - 3, S("candle", candles="4", lit="true"))
    for z in range(8, d - 2, 6):
        v.set(7, 8, z, S("lantern", hanging="true"))
    return v


def find_lot(g, near, w, d, facing, radius=60):
    """A free w x d lot (turned to face ``facing``) whose front touches a street, as close to ``near`` as can be."""
    fw, fd = (w, d) if facing in ("north", "south") else (d, w)
    nx, nz = near
    best = None
    for r in range(0, radius):
        for x in range(nx - r, nx + r + 1):
            for z in (nz - r, nz + r) if r else (nz,):
                for xx, zz in ((x, z), (nx + (z - nz), nz + (x - nx))):
                    if xx < 1 or zz < 1 or xx + fw >= g.free.shape[1] - 1 or zz + fd >= g.free.shape[0] - 1:
                        continue
                    if not g.free[zz:zz + fd, xx:xx + fw].all():
                        continue
                    if facing == "north":
                        front = g.cell[zz - 1, xx:xx + fw]
                    elif facing == "south":
                        front = g.cell[zz + fd, xx:xx + fw]
                    elif facing == "west":
                        front = g.cell[zz:zz + fd, xx - 1]
                    else:
                        front = g.cell[zz:zz + fd, xx + fw]
                    if ((front >= 1) & (front <= 3)).mean() > 0.9:
                        return xx, zz
    return best


@landmark
def churches(g, cv, rng, marks):
    s = cv.street
    plans = [
        ("storm", (140, 352), "north", pt.STONEBRICK, pt.POL_DEEP, pt.SLATE, "blue_stained_glass", "cyan_stained_glass", True,
         ("Church of the", "Lord of Storms", "", "")),
        ("steam", (280, 101), "east", pt.RED, pt.ASHLAR, pt.COPPER, "orange_stained_glass", "yellow_stained_glass", False,
         ("Church of the God", "of Steam and", "Machinery", "")),
        ("evernight", (560, 300), "west", pt.DEEP_BRICK, pt.BLACK, pt.DEEPTILE, "blue_stained_glass", "red_stained_glass", True,
         ("Evernight", "Chapel of", "Saint Arianna", "")),
        ("west_chapel", (75, 270), "south", pt.ASHLAR, pt.POL_DEEP, pt.SLATE, "blue_stained_glass", "red_stained_glass", True,
         ("Evernight", "Chapel of", "the Crimson Moon", "")),
    ]
    for key, near, facing, wall, trim, roof, ga, gb, spire, name in plans:
        v = church(rng, wall, trim, roof, ga, gb, spire, name)
        spot = find_lot(g, near, v.w, v.d, facing)
        if spot is None:
            print("no lot for church", key)
            continue
        x, z = spot
        fw, fd = (v.w, v.d) if facing in ("north", "south") else (v.d, v.w)
        ground(g, cv, x, z, x + fw - 1, z + fd - 1, "b:flagstones", clear=False)
        cv.stamp(v, x, z, facing=facing)
        reserve(g, x, z, x + fw - 1, z + fd - 1)
        marks["church_" + key] = (x + fw // 2, s, z + fd // 2, 0)


# ---------------------------------------------------------------- civic buildings along the avenues


@landmark
def civic(g, cv, rng, marks):
    s = cv.street
    plans = [
        ("library", "Tingen Public Library", (200, 183), "north", 34, 26, 3, 4, dict(dome=True, arched=True)),
        ("police", "Tingen Police Headquarters", (150, 160), "south", 30, 22, 3, 4, dict(wall="b:red_bricks")),
        ("infirmary", "Tingen Infirmary", (520, 352), "north", 40, 24, 3, 4, dict(wall="b:stock_bricks", lit=0.6)),
        ("courier", "The Tingen Courier", (60, 352), "north", 24, 20, 3, 3, dict(wall="b:red_bricks")),
        ("hotel", "Grand Hotel Tussock", (380, 338), "south", 40, 22, 4, 4, dict(lit=0.7, arched=True)),
        ("museum", "Tingen Museum", (300, 196), "north", 36, 26, 2, 6, dict(dome=True)),
    ]
    for key, name, near, facing, w, d, storeys, cols, kw in plans:
        dome = kw.pop("dome", False)
        v = classical(rng, w, d, storeys, cols, name, dome=dome, **kw)
        spot = find_lot(g, near, v.w, v.d, facing, radius=80)
        if spot is None:
            print("no lot for", key)
            continue
        x, z = spot
        fw, fd = (v.w, v.d) if facing in ("north", "south") else (v.d, v.w)
        ground(g, cv, x, z, x + fw - 1, z + fd - 1, "b:flagstones", clear=False)
        cv.stamp(v, x, z, facing=facing)
        reserve(g, x, z, x + fw - 1, z + fd - 1)
        marks[key] = (x + fw // 2, s, z + (fd + 4 if facing == "south" else -4), 0 if facing == "south" else 180)


# ---------------------------------------------------------------- ships on the Tussock


def steamer(rng, L=46, B=11, funnel="red_terracotta"):
    """A paddle-less screw steamer, bow to the east (+x): black hull with a red boot-top, white deckhouse,
    two funnels, masts. y=0 is the keel; the deck is at y=7."""
    v = Vol(L, 26, B)
    cz = B // 2
    deck = 7
    def half(x):
        if x < 0 or x >= L:
            return -1
        if x < 3:
            return 3 + x
        if x > L - 10:
            return max(0, int(round(5 * ((L - 1 - x) / 9.0) ** 0.7)))
        return 5

    for x in range(L):
        hw = half(x)
        for y in range(0, deck + 1):
            # sections narrow toward the keel
            shrink = 2 if y == 0 else 1 if y == 1 else 0
            w = hw - shrink
            if w < 0:
                continue
            wn, wp = half(x + 1) - shrink, half(x - 1) - shrink
            for z in range(cz - w, cz + w + 1):
                off = abs(z - cz)
                edge = off == w or y == 0 or off > wn or off > wp
                if y == deck:
                    v.set(x, y, z, S("spruce_planks") if off < w else S("b:dark_timber", axis="x"))
                elif edge:
                    v.set(x, y, z, S("red_terracotta") if y <= 3 else (S("polished_blackstone") if y < deck - 1 else S("white_terracotta")))
                else:
                    v.set(x, y, z, AIR)
        # railings on the deck edge
        if hw > 0:
            for z in (cz - hw, cz + hw):
                v.set(x, deck + 1, z, S("iron_bars"))
    # bow: a raised forecastle and a bowsprit
    for x in range(L - 9, L):
        for z in range(B):
            if v.get(x, deck, z) not in (KEEP, 0):
                v.set(x, deck + 1, z, S("spruce_slab", type="bottom") if v.get(x, deck + 1, z) != S("iron_bars") else S("iron_bars"))
    v.fill(L - 1, deck + 1, cz, L - 1, deck + 1, cz, S("b:dark_timber", axis="x"))
    # deckhouse with windows, the bridge above it
    hx0, hx1 = 14, 31
    v.fill(hx0, deck + 1, cz - 3, hx1, deck + 3, cz + 3, S("white_terracotta"))
    v.fill(hx0 + 1, deck + 1, cz - 2, hx1 - 1, deck + 2, cz + 2, AIR)
    for x in range(hx0 + 1, hx1, 2):
        v.set(x, deck + 2, cz - 3, S("glass_pane")); v.set(x, deck + 2, cz + 3, S("glass_pane"))
    v.fill(hx0, deck + 4, cz - 3, hx1, deck + 4, cz + 3, S("spruce_slab", type="bottom"))
    v.fill(hx1 - 5, deck + 4, cz - 2, hx1, deck + 6, cz + 2, S("white_terracotta"))
    v.fill(hx1 - 5, deck + 5, cz - 2, hx1, deck + 5, cz + 2, S("glass_pane"))
    v.fill(hx1 - 5, deck + 7, cz - 2, hx1, deck + 7, cz + 2, S("spruce_slab", type="bottom"))
    v.set(hx1 - 2, deck + 4, cz, AIR)
    # funnels: red with a black top
    for fx in (19, 25):
        v.fill(fx, deck + 4, cz - 1, fx + 1, deck + 12, cz, S(funnel))
        v.fill(fx, deck + 13, cz - 1, fx + 1, deck + 14, cz, S("black_terracotta"))
        v.set(fx, deck + 15, cz - 1, S("b:chimney"))
    # masts with yards
    for mx, h in ((8, 14), (L - 12, 16)):
        v.fill(mx, deck + 1, cz, mx, deck + h, cz, S("spruce_fence"))
        v.fill(mx, deck + h - 3, cz - 3, mx, deck + h - 3, cz + 3, S("spruce_fence"))
        v.set(mx, deck + h + 1, cz, S("lantern"))
    # lifeboats on the deckhouse roof sides, cargo hatch fore and aft
    for bx in (hx0 + 2, hx0 + 7):
        v.fill(bx, deck + 5, cz - 3, bx + 3, deck + 5, cz - 3, S("oak_slab", type="bottom"))
        v.fill(bx, deck + 5, cz + 3, bx + 3, deck + 5, cz + 3, S("oak_slab", type="bottom"))
    for hx in (5, L - 16):
        v.fill(hx, deck + 1, cz - 2, hx + 3, deck + 1, cz + 2, S("spruce_trapdoor", half="bottom", facing="north", open="false"))
    v.set(4, deck + 1, cz, S("b:crate")); v.set(L - 14, deck + 1, cz - 3, S("barrel", facing="up"))
    # name on the stern
    v.sign(0, deck - 1, cz, S("spruce_wall_sign", facing="west"), ["", rng.choice(["SS TUSSOCK QUEEN", "SS KHOY STAR", "SS PRITZ", "SS LOEN PRIDE"]), "TINGEN", ""])
    return v


def barge(rng, L=26, B=7):
    v = Vol(L, 12, B)
    cz = B // 2
    for x in range(L):
        hw = 3 if 1 < x < L - 2 else 2
        for y in range(0, 5):
            for z in range(cz - hw, cz + hw + 1):
                edge = abs(z - cz) == hw or y == 0 or x in (0, L - 1)
                v.set(x, y, z, S("spruce_planks") if edge else AIR)
        for z in (cz - hw, cz + hw):
            v.set(x, 5, z, S("b:dark_timber", axis="x"))
    for x in range(3, L - 3, 3):
        for z in range(cz - 2, cz + 3):
            v.set(x, 4, z, S(rng.choice(("coal_block", "b:crate", "barrel", "hay_block"))))
    v.fill(L - 5, 5, cz - 1, L - 2, 7, cz + 1, S("b:weathered_planks"))
    v.fill(L - 5, 8, cz - 1, L - 2, 8, cz + 1, S("spruce_slab", type="bottom"))
    v.fill(L - 4, 9, cz, L - 4, 11, cz, S("cobblestone_wall"))
    v.set(L - 4, 12 - 1, cz, S("b:chimney"))
    return v


@landmark
def ships(g, cv, rng, marks):
    from terrain import WATER_TOP
    s = cv.street
    water_top = s - WATER_TOP
    placed = 0
    for vol, near_x, near_z in ((steamer(rng), 330, 470), (steamer(rng, 40, 11, "black_terracotta"), 470, 482),
                                (barge(rng), 600, 486), (barge(rng), 200, 450)):
        best = None
        for dx in range(-40, 41, 2):
            for dz in range(-12, 13):
                x0, z0 = near_x + dx, near_z + dz - vol.d // 2
                if x0 < 2 or z0 < 2 or x0 + vol.w >= cv.w - 2 or z0 + vol.d >= cv.d - 2:
                    continue
                # the whole hull (plus a margin) must sit in open water
                if g.water[z0 - 1:z0 + vol.d + 1, x0 - 1:x0 + vol.w + 1].all():
                    best = (x0, z0)
                    break
            if best:
                break
        if not best:
            print("no water for a ship near", near_x, near_z)
            continue
        x0, z0 = best
        keel = water_top - 4 if vol.h > 20 else water_top - 2
        cv.stamp(vol, x0, z0, y=keel, facing="north")
        placed += 1
        if placed == 1:
            marks["river"] = (x0 + vol.w // 2, s, z0 - 14, 0)


# ---------------------------------------------------------------- the Nighthawks' underground


@landmark
def nighthawks_underground(g, cv, rng, marks):
    """Under Blackthorn Security: a hidden ladder down to a tunnel beneath Zouteland Street and a vaulted hall
    under the cathedral, with an armory, an archive, a duty room and the great sealed gate at its end."""
    s = cv.street
    lo, hi = s - 12, s - 6                 # floor and ceiling of the underground
    BR = I("polished_blackstone_bricks")
    WALL = I("deepslate_bricks")

    def room(x0, z0, x1, z1, floor=None):
        cv.a[lo:hi + 1, z0 - 1:z1 + 2, x0 - 1:x1 + 2] = WALL
        cv.a[lo + 1:hi, z0:z1 + 1, x0:x1 + 1] = 0
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                cv.a[lo, z, x] = floor if floor is not None else (I("polished_deepslate") if (x + z) % 2 else BR)

    # tunnel south under Zouteland Street, then west under the cathedral
    room(405, 201, 407, 240)
    room(393, 238, 407, 240)
    # the shaft (after the tunnel, whose end wall it cuts through) inside the office, under a trapdoor in the floor
    sx, sz = 406, 200
    cv.a[s + 1, sz, sx] = I("spruce_trapdoor", half="top", facing="south", open="false")
    for y in range(lo + 1, s + 1):
        cv.a[y, sz, sx] = I("ladder", facing="south")
    cv.a[lo:s + 1, sz - 1, sx] = WALL                       # wall the ladder hangs on
    cv.a[lo, sz, sx] = WALL
    for z in range(205, 238, 6):
        cv.a[hi - 1, z, 406] = I("lantern", hanging="true")
    # the duty room off the tunnel
    room(396, 226, 403, 236)
    cv.a[lo + 1:lo + 3, 231, 404] = 0
    for x in (398, 401):
        cv.a[lo + 1, 230:233, x] = I("dark_oak_fence")
        cv.a[lo + 2, 230:233, x] = I("dark_oak_pressure_plate")
        cv.a[lo + 1, 229, x] = I("dark_oak_stairs", facing="south")
        cv.a[lo + 1, 233, x] = I("dark_oak_stairs", facing="north")
    cv.a[lo + 3, 236, 399] = I("b:clock_face", facing="north")
    cv.a[hi - 1, 231, 399] = I("lantern", hanging="true")
    cv.a[lo + 1, 227, 396:399] = I("chest", facing="south")
    # the hall: piers, vaulting, lanterns
    hx0, hz0, hx1, hz1 = 368, 230, 392, 250
    room(hx0, hz0, hx1, hz1)
    for x in range(hx0 + 3, hx1, 5):
        for z in (hz0 + 3, hz1 - 3):
            cv.a[lo + 1:hi, z, x] = BR
            cv.a[hi - 1, z + (1 if z < 240 else -1), x] = I("polished_blackstone_brick_stairs",
                                                            facing="north" if z < 240 else "south", half="top")
            cv.a[lo + 1, z + (1 if z < 240 else -1), x] = I("candle", candles="3", lit="true")
        cv.a[hi - 1, 240, x] = I("chain") if False else cv.a[hi - 1, 240, x]
        cv.a[hi - 1, 240, x] = I("lantern", hanging="true")
    cv.a[lo + 1:lo + 4, 239:242, hx1 + 1] = 0               # opening from the tunnel
    # the gate at the west end: a towering black door in a carved frame, candles before it
    gx = hx0
    cv.a[lo + 1:hi, hz0:hz1 + 1, gx] = BR
    for z in range(236, 245):
        for y in range(lo + 1, hi):
            seam = z == 240
            edge = z in (236, 244) or y == hi - 1
            cv.a[y, z, gx] = I("chiseled_polished_blackstone") if seam or edge else I("polished_blackstone")
    for z in (238, 242):
        cv.a[lo + 3, z, gx + 1] = I("iron_bars")
    for z in range(236, 245, 2):
        cv.a[lo + 1, z, gx + 2] = I("candle", candles="4", lit="true")
    cv.a[lo + 1, 240, gx + 3] = I("soul_lantern")
    cv.sign(gx + 1, lo + 4, 240, S("dark_oak_wall_sign", facing="east"), ["", "CHANIS", "GATE", ""])
    # armory to the north
    room(374, 222, 386, 228)
    cv.a[lo + 1:lo + 3, 229, 380] = 0
    cv.a[lo + 1, 222, 374:387] = I("barrel", facing="south")
    cv.a[lo + 2, 222, 374:387:2] = I("b:crate")
    cv.a[lo + 1, 226, 376] = I("anvil", facing="north")
    cv.a[lo + 1, 226, 378] = I("grindstone", face="floor", facing="north")
    cv.a[lo + 1, 226, 380] = I("smithing_table")
    cv.a[lo + 1, 228, 383:387] = I("chest", facing="north")
    cv.a[hi - 1, 225, 380] = I("lantern", hanging="true")
    # archive to the south
    room(374, 252, 388, 260)
    cv.a[lo + 1:lo + 3, 251, 380] = 0
    for x in range(374, 389):
        cv.a[lo + 1:hi, 260, x] = I("bookshelf")
    for z in range(254, 260, 2):
        cv.a[lo + 1:hi - 1, z, 374:378] = I("chiseled_bookshelf", facing="east")
        cv.a[lo + 1:hi - 1, z, 384:389] = I("bookshelf")
    cv.a[lo + 1, 253, 380] = I("lectern", facing="south")
    cv.a[lo + 1, 256, 381] = I("cartography_table")
    cv.a[hi - 1, 256, 380] = I("lantern", hanging="true")
    marks["nighthawks"] = (385, lo + 1, 240, 90)
