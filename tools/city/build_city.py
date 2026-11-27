"""Generate Tingen: python3 tools/city/build_city.py [--preview-only]

Writes src/main/resources/data/beyonder/city/tingen.bin and previews in tools/city/out/.
The plan: the Khoy comes down from the north and meets the Tussock, which crosses the south of the city.
West of the Khoy: Khoy University and the West Borough terraces (Daffodil Street). Centre: Saint Selena's
Cathedral on its white plaza off Red Moonlight Street, Zouteland Street behind it. North: the station, the
canal and the works. East: Iron Cross Street, tenements, the market, the workhouse and the docks. Golden Indus
by the river. South bank: Tingen University, the gardens, Raphael Cemetery, villas and the gasworks.
"""
import os
import random
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)

import numpy as np                                  # noqa: E402
from world import Canvas, S, I, P, AIR, KEEP       # noqa: E402
from terrain import Ground, River, Street, tree     # noqa: E402
import blocks as BL                                  # noqa: E402
import buildings as B                                # noqa: E402
import parts as pt                                   # noqa: E402
import specials as SP                                # noqa: E402
import preview                                       # noqa: E402
import export                                        # noqa: E402

W = D = 640
H = 100
STREET = 16
OUT = os.path.join(HERE, "out")
BIN = os.path.join(HERE, "..", "..", "src", "main", "resources", "data", "beyonder", "city", "tingen.bin")


# ---------------------------------------------------------------- district profiles


def runs(rng, L, widths, run=(3, 8)):
    """Split a frontage into runs of matching houses."""
    ws = BL.split_run(rng, L, widths)
    out, i = [], 0
    while i < len(ws):
        n = rng.randint(*run)
        out.append(ws[i:i + n])
        i += n
    return out


def west_row(rng, L, st, d, lo, ro):
    segs = []
    if st.kind in ("avenue", "wide"):
        for group in runs(rng, L, [6, 7, 8], (2, 5)):
            sty = B.style(rng, rng.choice(("red", "stock", "daffodil", "redslate", "vbrick")))
            for w in group:
                if w >= 8 and rng.random() < 0.12:
                    segs.append((w, lambda end, w=w, sty=sty: B.pub(rng, w, d, sty, 3, end=end)))
                else:
                    segs.append((w, lambda end, w=w, sty=sty: B.shop_house(
                        rng, w, d, sty, 3, awning=rng.choice(("red", "green", "blue", None)), end=end)))
        return ends(segs, lo, ro)
    daffodil = st.name == "Daffodil Street"
    for group in runs(rng, L, [5, 6, 6, 6, 7]):
        name = "daffodil" if daffodil else rng.choice(("daffodil", "red", "stock", "redslate", "stucco", "vbrick"))
        sty = B.style(rng, name)
        storeys = 3 if daffodil else rng.choice((2, 3, 3))
        oriel = daffodil or rng.random() < 0.35
        dormer = storeys == 2 and rng.random() < 0.5
        bay = not daffodil and not oriel and rng.random() < 0.6
        lamp = rng.random() < 0.4
        for j, w in enumerate(group):
            segs.append((w, lambda end, w=w, sty=sty, j=j, storeys=storeys, oriel=oriel, dormer=dormer, lamp=lamp, bay=bay:
                         B.terrace_house(rng, w, d, sty, storeys, oriel=oriel and w >= 6, door_left=j % 2 == 0, end=end,
                                         lamp=lamp and j % 3 == 0, dormer=dormer, bay=bay)))
    return ends(segs, lo, ro)


def ends(segs, lo, ro):
    """Only give a row end its side windows when a street runs past that side."""
    out = []
    for i, (w, make) in enumerate(segs):
        def m(end, make=make, i=i):
            if end == "left" and not lo:
                end = None
            if end == "right" and not ro:
                end = None
            return make(end)
        out.append((w, m))
    return out


def east_row(rng, L, st, d, lo, ro):
    segs = []
    if st.kind in ("avenue", "wide"):
        for group in runs(rng, L, [6, 7, 8, 9], (2, 4)):
            sty = B.style(rng, rng.choice(("soot", "stock", "soot")))
            sty.lit = 0.25
            for w in group:
                if w >= 8 and rng.random() < 0.25:
                    segs.append((w, lambda end, w=w, sty=sty: B.pub(rng, w, d, sty, 3, end=end)))
                else:
                    segs.append((w, lambda end, w=w, sty=sty: B.shop_house(
                        rng, w, d, sty, rng.choice((2, 3)), awning=rng.choice((None, None, "red", "green")), end=end)))
        return ends(segs, lo, ro)
    rest = L
    while rest > 0:
        r = rng.random()
        if r < 0.45 and rest >= 10:
            w = min(rest, rng.choice((10, 12, 14, 16)))
            if rest - w < 5:
                w = rest
            sty = B.style(rng, rng.choice(("soot", "soot", "stock")))
            sty.lit = 0.22
            segs.append((w, lambda end, w=w, sty=sty: B.tenement(rng, w, d, sty, rng.choice((4, 4, 5)), end=end)))
            rest -= w
        elif r < 0.6 and rest >= 5:
            w = min(rest, rng.choice((4, 5, 6)))
            if rest - w < 4:
                w = rest
            segs.append((w, lambda end, w=w: B.shack(rng, w, min(d, 8))))
            rest -= w
        else:
            n = rng.randint(3, 6)
            sty = B.style(rng, "soot")
            sty.lit = 0.2
            for j in range(n):
                w = min(rest, 5)
                if w <= 0:
                    break
                if rest - w < 4:
                    w = rest
                segs.append((w, lambda end, w=w, sty=sty, j=j: B.terrace_house(rng, w, d, sty, 2, door_left=j % 2 == 0, end=end)))
                rest -= w
    return ends(segs, lo, ro)


