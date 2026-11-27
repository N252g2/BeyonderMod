"""Builds the character creator's skin parts: 64x64 pixel-art layers in the normal Minecraft skin
layout, drawn the way skin artists draw them (clean clusters, hue-shifted shades, selective
outlines, the outer layer for hair and anything that should stand out). No noise.

Run from the repo root:  python3 tools/gen_skin_parts.py [--preview DIR]

Each part is an *index* image: a visible pixel's red channel is a material id and its green
channel a shade (0 darkest .. 5 brightest, 3 = the chosen colour). The game builds a palette per
material from the player's choices (CharacterSkins.ramp mirrors `ramp` below) and paints the
parts in order: body, face, brows, facial hair, hair.
"""
import colorsys
import os
import sys

from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "beyonder", "textures", "skin")

# materials
SKIN, HAIR, IRIS, WHITE, LASH, LIP, CLOTH, TROUSER, SHIRT, LEATHER, BRASS, SILVER, SILK, STUBBLE = range(1, 15)


class Part:
    def __init__(self):
        self.img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()

    def set(self, x, y, mat, shade):
        if 0 <= x < 64 and 0 <= y < 64:
            self.px[x, y] = (mat, max(0, min(5, shade)), 0, 255)

    def get(self, x, y):
        p = self.px[x, y]
        return None if p[3] == 0 else (p[0], p[1])

    def save(self, name):
        os.makedirs(OUT, exist_ok=True)
        self.img.save(os.path.join(OUT, name + ".png"))


class Box:
    """A cuboid's faces on the skin: origin (u, v), size w x h x d."""

    def __init__(self, u, v, w, h, d):
        self.u, self.v, self.w, self.h, self.d = u, v, w, h, d

    def rect(self, face):
        u, v, w, h, d = self.u, self.v, self.w, self.h, self.d
        return {"top": (u + d, v, w, d), "bottom": (u + d + w, v, w, d), "right": (u, v + d, d, h),
                "front": (u + d, v + d, w, h), "left": (u + d + w, v + d, d, h), "back": (u + d + w + d, v + d, w, h)}[face]


HEAD, HAT = Box(0, 0, 8, 8, 8), Box(32, 0, 8, 8, 8)
BODY, JACKET = Box(16, 16, 8, 12, 4), Box(16, 32, 8, 12, 4)
RLEG, LLEG = Box(0, 16, 4, 12, 4), Box(16, 48, 4, 12, 4)
RPANTS, LPANTS = Box(0, 32, 4, 12, 4), Box(0, 48, 4, 12, 4)


def arm_boxes(slim):
    w = 3 if slim else 4
    return (Box(40, 16, w, 12, 4), Box(32, 48, w, 12, 4)), (Box(40, 32, w, 12, 4), Box(48, 48, w, 12, 4))


def paint(part, box, face, fn):
    """fn(x, y, w, h) -> (material, shade) or None, in the face's own coordinates."""
    x0, y0, w, h = box.rect(face)
    for y in range(h):
        for x in range(w):
            r = fn(x, y, w, h)
            if r is not None:
                part.set(x0 + x, y0 + y, r[0], r[1])


def grid(part, box, face, rows, legend):
    """Paints a face from a list of strings; characters map through `legend` to (mat, shade)."""
    x0, y0, w, h = box.rect(face)
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend and legend[ch] is not None:
                part.set(x0 + x, y0 + y, *legend[ch])


# ---------------------------------------------------------------- the body and outfit

