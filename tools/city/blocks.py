"""Filling city blocks: building rows along every street-facing edge, back yards in the middle."""
import numpy as np
from scipy import ndimage
from world import S, I, P, AIR, KEEP, Vol
import buildings as B
import parts as pt

FACE_K = {"north": 0, "east": 1, "south": 2, "west": 3}


def components(ground, rect, min_area=150):
    x0, z0, x1, z1 = rect
    sub = ground.free[z0:z1 + 1, x0:x1 + 1]
    lab, n = ndimage.label(sub)
    out = []
    for k, sl in enumerate(ndimage.find_objects(lab), 1):
        if sl is None:
            continue
        zs, xs = sl
        area = int((lab[sl] == k).sum())
        if area < min_area:
            continue
        out.append((x0 + xs.start, z0 + zs.start, x0 + xs.stop - 1, z0 + zs.stop - 1))
    return out


def edge_street(ground, bx0, bz0, bx1, bz1, side):
    """Name and kind of the street along one side of a block, or None."""
    D, W = ground.free.shape
    if side == "north":
        cells = [(bz0 - 1, x) for x in range(bx0, bx1 + 1)]
    elif side == "south":
        cells = [(bz1 + 1, x) for x in range(bx0, bx1 + 1)]
    elif side == "west":
        cells = [(z, bx0 - 1) for z in range(bz0, bz1 + 1)]
    else:
        cells = [(z, bx1 + 1) for z in range(bz0, bz1 + 1)]
    cells = [(z, x) for z, x in cells if 0 <= z < D and 0 <= x < W]
    if not cells:
        return None
    ids = [ground.street_id[z, x] for z, x in cells if 1 <= ground.cell[z, x] <= 3]
    if len(ids) < 0.5 * len(cells):
        return None
    st = ground.streets[max(set(ids), key=ids.count)]
    return st


def place_row(ground, cv, rng, side, lo, hi, line, depth, segments):
    """Stamp a row of buildings. lo..hi is the world span along the edge, line the street-side edge of the lots.
    segments: list of (width, make(end) -> Vol) in local (left-to-right seen from the street) order.
    A building shallower than the lot keeps its front on the street."""
    placed = []
    pos = 0
    for idx, (w, make) in enumerate(segments):
        end = "left" if idx == 0 else ("right" if idx == len(segments) - 1 else None)
        v = make(end)
        if v is None:
            pos += w
            continue
        if isinstance(v, tuple):
            v = v[0]
        dd = min(v.d, depth)
        if side == "north":
            x, z = lo + pos, line
        elif side == "south":
            x, z = hi - pos - w + 1, line - dd + 1
        elif side == "east":
            x, z = line - dd + 1, lo + pos
        else:
            x, z = line, hi - pos - w + 1
        fw, fd = (w, dd) if side in ("north", "south") else (dd, w)
        foot = ground.free[z:z + fd, x:x + fw]
        if foot.shape != (fd, fw) or foot.mean() < 0.999:
            pos += w
            continue
        cv.stamp(v, x, z, facing=side)
        # pave the strip in front of the facade so the pavement runs right up to the doors
        st_ = cv.street
        if side == "north":
            row = (slice(z, z + 1), slice(x, x + fw))
        elif side == "south":
            row = (slice(z + fd - 1, z + fd), slice(x, x + fw))
        elif side == "west":
            row = (slice(z, z + fd), slice(x, x + 1))
        else:
            row = (slice(z, z + fd), slice(x + fw - 1, x + fw))
        surf = cv.a[st_ - 1][row]
        surf[surf == I("grass_block")] = I("b:flagstones")
        ground.free[z:z + fd, x:x + fw] = False
        ground.built[z:z + fd, x:x + fw] = True
        placed.append((x, z, fw, fd, side))
        pos += w
    return placed


def split_run(rng, L, widths, max_run=None):
    """Split L into house widths drawn from ``widths`` (exactly filling L where possible)."""
    out = []
    rest = L
    while rest > 0:
        if rest <= max(widths):
            if rest >= min(widths):
                out.append(rest)
            elif out:
                out[-1] += rest
            else:
                out.append(rest)
            break
        w = rng.choice(widths)
        if rest - w < min(widths) and rest - w != 0:
            w = rest - min(widths) if rest - min(widths) in range(min(widths), max(widths) + 1) else w
        out.append(w)
        rest -= w
    return out


def free_runs(ground, side, lo, hi, line, depth, minlen=5):
    """Stretches along an edge where a whole lot of the given depth is still free."""
    runs, start = [], None
    for t in range(lo, hi + 2):
        ok = False
        if t <= hi:
            if side == "north":
                strip = ground.free[line:line + depth, t]
            elif side == "south":
                strip = ground.free[line - depth + 1:line + 1, t]
            elif side == "east":
                strip = ground.free[t, line - depth + 1:line + 1]
            else:
                strip = ground.free[t, line:line + depth]
            ok = strip.size == depth and bool(strip.all())
        if ok and start is None:
            start = t
        elif not ok and start is not None:
            if t - start >= minlen:
                runs.append((start, t - 1))
            start = None
    return runs


