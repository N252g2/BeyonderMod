"""Builds Beyonder's clothing: overlay textures in the Minecraft skin layout (drawn on a slightly
inflated player model over the character), hat textures for the hat models, and 32x32 icons.

Run from the repo root:  python3 tools/gen_clothing.py [--preview DIR]

Same pixel-art rules as the skins (gen_skin_parts.py): hue-shifted ramps, shade 3 = the cloth's
colour, light from the front-left, no noise. Only the base-layer UVs are painted; the game renders
the matching base parts of an inflated model.
"""
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(__file__))
from gen_skin_parts import Box, HEAD, BODY, RLEG, LLEG, arm_boxes, ramp  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "beyonder", "textures")
OUT = os.path.join(ROOT, "clothing")
ICONS = os.path.join(ROOT, "item", "clothing")

BRASS_C = (196, 154, 70)
SILVER_C = (190, 196, 206)


class Layer:
    """A 64x64 (or 64x32) RGBA texture painted with ramps: set(x, y, colour_ramp, shade)."""

    def __init__(self, h=64):
        self.img = Image.new("RGBA", (64, h), (0, 0, 0, 0))
        self.px = self.img.load()

    def set(self, x, y, rmp, shade):
        if 0 <= x < self.img.width and 0 <= y < self.img.height:
            self.px[x, y] = rmp[max(0, min(5, shade))] + (255,)

    def clear(self, x, y):
        self.px[x, y] = (0, 0, 0, 0)

    def face(self, box, face, fn):
        x0, y0, w, h = box.rect(face)
        for y in range(h):
            for x in range(w):
                r = fn(x, y, w, h)
                if r is not None:
                    self.set(x0 + x, y0 + y, r[0], r[1])


def lit(face, s, right_side=True):
    """Light from the front-left: backs and the far sides a step darker."""
    if face == "back":
        return s - 1
    if face == "top":
        return s + 1
    if face == "bottom":
        return s - 2
    if (face == "right" and right_side) or (face == "left" and not right_side):
        return s - 1
    return s


# ------------------------------------------------------------------ clothing pieces

def sleeves(L, slim, cloth, to_row, cuff=None, pattern=None):
    """Sleeves on both arms down to `to_row` (exclusive); the hand below stays bare."""
    (rarm, larm), _ = arm_boxes(slim)
    for arm, right in ((rarm, True), (larm, False)):
        for f in ("front", "back", "right", "left"):
            def a(x, y, w, h, f=f, right=right):
                if y >= to_row:
                    return None
                s = 3
                if f == "front" and (x, y) in ((1, 4), (2, 5)):
                    s = 2                                   # a crease at the elbow
                if cuff and y >= to_row - cuff[1]:
                    return (cuff[0], lit(f, 4 if y == to_row - cuff[1] else 3, right))
                if pattern:
                    s = pattern(x, y, s)
                return (cloth, lit(f, s, right))
            L.face(arm, f, a)
        L.face(arm, "top", lambda x, y, w, h: (cloth, 4))


def coat(L, slim, cloth, length, open_front, buttons=None, lapel=None, pattern=None, tails_open=True):
    """A coat: body, sleeves, and skirts down the upper legs (`length` rows of leg)."""
    trim = lapel or cloth
    def body(f):
        def fn(x, y, w, h):
            s = 3
            if pattern:
                s = pattern(x, y, s)
            if f == "front":
                if open_front and 3 <= x <= 4 and y <= 10:
                    return None                             # open: the waistcoat shows
                if x in (2, 5) and y <= 4:
                    return (trim, 4 if x == 2 else 3)       # lapels
                if y == 0 and (x in (1, 6)):
                    return (trim, 4)                        # collar
                if buttons and not open_front and x in (2, 5) and y in (5, 7, 9):
                    return (buttons, 4)
                if not open_front and x in (3, 4) and y <= 2:
                    return None                             # the neck
                if not open_front and x == 4 and y > 2:
                    s -= 1                                  # where the fronts overlap
                if y == 8:
                    s -= 1                                  # the waist seam
            if f == "back" and x in (3, 4) and y >= 8:
                s = 2 if x == 3 else s                      # the vent
            if f == "back" and y == 0:
                s += 1                                      # the collar at the back
            return (cloth, lit(f, s))
        return fn
    for f in ("front", "back", "right", "left"):
        L.face(BODY, f, body(f))
    L.face(BODY, "top", lambda x, y, w, h: None if 3 <= x <= 4 and y in (1, 2) else (cloth, 4))
    sleeves(L, slim, cloth, 10, cuff=(cloth, 2), pattern=pattern)
    # skirts over the upper legs
    for leg, right in ((RLEG, True), (LLEG, False)):
        for f in ("front", "back", "right", "left"):
            def sk(x, y, w, h, f=f, right=right):
                if y >= length:
                    return None
                inner = (x == w - 1) if right else (x == 0)
                if f == "front" and tails_open and inner:
                    return None                             # the coat parts at the front
                s = 3 if y < length - 1 else 2              # the hem
                if pattern:
                    s = pattern(x, y, s)
                if f == "back" and ((right and x == 0) or (not right and x == w - 1)):
                    s -= 1
                return (cloth, lit(f, s, right))
            L.face(leg, f, sk)