def base(slim):
    P = Part()
    (rarm, larm), (rsleeve, lsleeve) = arm_boxes(slim)

    # --- head: skin, lit from the front-left, framed darker at the edges
    def head_side(x, y, w, h, side):
        front = (w - 1 - x) if side == "right" else x  # 0 at the face edge
        s = 3
        if front >= 6:
            s = 2                      # towards the back of the head
        if y >= 6 and front <= 2:
            s = 2                      # jaw shadow
        if 3 <= front <= 4 and 3 <= y <= 5:
            s = 2 if y != 4 else 1     # the ear
        if front == 4 and y == 4:
            s = 2
        return (SKIN, s)
    paint(P, HEAD, "right", lambda x, y, w, h: head_side(x, y, w, h, "right"))
    paint(P, HEAD, "left", lambda x, y, w, h: head_side(x, y, w, h, "left"))
    paint(P, HEAD, "top", lambda x, y, w, h: (SKIN, 3))
    paint(P, HEAD, "bottom", lambda x, y, w, h: (SKIN, 1))
    paint(P, HEAD, "back", lambda x, y, w, h: (SKIN, 2 if y >= 6 else 3))

    def face(x, y, w, h):
        s = 3
        if y <= 4 and 2 <= x <= 5:
            s = 4                      # forehead and cheeks catch the light (mostly under the fringe)
        if y == 7 and x in (0, 7):
            s = 2                      # jaw corners
        return (SKIN, s)
    paint(P, HEAD, "front", face)

    # --- shirt sleeves, rolled cuffs, forearms and hands
    for arm, sleeve, right in ((rarm, rsleeve, True), (larm, lsleeve, False)):
        for f in ("front", "back", "right", "left"):
            def a(x, y, w, h, f=f):
                if y <= 5:
                    s = 4 if y == 0 else 3
                    if (x, y) in ((1, 2), (2, 3), (0, 4)) and f == "front":
                        s = 2              # a fold at the elbow
                    if f in ("back",) or (f == "left" and right) or (f == "right" and not right):
                        s -= 1             # the side away from the light
                    return (SHIRT, s)
                if y <= 7:
                    return (SHIRT, 4 if y == 6 else 2)   # rolled cuff
                s = 3 if y < 10 else 2
                if y == 11 and f == "front" and x in (0, w - 1):
                    s = 1                  # knuckles
                if f == "back" or (f == "left" and right) or (f == "right" and not right):
                    s = max(1, s - 1)
                return (SKIN, s)
            paint(P, arm, f, a)
            # the cuff stands out on the outer layer
            paint(P, sleeve, f, lambda x, y, w, h: (SHIRT, 4 if y == 6 else 3) if y in (6, 7) else None)
        paint(P, arm, "top", lambda x, y, w, h: (SHIRT, 4))
        paint(P, arm, "bottom", lambda x, y, w, h: (SKIN, 1))

    # --- waistcoat over the shirt, cravat, buttons and a watch chain
    front = [
        "CC#WW#CC",
        "CCC#WCCC",
        "CCCC#CCC",
        "CCCCbCCC",
        "CCCCcCCC",
        "CCCCbCCC",
        "CCCCcmmC",
        "CpCCbCpC",
        "CCCCcCCC",
        "CCCCbCCC",
        "CCCCcCCC",
        "vC.CCC.C",
    ]
    # '#' is the cravat; 'W' shirt; 'C' cloth; 'b' button; 'c' the centre seam; 'm' chain; 'p' pocket; 'v' point
    for y, row in enumerate(front):
        for x, ch in enumerate(row):
            fx, fy, _, _ = BODY.rect("front")
            if ch == "#":
                P.set(fx + x, fy + y, SILK, 2 if y else 3)
            elif ch == "W":
                P.set(fx + x, fy + y, SHIRT, 4)
            elif ch in "Cv.":
                s = 3
                if x == 0 or x == 7:
                    s = 2
                if y <= 2 and x in (1, 2):
                    s = 4                         # chest catches the light
                if y == 11:
                    s = 1 if ch == "." else 2     # hem
                if ch == "v":
                    s = 2
                P.set(fx + x, fy + y, CLOTH, s)
            elif ch == "c":
                P.set(fx + x, fy + y, CLOTH, 1)
            elif ch == "b":
                P.set(fx + x, fy + y, BRASS, 4)
            elif ch == "m":
                P.set(fx + x, fy + y, SILVER, 4 if x == 5 else 3)
            elif ch == "p":
                P.set(fx + x, fy + y, CLOTH, 1)
    # the V neck's edge on the outer layer: lapels that stand out
    jx, jy, _, _ = JACKET.rect("front")
    for (x, y) in ((1, 0), (2, 1), (3, 2), (6, 0), (5, 1)):
        P.set(jx + x, jy + y, CLOTH, 4)
    P.set(jx + 3, jy + 0, SILK, 3)  # the cravat's knot pops out
    P.set(jx + 4, jy + 0, SILK, 2)

    paint(P, BODY, "back", lambda x, y, w, h: (BRASS, 3) if (y == 8 and x in (3, 4)) else
          (CLOTH, 1) if y == 8 else (CLOTH, 2 if y >= 10 or x in (0, 7) else 3) if y != 11 else (CLOTH, 1))
    paint(P, BODY, "right", lambda x, y, w, h: (CLOTH, 2) if y < 11 else (CLOTH, 1))
    paint(P, BODY, "left", lambda x, y, w, h: (CLOTH, 2) if y < 11 else (CLOTH, 1))
    paint(P, BODY, "top", lambda x, y, w, h: (SHIRT, 4) if 2 <= x <= 5 else (CLOTH, 4))
    paint(P, BODY, "bottom", lambda x, y, w, h: (TROUSER, 2))

    # --- trousers with a pressed crease, and laced boots
    for leg, pants, right in ((RLEG, RPANTS, True), (LLEG, LPANTS, False)):
        for f in ("front", "back", "right", "left"):
            def l(x, y, w, h, f=f):
                inner = (f == "left" and right) or (f == "right" and not right)
                if y <= 8:
                    s = 3
                    if f == "front" and x == (2 if right else 1):
                        s = 4                   # the crease
                    if inner or f == "back":
                        s = 2
                    if y == 0:
                        s = 2                   # under the waistcoat
                    if y in (5, 6) and f == "front" and x == (0 if right else 3):
                        s = 2                   # knee fold
                    return (TROUSER, s)
                if y == 9:
                    return (LEATHER, 4)
                if y == 10:
                    if f == "front" and x in (1, 2):
                        return (LEATHER, 1)     # laces
                    return (LEATHER, 2 if inner or f == "back" else 3)
                return (LEATHER, 0 if y == 11 and f != "front" else 1)
            paint(P, leg, f, l)
            # boot tops stand out a little
            paint(P, pants, f, lambda x, y, w, h: (LEATHER, 4) if y == 9 else None)
        paint(P, leg, "top", lambda x, y, w, h: (TROUSER, 2))
        paint(P, leg, "bottom", lambda x, y, w, h: (LEATHER, 0))
    P.save("base_slim" if slim else "base_wide")


