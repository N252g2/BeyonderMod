"""The outer districts: a 128-block ring round the old city, which makes Tingen 896 x 896.

North: Northgate's terraces and the brickfields with their bottle kilns. West: Westbrook and its market square.
East: Eastwharf, where the canal ends in a dock basin and the railway runs out of town. South: Southwick's villas,
the County Asylum in its grounds and Tingen Gaol. A Boundary Road follows the old city limit all the way round.
"""
import copy
import math
import numpy as np
from world import S, I, P, AIR, KEEP, Vol, Canvas
from terrain import Ground, River, Street, tree, WATER_TOP
import blocks as BL
import buildings as B
import parts as pt
import specials as SP

OFF = 128


def wrap(inner_cv, inner_g, inner_marks, rng, profiles):
    n = inner_cv.w + 2 * OFF
    cv = Canvas(n, inner_cv.h, n, inner_cv.street)
    g = Ground(cv, rng)
    g.built = np.zeros((n, n), bool)
    g.yard = np.zeros((n, n), bool)
    g.base()
    a, b = OFF, OFF + inner_cv.w
    cv.a[:, a:b, a:b] = inner_cv.a
    for name in ("water", "wall", "prom", "cell", "street_id", "built", "yard"):
        getattr(g, name)[a:b, a:b] = getattr(inner_g, name)
    g.free[a:b, a:b] = False
    g.only[a:b, a:b] = False
    for st in inner_g.streets:
        t = copy.copy(st)
        t.c += OFF
        t.a += OFF
        t.b += OFF
        g.streets.append(t)
    cv.signs = [(x + OFF, y, z + OFF, *rest) for (x, y, z, *rest) in inner_cv.signs]
    marks = {k: (x + OFF, y, z + OFF, yaw) for k, (x, y, z, yaw) in inner_marks.items()}
    start = len(g.streets)
    plan_ring(g)
    g.paint_streets(start)
    for fn in RING_LANDMARKS:
        fn(g, cv, rng, marks)
    g.street_lamps(start=start)
    districts = [
        ("west", (0, 0, 300, 127)),
        ("north", (300, 0, n - 1, 127)),
        ("west", (0, 128, 127, b - 1)),
        ("docks", (b, 128, n - 1, 700)),
        ("east", (b, 700, n - 1, n - 1)),
        ("villas", (0, b, 560, n - 1)),
        ("west", (560, b, b - 1, n - 1)),
    ]
    BL.fill_city(g, cv, rng, districts, profiles)
    SP.street_signs(g, cv, rng, start)
    g.sewers(start)
    SP.leftovers(g, cv, rng)
    # weirs where the rivers leave the bigger city
    s = cv.street
    edge = np.zeros_like(g.water)
    edge[:2, :] = edge[-2:, :] = True
    edge[:, :2] = edge[:, -2:] = True
    zs, xs = np.nonzero(g.water & edge)
    cv.a[s - 12:s - WATER_TOP + 1, zs, xs] = I("mossy_stone_bricks")
    cv.a[s - WATER_TOP + 1, zs, xs] = I("b:ashlar_slab", type="bottom")
    return cv, g, marks


def plan_ring(g):
    n = g.free.shape[0]
    e = n - 1
    g.add_river(River("Khoy", [(358, -8), (364, 40), (370, 90), (374, 140)], [18] * 4))
    g.add_river(River("Tussock", [(-8, 540), (40, 552), (90, 560), (136, 566)], [22] * 4))
    g.add_river(River("Tussock", [(760, 600), (812, 607), (860, 603), (n + 8, 598)], [30] * 4))
    g.add_river(River("Tingen Canal", [(760, 274), (806, 274)], [8, 8]))
    g.finish_rivers()
    A = g.add_street
    # the arterial avenues run on out of town
    A(Street("Kingsway", "x", 303, 0, e, "avenue", True))
    A(Street("Harvest Avenue", "x", 473, 0, e, "avenue", True, trees=True))
    A(Street("Lindon Road", "z", 238, 0, e, "avenue", True))
    A(Street("Grand Avenue", "z", 598, 700, e, "avenue", True, trees=True))
    A(Street("Iron Cross Street", "z", 718, 700, e, "avenue", True))
    # Boundary Road, along the old city limit
    A(Street("Boundary Road", "x", 122, 0, e, "street"))
    A(Street("Boundary Road", "x", 773, 0, e, "street"))
    A(Street("Boundary Road", "z", 122, 0, e, "street"))
    A(Street("Boundary Road", "z", 773, 0, e, "street"))
    # older streets carried on outward
    A(Street("Thornhill Street", "x", 183, 0, 130, "street"))
    A(Street("Cutler Street", "x", 243, 0, 130, "street"))
    A(Street("Daffodil Street", "x", 368, 0, 130, "street"))
    A(Street("Willow Street", "x", 423, 0, 130, "street"))
    A(Street("Weir Street", "x", 526, 0, 130, "street"))
    A(Street("Tussock Embankment", "x", 630, 0, e, "street"))
    A(Street("Elm Street", "x", 718, 0, 130, "street"))
    A(Street("Canal Street", "x", 250, 765, e, "street"))
    A(Street("Coalyard Lane", "x", 360, 765, e, "street"))
    A(Street("Tanner Street", "x", 413, 765, e, "street"))
    A(Street("Lower Ward Street", "x", 523, 765, e, "street"))
    A(Street("Chapel Street", "z", 303, 765, e, "street"))
    A(Street("Garden Row", "z", 498, 765, e, "street"))
    # the ring's own streets
    A(Street("Brickfield Lane", "x", 58, 0, e, "street"))
    A(Street("Westbrook Road", "z", 58, 0, 778, "street"))
    A(Street("Wharf Road", "z", 835, 0, 778, "street"))
    A(Street("Southwick Road", "x", 835, 117, 778, "street"))
    A(Street("Kiln Street", "z", 470, 0, 120, "street"))
    A(Street("Clay Street", "z", 640, 0, 120, "street"))
    A(Street("Northgate Street", "z", 170, 0, 120, "street"))


