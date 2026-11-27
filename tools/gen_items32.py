"""Beyonder's item icons at 32x32: shaded pixel art drawn from shapes.

Run from the repo root:  python3 tools/gen_items32.py
Every icon is lit from the top left, shaded on a hand-picked colour ramp (dark → light), and gets a
coloured outline one step darker than its darkest shade, the way good item sprites are drawn.
All of it is original and generated here so it can be re-tuned.
"""
import math
import os
import random

from PIL import Image

ITEM = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "beyonder", "textures", "item")
N = 32
LIGHT = (-0.55, -0.65, 0.52)  # from the top left, towards the viewer
_l = math.sqrt(sum(c * c for c in LIGHT))
LIGHT = tuple(c / _l for c in LIGHT)


def hexc(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def ramp(*hexes):
    return [hexc(h) for h in hexes]


class Canvas:
    def __init__(self):
        self.px = [[None] * N for _ in range(N)]   # (r, g, b, a)
        self.outline_of = [[None] * N for _ in range(N)]  # outline colour for this pixel's layer

    def put(self, x, y, c, a=255, outline=None):
        if 0 <= x < N and 0 <= y < N:
            if a >= 255 or self.px[y][x] is None:
                self.px[y][x] = tuple(c) + (a,)
            else:
                r0, g0, b0, a0 = self.px[y][x]
                t = a / 255.0
                self.px[y][x] = (int(r0 + (c[0] - r0) * t), int(g0 + (c[1] - g0) * t), int(b0 + (c[2] - b0) * t), max(a0, a))
            if outline is not None:
                self.outline_of[y][x] = outline

    def glow(self, cx, cy, radius, c, strength=120):
        """A soft halo painted under everything (only into empty or translucent pixels)."""
        for y in range(N):
            for x in range(N):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if d < radius:
                    a = int(strength * (1 - d / radius) ** 2)
                    if a > 8 and (self.px[y][x] is None or self.px[y][x][3] < a):
                        self.px[y][x] = tuple(c) + (a,)

    def outline(self):
        src = [row[:] for row in self.px]
        for y in range(N):
            for x in range(N):
                cur = src[y][x]
                if cur is not None and cur[3] >= 200:
                    continue
                best = None
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < N and 0 <= ny < N and src[ny][nx] is not None and src[ny][nx][3] >= 200 \
                            and self.outline_of[ny][nx] is not None:
                        best = self.outline_of[ny][nx]
                        break
                if best is not None:
                    self.px[y][x] = tuple(best) + (255,)

    def save(self, name):
        img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
        p = img.load()
        for y in range(N):
            for x in range(N):
                if self.px[y][x] is not None:
                    p[x, y] = self.px[y][x]
        os.makedirs(ITEM, exist_ok=True)
        img.save(os.path.join(ITEM, name + ".png"))


def shade_index(normal, pal, ambient=0.28, spec=True, dither_xy=None):
    nx, ny, nz = normal
    lam = max(0.0, nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2])
    v = ambient + (1 - ambient) * lam
    if spec:
        # Blinn-ish highlight for the top-left glint
        hx, hy, hz = LIGHT[0], LIGHT[1], LIGHT[2] + 1
        hl = math.sqrt(hx * hx + hy * hy + hz * hz)
        sp = max(0.0, (nx * hx + ny * hy + nz * hz) / hl) ** 24
        v += sp * 0.8
    v = max(0.0, min(0.999, v))
    f = v * len(pal)
    i = int(f)
    if dither_xy is not None:
        x, y = dither_xy
        if (f - i) > 0.62 and (x + y) % 2 == 0 and i + 1 < len(pal):
            i += 1
    return min(len(pal) - 1, i)


def ellipsoid(cv, cx, cy, rx, ry, pal, outline, rz=None, ambient=0.28, spec=True, clip=None):
    rz = rz or min(rx, ry)
    for y in range(N):
        for x in range(N):
            dx = (x + 0.5 - cx) / rx
            dy = (y + 0.5 - cy) / ry
            d = dx * dx + dy * dy
            if d <= 1.0 and (clip is None or clip(x, y)):
                nz = math.sqrt(max(0.0, 1 - d))
                n = (dx, dy, nz)
                cv.put(x, y, pal[shade_index(n, pal, ambient, spec, (x, y))], outline=outline)