def waistcoat(L, cloth, back, pattern=None):
    def fn_front(x, y, w, h):
        if y <= 3 and 3 - y <= x - 2 <= y + 0 and False:
            return None
        if (y <= 1 and 2 <= x <= 5) or (y <= 3 and 3 <= x <= 4):
            return None                                     # the V of the neckline
        s = 3
        if pattern:
            s = pattern(x, y, s)
        if x in (3, 4) and y in (5, 7, 9):
            return (BRASS_R, 4 if x == 3 else 3)
        if y == 11 and x in (0, 7):
            return None                                     # the pointed hem
        if x in (0, 7):
            s -= 1
        return (cloth, s)
    L.face(BODY, "front", fn_front)
    L.face(BODY, "back", lambda x, y, w, h: (back, 2 if x in (3, 4) else 3))
    L.face(BODY, "right", lambda x, y, w, h: (cloth, 2))
    L.face(BODY, "left", lambda x, y, w, h: (cloth, 2))


def gloves(L, slim, leather, cuff_rows=3):
    (rarm, larm), _ = arm_boxes(slim)
    for arm, right in ((rarm, True), (larm, False)):
        for f in ("front", "back", "right", "left"):
            L.face(arm, f, lambda x, y, w, h, f=f, right=right: None if y < 12 - cuff_rows else
                   (leather, lit(f, 4 if y == 12 - cuff_rows else (2 if y == 11 else 3), right)))
        L.face(arm, "bottom", lambda x, y, w, h: (leather, 2))


def trousers(L, cloth, stripes=False, crease=True):
    for leg, right in ((RLEG, True), (LLEG, False)):
        for f in ("front", "back", "right", "left"):
            def t(x, y, w, h, f=f, right=right):
                if y >= 11:
                    return None
                s = 3
                if stripes and x % 2 == 1:
                    s = 4
                if crease and f == "front" and x == 1:
                    s = 4 if not stripes else 5
                if y == 10:
                    s -= 1
                return (cloth, lit(f, s, right))
            L.face(leg, f, t)
        L.face(leg, "top", lambda x, y, w, h: (cloth, 3))
    # the waistband, just under the waistcoat
    for f in ("front", "back", "right", "left"):
        L.face(BODY, f, lambda x, y, w, h, f=f: (cloth, lit(f, 2)) if y == 11 else None)


def shoes(L, leather, rows, shine=True, tall=False):
    for leg, right in ((RLEG, True), (LLEG, False)):
        for f in ("front", "back", "right", "left"):
            def sh(x, y, w, h, f=f, right=right):
                top = 12 - rows
                if y < top:
                    return None
                s = 3
                if y == top and tall:
                    s = 4                                   # the turned-down top of a riding boot
                if y == 11:
                    s = 1                                   # sole
                if shine and f == "front" and y == 10 and x == 1:
                    s = 5
                return (leather, lit(f, s, right))
            L.face(leg, f, sh)
        L.face(leg, "bottom", lambda x, y, w, h: (leather, 1))


BRASS_R = ramp(BRASS_C)


def tweed(x, y, s):
    return s + (1 if (x + 2 * y) % 5 == 0 else -1 if (2 * x + y) % 7 == 0 else 0)


def brocade(x, y, s):
    return s + (1 if (x + y) % 4 == 0 and y % 2 == 0 else 0)


# ------------------------------------------------------------------ hats (their own models)

TOP_HAT_CROWN, TOP_HAT_BRIM = Box(0, 0, 8, 9, 8), Box(0, 17, 12, 1, 12)
BOWLER_DOME, BOWLER_BRIM = Box(0, 0, 8, 4, 8), Box(0, 12, 10, 1, 10)
CAP_CROWN, CAP_PEAK = Box(0, 0, 8, 2, 8), Box(0, 10, 8, 1, 3)


