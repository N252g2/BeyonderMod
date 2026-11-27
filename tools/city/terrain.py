"""Ground, rivers, embankments, streets, bridges, sewers and street furniture."""
import math
import numpy as np
from scipy import ndimage
from scipy.spatial import cKDTree
from world import S, I, P, AIR, Canvas

# ground layers relative to the street air layer s
BED = 11          # river bed is s-BED
WATER_TOP = 6     # water surface is s-WATER_TOP (water from s-10 .. s-6)


def catmull(points, step=1.0):
    pts = np.array(points, float)
    out = []
    for i in range(len(pts) - 1):
        p0 = pts[max(i - 1, 0)]; p1 = pts[i]; p2 = pts[i + 1]; p3 = pts[min(i + 2, len(pts) - 1)]
        n = max(2, int(np.linalg.norm(p2 - p1) / step))
        for t in np.linspace(0, 1, n, endpoint=False):
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(pts[-1])
    return np.array(out)


class River:
    def __init__(self, name, points, widths):
        self.name = name
        self.line = catmull(points, 0.5)
        # width interpolated along the line
        self.w = np.interp(np.linspace(0, 1, len(self.line)), np.linspace(0, 1, len(widths)), widths)


class Street:
    KINDS = {"avenue": "PPPKRRRRRRRKPPP", "street": "PPKRRRRRKPP", "lane": "RRRRR", "wide": "PPPPKRRRRRRRRRKPPPP"}

    def __init__(self, name, axis, c, a, b, kind="street", sewer=False, trees=False):
        self.name, self.axis, self.c, self.a, self.b, self.kind, self.sewer = name, axis, c, a, b, kind, sewer
        self.trees = trees
        self.prof = self.KINDS[kind]
        self.hw = len(self.prof) // 2

    @property
    def rect(self):
        if self.axis == "x":      # runs east-west at z=c
            return self.a, self.c - self.hw, self.b, self.c + self.hw
        return self.c - self.hw, self.a, self.c + self.hw, self.b


