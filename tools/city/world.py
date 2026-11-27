"""Voxel canvas, block palette and building volumes for the Tingen city generator.

Coordinates: x runs east, z runs south, y up. Arrays are indexed [y, z, x].
Block states are canonical strings, e.g. ``minecraft:oak_stairs[facing=north,half=bottom]``.
Building volumes are drawn facing north (the street is on the low-z side) and turned when stamped.
"""
import numpy as np

AIR = "minecraft:air"
KEEP = 0xFFFF            # "leave whatever is there" in a building volume
DIRS = ("north", "east", "south", "west")      # clockwise
STEP = {"north": (0, -1), "east": (1, 0), "south": (0, 1), "west": (-1, 0)}


def S(name, **props):
    """Canonical block-state string. ``b:`` means the beyonder namespace; no namespace means minecraft."""
    if name.startswith("b:"):
        name = "beyonder:" + name[2:]
    elif ":" not in name:
        name = "minecraft:" + name
    if not props:
        return name
    return name + "[" + ",".join(f"{k}={str(v).lower()}" for k, v in sorted(props.items())) + "]"


def split(state):
    if "[" not in state:
        return state, {}
    name, rest = state[:-1].split("[", 1)
    return name, dict(kv.split("=", 1) for kv in rest.split(",") if kv)


def join(name, props):
    return S(name, **props) if props else name


def turn(d, k):
    return DIRS[(DIRS.index(d) + k) % 4] if d in DIRS else d


_RAIL = {"north_south": "east_west", "east_west": "north_south"}


def rotate_state(state, k):
    """Turn a block state k quarter-turns clockwise seen from above (Minecraft's Rotation.CLOCKWISE_90)."""
    k %= 4
    if k == 0 or "[" not in state:
        return state
    name, p = split(state)
    q = dict(p)
    for key in ("facing", "horizontal_facing"):
        if key in p:
            q[key] = turn(p[key], k)
    if "axis" in p and p["axis"] in ("x", "z") and k % 2:
        q["axis"] = "z" if p["axis"] == "x" else "x"
    if any(d in p for d in DIRS):
        for d in DIRS:
            if d in p:
                q[turn(d, k)] = p[d]
    if "rotation" in p:
        q["rotation"] = str((int(p["rotation"]) + 4 * k) % 16)
    if "shape" in p and ("rail" in name):
        s = p["shape"]
        for _ in range(k):
            if s in _RAIL:
                s = _RAIL[s]
            elif s.startswith("ascending_"):
                s = "ascending_" + turn(s[10:], 1)
            elif "_" in s:
                a, b = s.split("_")
                a, b = turn(a, 1), turn(b, 1)
                # Rails name curves north/south first.
                if a in ("east", "west"):
                    a, b = b, a
                s = f"{a}_{b}"
        q["shape"] = s
    return join(name, q)


class Palette:
    def __init__(self):
        self.states = [AIR]
        self.index = {AIR: 0}
        self._rot = {}

    def id(self, state):
        i = self.index.get(state)
        if i is None:
            i = len(self.states)
            if i >= KEEP:
                raise ValueError("palette full")
            self.states.append(state)
            self.index[state] = i
        return i

    def rot(self, i, k):
        if i == KEEP or k % 4 == 0:
            return i
        key = (i, k % 4)
        r = self._rot.get(key)
        if r is None:
            r = self.id(rotate_state(self.states[i], k))
            self._rot[key] = r
        return r

    def name(self, i):
        return split(self.states[i])[0]


P = Palette()


def I(name, **props):
    """Palette id for a block state."""
    return P.id(S(name, **props))