EYE_STYLES = {
    # one eye, outer pixel first, rows 5 and 6 (the right eye is mirrored). Two pixels wide, one or two
    # tall, as in community skins: a dark lid line over white + iris, the iris towards the nose.
    0: (["LL", "wi"], {"i": (IRIS, 3)}),                       # classic
    1: (["wI", "wi"], {"I": (IRIS, 1), "i": (IRIS, 4)}),       # bright: tall iris, dark under the lid
    2: (["LL", "wi"], {"i": (IRIS, 3)}),                       # lashed: plus a flick at the outer corner
    3: (["dd", "wi"], {"i": (IRIS, 2)}),                       # soft: a skin-toned lid, no dark line
    4: (["ee", "Li"], {"i": (IRIS, 2)}),                       # narrow: one row, hooded lid above
}


def face_part(style):
    """Eyes and mouth for one eye shape."""
    P = Part()
    fx, fy, _, _ = HEAD.rect("front")
    rows, extra = EYE_STYLES[style]
    legend = {"L": (LASH, 1), "w": (WHITE, 4), "d": (SKIN, 1), "e": (SKIN, 2)}
    legend.update(extra)
    for y, row in enumerate(rows):
        for i, ch in enumerate(row):
            for x in (1 + i, 6 - i):                  # left eye outer col 1, right eye outer col 6
                P.set(fx + x, fy + 5 + y, *legend[ch])
    if style == 2:
        P.set(fx + 0, fy + 5, LASH, 1)
        P.set(fx + 7, fy + 5, LASH, 1)
    P.set(fx + 3, fy + 7, LIP, 3)
    P.set(fx + 4, fy + 7, LIP, 3)
    P.save(f"face_{style}")


def brows(style):
    P = Part()
    rows = {
        0: ["", "", "", "", ".BB..BB."],                      # straight
        1: ["", "", "", ".B....B.", "..B..B.."],              # arched (rises towards the outer edge)
        2: ["", "", "", "", "BBB..BBB"],                      # heavy
    }[style]
    grid(P, HEAD, "front", rows, {"B": (HAIR, 1 if style == 2 else 2)})
    P.save(f"brows_{style}")


# ---------------------------------------------------------------- hair