def hat_top(L, felt, band):
    def crown(f):
        def fn(x, y, w, h):
            if y >= 7:
                return (band, lit(f, 3 if y == 7 else 2))
            s = 3
            if f == "front" and x == 1:
                s = 4                                       # sheen of silk felt
            return (felt, lit(f, s))
        return fn
    for f in ("front", "back", "right", "left"):
        L.face(TOP_HAT_CROWN, f, crown(f))
    L.face(TOP_HAT_CROWN, "top", lambda x, y, w, h: (felt, 4 if (x, y) in ((2, 2), (3, 2)) else 3))
    for f in ("top", "bottom", "front", "back", "right", "left"):
        L.face(TOP_HAT_BRIM, f, lambda x, y, w, h, f=f: (felt, 1 if f == "bottom" else 2 if f in ("top",) else 3))


def hat_bowler(L, felt, band):
    for f in ("front", "back", "right", "left"):
        L.face(BOWLER_DOME, f, lambda x, y, w, h, f=f: (band, lit(f, 3)) if y == 3 else
               (felt, lit(f, 4 if y == 0 else 3)))
    L.face(BOWLER_DOME, "top", lambda x, y, w, h: (felt, 4 if 2 <= x <= 5 and 2 <= y <= 5 else 3))
    for f in ("top", "bottom", "front", "back", "right", "left"):
        L.face(BOWLER_BRIM, f, lambda x, y, w, h, f=f: (felt, 1 if f == "bottom" else 3))


def hat_cap(L, cloth):
    for f in ("front", "back", "right", "left"):
        L.face(CAP_CROWN, f, lambda x, y, w, h, f=f: (cloth, lit(f, tweed(x, y, 3))))
    L.face(CAP_CROWN, "top", lambda x, y, w, h: (cloth, tweed(x, y, 4 if y < 5 else 3)))
    for f in ("top", "bottom", "front", "back", "right", "left"):
        L.face(CAP_PEAK, f, lambda x, y, w, h, f=f: (cloth, 2 if f == "bottom" else 3))


# ------------------------------------------------------------------ icons (32x32)

def icon_from_rows(name, rows, legend):
    """Draws a 16x16 design at 2x with its own shading, then outlines it."""
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    p = img.load()
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if ch in legend:
                rmp, s = legend[ch]
                for dy in range(2):
                    for dx in range(2):
                        ss = s + (1 if dy == 0 and dx == 0 and ch.isupper() else 0)
                        p[x * 2 + dx, y * 2 + dy] = rmp[max(0, min(5, ss))] + (255,)
    # a dark outline where the design meets transparency
    out = img.copy()
    op = out.load()
    for y in range(32):
        for x in range(32):
            if p[x, y][3] == 0:
                for nx, ny in ((x + 1, y), (x - 1, y), (x, y + 1), (x, y - 1)):
                    if 0 <= nx < 32 and 0 <= ny < 32 and p[nx, ny][3]:
                        op[x, y] = (24, 18, 26, 255)
                        break
    os.makedirs(ICONS, exist_ok=True)
    out.save(os.path.join(ICONS, name + ".png"))