class Ground:
    """2-D plan of the city plus the canvas it paints into."""

    def __init__(self, canvas: Canvas, rng):
        self.cv = canvas
        self.rng = rng
        W, D = canvas.w, canvas.d
        self.free = np.ones((D, W), bool)
        self.water = np.zeros((D, W), bool)
        self.wall = np.zeros((D, W), bool)
        self.prom = np.zeros((D, W), bool)
        self.street_id = np.full((D, W), -1, np.int32)
        self.cell = np.zeros((D, W), np.uint8)         # 1 pavement, 2 kerb, 3 road, 4 promenade, 5 plaza
        self.streets = []
        self.rivers = []
        self.lamps = []
        self.only = np.ones((D, W), bool)        # cells this pass may change
        self.weirs = True

    # ------------------------------------------------------------ base ground
    def base(self):
        cv, s = self.cv, self.cv.street
        cv.fill(0, 0, 0, cv.w - 1, 0, cv.d - 1, S("bedrock"))
        cv.fill(0, 1, 0, cv.w - 1, s - 6, cv.d - 1, S("stone"))
        cv.fill(0, s - 5, 0, cv.w - 1, s - 2, cv.d - 1, S("dirt"))
        cv.fill(0, s - 1, 0, cv.w - 1, s - 1, cv.d - 1, S("grass_block"))

    # ------------------------------------------------------------ rivers
    def add_river(self, river):
        self.rivers.append(river)
        D, W = self.free.shape
        tree = cKDTree(river.line)
        zz, xx = np.mgrid[0:D, 0:W]
        pts = np.stack([xx.ravel(), zz.ravel()], 1)
        dist, idx = tree.query(pts, distance_upper_bound=60)
        ok = np.isfinite(dist)
        hw = np.full(len(dist), -1.0)
        hw[ok] = river.w[idx[ok]] / 2
        dist = dist.reshape(D, W); hw = hw.reshape(D, W)
        self.water |= (dist <= hw) & self.only

    def finish_rivers(self):
        """Embankment walls two thick around the water, a promenade beyond."""
        w2 = ndimage.binary_dilation(self.water, iterations=2)
        w5 = ndimage.binary_dilation(self.water, iterations=5)
        only = self.only
        new_water = self.water & only
        self.wall = (self.wall & ~only) | (w2 & ~self.water & only)
        self.prom = (self.prom & ~only) | (w5 & ~w2 & only)
        self.free &= ~(w5 & only)
        cv, s = self.cv, self.cv.street
        zs, xs = np.nonzero(new_water)
        cv.a[s - BED, zs, xs] = I("gravel")
        cv.a[s - BED + 1:s - WATER_TOP + 1, zs, xs] = I("water")
        cv.a[s - WATER_TOP + 1:s, zs, xs] = 0
        # weirs where the rivers leave the city, so the water stays in its channels
        D_, W_ = self.water.shape
        edge = np.zeros_like(self.water)
        if self.weirs:
            edge[:2, :] = edge[-2:, :] = True
            edge[:, :2] = edge[:, -2:] = True
        zs, xs = np.nonzero(self.water & edge & only)
        cv.a[s - BED - 1:s - WATER_TOP + 1, zs, xs] = I("mossy_stone_bricks")
        cv.a[s - WATER_TOP + 1, zs, xs] = I("b:ashlar_slab", type="bottom")
        zs, xs = np.nonzero(self.wall & only)
        cv.a[s - BED - 1:s, zs, xs] = I("b:ashlar")
        # water-stained lower courses
        cv.a[s - BED:s - WATER_TOP + 1, zs, xs] = I("mossy_stone_bricks")
        # coping and parapet on the river-side ring
        inner = self.wall & ndimage.binary_dilation(self.water, iterations=1) & only
        outer = self.wall & ~inner & only
        zs, xs = np.nonzero(outer)
        cv.a[s - 1, zs, xs] = I("b:ashlar_slab", type="double")
        zs, xs = np.nonzero(inner)
        cv.a[s - 1, zs, xs] = I("b:ashlar")
        cv.a[s, zs, xs] = I("b:ashlar_wall")
        self.cell[self.prom & only] = 4
        zs, xs = np.nonzero(self.prom & only)
        cv.a[s - 1, zs, xs] = I("b:flagstones")
        # railings between parapet posts, lamps every so often
        k = 0
        for z, x in zip(*np.nonzero(inner)):
            if (x * 7 + z * 13) % 11 == 0:
                cv.a[s, z, x] = I("b:ashlar_wall")
                cv.a[s + 1, z, x] = I("b:ashlar_wall")
            if (x + 2 * z) % 23 == 0:
                cv.a[s + 1, z, x] = I("b:lamp_post"); cv.a[s + 2, z, x] = I("b:lamp_post"); cv.a[s + 3, z, x] = I("b:gas_lamp")
        # benches along the promenade looking over the water
        near = self.prom & ndimage.binary_dilation(self.wall, iterations=1) & only
        for z, x in zip(*np.nonzero(near)):
            if (x * 3 + z * 5) % 29 == 0:
                for d, (dx, dz) in (("north", (0, -1)), ("south", (0, 1)), ("west", (-1, 0)), ("east", (1, 0))):
                    if 0 <= z + dz < self.wall.shape[0] and 0 <= x + dx < self.wall.shape[1] and self.wall[z + dz, x + dx]:
                        cv.a[s, z, x] = I("b:park_bench", facing=d)
                        break
        # trees along the promenade
        for z, x in zip(*np.nonzero(self.prom & only)):
            if (x * 31 + z * 17) % 97 == 0 and self.prom[max(z - 1, 0):z + 2, max(x - 1, 0):x + 2].all():
                cv.a[s - 1, z, x] = I("grass_block")
                tree(cv, x, s, z, self.rng, "plane")

    # ------------------------------------------------------------ streets
    def add_street(self, st):
        self.streets.append(st)
        return st

    def paint_streets(self, start=0):
        cv, s = self.cv, self.cv.street
        layers = {"P": 1, "K": 2, "R": 3}
        mats = {1: I("b:flagstones"), 2: I("b:kerbstone"), 3: I("b:tingen_cobbles")}
        # kerbs, then pavements, then roads (so crossings stay clean)
        only = self.only
        for order in ("K", "P", "R"):
            for sid, st in enumerate(self.streets):
                if sid < start:
                    continue
                for off, ch in enumerate(st.prof):
                    if ch != order:
                        continue
                    c = st.c - st.hw + off
                    D, W = self.cell.shape
                    if not (0 <= c < (D if st.axis == "x" else W)):
                        continue
                    run = slice(max(st.a, 0), min(st.b, (W if st.axis == "x" else D) - 1) + 1)
                    zsl, xsl = (c, run) if st.axis == "x" else (run, c)
                    m = only[zsl, xsl]
                    self.cell[zsl, xsl] = np.where(m, layers[ch], self.cell[zsl, xsl])
                    self.street_id[zsl, xsl] = np.where(m, sid, self.street_id[zsl, xsl])
        self.free &= ~((self.cell >= 1) & (self.cell <= 3) & only)
        bridge = (self.water | self.wall) & (self.cell >= 1) & (self.cell <= 3) & only
        land = (self.cell >= 1) & (self.cell <= 3) & ~bridge & only
        for code, mat in mats.items():
            zs, xs = np.nonzero(land & (self.cell == code))
            cv.a[s - 1, zs, xs] = mat
            cv.a[s - 2, zs, xs] = I("stone")
            cv.a[s:s + 3, zs, xs] = 0
        self.bridges(bridge, mats)

    def bridges(self, mask, mats):
        """Stone arch bridges wherever a street crosses water."""
        cv, s = self.cv, self.cv.street
        lab, n = ndimage.label(mask)
        for k in range(1, n + 1):
            zs, xs = np.nonzero(lab == k)
            sid = np.bincount(self.street_id[zs, xs][self.street_id[zs, xs] >= 0]).argmax()
            st = self.streets[sid]
            for z, x in zip(zs, xs):
                code = self.cell[z, x]
                cv.a[s - 1, z, x] = mats[code]
                cv.a[s - 2, z, x] = I("b:ashlar")
                cv.a[s:s + 3, z, x] = 0
            # piers and arches: along the street's run direction
            along = xs if st.axis == "x" else zs
            lo, hi = along.min(), along.max()
            span = hi - lo + 1
            npiers = max(1, span // 10)
            pier_at = [lo + round((i + 0.5) * span / npiers) for i in range(npiers)] if span > 12 else []
            for z, x in zip(zs, xs):
                t = x if st.axis == "x" else z
                if not self.water[z, x]:
                    continue
                dpier = min([abs(t - p) for p in pier_at] + [abs(t - lo + 1), abs(hi + 1 - t)])
                if dpier <= 1:
                    cv.a[s - BED:s - 2, z, x] = I("b:ashlar")
                    cv.a[s - BED:s - WATER_TOP + 1, z, x] = I("mossy_stone_bricks") if dpier == 1 else I("b:ashlar")
                else:
                    # arch soffit: deeper near piers
                    drop = max(0, 3 - dpier)
                    cv.a[s - 3 - drop:s - 2, z, x] = I("b:ashlar")
            # parapets on the outer pavement cells
            c0, c1 = st.c - st.hw, st.c + st.hw
            for z, x in zip(zs, xs):
                cross = z if st.axis == "x" else x
                if cross in (c0, c1):
                    cv.a[s, z, x] = I("b:ashlar_wall")
                    t = x if st.axis == "x" else z
                    if (t - lo) % 8 == 4:
                        cv.a[s, z, x] = I("b:ashlar")
                        cv.a[s + 1, z, x] = I("b:lamp_post"); cv.a[s + 2, z, x] = I("b:lamp_post")
                        cv.a[s + 3, z, x] = I("b:gas_lamp")

    def street_lamps(self, spacing=12, start=0):
        cv, s = self.cv, self.cv.street
        for sid, st in enumerate(self.streets):
            if st.kind == "lane" or sid < start:
                continue
            # pavement cells next to the kerbs, both sides, staggered
            kerb_offsets = [i for i, ch in enumerate(st.prof) if ch == "K"]
            for side, ko in enumerate(kerb_offsets):
                po = ko - 1 if side == 0 else ko + 1
                c = st.c - st.hw + po
                for t in range(st.a + 3 + side * spacing // 2, st.b - 2, spacing):
                    x, z = (t, c) if st.axis == "x" else (c, t)
                    if not (0 <= x < cv.w and 0 <= z < cv.d):
                        continue
                    if self.street_id[z, x] != sid or self.cell[z, x] != 1 or not self.only[z, x]:
                        continue
                    # skip crossings: the cell across the kerb must still be this street's road
                    if cv.a[s, z, x] != 0:
                        continue
                    cv.a[s, z, x] = I("b:lamp_post"); cv.a[s + 1, z, x] = I("b:lamp_post")
                    cv.a[s + 2, z, x] = I("b:gas_lamp")
                    self.lamps.append((x, z))
                    # now and then a pillar box beside the lamp, its slot to the pavement
                    if (t // spacing + sid) % 5 == 2:
                        bx, bz = (x + 1, z) if st.axis == "x" else (x, z + 1)
                        if self.cell[bz, bx] == 1 and cv.a[s, bz, bx] == 0:
                            face = ("north" if side == 0 else "south") if st.axis == "x" else ("west" if side == 0 else "east")
                            cv.a[s, bz, bx] = I("b:post_box", facing=face)
                    # plane trees between the lamps on the avenues
                    if st.kind in ("avenue", "wide") and st.trees:
                        tx, tz = (x + spacing // 2, z) if st.axis == "x" else (x, z + spacing // 2)
                        if 0 <= tx < cv.w and 0 <= tz < cv.d and self.cell[tz, tx] == 1 and self.street_id[tz, tx] == sid \
                                and cv.a[s, tz, tx] == 0:
                            cv.a[s - 1, tz, tx] = I("coarse_dirt")
                            tree(cv, tx, s, tz, self.rng, "avenue")

    def sewers(self, start=0):
        """Brick sewers under streets marked sewer=True: walkways either side of a channel, manholes."""
        cv, s = self.cv, self.cv.street
        lo, hi = s - 7, s - 3            # floor .. roof
        for sid, st in enumerate(self.streets):
            if not st.sewer or sid < start:
                continue
            for t in range(st.a, st.b + 1):
                for o in range(-3, 4):
                    x, z = (t, st.c + o) if st.axis == "x" else (st.c + o, t)
                    if not (0 <= x < cv.w and 0 <= z < cv.d) or self.water[z, x] or not self.only[z, x]:
                        continue
                    if self.wall[z, x]:
                        # outfall through the embankment, barred
                        if abs(o) <= 1:
                            cv.a[s - 6, z, x] = I("iron_bars")
                            cv.a[s - 5, z, x] = I("iron_bars")
                        continue
                    if abs(o) == 3:
                        cv.a[lo:hi + 1, z, x] = I("b:soot_bricks")
                    else:
                        cv.a[lo, z, x] = I("stone_bricks") if o else I("b:soot_bricks")
                        cv.a[lo + 1:hi, z, x] = 0
                        cv.a[hi, z, x] = I("b:soot_bricks")
                        if o == 0:
                            cv.a[lo, z, x] = I("water")
                            cv.a[lo - 1, z, x] = I("b:soot_bricks")
                        elif (t * 3 + o) % 7 == 0:
                            cv.a[lo + 1, z, x] = I("moss_carpet")
                # manhole every 40 blocks: grate in the road with a ladder down
                if t % 40 == 20:
                    x, z = (t, st.c + 2) if st.axis == "x" else (st.c + 2, t)
                    if 0 <= x < cv.w and 0 <= z < cv.d and self.cell[z, x] == 3 and not self.wall[z, x] and not self.water[z, x]:
                        face = "south" if st.axis == "x" else "east"
                        x2, z2 = (t, st.c + 3) if st.axis == "x" else (st.c + 3, t)
                        cv.a[s - 1, z, x] = I("b:sewer_grate")
                        cv.a[s - 2, z, x] = 0
                        cv.a[hi, z, x] = 0
                        for y in range(lo + 1, s - 1):
                            cv.a[y, z, x] = I("ladder", facing="north" if st.axis == "x" else "west")
                        cv.a[s - 1, z, x] = I("b:sewer_grate")
                    if t % 80 == 20:
                        x, z = (t, st.c) if st.axis == "x" else (st.c, t)
                        if 0 <= x < cv.w and 0 <= z < cv.d and cv.a[hi, z, x] == I("b:soot_bricks"):
                            cv.a[hi - 1, z, x] = I("chain")
                            cv.a[hi - 2, z, x] = I("lantern", hanging="true")


# ------------------------------------------------------------ trees


def tree(cv, x, y, z, rng, kind="oak"):
    """A small hand-shaped tree. y is the first air layer; leaves are persistent so they never decay."""
    if kind == "plane":
        log, leaf, h = "oak_log", "oak_leaves", rng.randint(5, 7)
    elif kind == "yew":
        log, leaf, h = "spruce_log", "spruce_leaves", rng.randint(4, 6)
    elif kind == "birch":
        log, leaf, h = "birch_log", "birch_leaves", rng.randint(5, 7)
    elif kind == "avenue":
        log, leaf, h = "oak_log", "oak_leaves", rng.randint(6, 7)
    else:
        log, leaf, h = "dark_oak_log", "dark_oak_leaves", rng.randint(5, 6)
    L = I(leaf, persistent="true")
    for dy in range(h):
        cv.set(x, y + dy, z, I(log, axis="y"))
    r = 2 if kind not in ("yew", "avenue") else 1
    top = y + h
    for dy in range(-2, 2):
        rr = r if dy < 1 else r - 1
        if kind == "yew":
            rr = max(0, 2 - (dy + 2) // 2)
        for dx in range(-rr, rr + 1):
            for dz in range(-rr, rr + 1):
                if abs(dx) == rr and abs(dz) == rr and rng.random() < 0.6:
                    continue
                if cv.get(x + dx, top + dy, z + dz) == 0:
                    cv.set(x + dx, top + dy, z + dz, L)
    cv.set(x, top + 1, z, L)