def strand(u, y, length, clump=3, shine=(1, 2)):
    """Hair as clumps of strands. `u` runs all the way round the head (right side, front, left side,
    back) so the clumps carry over the edges. Each clump is lit in the middle, has a dark gap on one
    side, a shine band near the top and darker tips."""
    k = u % clump
    s = 3
    if k == 0:
        s = 4 if y in shine else 3
    if k == clump - 1:
        s = 2                                   # the gap between clumps
    if y in shine and k == 1 % clump and clump > 1:
        s = 5
    if y >= length - 1:
        s = max(1, s - 1)                       # tips
    if y >= length - 1 and k == clump - 1:
        s = 1
    return s


# around-the-head column of each face's x (0 = back edge of the right side)
def around(face, x):
    return {"right": x, "front": 8 + x, "left": 16 + x, "back": 24 + x}[face]


FRONTS = {
    # H hair, '.' skin. Tips end in points; partings show forehead.
    0: ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHH.HH.H", "H......H"],                       # short and neat
    1: ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHH.HH", "HHH....H", "HH.....H"],            # side parting
    2: ["HHHHHHHH", "HHHHHHHH", "H.HHHH.H", "H......H"],                                   # slicked back
    3: ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "H.HH.HHH", "H..H...H", "H......H"],            # tousled
    4: ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HH.HH.HH", "H......H", "H......H"],            # curly
    5: ["HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "HHHHHHHH", "H.H..H.H", "H......H", "H......H", "H......H"],  # bob
    6: ["HHHHHHHH", "HHHHHHHH", "HHH..HHH", "HH....HH", "H......H", "H......H"],            # tied back, centre part
    7: ["HHHHHHHH", "HHHHHHHH", "HHH..HHH", "HH....HH", "H......H", "H......H", "H......H", "H......H"],  # long, loose
    8: ["HHHHHHHH", "HHHHHHHH"],                                                           # close-cropped
}
SIDES = {0: [6, 6, 6, 5, 4, 4, 5, 5], 1: [6, 6, 6, 5, 5, 4, 5, 6], 2: [6, 6, 6, 5, 4, 3, 3, 4],
         3: [6, 6, 6, 6, 5, 5, 5, 6], 4: [6, 6, 6, 6, 6, 5, 5, 6], 5: [8, 8, 8, 8, 8, 8, 8, 8],
         6: [7, 7, 6, 6, 5, 4, 5, 6], 7: [8, 8, 8, 8, 8, 8, 8, 8], 8: [3, 3, 3, 3, 3, 2, 2, 2]}
BACK = {0: 6, 1: 6, 2: 7, 3: 7, 4: 7, 5: 8, 6: 8, 7: 8, 8: 4}
PARTING = {1: 5, 6: 4, 7: 4, 5: 4}          # top-face column of the parting