ICON_SHAPES = {
    "top_hat": [
        "................",
        "....aaaaaaaa....",
        "....Aaaaaaa.....",
        "....Aaaaaaaa....",
        "....Aaaaaaaa....",
        "....Aaaaaaaa....",
        "....Aaaaaaaa....",
        "....bbbbbbbb....",
        "....bbbbbbbb....",
        "....Aaaaaaaa....",
        ".aaaaaaaaaaaaaa.",
        "..cccccccccccc..",
        "................",
        "................",
        "................",
        "................"],
    "bowler_hat": [
        "................",
        "................",
        "................",
        "................",
        "......aaaa......",
        "....Aaaaaaaa....",
        "...Aaaaaaaaaa...",
        "...Aaaaaaaaaa...",
        "...bbbbbbbbbb...",
        ".aaaaaaaaaaaaaa.",
        "..cccccccccccc..",
        "................",
        "................",
        "................",
        "................",
        "................"],
    "flat_cap": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "......aaaaaa....",
        "....Aaaaaaaaa...",
        "...Aaaaaaaaaaa..",
        "...aaaaaaaaaaa..",
        ".ccccccaaaaaaa..",
        "cccccc..........",
        "................",
        "................",
        "................",
        "................",
        "................"],
    "coat": [
        "....aa....aa....",
        "...aaAb..bAaa...",
        "..aaaAb..bAaaa..",
        ".aaaaAb..bAaaaa.",
        ".aa.aAbb.bAa.aa.",
        ".aa.aAa..aAa.aa.",
        ".aa.aAa..aAa.aa.",
        ".aa.aaaccaaa.aa.",
        ".dd.aAa..aAa.dd.",
        "....aAa..aAa....",
        "....aAa..aAa....",
        "....aaa..aaa....",
        "....aaa..aaa....",
        "....aaa..aaa....",
        "....ccc..ccc....",
        "................"],
    "jacket": [
        "................",
        "....aa....aa....",
        "...aaAb..bAaa...",
        "..aaaAb..bAaaa..",
        ".aaaaAbccbAaaaa.",
        ".aa.aAaccaAa.aa.",
        ".aa.aAa..aAa.aa.",
        ".aa.aaa..aaa.aa.",
        ".aa.aAacca.a.aa.",
        ".dd.aAa..aAa.dd.",
        "....aaa..aaa....",
        "....ccc..ccc....",
        "................",
        "................",
        "................",
        "................"],
    "waistcoat": [
        "................",
        "................",
        "....a......a....",
        "....aa....aa....",
        "...aAaa..aaAa...",
        "...aAaaccaaAa...",
        "...aAaa..aaAa...",
        "...aAaacca.Aa...",
        "...aAaa..aaAa...",
        "...aAaacca.Aa...",
        "...aAaa..aaAa...",
        "...aa.a..a.aa...",
        "................",
        "................",
        "................",
        "................"],
    "gloves": [
        "................",
        "................",
        "....a.a.a.......",
        "....a.a.a.a.....",
        "....aAaAaAa.....",
        "....aAaAaAa.....",
        "..a.aaaaaaa.....",
        "..aaaAaaaaa.....",
        "...aaAaaaaa.....",
        "....aaaaaa......",
        "....bbbbbb......",
        "....bbbbbb......",
        "................",
        "................",
        "................",
        "................"],
    "trousers": [
        "................",
        "....bbbbbbbb....",
        "....aaaaaaaa....",
        "....aAaaaAaa....",
        "....aAaaaAaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aAa..Aaa....",
        "....aaa..aaa....",
        "................",
        "................",
        "................"],
    "shoes": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "......aaa.......",
        "......aAa.......",
        "......aAaa......",
        "......aAaaaa....",
        "......aAaaaaaa..",
        "......cccccccc..",
        "................",
        "................",
        "................"],
    "boots": [
        "................",
        "................",
        "......bbbb......",
        "......aAaa......",
        "......aAaa......",
        "......aAaa......",
        "......aAaa......",
        "......aAaa......",
        "......aAaa......",
        "......aAaaa.....",
        "......aAaaaaa...",
        "......aAaaaaaa..",
        "......cccccccc..",
        "................",
        "................",
        "................"],
}


# ------------------------------------------------------------------ the wardrobe

def C(rgb):
    return ramp(rgb)


WARDROBE = []  # (id, slot, kind)