def golden_row(rng, L, st, d, lo, ro):
    segs = []
    for group in runs(rng, L, [7, 8, 8, 9], (3, 6)):
        sty = B.style(rng, rng.choice(("stucco", "stucco", "redslate")))
        sty.roof = rng.choice((pt.SLATE, pt.SLATE, pt.DEEPTILE, pt.COPPER))
        sty.lit = 0.45
        for w in group:
            if st.kind in ("avenue", "wide") and rng.random() < 0.3:
                segs.append((w, lambda end, w=w, sty=sty: B.shop_house(rng, w, d, sty, 4, awning=rng.choice(("green", "blue", None)), end=end)))
            else:
                segs.append((w, lambda end, w=w, sty=sty: B.townhouse(rng, w, d, sty, 4, end=end)))
    return ends(segs, lo, ro)


def north_row(rng, L, st, d, lo, ro):
    segs = []
    rest = L
    while rest > 0:
        if rest >= 12 and rng.random() < 0.6:
            w = min(rest, rng.choice((12, 14, 16, 18)))
            if rest - w < 5:
                w = rest
            sty = B.style(rng, rng.choice(("red", "stock", "soot", "vbrick")))
            segs.append((w, lambda end, w=w, sty=sty: B.warehouse(rng, w, d, sty, rng.choice((3, 4)), end=end)))
            rest -= w
        else:
            sty = B.style(rng, rng.choice(("soot", "stock")))
            for j in range(rng.randint(3, 6)):
                w = min(rest, 5)
                if w <= 0:
                    break
                if rest - w < 4:
                    w = rest
                segs.append((w, lambda end, w=w, sty=sty, j=j: B.terrace_house(rng, w, d, sty, 2, door_left=j % 2 == 0, end=end)))
                rest -= w
    return ends(segs, lo, ro)


def villa_row(rng, L, st, d, lo, ro):
    segs = []
    for w in BL.split_run(rng, L, [20, 22, 24]):
        sty = B.style(rng, rng.choice(("stucco", "red", "redslate", "stucco")))
        sty.lit = 0.4
        segs.append((w, lambda end, w=w, sty=sty: B.villa(rng, w, d, sty, rng.choice((2, 3)))))
    return segs


def docks_row(rng, L, st, d, lo, ro):
    segs = []
    for w in BL.split_run(rng, L, [14, 16, 18, 20]):
        sty = B.style(rng, rng.choice(("red", "stock", "soot")))
        segs.append((w, lambda end, w=w, sty=sty: B.warehouse(rng, w, d, sty, rng.choice((3, 4)), end=end)))
    return ends(segs, lo, ro)


PROFILES = {
    "west": BL.Profile("west", 12, west_row, "yards"),
    "east": BL.Profile("east", 11, east_row, "court"),
    "golden": BL.Profile("golden", 14, golden_row, "garden"),
    "north": BL.Profile("north", 16, north_row, "works"),
    "villas": BL.Profile("villas", 26, villa_row, "garden"),
    "docks": BL.Profile("docks", 18, docks_row, "works"),
}


# ---------------------------------------------------------------- the plan