def hair_part(style):
    P = Part()
    H = lambda s: (HAIR, s)
    cropped = style == 8
    curly = style == 4
    clump = 2 if curly else 3
    sides, back = SIDES[style], BACK[style]

    def shade(u, y, length):
        if cropped:                             # short and even, with a faint grain
            return 2 if (2 * u + y) % 5 == 0 else 3
        if curly:                               # curls: little lit loops rather than long strands
            return 4 if (u + y) % 3 == 0 else (2 if (u + 2 * y) % 3 == 0 else 3)
        return strand(u, y, length, clump)

    # ---- front: fringe with pointed tips, a shadow on the forehead under it
    fx, fy, _, _ = HEAD.rect("front")
    rows = FRONTS[style]
    for x in range(8):
        col = [y for y, r in enumerate(rows) if r[x] == "H"]
        length = (max(col) + 1) if col else 0
        for y in col:
            P.set(fx + x, fy + y, *H(shade(around("front", x), y, length)))
        if length and length <= 4 and length < len(rows) + 1 and not cropped:
            P.set(fx + x, fy + length, SKIN, 2)  # the hair's shadow
    for y in range(len(rows), 4):
        pass

    # ---- sides and back: strands, ragged ends
    for side in ("right", "left"):
        def sf(x, y, w, h, side=side):
            col = x if side == "right" else w - 1 - x      # 0 = back of the head
            u = around(side, x)
            length = sides[col] - (1 if not cropped and u % clump == clump - 1 and sides[col] < 8 else 0)
            return H(shade(u, y, length)) if y < length else None
        paint(P, HEAD, side, sf)

    def bf(x, y, w, h):
        u = around("back", x)
        length = back - (1 if x in (0, 7) and back < 8 else 0) - (1 if not cropped and u % clump == clump - 1 and back < 8 else 0)
        return H(shade(u, y, length)) if y < length else None
    paint(P, HEAD, "back", bf)

    # ---- top: strands combed away from a parting (or from the crown), lit next to it
    part = PARTING.get(style)

    def tf(x, y, w, h):
        if cropped:
            return H(4 if (x + 2 * y) % 7 == 0 else 2 if (2 * x + y) % 5 == 0 else 3)
        if curly:
            return H(4 if (x + y) % 3 == 0 else (2 if (x + 2 * y) % 3 == 0 else 3))
        if part is not None:
            if x == part and y >= 2:
                return (SKIN, 2)                  # the parting itself
            d = abs(x - part)
            s = 5 if d == 1 and y % 3 != 0 else 4 if d <= 2 else 3
            if (y + x) % 3 == 0 and d > 1:
                s -= 1                            # strand lines running front to back
            if x in (0, 7):
                s = 2
            return H(max(1, s))
        # crown whorl towards the back, strands radiating forward
        s = 3
        if 2 <= x <= 5 and 1 <= y <= 4:
            s = 4
        if (x, y) in ((3, 2), (4, 2), (4, 3)):
            s = 5
        if (x + y) % 3 == 0 and s < 5:
            s -= 1
        if x in (0, 7) or y == 0:
            s = 2
        return H(max(1, s))
    paint(P, HEAD, "top", tf)

    # ---- outer layer: 3D locks that frame the face and give the fringe some lift
    hx, hy, _, _ = HAT.rect("front")
    if not cropped:
        # fringe lift: the top edge stands proud, with a few tips
        for x in range(8):
            if x % 3 != 2:
                P.set(hx + x, hy, HAIR, 4 if x % 3 == 0 else 3)
        paint(P, HAT, "top", lambda x, y, w, h: H(4 if (x + y) % 4 == 0 else 3) if (x in (0, 7) or y in (0, 7)) else None)
        # side locks at the edges of the face (cols 0 and 7 keep clear of the eyes)
        lock = {0: 2, 1: 5, 2: 1, 3: 4, 4: 4, 5: 7, 6: 5, 7: 7}[style]
        for y in range(1, lock + 1):
            for x in (0, 7):
                P.set(hx + x, hy + y, HAIR, 2 if y == lock else 3)
        if style in (6, 7, 5):  # the parted fringe falls either side of the parting
            for x, y in ((2, 1), (5, 1), (1, 2), (6, 2), (1, 3), (6, 3)):
                P.set(hx + x, hy + y, HAIR, 3 if y < 3 else 2)
        if style == 1:          # the swept side hangs over the brow
            for x, y in ((1, 1), (2, 1), (3, 1), (1, 2), (2, 2), (1, 3)):
                P.set(hx + x, hy + y, HAIR, 4 if y == 1 else 3 if y == 2 else 2)
    if style in (3, 4):  # tousled and curly: tufts that stand out
        tuft = ["H.HHH.HH", "..H...H."] if style == 3 else ["HH.HHH.H", "H..H..HH", "..H..H.."]
        for y, row in enumerate(tuft):
            for x, ch in enumerate(row):
                if ch == "H":
                    P.set(hx + x, hy + y, HAIR, 4 if y == 0 and x % 2 == 0 else 3)
        paint(P, HAT, "top", lambda x, y, w, h: H(4 if (x + y) % 3 == 0 else 3) if (style == 4 or (x * 3 + y) % 4 == 0) else None)
        for f in ("right", "left", "back"):
            paint(P, HAT, f, lambda x, y, w, h: H(2 if y == 2 else 3) if y <= (3 if style == 4 else 1) and (style == 4 or x % 2 == 0) else None)
    if style in (5, 7):  # bob and long hair: a full outer shell with ragged ends
        length = 7 if style == 5 else 8
        for f in ("right", "left", "back"):
            def shell(x, y, w, h, f=f):
                u = around(f, x)
                ln = length - (1 if u % 2 == 1 and length < 8 else 0)
                if y >= ln:
                    return None
                if f != "back":
                    col = x if f == "right" else w - 1 - x
                    if col == 7 and y >= 2:
                        return None        # keep the face clear
                return H(strand(u, y, ln, clump=2))
            paint(P, HAT, f, shell)
        paint(P, HAT, "top", lambda x, y, w, h: (SKIN, 2) if x == 4 and y >= 3 else H(4 if abs(x - 4) == 1 else 3))
    if style == 7:       # down the back, ending in points
        paint(P, BODY, "back", lambda x, y, w, h: H(strand(x, y, 6 - (x % 2), clump=2)) if y < 6 - (x % 2) and 1 <= x <= 6 else None)
    if style == 6:       # a tail tied with a dark ribbon
        paint(P, BODY, "back", lambda x, y, w, h: H(4 if y == 1 else 3 if y < 4 else 2) if 3 <= x <= 4 and y < 5 else None)
        bx, by, _, _ = HAT.rect("back")
        P.set(bx + 3, by + 6, SILK, 3)
        P.set(bx + 4, by + 6, SILK, 2)
    P.save(f"hair_{style}")