RING_LANDMARKS = []


def ring_landmark(fn):
    RING_LANDMARKS.append(fn)
    return fn


# ---------------------------------------------------------------- the brickfields


def bottle_kiln(rng, r=5, h=16):
    """A bottle oven: a fat brick cone that narrows into a neck, smoking."""
    v = Vol(2 * r + 3, h + 4, 2 * r + 3)
    c = r + 1
    for y in range(h):
        t = y / (h - 1)
        rr = r * (1 - 0.75 * t ** 1.6) + 0.6
        for x in range(v.w):
            for z in range(v.d):
                d = math.hypot(x - c, z - c)
                if d <= rr:
                    v.set(x, y, z, S("b:red_bricks") if d > rr - 1.2 else AIR)
    v.fill(c - 1, 0, 0, c + 1, 2, c, AIR)
    v.set(c, 0, c, S("campfire", lit="true"))
    v.set(c, h, c, S("b:chimney"))
    for y in (3, 7):
        for x in range(v.w):
            for z in range(v.d):
                d = math.hypot(x - c, z - c)
                rr = r * (1 - 0.75 * (y / (h - 1)) ** 1.6) + 0.6
                if rr - 1.2 < d <= rr:
                    v.set(x, y, z, S("b:soot_bricks"))
    return v