def polygon_fill(cv, pts, pal, outline, normal_fn=None):
    """Fills a polygon; normal_fn(x, y) gives the surface normal for shading."""
    for y in range(N):
        for x in range(N):
            if inside(pts, x + 0.5, y + 0.5):
                n = normal_fn(x, y) if normal_fn else (0, 0, 1)
                cv.put(x, y, pal[shade_index(n, pal, 0.3, False, (x, y))], outline=outline)


def inside(pts, x, y):
    c = False
    n = len(pts)
    for i in range(n):
        x1, y1 = pts[i]
        x2, y2 = pts[(i + 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1) + x1:
            c = not c
    return c


def line(cv, x0, y0, x1, y1, c, a=255, outline=None, width=1):
    steps = int(max(abs(x1 - x0), abs(y1 - y0)) * 2) + 1
    for i in range(steps + 1):
        t = i / steps
        x = x0 + (x1 - x0) * t
        y = y0 + (y1 - y0) * t
        for w in range(width):
            cv.put(int(round(x)) + (w if abs(y1 - y0) > abs(x1 - x0) else 0),
                   int(round(y)) + (w if abs(y1 - y0) <= abs(x1 - x0) else 0), c, a, outline)


def sparkle(cv, x, y, c, big=False):
    cv.put(x, y, c)
    for d in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        cv.put(x + d[0], y + d[1], c, 170)
    if big:
        for d in ((2, 0), (-2, 0), (0, 2), (0, -2)):
            cv.put(x + d[0], y + d[1], c, 90)


# ---------------------------------------------------------------- palettes

GREEN = ramp("1f3a2b", "2f5a3a", "467a4a", "6a9e5c", "9cc77a")
LAVENDER = ramp("3a2668", "5a3c9a", "7d5cc8", "a688e6", "d2c2fa")
MOSS = ramp("17322e", "24504a", "357066", "4f9484", "86c2ae")
ASH = ramp("3c3a44", "5a5864", "7c7a86", "a3a1ac", "cfcdd6")
SILK = ramp("1a1424", "2c2240", "43355e", "5f4c82", "8a76b0")
WOOD = ramp("3a2418", "5a3824", "7a5032", "9c6c44", "c08e5e")
SCLERA = ramp("6a6e5e", "9aa08a", "c4c9b0", "e2e6d0", "f8faf0")
IRIS = ramp("0e3a38", "17605a", "22887c", "3cb4a0", "8ae6d0")
GLASS = ramp("2e3a52", "4a5c7e", "7088ac", "a2b8d6", "e0ecfa")
CRIMSON = ramp("3a0a12", "6a1420", "a01e2e", "d23a44", "ff8a80")
PAPER = ramp("8a6a44", "b0905e", "d2b47e", "e8d2a2", "f6ead0")
COPPER = ramp("4a2416", "7a3c22", "a85a34", "d0804e", "f0b080")
SILVER = ramp("4a5060", "727a8c", "9ca4b4", "c8ced8", "f2f4f8")
GOLDR = ramp("5a3c10", "8a6018", "c08e28", "e8bc48", "fff0a0")
BRASS = ramp("4a3410", "7a5a20", "a88030", "d0a848", "f4dc90")
LEAD = ramp("2a2c34", "44464e", "60626c", "868894", "b4b6c0")
WAX = {
    "seer": ramp("1a2250", "263a80", "3858b0", "5a80d8", "9ab8f4"),
    "clown": ramp("3a0c10", "6a1418", "9c2228", "cc3c3c", "f07a70"),
    "magician": ramp("24103a", "3e1c64", "5c2c90", "8048bc", "b88ae6"),
}


def dark(c, k=0.55):
    return tuple(int(v * k) for v in c)


# ---------------------------------------------------------------- ingredients

def starlit_lavender():
    cv = Canvas()
    cv.glow(17, 9, 14, (150, 140, 255), 70)
    out_g = dark(GREEN[0], 0.7)
    # stems
    line(cv, 15, 30, 17, 12, GREEN[2], outline=out_g)
    line(cv, 16, 30, 18, 12, GREEN[1], outline=out_g)
    line(cv, 16, 24, 9, 15, GREEN[2], outline=out_g)
    line(cv, 17, 21, 24, 13, GREEN[2], outline=out_g)
    # leaves
    for (x, y, dx) in ((13, 26, -1), (19, 25, 1)):
        for i in range(5):
            cv.put(x + dx * i, y - i // 2, GREEN[3 if i < 2 else 2], outline=out_g)
            cv.put(x + dx * i, y - i // 2 + 1, GREEN[1], outline=out_g)
    # flower spikes: little florets stacked up each stem
    out_l = dark(LAVENDER[0], 0.8)
    for (sx, sy, ex, ey) in ((17, 13, 18, 3), (9, 16, 7, 8), (24, 14, 26, 6)):
        n = 6
        for i in range(n):
            t = i / (n - 1)
            x = sx + (ex - sx) * t
            y = sy + (ey - sy) * t
            r = 2.3 - t * 0.9
            ellipsoid(cv, x + (1.2 if i % 2 else -1.2) * (1 - t * 0.5), y, r, r * 0.9, LAVENDER, out_l)
    cv.outline()
    sparkle(cv, 21, 3, (235, 240, 255), big=True)
    sparkle(cv, 5, 6, (220, 228, 255))
    sparkle(cv, 28, 10, (220, 228, 255))
    cv.save("starlit_lavender")


def dream_moss():
    cv = Canvas()
    cv.glow(16, 18, 15, (110, 210, 200), 55)
    out = dark(MOSS[0], 0.7)
    rnd = random.Random(3)
    lumps = [(16, 19, 10, 7), (10, 17, 6, 5), (22, 16, 6, 5), (16, 13, 6, 4.5), (12, 22, 5, 4), (21, 22, 5, 4)]
    for (x, y, rx, ry) in lumps:
        ellipsoid(cv, x, y, rx, ry, MOSS, out, spec=False)
    # fronds: tiny lighter tufts on top
    for _ in range(26):
        x = rnd.randint(7, 25)
        y = rnd.randint(10, 24)
        if cv.px[y][x] is not None:
            cv.put(x, y - 1, MOSS[4] if rnd.random() < 0.4 else MOSS[3], outline=out)
    cv.outline()
    for (x, y) in ((7, 8), (24, 7), (16, 5), (27, 13)):
        cv.put(x, y, (190, 255, 240))
        cv.put(x + 1, y, (120, 220, 210), 140)
    cv.save("dream_moss")


def wraith_ash():
    cv = Canvas()
    out = dark(ASH[0], 0.7)
    # a shallow pewter dish
    for y in range(24, 29):
        half = 13 - (y - 24) * 1.4
        for x in range(N):
            if abs(x + 0.5 - 16) <= half:
                k = 3 if y == 24 else 2 if y < 27 else 1
                cv.put(x, y, LEAD[k], outline=dark(LEAD[0], 0.7))
    # the heap of ash: a cone, shaded as a dome
    for y in range(13, 25):
        for x in range(N):
            h = (25 - y) / 12.0
            half = 11 * (1 - h) + 1
            dx = (x + 0.5 - 16) / half
            if abs(dx) <= 1:
                n = (dx * 0.8, -0.6, 0.5)
                cv.put(x, y, ASH[shade_index(n, ASH, 0.35, False, (x, y))], outline=out)
    rnd = random.Random(9)
    for _ in range(10):  # cinders still glowing pale blue
        x, y = rnd.randint(9, 23), rnd.randint(16, 24)
        if cv.px[y][x] is not None:
            cv.put(x, y, (170, 220, 240))
    cv.outline()
    # a wisp of the restless dead curling up
    for i in range(26):
        t = i / 25
        x = 16 + math.sin(t * 7) * 5 * t
        y = 13 - t * 12
        cv.put(int(x), int(y), (200, 230, 245), int(200 * (1 - t) + 40))
        cv.put(int(x) + 1, int(y), (150, 190, 220), int(110 * (1 - t)))
    cv.save("wraith_ash")


def shadow_silk():
    cv = Canvas()
    outw = dark(WOOD[0], 0.7)
    outs = dark(SILK[0], 0.6)
    # spool flanges
    for y0 in (5, 25):
        ellipsoid(cv, 16, y0 + 1, 10, 2.3, WOOD, outw, spec=False)
    # the thread wound around the barrel
    for y in range(7, 26):
        for x in range(N):
            dx = (x + 0.5 - 16) / 8.5
            if abs(dx) <= 1:
                n = (dx, 0.0, math.sqrt(1 - dx * dx))
                idx = shade_index(n, SILK, 0.25, True, (x, y))
                if (y + (x // 3)) % 3 == 0:
                    idx = max(0, idx - 1)  # the winding
                cv.put(x, y, SILK[idx], outline=outs)
    ellipsoid(cv, 16, 6, 10, 2.3, WOOD, outw, spec=False)
    cv.outline()
    # a loose end trailing off
    for i in range(14):
        cv.put(26 + i // 4, 18 + i, SILK[3], outline=outs)
    for (x, y) in ((11, 12), (19, 17), (13, 21)):
        cv.put(x, y, (200, 180, 255))
    cv.save("shadow_silk")


def phantom_eye():
    cv = Canvas()
    cv.glow(16, 16, 16, (80, 220, 200), 50)
    ellipsoid(cv, 16, 16, 12.5, 11, SCLERA, dark(SCLERA[0], 0.5))
    # veins
    rnd = random.Random(5)
    for _ in range(6):
        a = rnd.random() * math.tau
        x, y = 16 + math.cos(a) * 11, 16 + math.sin(a) * 10
        for i in range(6):
            x += (16 - x) * 0.12 + rnd.uniform(-0.8, 0.8)
            y += (16 - y) * 0.12 + rnd.uniform(-0.8, 0.8)
            cv.put(int(x), int(y), (170, 90, 90), 150)
    # iris and a slit pupil
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - 17, y + 0.5 - 16)
            if d <= 6.5:
                ring = 1.0 - d / 6.5
                k = int(ring * 3.5) + (1 if (int(math.atan2(y - 16, x - 17) * 6) % 2 == 0) else 0)
                cv.put(x, y, IRIS[max(0, min(4, k))])
            if abs(x + 0.5 - 17) < 1.2 - abs(y + 0.5 - 16) * 0.06 and abs(y + 0.5 - 16) < 5.5:
                cv.put(x, y, (8, 14, 16))
    cv.put(13, 12, (255, 255, 255))
    cv.put(14, 12, (230, 255, 250))
    cv.put(13, 13, (210, 240, 235))
    cv.outline()
    cv.save("phantom_eye")


def mirror_shard():
    cv = Canvas()
    out = (30, 36, 54)
    pts = [(9, 3), (24, 8), (27, 15), (19, 30), (6, 21), (5, 11)]

    def normal(x, y):
        # facets: split the shard into a lit upper-left plane and a darker lower-right one
        return (-0.4, -0.6, 0.7) if x + y < 30 else (0.5, 0.4, 0.75)
    polygon_fill(cv, pts, GLASS, out, normal)
    # reflected sky streaks
    for i in range(9):
        cv.put(9 + i, 8 + i, GLASS[4])
        cv.put(10 + i, 8 + i, GLASS[3])
    for i in range(5):
        cv.put(17 + i, 21 + i // 2, GLASS[4], 200)
    # a reflection that is not yours: a violet eye in the glass
    ellipsoid(cv, 15, 18, 3.2, 1.8, ramp("3a1a5a", "5c2c90", "8a50c8", "b88ae6", "e0ccff"), out, spec=False)
    cv.put(15, 18, (20, 10, 30))
    cv.outline()
    sparkle(cv, 22, 9, (255, 255, 255))
    cv.save("mirror_shard")


def crimson_spore():
    cv = Canvas()
    cv.glow(16, 16, 16, (255, 60, 70), 120)
    ellipsoid(cv, 16, 16, 8, 8, CRIMSON, dark(CRIMSON[0], 0.6), ambient=0.35)
    # a lighter, pulsing core showing through the membrane
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - 15, y + 0.5 - 15)
            if d < 3.5:
                cv.put(x, y, (255, 170, 150), int(200 * (1 - d / 3.5)))
    rnd = random.Random(11)
    for _ in range(8):  # spines
        a = rnd.random() * math.tau
        for r in (8.5, 9.5, 10.5):
            cv.put(int(16 + math.cos(a) * r), int(16 + math.sin(a) * r), CRIMSON[2 if r < 10 else 3], int(255 - (r - 8) * 70))
    cv.outline()
    for (x, y) in ((5, 7), (27, 9), (25, 26), (7, 25)):
        cv.put(x, y, (255, 120, 110))
    cv.save("crimson_spore")


# ---------------------------------------------------------------- formulas

def formula(name, wax):
    cv = Canvas()
    out = dark(PAPER[0], 0.6)
    rnd = random.Random(len(name))
    # a sheet of parchment, its top edge curling over
    for y in range(4, 29):
        for x in range(6, 26):
            wave = math.sin(y * 0.6) * 0.06
            k = 2 + (1 if x < 12 else 0) - (1 if x > 22 else 0)
            if (x * 7 + y * 13) % 17 == 0:
                k -= 1  # foxing
            cv.put(x, y, PAPER[max(0, min(4, k + (1 if wave > 0.04 else 0)))], outline=out)
    for x in range(6, 26):  # the rolled top edge
        cv.put(x, 3, PAPER[4], outline=out)
        cv.put(x, 4, PAPER[3], outline=out)
        cv.put(x, 5, PAPER[1], outline=out)
    for y in range(25, 29):  # dog-eared bottom right corner
        for x in range(26 - (y - 24), 26):
            cv.px[y][x] = None
    for i in range(4):
        cv.put(22 + i, 28 - i, PAPER[0], outline=out)
    cv.outline()
    # lines of faded script
    ink = (64, 46, 40)
    for row, length in ((8, 15), (11, 13), (14, 16), (17, 10), (20, 12)):
        x = 8
        while x < 8 + length:
            w = rnd.randint(1, 3)
            for i in range(w):
                yy = row + (1 if rnd.random() < 0.25 else 0)
                cv.put(x + i, yy, ink, 210)
            x += w + 1
    # a little circle diagram, as alchemists draw
    for a in range(0, 360, 30):
        cv.put(int(21 + math.cos(math.radians(a)) * 2.5), int(18 + math.sin(math.radians(a)) * 2.5), (120, 40, 40), 200)
    # the wax seal, with a ribbon tail
    pal = WAX[name.split("_")[0]]
    ellipsoid(cv, 13, 25, 4.2, 3.8, pal, dark(pal[0], 0.6))
    for i in range(4):
        cv.put(11 - i // 2, 28 + i // 2, pal[1])
        cv.put(15 + i // 2, 28 + i // 2, pal[1])
    cv.put(13, 25, pal[0])
    cv.put(12, 24, pal[4])
    cv.save(name)


# ---------------------------------------------------------------- coins and rounds

def coin(name, pal, symbol):
    cv = Canvas()
    out = dark(pal[0], 0.6)
    # a coin seen at a slight tilt: the face, then the milled edge below it
    for y in range(N):
        for x in range(N):
            dx = (x + 0.5 - 16) / 12.5
            dy = (y + 0.5 - 17.5) / 10.5
            if dx * dx + dy * dy <= 1.0:
                cv.put(x, y, pal[1] if (x % 2) else pal[0], outline=out)  # edge
    for y in range(N):
        for x in range(N):
            dx = (x + 0.5 - 16) / 12.5
            dy = (y + 0.5 - 15.5) / 10.5
            d = dx * dx + dy * dy
            if d <= 1.0:
                rim = d > 0.72
                n = (dx * 0.5, dy * 0.5, 0.8) if not rim else (dx, dy, 0.4)
                idx = shade_index(n, pal, 0.35, True, (x, y))
                if rim:
                    idx = min(4, idx + 1)
                cv.put(x, y, pal[idx], outline=out)
    # embossed symbol: light on the upper-left edges, dark on the lower-right
    for (x, y) in symbol:
        cv.put(x, y, pal[4])
        cv.put(x + 1, y + 1, pal[0], 200)
    cv.outline()
    sparkle(cv, 10, 9, (255, 255, 255))
    cv.save(name)


def symbol_points(kind):
    pts = []
    if kind == "penny":       # a single bar and dot: one penny
        pts += [(15, y) for y in range(11, 20)] + [(16, y) for y in range(11, 20)]
    elif kind == "soli":      # a crescent moon
        for a in range(40, 330, 12):
            pts.append((int(16 + math.cos(math.radians(a)) * 5), int(15.5 + math.sin(math.radians(a)) * 5)))
            pts.append((int(16 + math.cos(math.radians(a)) * 4), int(15.5 + math.sin(math.radians(a)) * 4)))
    else:                     # a crown
        base = [(x, 19) for x in range(11, 22)] + [(x, 18) for x in range(11, 22)]
        spikes = [(11, 17), (11, 16), (11, 15), (13, 17), (14, 16), (16, 17), (16, 16), (16, 15), (16, 14),
                  (18, 16), (19, 17), (21, 17), (21, 16), (21, 15)]
        pts += base + spikes
    return pts


def round_icon(name, tip_pal):
    cv = Canvas()
    for (ox, oy, lean) in ((10, 9, 0.0), (21, 5, 0.0)):
        out = dark(BRASS[0], 0.6)
        # brass case: a vertical cylinder
        for y in range(oy + 8, oy + 22):
            for x in range(ox - 3, ox + 4):
                xx = x + lean * (y - oy)
                dx = (xx + 0.5 - ox) / 3.5
                if abs(dx) <= 1:
                    n = (dx, 0.0, math.sqrt(1 - dx * dx))
                    cv.put(int(round(xx)), y, BRASS[shade_index(n, BRASS, 0.3, True, (x, y))], outline=out)
        for x in range(ox - 4, ox + 5):  # the rim
            cv.put(x, oy + 22, BRASS[1], outline=out)
            cv.put(x, oy + 21, BRASS[2], outline=out)
        for x in range(ox - 3, ox + 4):  # the crimp where the bullet meets the case
            cv.put(x, oy + 8, BRASS[0], outline=out)
        # the bullet: a rounded nose
        outt = dark(tip_pal[0], 0.6)
        for y in range(oy, oy + 9):
            t = (y - oy) / 8.0
            half = 3.3 * math.sqrt(max(0.0, t)) + 0.4
            for x in range(ox - 4, ox + 5):
                xx = x + lean * (y - oy)
                dx = (xx + 0.5 - ox) / half
                if abs(dx) <= 1:
                    n = (dx, -0.7 * (1 - t), math.sqrt(max(0.05, 1 - dx * dx)))
                    cv.put(int(round(xx)), y, tip_pal[shade_index(n, tip_pal, 0.3, True, (x, y))], outline=outt)
    cv.outline()
    cv.save(name)


# ---------------------------------------------------------------- potions and the pendulum

def potion(name, liquid, shape):
    cv = Canvas()
    glow = liquid[3]
    cv.glow(16, 20, 15, glow, 60)
    outg = dark(GLASS[0], 0.6)
    if shape == "round":      # Seer: a round flask
        body = lambda x, y: ((x + 0.5 - 16) / 10.5) ** 2 + ((y + 0.5 - 20) / 9.5) ** 2 <= 1
        neck = (13, 19, 4, 11)
        surface = 17
    elif shape == "tall":     # Clown: a tall shouldered bottle
        def body(x, y):
            if 12 <= y <= 29:
                half = 8.5 if y > 15 else 8.5 - (15 - y) * 1.6
                return abs(x + 0.5 - 16) <= half
            return False
        neck = (13, 19, 3, 12)
        surface = 16
    else:                     # Magician: a faceted vial
        def body(x, y):
            if 10 <= y <= 29:
                half = 7.5 - max(0, y - 25) * 1.2
                return abs(x + 0.5 - 16) <= half
            return False
        neck = (13, 19, 3, 10)
        surface = 14
    # glass body with the liquid inside
    for y in range(N):
        for x in range(N):
            if body(x, y):
                dx = (x + 0.5 - 16) / 10
                n = (dx, 0.1, math.sqrt(max(0.0, 1 - dx * dx)))
                if y >= surface:
                    idx = shade_index(n, liquid, 0.4, False, (x, y))
                    cv.put(x, y, liquid[idx], outline=outg)
                else:
                    cv.put(x, y, GLASS[shade_index(n, GLASS, 0.4, False, (x, y))], 140, outline=outg)
    # neck
    x0, x1, y0, y1 = neck
    for y in range(y0, y1):
        for x in range(x0, x1):
            k = 3 if x == x0 + 1 else 1 if x == x1 - 1 else 2
            cv.put(x, y, GLASS[k], 170, outline=outg)
    # cork and wax
    for y in range(y0 - 3, y0 + 1):
        for x in range(x0 + 1, x1 - 1):
            cv.put(x, y, WOOD[3 if x == x0 + 1 else 2], outline=dark(WOOD[0], 0.6))
    wax = WAX["clown"]
    for x in range(x0, x1):
        cv.put(x, y0 + 1, wax[2], outline=dark(wax[0], 0.6))
        cv.put(x, y0 + 2, wax[1], outline=dark(wax[0], 0.6))
    cv.put(x1 - 2, y0 + 3, wax[1], outline=dark(wax[0], 0.6))
    cv.outline()
    # surface line, glints and floating motes
    for x in range(N):
        if cv.px[surface][x] is not None and body(x, surface):
            cv.put(x, surface, liquid[4], 200)
    for (gx, gy) in ((11, 21), (11, 22), (12, 20), (11, 23)):
        if body(gx, gy):
            cv.put(gx, gy, (255, 255, 255), 220)
    rnd = random.Random(len(name) * 7)
    for _ in range(5):
        x, y = rnd.randint(12, 20), rnd.randint(surface + 2, 27)
        if body(x, y):
            cv.put(x, y, liquid[4])
    cv.save(name)


def pendulum():
    cv = Canvas()
    cv.glow(19, 23, 12, (160, 200, 255), 80)
    out = dark(BRASS[0], 0.6)
    # chain links
    for i in range(6):
        x, y = 7 + i * 1.9, 3 + i * 2.0
        if i % 2 == 0:
            ellipsoid(cv, x, y, 1.6, 1.1, BRASS, out, spec=False)
        else:
            ellipsoid(cv, x, y, 0.9, 1.5, BRASS, out, spec=False)
    ellipsoid(cv, 5, 2, 2.2, 2.2, BRASS, out)  # the ring you hold
    ellipsoid(cv, 19, 15.5, 3.0, 1.8, BRASS, out)  # cap
    # crystal: two facets, pointed tip
    crystal = ramp("2a3a6a", "3e5c9c", "6a8ccc", "a2c2f0", "e8f4ff")
    left = [(14, 17), (19, 17), (19, 31)]
    right = [(19, 17), (24, 17), (19, 31)]
    polygon_fill(cv, left, crystal, dark(crystal[0], 0.6), lambda x, y: (-0.6, -0.4, 0.7))
    polygon_fill(cv, right, crystal, dark(crystal[0], 0.6), lambda x, y: (0.6, 0.2, 0.7))
    cv.outline()
    sparkle(cv, 17, 19, (255, 255, 255))
    cv.save("spirit_pendulum")


def main():
    starlit_lavender()
    dream_moss()
    wraith_ash()
    shadow_silk()
    phantom_eye()
    mirror_shard()
    crimson_spore()
    for f in ("seer_formula", "clown_formula", "magician_formula"):
        formula(f, None)
    coin("copper_penny", COPPER, symbol_points("penny"))
    coin("silver_soli", SILVER, symbol_points("soli"))
    coin("gold_pound", GOLDR, symbol_points("pound"))
    round_icon("revolver_round", LEAD)
    round_icon("silver_round", ramp("6a7488", "98a2b4", "c4ccd8", "e6eaf2", "ffffff"))
    potion("seer_potion", ramp("1c2a6a", "2e4aa0", "4a70d0", "7aa0f0", "c0d8ff"), "round")
    potion("clown_potion", ramp("4a0c14", "7c1822", "b02a32", "e04a48", "ff9a88"), "tall")
    potion("magician_potion", ramp("2a0e4a", "481e7a", "6c34b0", "9a5ad8", "dcb8ff"), "facet")
    pendulum()
    print("32px item icons written")


if __name__ == "__main__":
    main()