def facial_part(style):
    """1 stubble, 2 moustache, 3 mutton chops, 4 full beard."""
    P = Part()
    fx, fy, _, _ = HEAD.rect("front")
    if style == 1:
        for x in range(8):
            if x % 2 == 0 and x not in (3, 4):
                P.set(fx + x, fy + 7, STUBBLE, 3)
        for side in ("right", "left"):
            paint(P, HEAD, side, lambda x, y, w, h, side=side:
                  (STUBBLE, 2) if y >= 6 and ((w - 1 - x) if side == "right" else x) <= 2 and (x + y) % 2 == 0 else None)
    if style == 2:
        grid(P, HEAD, "front", ["", "", "", "", "", "", "", ".HHHHHH."], {"H": (HAIR, 3)})
        P.set(fx + 3, fy + 7, HAIR, 4)
    if style == 3:
        for y in range(3, 8):
            P.set(fx, fy + y, HAIR, 3 if y < 6 else 2)
            P.set(fx + 7, fy + y, HAIR, 3 if y < 6 else 2)
        P.set(fx + 1, fy + 6, HAIR, 3)
        P.set(fx + 6, fy + 6, HAIR, 3)
        P.set(fx + 1, fy + 7, HAIR, 2)
        P.set(fx + 6, fy + 7, HAIR, 2)
        for side in ("right", "left"):
            paint(P, HEAD, side, lambda x, y, w, h, side=side:
                  (HAIR, 3 if y < 6 else 2) if y >= 3 and ((w - 1 - x) if side == "right" else x) <= 1 else None)
    if style == 4:
        rows = ["", "", "", "", "", "H......H", "H......H", "HHHHHHHH"]
        grid(P, HEAD, "front", rows, {"H": (HAIR, 3)})
        P.set(fx + 3, fy + 7, HAIR, 4)
        for side in ("right", "left"):
            paint(P, HEAD, side, lambda x, y, w, h, side=side:
                  (HAIR, 3 if y < 7 else 2) if y >= 4 and ((w - 1 - x) if side == "right" else x) <= 2 else None)
        paint(P, HEAD, "bottom", lambda x, y, w, h: (HAIR, 2) if y >= 3 else None)
        # the beard juts out on the outer layer
        hx, hy, _, _ = HAT.rect("front")
        for x in range(8):
            if x not in (3, 4):
                P.set(hx + x, hy + 7, HAIR, 2 if x in (0, 7) else 3)
    P.save(f"facial_{style}")


# ---------------------------------------------------------------- palettes (mirrored in CharacterSkins.java)

SKIN_C = [(250, 226, 210), (240, 204, 174), (232, 186, 160), (206, 166, 122), (192, 144, 102), (158, 108, 72), (120, 80, 52), (82, 52, 38)]
EYES_C = [(118, 134, 148), (70, 122, 208), (70, 148, 96), (142, 112, 58), (112, 72, 42), (52, 38, 32), (210, 146, 42), (138, 92, 190)]
HAIR_C = [(40, 34, 40), (66, 46, 34), (104, 70, 44), (130, 76, 44), (148, 62, 40), (196, 104, 54), (222, 186, 114), (236, 228, 206), (152, 152, 158), (230, 230, 234)]
CLOTH_C = [(62, 64, 74), (40, 52, 94), (98, 66, 46), (112, 34, 48), (40, 80, 58)]
FIXED = {WHITE: (236, 234, 230), LASH: (46, 30, 32), SHIRT: (226, 222, 210), LEATHER: (76, 52, 38),
         BRASS: (196, 154, 70), SILVER: (196, 202, 212), SILK: (52, 44, 60)}