class Vol:
    """A building volume, drawn facing north. Cells start as KEEP (transparent when stamped)."""

    def __init__(self, w, h, d, fill=KEEP):
        self.w, self.h, self.d = w, h, d
        self.a = np.full((h, d, w), fill, dtype=np.uint16)
        self.signs = []      # (x, y, z, lines)
        self.ground = np.full((d, w), KEEP, dtype=np.uint16)   # surface layer just below the pavement

    # ---- drawing; all ranges inclusive, clipped to the volume
    def _c(self, x0, x1, y0, y1, z0, z1):
        x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
        return (slice(max(y0, 0), min(y1, self.h - 1) + 1), slice(max(z0, 0), min(z1, self.d - 1) + 1),
                slice(max(x0, 0), min(x1, self.w - 1) + 1))

    def fill(self, x0, y0, z0, x1, y1, z1, b):
        self.a[self._c(x0, x1, y0, y1, z0, z1)] = b if isinstance(b, (int, np.integer)) else P.id(b)

    def set(self, x, y, z, b):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.a[y, z, x] = b if isinstance(b, (int, np.integer)) else P.id(b)

    def get(self, x, y, z):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            return int(self.a[y, z, x])
        return KEEP

    def walls(self, x0, y0, z0, x1, y1, z1, b):
        self.fill(x0, y0, z0, x1, y1, z0, b); self.fill(x0, y0, z1, x1, y1, z1, b)
        self.fill(x0, y0, z0, x0, y1, z1, b); self.fill(x1, y0, z0, x1, y1, z1, b)

    def replace(self, x0, y0, z0, x1, y1, z1, old, new):
        s = self._c(x0, x1, y0, y1, z0, z1)
        old = old if isinstance(old, (int, np.integer)) else P.id(old)
        new = new if isinstance(new, (int, np.integer)) else P.id(new)
        v = self.a[s]
        v[v == old] = new

    def sign(self, x, y, z, state, lines):
        self.set(x, y, z, state)
        self.signs.append((x, y, z, list(lines)))

    def paste(self, other, x, y, z):
        """Copy another (unturned) volume in at (x, y, z); KEEP cells stay transparent."""
        h = min(other.h, self.h - y); d = min(other.d, self.d - z); w = min(other.w, self.w - x)
        src = other.a[:h, :d, :w]
        dst = self.a[y:y + h, z:z + d, x:x + w]
        np.copyto(dst, src, where=src != KEEP)
        self.signs += [(sx + x, sy + y, sz + z, t) for sx, sy, sz, t in other.signs]
        if y == 0:
            g = other.ground[:d, :w]
            np.copyto(self.ground[z:z + d, x:x + w], g, where=g != KEEP)

    def ground_fill(self, x0, z0, x1, z1, b):
        x0, x1 = sorted((x0, x1)); z0, z1 = sorted((z0, z1))
        self.ground[max(z0, 0):min(z1, self.d - 1) + 1, max(x0, 0):min(x1, self.w - 1) + 1] = \
            b if isinstance(b, (int, np.integer)) else P.id(b)

    def turned(self, k):
        """This volume turned k quarter-turns clockwise (seen from above), block states included."""
        k %= 4
        a = self.a
        g = self.ground
        signs = [(x, y, z, t) for x, y, z, t in self.signs]
        w, d = self.w, self.d
        for _ in range(k):
            # (x, z) -> (d-1-z, x): the front (low z) ends up facing east.
            a = np.flip(np.swapaxes(a, 1, 2), axis=2)
            g = np.flip(np.swapaxes(g, 0, 1), axis=1)
            signs = [(d - 1 - z, y, x, t) for x, y, z, t in signs]
            w, d = d, w
        out = Vol(w, self.h, d)
        if k:
            full = np.arange(KEEP + 1, dtype=np.uint16)
            for i in np.unique(a):
                if i != KEEP:
                    full[i] = P.rot(int(i), k)
            a = full[a]
            g = full[g]
        out.a = np.ascontiguousarray(a)
        out.ground = np.ascontiguousarray(g)
        out.signs = signs
        return out


FACING_K = {"north": 0, "east": 1, "south": 2, "west": 3}


class Canvas:
    """The whole city. ``street`` is the y index of the first air layer above the paving."""

    def __init__(self, w, h, d, street):
        self.w, self.h, self.d, self.street = w, h, d, street
        self.a = np.zeros((h, d, w), dtype=np.uint16)
        self.signs = []
        self.marks = {}

    def stamp(self, vol, x, z, y=None, facing="north"):
        """Stamp a north-drawn volume so its front faces ``facing``; (x, z) is the min corner of the turned box."""
        y = self.street if y is None else y
        v = vol.turned(FACING_K[facing])
        x0, z0 = max(x, 0), max(z, 0)
        x1, z1 = min(x + v.w, self.w), min(z + v.d, self.d)
        y1 = min(y + v.h, self.h)
        if x1 <= x0 or z1 <= z0:
            return v
        src = v.a[: y1 - y, z0 - z: z1 - z, x0 - x: x1 - x]
        dst = self.a[y:y1, z0:z1, x0:x1]
        np.copyto(dst, src, where=src != KEEP)
        if y >= 1:
            gs = v.ground[z0 - z: z1 - z, x0 - x: x1 - x]
            np.copyto(self.a[y - 1, z0:z1, x0:x1], gs, where=gs != KEEP)
        for sx, sy, sz, t in v.signs:
            self.signs.append((sx + x, sy + y, sz + z, t))
        return v

    def fill(self, x0, y0, z0, x1, y1, z1, b):
        x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
        self.a[max(y0, 0):min(y1, self.h - 1) + 1, max(z0, 0):min(z1, self.d - 1) + 1,
               max(x0, 0):min(x1, self.w - 1) + 1] = b if isinstance(b, (int, np.integer)) else P.id(b)

    def set(self, x, y, z, b):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            self.a[y, z, x] = b if isinstance(b, (int, np.integer)) else P.id(b)

    def get(self, x, y, z):
        if 0 <= x < self.w and 0 <= y < self.h and 0 <= z < self.d:
            return int(self.a[y, z, x])
        return 0

    def sign(self, x, y, z, state, lines):
        self.set(x, y, z, state)
        self.signs.append((x, y, z, list(lines)))
