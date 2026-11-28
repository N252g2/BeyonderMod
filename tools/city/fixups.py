"""Last checks over the finished city."""
import numpy as np
from world import P, I, S, split

STEP = {"north": (0, -1), "south": (0, 1), "west": (-1, 0), "east": (1, 0)}
_LOW = ("_carpet", "_pressure_plate", "short_grass", "fern", "poppy", "dandelion", "tulip", "daisy", "bluet",
        "cornflower", "allium", "lily_of_the_valley", "wither_rose", "moss_carpet", "vine", "rail", "snow",
        "torchflower", "lily_pad", "glow_lichen")


def _kind(i):
    """'open' for air and things you walk through, 'step' for something you can step onto, else 'solid'."""
    if i == 0:
        return "open"
    name, props = split(P.states[i])
    n = name.split(":")[1]
    if any(t in n for t in _LOW):
        return "open"
    if n.endswith("_stairs") and props.get("half", "bottom") == "bottom":
        return "step"
    if n.endswith("_slab") and props.get("type", "bottom") == "bottom":
        return "step"
    return "solid"


def _reach(k, y, z0, x0, block, want, radius):
    """How many open cells at height y can be reached from (z0, x0) without passing ``block`` (capped)."""
    seen = {(z0, x0), block}
    todo = [(z0, x0)]
    n = 0
    while todo and n < want:
        z, x = todo.pop()
        if k(y, z, x) != "open":
            continue
        n += 1
        for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            c = (z + dz, x + dx)
            if c not in seen and abs(c[0] - z0) <= radius and abs(c[1] - x0) <= radius:
                seen.add(c)
                todo.append(c)
    return n


def doors(cv):
    """Every door must open onto somewhere you can stand on both sides. Doors that open into a wall, onto a drop
    or into a cupboard are bricked up; doors a block above the yard get a step."""
    a = cv.a
    lower = [i for i, st in enumerate(P.states) if st.split("[")[0].endswith("_door") and "half=lower" in st]
    kind = {}
    ys, zs, xs = np.nonzero(np.isin(a, lower))
    removed = steps = pockets = 0
    H, D, W = a.shape

    def k(y, z, x):
        if not (0 <= y < H and 0 <= z < D and 0 <= x < W):
            return "solid"
        i = int(a[y, z, x])
        if i not in kind:
            kind[i] = _kind(i)
        return kind[i]

    for y, z, x in zip(ys, zs, xs):
        name, props = split(P.states[int(a[y, z, x])])
        dx, dz = STEP[props.get("facing", "north")]
        ok = True
        for sgn in (1, -1):
            cx, cz = x + sgn * dx, z + sgn * dz
            if k(y, cz, cx) == "solid" or k(y + 1, cz, cx) != "open":
                ok = False
                break
            if k(y, cz, cx) == "open" and k(y - 1, cz, cx) == "open":
                # a drop: one block down onto solid ground gets a step, anything deeper is no way out
                if k(y - 2, cz, cx) == "solid" and k(y - 1, cz + 0, cx) == "open":
                    base = "b:ashlar_stairs"
                    face = {(0, -1): "south", (0, 1): "north", (-1, 0): "east", (1, 0): "west"}[(sgn * dx, sgn * dz)]
                    a[y - 1, cz, cx] = I(base, facing=face, half="bottom")
                    kind.pop(int(a[y - 1, cz, cx]), None)
                    steps += 1
                else:
                    ok = False
                    break
        if ok:
            # both sides must lead somewhere: a room on one side, and no dead-end pocket on either
            ra = _reach(k, y + 1, z + dz, x + dx, (z, x), 18, 10)
            rb = _reach(k, y + 1, z - dz, x - dx, (z, x), 18, 10)
            if min(ra, rb) >= 6 and max(ra, rb) >= 18:
                continue
            pockets += 1
        # brick it up with whatever the wall beside it is made of
        fill = None
        for ox, oz in ((dz, dx), (-dz, -dx)):
            for yy in (y, y + 1):
                j = int(a[yy, z + oz, x + ox]) if 0 <= z + oz < D and 0 <= x + ox < W else 0
                if j and k(yy, z + oz, x + ox) == "solid" and "_door" not in P.states[j] and "window" not in P.states[j]:
                    fill = j
                    break
            if fill:
                break
        a[y, z, x] = fill or I("b:stock_bricks")
        a[y + 1, z, x] = fill or I("b:stock_bricks")
        removed += 1
    print(f"  ({pockets} of them led into dead-end pockets)")
    return removed, steps


def find_door(cv, cx, cz, facing, radius):
    """The cell just inside the front door nearest (cx, cz) that opens toward ``facing``, and the yaw looking in."""
    a = cv.a
    s = cv.street
    want = [i for i, st in enumerate(P.states) if st.split("[")[0].endswith("_door") and "half=lower" in st
            and f"facing={facing}" in st]
    sub = a[s + 1, cz - radius:cz + radius, cx - radius:cx + radius]
    zs, xs = np.nonzero(np.isin(sub, want))
    if not len(zs):
        return None
    j = int(np.argmin((zs - radius) ** 2 + (xs - radius) ** 2))
    x, z = cx - radius + int(xs[j]), cz - radius + int(zs[j])
    dx, dz = STEP[facing]
    yaw = {"north": 0, "south": 180, "west": -90, "east": 90}[facing]      # looking in, away from the street
    return (x - 2 * dx, s + 1, z - 2 * dz), yaw