def ramp(rgb, dark_hue=250.0):
    """Six shades around a base colour (index 3), hue-shifted: shadows cooler and richer, lights warmer."""
    r, g, b = (c / 255.0 for c in rgb)
    h, s, v = colorsys.rgb_to_hsv(r, g, b)
    h *= 360.0
    out = []
    for k in range(-3, 3):
        if k < 0:
            vv = v * (1 + 0.17 * k)
            ss = min(1.0, s * (1 - 0.12 * k) + 0.02 * -k)
            hh = shift(h, dark_hue, 8.0 * -k * min(1.0, s * 3))
        elif k > 0:
            vv = v + (1 - v) * 0.30 * k
            ss = s * (1 - 0.18 * k)
            hh = shift(h, 55.0, 6.0 * k * min(1.0, s * 3))
        else:
            vv, ss, hh = v, s, h
        rr, gg, bb = colorsys.hsv_to_rgb((hh % 360) / 360.0, max(0, min(1, ss)), max(0, min(1, vv)))
        out.append((int(rr * 255), int(gg * 255), int(bb * 255)))
    return out


def shift(h, target, amount):
    d = ((target - h + 540) % 360) - 180
    step = max(-abs(d), min(abs(d), amount)) * (1 if d >= 0 else -1)
    return h + step


def palettes(app):
    skin_i, eyes_i, brows_i, hair_i, hc_i, fh_i, build_i, cloth_i, eye_i = app
    skin, hair, cloth = SKIN_C[skin_i], HAIR_C[hc_i], CLOTH_C[cloth_i]
    trousers = tuple(int(c * 0.6 + 24 * 0.4) for c in cloth)
    lip = tuple(int(a * 0.72 + b * 0.28) for a, b in zip(skin, (170, 60, 64)))
    stubble = tuple(int(a * 0.5 + b * 0.5) for a, b in zip(skin, hair))
    pal = {SKIN: ramp(skin, 340.0), HAIR: ramp(hair), IRIS: ramp(EYES_C[eyes_i]), CLOTH: ramp(cloth),
           TROUSER: ramp(trousers), LIP: ramp(lip, 340.0), STUBBLE: ramp(stubble, 340.0)}
    for m, c in FIXED.items():
        pal[m] = ramp(c)
    return pal


def compose(app):
    skin_i, eyes_i, brows_i, hair_i, hc_i, fh_i, build_i, cloth_i, eye_i = app
    pal = palettes(app)
    parts = ["base_slim" if build_i else "base_wide", f"face_{eye_i}"] + ([f"brows_{brows_i}"] if brows_i != 3 else [])
    if fh_i:
        parts.append(f"facial_{fh_i}")
    parts.append(f"hair_{hair_i}")
    out = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    o = out.load()
    for name in parts:
        im = Image.open(os.path.join(OUT, name + ".png")).convert("RGBA")
        p = im.load()
        for y in range(64):
            for x in range(64):
                m, s, _, a = p[x, y]
                if a:
                    o[x, y] = pal[m][s] + (255,)
    return out


def main():
    for slim in (False, True):
        base(slim)
    for e in EYE_STYLES:
        face_part(e)
    for b in (0, 1, 2):
        brows(b)
    for h in range(9):
        hair_part(h)
    for f in (1, 2, 3, 4):
        facial_part(f)
    print("skin parts written")
    if "--preview" in sys.argv:
        out = sys.argv[sys.argv.index("--preview") + 1]
        os.makedirs(out, exist_ok=True)
        samples = [(1, 1, 0, 0, 1, 0, 0, 0, 0), (3, 4, 2, 2, 0, 4, 0, 2, 4), (0, 1, 1, 5, 6, 0, 1, 3, 2), (6, 5, 0, 4, 0, 1, 0, 1, 3),
                   (2, 2, 3, 7, 4, 0, 1, 4, 1), (4, 3, 0, 6, 3, 3, 0, 0, 0), (5, 0, 2, 8, 8, 2, 0, 2, 4), (1, 6, 1, 3, 5, 0, 1, 1, 2),
                   (2, 1, 0, 1, 2, 0, 0, 3, 1)]
        for i, s in enumerate(samples):
            compose(s).save(os.path.join(out, f"skin{i}{'_slim' if s[6] else ''}.png"))


if __name__ == "__main__":
    main()