def plan(g):
    # rivers and the canal
    g.add_river(River("Khoy", [(246, -8), (240, 60), (246, 120), (258, 180), (272, 240), (283, 300), (291, 360),
                               (298, 420), (306, 458)], [18] * 9))
    g.add_river(River("Tussock", [(-8, 436), (60, 444), (130, 450), (200, 444), (262, 450), (306, 462), (360, 472),
                                  (420, 478), (490, 481), (560, 478), (648, 472)],
                      [22, 22, 22, 22, 22, 26, 30, 30, 30, 30, 30]))
    g.add_river(River("Tingen Canal", [(244, 146), (420, 146), (648, 146)], [8, 8, 8]))
    g.finish_rivers()
    A = g.add_street
    # arterial avenues (with sewers beneath)
    A(Street("Kingsway", "x", 175, 0, 639, "avenue", True))
    A(Street("Harvest Avenue", "x", 345, 0, 639, "avenue", True, trees=True))
    A(Street("Lindon Road", "z", 110, 0, 639, "avenue", True))
    A(Street("Grand Avenue", "z", 470, 84, 639, "avenue", True, trees=True))
    A(Street("Iron Cross Street", "z", 590, 84, 639, "avenue", True))
    A(Street("Station Road", "z", 420, 104, 168, "avenue", True))
    # north
    A(Street("Canal Street", "x", 122, 262, 639, "street"))
    A(Street("Foundry Street", "z", 535, 0, 116, "street"))
    A(Street("Mill Street", "z", 300, 0, 116, "street"))
    A(Street("Kiln Street", "x", 45, 255, 335, "street"))
    # north-west
    A(Street("Thornhill Street", "x", 55, 0, 103, "street"))
    A(Street("Cutler Street", "x", 115, 0, 103, "street"))
    A(Street("Old Weir Road", "z", 50, 0, 168, "street"))
    # West Borough
    A(Street("Daffodil Street", "x", 240, 0, 262, "street"))
    A(Street("Willow Street", "x", 295, 0, 276, "street"))
    A(Street("Weir Street", "x", 398, 0, 284, "street"))
    A(Street("Rose Street", "z", 40, 183, 437, "street"))
    A(Street("Hollow Street", "z", 175, 183, 447, "street"))
    A(Street("Khoy Bank Street", "z", 230, 183, 440, "street"))
    # centre
    A(Street("Zouteland Street", "x", 215, 284, 463, "street"))
    A(Street("Red Moonlight Street", "x", 312, 290, 463, "street"))
    # Golden Indus
    A(Street("Regent Street", "z", 360, 353, 455, "street"))
    A(Street("Bank Street", "z", 415, 353, 455, "street"))
    A(Street("Golden Street", "x", 402, 300, 463, "street"))
    # East Borough
    A(Street("Coalyard Lane", "x", 232, 478, 639, "street"))
    A(Street("Tanner Street", "x", 285, 478, 639, "street"))
    A(Street("Lower Ward Street", "x", 395, 478, 639, "street"))
    A(Street("Mire Street", "z", 535, 183, 440, "street"))
    # south bank
    A(Street("Tussock Embankment", "x", 502, 0, 639, "street"))
    A(Street("Elm Street", "x", 590, 0, 470, "street"))
    A(Street("Garden Row", "z", 370, 508, 639, "street"))
    A(Street("Chapel Street", "z", 175, 508, 639, "street"))


DISTRICTS = [
    # (profile, rect x0, z0, x1, z1)
    ("west", (0, 0, 103, 168)),
    ("west", (0, 183, 290, 470)),
    ("east", (478, 183, 639, 390)),
    ("docks", (478, 401, 639, 470)),
    ("golden", (290, 183, 463, 470)),
    ("north", (255, 0, 639, 168)),
    ("west", (0, 470, 639, 507)),
    ("villas", (0, 508, 639, 639)),
]


def main():
    t0 = time.time()
    rng = random.Random(1907)
    cv = Canvas(W, H, D, STREET)
    g = Ground(cv, rng)
    g.built = np.zeros((D, W), bool)
    g.yard = np.zeros((D, W), bool)
    g.base()
    plan(g)
    g.paint_streets()
    marks = {}
    SP.place_all(g, cv, rng, marks)          # landmarks reserve and build their own ground first
    g.street_lamps()
    BL.fill_city(g, cv, rng, DISTRICTS, PROFILES)
    SP.street_signs(g, cv, rng)
    g.sewers()
    SP.leftovers(g, cv, rng)
    s = STREET
    for k, val in {"daffodil": (60, s, 240, -90), "confluence": (306, s, 455, 180), "station": (420, s, 95, 180),
                   "iron_cross": (590, s, 250, 180), "golden": (390, s, 380, 180), "villas": (240, s, 600, 180),
                   "university": (175, s, 90, 180), "market": (505, s, 230, 180), "cemetery": (530, s, 570, 180),
                   "sewer": (200, s - 6, 177, 90), "works": (570, s, 125, 180)}.items():
        marks.setdefault(k, val)
    print(f"generated in {time.time() - t0:.1f}s, palette {len(P.states)}")
    bad = export.check_palette()
    if bad:
        print("UNKNOWN BLOCKS:", bad)
    os.makedirs(OUT, exist_ok=True)
    preview.topdown(cv, os.path.join(OUT, "map.png"), 2)
    if "--preview-only" not in sys.argv:
        n = export.write(cv, BIN, marks)
        print(f"wrote {BIN} ({n / 1e6:.1f} MB), {len(cv.signs)} signs, marks {list(marks)}")
    return cv, g, marks


if __name__ == "__main__":
    main()
