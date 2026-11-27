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


def street_signs(g, cv, rng):
    """Name plates on the corner buildings at every junction, and on posts where there is no wall."""
    s = cv.street
    done = set()
    for sid, st in enumerate(g.streets):
        if st.kind == "lane":
            continue
        # walk the outer pavement edge on both sides and put a sign where a building corner meets a crossing
        for side in (-1, 1):
            c = st.c + side * (st.hw + 1)          # first cell beyond the street
            for t in range(st.a, st.b + 1, 1):
                x, z = (t, c) if st.axis == "x" else (c, t)
                if not (1 <= x < cv.w - 1 and 1 <= z < cv.d - 1):
                    continue
                if not g.built[z, x]:
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
        if (x * 13 + z * 7) % 61 == 0 and g.free[max(z - 2, 0):z + 3, max(x - 2, 0):x + 3].all():
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


def dial(v, cx, cy, z, face_dir, r=3):
    """A big clock dial of blocks on the plane z (or x) with hands at ten past ten."""
    for dx in range(-r, r + 1):
        for dy in range(-r, r + 1):
            d2 = dx * dx + dy * dy
            if d2 <= r * r + 1:
                b = S("calcite") if d2 < (r - 0.5) ** 2 else S("polished_blackstone")
                v.set(cx + dx, cy + dy, z, b)
    for dx, dy in ((0, r - 1), (r - 1, 0), (0, -(r - 1)), (-(r - 1), 0)):
        v.set(cx + dx, cy + dy, z, S("polished_blackstone"))
    for k in range(1, r - 1):
        v.set(cx - k, cy + k // 2, z, S("gray_concrete")) if False else v.set(cx - k, cy + (k + 1) // 2, z, S("polished_blackstone"))
        v.set(cx + k, cy + k, z, S("polished_blackstone"))
    v.set(cx, cy, z, S("b:clock_face", facing=face_dir))


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
    dial(v, cx, 29, 0, "north")
    for y in range(26, 33):
        for z in range(2, 9):
            pass
    for side_x, face in ((t0, "west"), (t1, "east")):
        for dz in range(-3, 4):
            for dy in range(-3, 4):
                d2 = dz * dz + dy * dy
                if d2 <= 10:
                    v.set(side_x, 29 + dy, 5 + dz, S("calcite") if d2 < 6.25 else S("polished_blackstone"))
        v.set(side_x, 29, 5, S("b:clock_face", facing=face))
        v.set(side_x, 31, 5, S("polished_blackstone")); v.set(side_x, 30, 5, S("polished_blackstone"))
        v.set(side_x, 29, 6 if face == "west" else 4, S("polished_blackstone"))
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
                cv.a[s, z, x + dx] = I("dark_oak_stairs", facing="south" if z == pz0 + 2 else "north")
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