def make_all():
    os.makedirs(OUT, exist_ok=True)

    def body_item(name, slot, painter, icon, legend, arms=True):
        for slim in ((False, True) if arms else (False,)):
            L = Layer()
            painter(L, slim)
            L.img.save(os.path.join(OUT, name + ("_slim" if slim else "") + ".png"))
        icon_from_rows(name, ICON_SHAPES[icon], legend)
        WARDROBE.append((name, slot))

    def hat_item(name, painter, icon, legend):
        L = Layer(32)
        painter(L)
        L.img.save(os.path.join(OUT, name + ".png"))
        icon_from_rows(name, ICON_SHAPES[icon], legend)
        WARDROBE.append((name, "hat"))

    black, band_red, brown, tweed_grey = C((40, 36, 44)), C((92, 30, 42)), C((84, 58, 40)), C((112, 108, 96))
    hat_item("top_hat", lambda L: hat_top(L, black, band_red), "top_hat", {"a": (black, 3), "A": (black, 4), "b": (band_red, 3), "c": (black, 1)})
    hat_item("bowler_hat", lambda L: hat_bowler(L, brown, C((44, 32, 28))), "bowler_hat", {"a": (brown, 3), "A": (brown, 4), "b": (C((44, 32, 28)), 3), "c": (brown, 1)})
    hat_item("flat_cap", lambda L: hat_cap(L, tweed_grey), "flat_cap", {"a": (tweed_grey, 3), "A": (tweed_grey, 4), "c": (tweed_grey, 1)})

    frock, frock_trim = C((36, 34, 44)), C((24, 22, 30))
    body_item("frock_coat", "coat", lambda L, slim: coat(L, slim, frock, 7, True, lapel=frock_trim), "coat",
              {"a": (frock, 3), "A": (frock, 4), "b": (frock_trim, 3), "c": (frock, 1), "d": (C((228, 222, 210)), 4)})
    over = C((96, 66, 46))
    body_item("overcoat", "coat", lambda L, slim: coat(L, slim, over, 9, False, buttons=BRASS_R, tails_open=False), "coat",
              {"a": (over, 3), "A": (over, 4), "b": (over, 2), "c": (BRASS_R, 4), "d": (C((228, 222, 210)), 4)})
    tw = C((104, 100, 88))
    body_item("tweed_jacket", "coat", lambda L, slim: coat(L, slim, tw, 2, False, buttons=C((60, 44, 34)), pattern=tweed), "jacket",
              {"a": (tw, 3), "A": (tw, 4), "b": (tw, 2), "c": (C((60, 44, 34)), 3), "d": (C((228, 222, 210)), 4)})

    burg = C((116, 34, 50))
    body_item("burgundy_waistcoat", "shirt", lambda L, slim: waistcoat(L, burg, C((70, 30, 40))), "waistcoat",
              {"a": (burg, 3), "A": (burg, 4), "c": (BRASS_R, 4)}, arms=False)
    green = C((40, 86, 62))
    body_item("brocade_waistcoat", "shirt", lambda L, slim: waistcoat(L, green, C((30, 50, 40)), pattern=brocade), "waistcoat",
              {"a": (green, 3), "A": (green, 4), "c": (BRASS_R, 4)}, arms=False)

    lg, wg = C((44, 36, 34)), C((226, 222, 212))
    body_item("leather_gloves", "gloves", lambda L, slim: gloves(L, slim, lg), "gloves",
              {"a": (lg, 3), "A": (lg, 4), "b": (lg, 2)})
    body_item("white_gloves", "gloves", lambda L, slim: gloves(L, slim, wg), "gloves",
              {"a": (wg, 3), "A": (wg, 4), "b": (wg, 2)})

    pin, blk = C((62, 64, 74)), C((34, 34, 42))
    body_item("pinstripe_trousers", "trousers", lambda L, slim: trousers(L, pin, stripes=True), "trousers",
              {"a": (pin, 3), "A": (pin, 4), "b": (pin, 2)}, arms=False)
    body_item("black_trousers", "trousers", lambda L, slim: trousers(L, blk), "trousers",
              {"a": (blk, 3), "A": (blk, 4), "b": (blk, 2)}, arms=False)

    ox, rb = C((32, 28, 30)), C((96, 60, 38))
    body_item("oxford_shoes", "shoes", lambda L, slim: shoes(L, ox, 2), "shoes",
              {"a": (ox, 3), "A": (ox, 5), "c": (ox, 1)}, arms=False)
    body_item("riding_boots", "shoes", lambda L, slim: shoes(L, rb, 6, tall=True), "boots",
              {"a": (rb, 3), "A": (rb, 4), "b": (rb, 4), "c": (rb, 1)}, arms=False)


def preview(out_dir):
    """Every outfit piece on a plain character (uses tools/../skintest/view.py if present)."""
    os.makedirs(out_dir, exist_ok=True)
    from gen_skin_parts import compose
    base = compose((1, 1, 0, 0, 1, 0, 0, 0, 0)).convert("RGBA")
    outfits = [("top_hat", None), ("frock_coat", "burgundy_waistcoat", "pinstripe_trousers", "oxford_shoes", "leather_gloves"),
               ("overcoat", "black_trousers", "riding_boots", "white_gloves"),
               ("tweed_jacket", "brocade_waistcoat", "black_trousers", "oxford_shoes")]
    for i, outfit in enumerate(outfits):
        img = base.copy()
        for name in outfit:
            if name and name not in ("top_hat", "bowler_hat", "flat_cap"):
                img.alpha_composite(Image.open(os.path.join(OUT, name + ".png")).convert("RGBA"))
        img.save(os.path.join(out_dir, f"outfit{i}.png"))


if __name__ == "__main__":
    make_all()
    for name, slot in WARDROBE:
        print(slot, name)
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