@ring_landmark
def brickfields(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 480, 66, 630, 114
    if not g.free[z0:z1 + 1, x0:x1 + 1].all():
        print("no room for the brickfields")
        return
    SP.reserve(g, x0, z0, x1, z1)
    SP.ground(g, cv, x0, z0, x1, z1, "packed_mud")
    # a flooded clay pit and heaps of clay
    for x in range(x0 + 4, x0 + 40):
        for z in range(z0 + 6, z1 - 6):
            e = ((x - x0 - 22) / 18) ** 2 + ((z - (z0 + z1) / 2) / 15) ** 2
            if e < 1:
                depth = int(5 * (1 - e)) + 1
                cv.a[s - depth:s, z, x] = 0
                cv.a[s - depth - 1, z, x] = I("clay")
                if depth > 3:
                    cv.a[s - depth:s - 2, z, x] = I("water")
    for i in range(6):
        k = bottle_kiln(rng)
        kx = x0 + 48 + (i % 3) * 18
        kz = z0 + 4 + (i // 3) * 22
        cv.stamp(k, kx, kz, facing="north")
    st = B.style(rng, "red")
    shed = B.warehouse(rng, 20, 14, st, 2, name="Tussock Brick Co.")
    cv.stamp(shed, x1 - 20, z0, facing="north")
    for i in range(8):
        x = rng.randint(x0 + 44, x1 - 4); z = rng.randint(z0 + 2, z1 - 3)
        if cv.a[s, z, x] == 0 and cv.a[s, z, x + 1] == 0:
            cv.a[s:s + rng.randint(1, 2), z:z + 2, x:x + 2] = I("b:red_bricks")
    marks["brickfields"] = ((x0 + x1) // 2, s, z1 + 8, 180)


# ---------------------------------------------------------------- Westbrook market square


@ring_landmark
def westbrook_square(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 66, 377, 115, 416
    if not g.free[z0:z1 + 1, x0:x1 + 1].all():
        print("no room for Westbrook square")
        return
    SP.reserve(g, x0, z0, x1, z1)
    SP.ground(g, cv, x0, z0, x1, z1, "b:flagstones")
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    # a market cross: a little clock tower on four arches
    for x in range(cx - 2, cx + 3):
        for z in range(cz - 2, cz + 3):
            if abs(x - cx) == 2 and abs(z - cz) == 2:
                cv.a[s:s + 4, z, x] = I("b:ashlar_pillar", axis="y")
    cv.a[s + 4, cz - 2:cz + 3, cx - 2:cx + 3] = I("b:ashlar")
    cv.a[s + 5:s + 11, cz - 1:cz + 2, cx - 1:cx + 2] = I("b:ashlar")
    for face, (dx, dz) in (("north", (0, -2)), ("south", (0, 2)), ("west", (-2, 0)), ("east", (2, 0))):
        cv.a[s + 9, cz + dz // 2 * 1, cx + dx // 2 * 1] = I("b:clock_face", facing=face)
    cv.a[s + 11, cz - 1:cz + 2, cx - 1:cx + 2] = I("b:slate_roof_slab", type="bottom")
    cv.a[s + 12, cz, cx] = I("b:ashlar_wall")
    cv.a[s + 13, cz, cx] = I("lightning_rod")
    cv.a[s + 3, cz, cx] = I("lantern", hanging="true")
    # stalls round the edge
    for x in range(x0 + 3, x1 - 2, 6):
        for z, f in ((z0 + 3, "south"), (z1 - 3, "north")):
            cv.a[s, z, x:x + 3] = I("spruce_slab", type="top")
            cv.a[s + 2, z + (-1 if f == "south" else 1), x:x + 3] = I(f"b:{rng.choice(('red', 'green', 'blue'))}_awning",
                                                                     facing="south" if f == "south" else "north")
            cv.a[s, z + (1 if f == "south" else -1), x] = I(rng.choice(("b:crate", "barrel", "pumpkin", "melon", "hay_block")))
    for x, z in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
        SP.lamp(cv, x, z)
    marks["westbrook"] = (cx, s, z1 + 6, 180)


# ---------------------------------------------------------------- Eastwharf: the canal basin and the railway out


@ring_landmark
def eastwharf(g, cv, rng, marks):
    s = cv.street
    # the dock basin at the end of the canal
    bx0, bz0, bx1, bz1 = 790, 261, 826, 291
    if g.free[bz0 - 3:bz1 + 4, bx0 - 3:bx1 + 4].mean() < 0.6:
        print("no room for the Eastwharf basin")
        return
    SP.reserve(g, bx0 - 3, bz0 - 3, bx1 + 3, bz1 + 3)
    for x in range(bx0 - 3, bx1 + 4):
        for z in range(bz0 - 3, bz1 + 4):
            inside = bx0 <= x <= bx1 and bz0 <= z <= bz1
            if inside:
                cv.a[s - 11, z, x] = I("gravel")
                cv.a[s - 10:s - WATER_TOP + 1, z, x] = I("water")
                cv.a[s - WATER_TOP + 1:s + 3, z, x] = 0
                g.water[z, x] = True
            else:
                cv.a[s - 12:s, z, x] = I("b:ashlar")
                cv.a[s - 1, z, x] = I("b:flagstones")
                cv.a[s:s + 3, z, x] = 0
    # open the canal into the basin
    for z in range(270, 279):
        for x in range(790, bx0 + 1):
            if g.water[z, x - 1] or x >= bx0 - 3:
                cv.a[s - 10:s - WATER_TOP + 1, z, x] = I("water")
                cv.a[s - WATER_TOP + 1:s, z, x] = 0
                cv.a[s - 11, z, x] = I("gravel")
    # cranes and bollards on the quays
    for z in range(bz0 + 4, bz1 - 2, 10):
        for x in (bx0 - 2, bx1 + 2):
            cv.a[s:s + 9, z, x] = I("b:dark_timber", axis="y")
            dx = 1 if x == bx0 - 2 else -1
            for k in range(1, 6):
                cv.a[s + 9, z, x + dx * k] = I("b:dark_timber", axis="x")
            cv.a[s + 6:s + 9, z, x + dx * 5] = I("chain")
            cv.a[s + 5, z, x + dx * 5] = I("b:crate")
    from specials import barge
    cv.stamp(barge(rng), bx0 + 3, bz0 + 10, y=s - WATER_TOP - 2, facing="east")
    marks["eastwharf"] = ((bx0 + bx1) // 2, s, bz1 + 10, 180)
    # the railway carries on east to the edge of town
    for tz in (142, 150, 158, 166, 174, 182):
        for x in range(768, cv.w):
            if g.free[tz, x] or g.cell[tz, x] in (1, 2, 3):
                cv.a[s - 1, tz, x] = I("gravel")
                cv.a[s, tz, x] = I("rail", shape="east_west")
                g.free[tz, x] = False


# ---------------------------------------------------------------- the County Asylum


@ring_landmark
def asylum(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 8, 784, 112, 888
    if g.free[z0:z1 + 1, x0:x1 + 1].mean() < 0.95:
        print("no room for the asylum")
        return
    SP.reserve(g, x0, z0, x1, z1)
    SP.ground(g, cv, x0, z0, x1, z1, "grass_block")
    SP.enclose(cv, x0, z0, x1, z1, (((x0 + x1) // 2, z0),), "b:red_brick_wall", "b:iron_railing", "b:red_bricks")
    st = B.Style(pt.RED, pt.ASHLAR, pt.STOCK, pt.SLATE, "dark_oak", "oak_planks", 0.25)
    cx = (x0 + x1) // 2
    # a long symmetrical front with a central clock tower and pavilion wings
    main = SP.college_range(rng, 70, 12, 3, pt.RED, pt.ASHLAR, pt.SLATE, 0.25)
    cv.stamp(main, cx - 35, z0 + 30, facing="north")
    tower = SP.gate_tower(rng, 9, 12, 30, pt.RED, pt.ASHLAR, pt.SLATE)
    cv.stamp(tower, cx - 4, z0 + 28, facing="north")
    for wx in (cx - 35, cx + 23):
        wing = SP.college_range(rng, 40, 12, 3, pt.RED, pt.ASHLAR, pt.SLATE, 0.25)
        cv.stamp(wing, wx, z0 + 42, facing="east" if wx < cx else "west")
    # the drive and the lawns
    cv.a[s - 1, z0 + 1:z0 + 30, cx - 1:cx + 2] = I("gravel")
    for i in range(14):
        x = rng.randint(x0 + 3, x1 - 3); z = rng.randint(z0 + 3, z0 + 26)
        if cv.a[s, z, x] == 0 and abs(x - cx) > 3:
            tree(cv, x, s, z, rng, rng.choice(("yew", "dark", "oak")))
    SP.lamp(cv, cx - 3, z0 + 3); SP.lamp(cv, cx + 3, z0 + 3)
    cv.sign(cx, s + 3, z0 - 1, S("spruce_wall_sign", facing="north"), ["", "TINGEN COUNTY", "ASYLUM", ""])
    cv.a[s:s + 3, z0, cx] = I("b:red_bricks")
    marks["asylum"] = (cx, s, z0 - 6, 0)


# ---------------------------------------------------------------- Tingen Gaol


@ring_landmark
def gaol(g, cv, rng, marks):
    s = cv.street
    x0, z0, x1, z1 = 782, 784, 888, 888
    if g.free[z0:z1 + 1, x0:x1 + 1].mean() < 0.95:
        print("no room for the gaol")
        return
    SP.reserve(g, x0, z0, x1, z1)
    SP.ground(g, cv, x0, z0, x1, z1, "b:tingen_cobbles")
    # a high curtain wall with corner towers
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            cv.a[s:s + 8, z, x] = I("b:soot_bricks")
            cv.a[s + 8, z, x] = I("b:soot_brick_wall")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            cv.a[s:s + 8, z, x] = I("b:soot_bricks")
            cv.a[s + 8, z, x] = I("b:soot_brick_wall")
    for x, z in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        cv.a[s:s + 12, z - 1:z + 2, x - 1:x + 2] = I("b:soot_bricks")
        cv.a[s + 12, z - 1:z + 2, x - 1:x + 2] = I("b:soot_brick_slab", type="bottom")
    # gatehouse on the north side
    gx = (x0 + x1) // 2
    gh = SP.gate_tower(rng, 11, 9, 14, pt.SOOT, pt.COBBLE, pt.DEEPTILE)
    cv.stamp(gh, gx - 5, z0 - 4, facing="north")
    # radial cell blocks from a central hall
    cx, cz = gx, (z0 + z1) // 2 + 6
    hall = Vol(15, 24, 15)
    hall.fill(0, 0, 0, 14, 14, 14, S("b:soot_bricks")); hall.fill(1, 1, 1, 13, 13, 13, AIR)
    pt.hip(hall, 0, 14, 0, 14, 15, pt.DEEPTILE)
    cv.stamp(hall, cx - 7, cz - 7, facing="north")
    st = B.Style(pt.SOOT, pt.COBBLE, pt.SOOT, pt.DEEPTILE, "spruce", "b:weathered_planks", 0.08)
    for face, (x, z) in (("north", (cx - 6, cz + 7)), ("south", (cx - 6, cz - 7 - 34)),
                         ("east", (cx - 7 - 34, cz - 6)), ("west", (cx + 7, cz - 6))):
        wing = B.tenement(rng, 13, 34, st, 3)
        cv.stamp(wing, x, z, facing=face)
    cv.sign(gx, s + 5, z0 - 5, S("spruce_wall_sign", facing="north"), ["", "TINGEN", "GAOL", ""])
    marks["gaol"] = (gx, s, z0 - 12, 0)