def build_row(ground, cv, rng, profile, side, lo, hi, line, depth, street, left_open, right_open):
    placed = []
    for a, b in free_runs(ground, side, lo, hi, line, depth):
        lo_open = left_open if (a == lo if side in ("north", "west") else b == hi) else False
        hi_open = right_open if (b == hi if side in ("north", "west") else a == lo) else False
        if side == "west":
            lo_open, hi_open = (right_open if a == lo else False), (left_open if b == hi else False)
        segs = profile.row(rng, b - a + 1, street, depth, lo_open, hi_open)
        placed += place_row(ground, cv, rng, side, a, b, line, depth, segs)
    return placed


def fill_block(ground, cv, rng, rect, profile):
    bx0, bz0, bx1, bz1 = rect
    W, D = bx1 - bx0 + 1, bz1 - bz0 + 1
    sides = {s: edge_street(ground, bx0, bz0, bx1, bz1, s) for s in ("north", "south", "west", "east")}
    depth = profile.depth
    dN = dS = 0
    if sides["north"] and sides["south"]:
        if D >= 2 * depth + 4:
            dN = dS = depth
        elif D >= 2 * 9:
            dN = dS = D // 2
        elif D >= 8:
            dN = D
    elif sides["north"] and D >= 8:
        dN = min(depth, D)
    elif sides["south"] and D >= 8:
        dS = min(depth, D)
    placed = []
    if dN:
        placed += build_row(ground, cv, rng, profile, "north", bx0, bx1, bz0, dN, sides["north"],
                            sides["west"] is not None, sides["east"] is not None)
    if dS:
        placed += build_row(ground, cv, rng, profile, "south", bx0, bx1, bz1, dS, sides["south"],
                            sides["east"] is not None, sides["west"] is not None)
    zlo, zhi = bz0 + dN, bz1 - dS
    span = zhi - zlo + 1
    for side, line in (("west", bx0), ("east", bx1)):
        if not sides[side] or span < 5:
            continue
        other = sides["east" if side == "west" else "west"]
        dd = depth if W >= 2 * depth + 4 else (W // 2 if W >= 18 else (W if not other else 0))
        if dd < 8:
            continue
        placed += build_row(ground, cv, rng, profile, side, zlo, zhi, line, dd, sides[side], False, False)
    profile.yard(ground, cv, rng, rect, placed)
    return placed


class Profile:
    """District character: what lines its streets and what fills its back yards."""

    def __init__(self, name, depth, row, yard_kind="yards"):
        self.name, self.depth, self._row, self.yard_kind = name, depth, row, yard_kind

    def row(self, rng, L, street, d, left_open, right_open):
        return self._row(rng, L, street, d, left_open, right_open)

    def yard(self, ground, cv, rng, rect, placed):
        yards(ground, cv, rng, rect, placed, self.yard_kind)


def yards(ground, cv, rng, rect, placed, kind):
    """Back yards: the unbuilt middle of a block, with walls between plots, sheds and the odd tree."""
    bx0, bz0, bx1, bz1 = rect
    s = cv.street
    free = ground.free[bz0:bz1 + 1, bx0:bx1 + 1]
    zs, xs = np.nonzero(free)
    if len(zs) == 0:
        return
    zs += bz0; xs += bx0
    surface = {"yards": I("grass_block"), "court": I("b:tingen_cobbles"), "garden": I("grass_block"),
               "works": I("b:tingen_cobbles")}[kind]
    cv.a[s - 1, zs, xs] = surface
    if kind == "yards":
        # patches of worn earth
        m = ((xs * 7 + zs * 3) % 5 == 0)
        cv.a[s - 1, zs[m], xs[m]] = I("coarse_dirt")
    if kind == "court":
        m = ((xs * 5 + zs * 11) % 7 == 0)
        cv.a[s - 1, zs[m], xs[m]] = I("mossy_cobblestone")
    ground.free[zs, xs] = False
    ground.yard[zs, xs] = True
    # plot walls: carry each building's side walls back through the yard
    wallb = I("b:stock_brick_wall") if kind != "court" else I("b:soot_brick_wall")
    if kind in ("yards", "court"):
        for x, z, fw, fd, side in placed:
            if side == "north":
                for xx in (x,):
                    for zz in range(z + fd, bz1 + 1):
                        if ground.yard[zz, xx]:
                            cv.a[s, zz, xx] = wallb
                        else:
                            break
            elif side == "south":
                for xx in (x + fw - 1,):
                    for zz in range(z - 1, bz0 - 1, -1):
                        if ground.yard[zz, xx]:
                            cv.a[s, zz, xx] = wallb
                        else:
                            break
        # the mid-line wall between back-to-back rows
        mid = (bz0 + bz1) // 2
        for xx in range(bx0, bx1 + 1):
            if ground.yard[mid, xx]:
                cv.a[s, mid, xx] = wallb
        # a water butt or a coal bin against the mid wall now and then
        for x, z, fw, fd, side in placed:
            if side not in ("north", "south") or rng.random() < 0.6:
                continue
            sz = mid - 1 if side == "north" else mid + 1
            sx = x + 1 + rng.randint(0, max(0, fw - 3))
            if bx0 <= sx <= bx1 and ground.yard[sz, sx] and cv.a[s, sz, sx] == 0:
                cv.a[s, sz, sx] = I(rng.choice(("barrel", "cauldron", "composter")))
    if kind in ("yards", "garden"):
        from terrain import tree
        n = len(zs) // (60 if kind == "yards" else 30)
        for _ in range(n):
            j = rng.randrange(len(zs))
            z, x = zs[j], xs[j]
            if ground.yard[max(z - 2, 0):z + 3, max(x - 2, 0):x + 3].all() and cv.a[s, z, x] == 0:
                tree(cv, x, s, z, rng, rng.choice(("plane", "birch", "oak")))
    if kind == "garden":
        for j in range(0, len(zs), 9):
            z, x = zs[j], xs[j]
            if cv.a[s, z, x] == 0 and (x + z) % 4 == 0:
                cv.a[s, z, x] = I(rng.choice(("rose_bush", "lilac", "peony", "fern", "short_grass")), half="lower") \
                    if False else I(rng.choice(("poppy", "fern", "short_grass", "oxeye_daisy", "azure_bluet")))
    if kind == "works":
        # tidy stacks of cargo here and there, not litter everywhere
        for _ in range(len(zs) // 260):
            j = rng.randrange(len(zs))
            z0, x0 = zs[j], xs[j]
            w, d, h = rng.randint(2, 4), rng.randint(2, 3), rng.randint(1, 2)
            if not ground.yard[z0:z0 + d, x0:x0 + w].all() or (cv.a[s, z0:z0 + d, x0:x0 + w] != 0).any():
                continue
            item = rng.choice(("b:crate", "barrel", "b:crate", "hay_block", "coal_block"))
            cv.a[s:s + h, z0:z0 + d, x0:x0 + w] = I(item)


def fill_city(ground, cv, rng, districts, profiles):
    """Line every street with buildings wherever there is room behind its pavement, avenues first; then turn
    what is left in each district into back yards."""
    D, W = ground.free.shape

    def district(x, z):
        for name, (x0, z0, x1, z1) in districts:
            if x0 <= x <= x1 and z0 <= z <= z1:
                return name
        return None

    order = sorted(range(len(ground.streets)), key=lambda i: {"wide": 0, "avenue": 1, "street": 2, "lane": 3}[ground.streets[i].kind])
    placed = []
    for sid in order:
        st = ground.streets[sid]
        for sgn in (-1, 1):
            line = st.c + sgn * (st.hw + 1)
            if st.axis == "x":
                side = "south" if sgn < 0 else "north"
                if not 0 <= line < D:
                    continue
            else:
                side = "east" if sgn < 0 else "west"
                if not 0 <= line < W:
                    continue
            lo, hi = max(st.a, 0), min(st.b, (W if st.axis == "x" else D) - 1)
            # which district each frontage cell belongs to
            prof_at = []
            for t in range(lo, hi + 1):
                x, z = (t, line) if st.axis == "x" else (line, t)
                prof_at.append(district(x, z))
            t = lo
            while t <= hi:
                name = prof_at[t - lo]
                u = t
                while u + 1 <= hi and prof_at[u + 1 - lo] == name:
                    u += 1
                if name is not None:
                    prof = profiles[name]
                    for depth in range(prof.depth, 7, -2):
                        for a, b in free_runs(ground, side, t, u, line, depth, minlen=5 if depth < prof.depth else 5):
                            def street_at(tt):
                                x, z = (tt, line) if st.axis == "x" else (line, tt)
                                return 0 <= x < W and 0 <= z < D and 1 <= ground.cell[z, x] <= 3
                            lo_open, hi_open = street_at(a - 1), street_at(b + 1)
                            # local left/right as seen from the street
                            if side in ("north", "east"):
                                left, right = lo_open, hi_open
                            else:
                                left, right = hi_open, lo_open
                            segs = prof.row(rng, b - a + 1, st, depth, left, right)
                            placed += place_row(ground, cv, rng, side, a, b, line, depth, segs)
                t = u + 1
    # back yards
    for name, rect in districts:
        prof = profiles[name]
        for comp in components(ground, rect, min_area=1):
            bx0, bz0, bx1, bz1 = comp
            inside = [p for p in placed if bx0 - 30 <= p[0] <= bx1 + 30 and bz0 - 30 <= p[1] <= bz1 + 30]
            yards(ground, cv, rng, comp, inside, prof.yard_kind)
    return placed
