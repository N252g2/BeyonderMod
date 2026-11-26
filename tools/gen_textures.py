"""Generates Beyonder's original block and sky textures.

Run from the repo root:  python3 tools/gen_textures.py
Everything here is drawn procedurally so the art stays ours and can be re-tuned.
"""
import math
import os
import random

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets")
BLOCK = os.path.join(ROOT, "beyonder", "textures", "block")
ENV = os.path.join(ROOT, "minecraft", "textures", "environment")
MISC = os.path.join(ROOT, "beyonder", "textures", "misc")


def clamp(v):
    return max(0, min(255, int(round(v))))


def rgb(c, k=1.0, add=0):
    return tuple(clamp(x * k + add) for x in c)


def save(img, folder, name):
    os.makedirs(folder, exist_ok=True)
    img.save(os.path.join(folder, name))


def cobbles():
    rnd = random.Random(11)
    img = Image.new("RGBA", (16, 16))
    mortar = (34, 36, 42)
    # Irregular stones laid out by hand on a 16x16 grid (x, y, w, h).
    stones = [(0, 0, 6, 5), (6, 0, 5, 4), (11, 0, 5, 5), (0, 5, 4, 5), (4, 5, 6, 5), (10, 5, 6, 4),
              (0, 10, 5, 6), (5, 10, 5, 6), (10, 9, 6, 4), (10, 13, 6, 3)]
    px = img.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = mortar + (255,)
    for (sx, sy, w, h) in stones:
        base = rnd.choice([(88, 92, 101), (80, 84, 94), (96, 98, 104), (74, 80, 88)])
        for y in range(sy + 1, sy + h):
            for x in range(sx + 1, sx + w):
                if x >= 16 or y >= 16:
                    continue
                # round the corners
                if (x == sx + 1 or x == sx + w - 1) and (y == sy + 1 or y == sy + h - 1):
                    continue
                shade = 1.0 + (rnd.random() - 0.5) * 0.12
                if y == sy + 1:
                    shade += 0.12  # wet top edge catching lamplight
                if y == sy + h - 1:
                    shade -= 0.15
                px[x, y] = rgb(base, shade) + (255,)
    save(img, BLOCK, "tingen_cobbles.png")


def soot_bricks():
    rnd = random.Random(23)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    mortar = (58, 52, 50)
    for y in range(16):
        row = y // 4
        offset = 4 if row % 2 else 0
        for x in range(16):
            if y % 4 == 3 or (x + offset) % 8 == 7:
                px[x, y] = mortar + (255,)
                continue
            brick = ((x + offset) // 8, row)
            r2 = random.Random(hash(brick) & 0xFFFF)
            base = r2.choice([(112, 58, 46), (98, 52, 44), (120, 66, 52), (90, 50, 46)])
            soot = 0.70 + 0.30 * (1 - (y % 4) / 3) * r2.random()  # soot pools under each course
            shade = soot * (1.0 + (rnd.random() - 0.5) * 0.10)
            px[x, y] = rgb(base, shade) + (255,)
    save(img, BLOCK, "soot_bricks.png")


def iron(name, seed, fluted=False):
    rnd = random.Random(seed)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    base = (40, 42, 48)
    for y in range(16):
        for x in range(16):
            shade = 1.0 + (rnd.random() - 0.5) * 0.18
            if fluted:
                shade += 0.25 if x % 4 == 1 else (-0.15 if x % 4 == 3 else 0.0)
            px[x, y] = rgb(base, shade) + (255,)
    save(img, BLOCK, name)


def lamp_glass():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    cx, cy = 7.5, 9.0
    for y in range(16):
        for x in range(16):
            # warm amber glass, brightest around the flame
            d = math.hypot((x - cx) / 1.0, (y - cy) / 1.6)
            glow = max(0.0, 1.0 - d / 9.0)
            r = 150 + 105 * glow
            g = 82 + 120 * glow ** 1.3
            b = 30 + 70 * glow ** 2.5
            # the flame itself
            if abs(x - cx) <= 1 and 7 <= y <= 11:
                r, g, b = 255, 236 - (11 - y) * 10, 170
            # soot at the top of the glass
            if y < 2:
                r, g, b = r * 0.55, g * 0.5, b * 0.5
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    save(img, BLOCK, "gas_lamp_glass.png")


def moon_phases():
    """Eight phases of the red moon, 4x2 grid of 32px cells (vanilla layout, full moon first)."""
    rnd = random.Random(7)
    cell = 32
    img = Image.new("RGBA", (cell * 4, cell * 2), (0, 0, 0, 255))
    px = img.load()
    radius = 11.0
    # Dark maria (seas) on the moon's face: (x, y, r) in moon-local units.
    maria = [(-3.5, -3.0, 3.6), (3.0, 1.5, 3.0), (-1.0, 4.5, 2.4), (4.5, -4.0, 1.8)]
    craters = [(rnd.uniform(-8, 8), rnd.uniform(-8, 8), rnd.uniform(0.8, 1.6)) for _ in range(9)]
    # Lit fraction per phase: 1, waning gibbous ... new ... waxing gibbous.
    phases = [(1.0, 0), (0.75, -1), (0.5, -1), (0.25, -1), (0.0, 0), (0.25, 1), (0.5, 1), (0.75, 1)]
    for i, (lit, side) in enumerate(phases):
        ox = (i % 4) * cell + cell / 2 - 0.5
        oy = (i // 4) * cell + cell / 2 - 0.5
        for y in range(cell):
            for x in range(cell):
                lx = x - cell / 2 + 0.5
                ly = y - cell / 2 + 0.5
                d = math.hypot(lx, ly)
                if d > radius + 0.5:
                    continue
                edge = max(0.0, min(1.0, radius + 0.5 - d))
                # terminator: lit if the point is on the bright side of an ellipse
                nx = lx / radius
                half = math.sqrt(max(0.0, 1 - (ly / radius) ** 2))
                if lit >= 1.0:
                    on = 1.0
                elif lit <= 0.0:
                    on = 0.0
                else:
                    t = (1 - 2 * lit) * half  # terminator x position
                    on = 1.0 if (nx * (side if side else 1)) > t / 1.0 else 0.0
                # surface colour: crimson with dark seas and pale crater rims
                r, g, b = 205, 42, 40
                for (mx, my, mr) in maria:
                    if math.hypot(lx - mx, ly - my) < mr:
                        r, g, b = 150, 24, 30
                for (cx_, cy_, cr) in craters:
                    dd = math.hypot(lx - cx_, ly - cy_)
                    if abs(dd - cr) < 0.5:
                        r, g, b = 235, 80, 70
                limb = 0.75 + 0.25 * math.sqrt(max(0.0, 1 - (d / radius) ** 2))
                k = edge * limb * (on if on else 0.06)  # the dark side keeps a faint ember
                px[int(ox - cell / 2 + 0.5) + x, int(oy - cell / 2 + 0.5) + y] = (clamp(r * k), clamp(g * k), clamp(b * k), 255)
    save(img, ENV, "moon_phases.png")


def value_noise(x, y, seed):
    def h(ix, iy):
        n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
        n = (n ^ (n >> 13)) * 1274126177 & 0xFFFFFFFF
        return (n ^ (n >> 16)) / 0xFFFFFFFF
    ix, iy = math.floor(x), math.floor(y)
    fx, fy = x - ix, y - iy
    fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
    a, b = h(ix, iy), h(ix + 1, iy)
    c, d = h(ix, iy + 1), h(ix + 1, iy + 1)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


def aura():
    """Soft halo, brightest near the body's edge, with wisps rising off the top. White; tinted in game."""
    size = 128
    img = Image.new("RGBA", (size, size))
    px = img.load()
    for y in range(size):
        for x in range(size):
            u = (x + 0.5) / size * 2 - 1
            v = (y + 0.5) / size * 2 - 1
            d = math.hypot(u, v * 1.05)
            # seamless wisps: sample noise on a circle, longer on the upper half (v < 0 is up)
            ca, sa = (u / d, v / d) if d > 1e-6 else (1.0, 0.0)
            wisp = value_noise(ca * 2.2 + 10, sa * 2.2 + d * 3.0, 5)
            up = max(0.0, -v)
            reach = 0.70 + 0.22 * wisp + 0.18 * up * wisp
            rim = math.exp(-((d - 0.42) ** 2) / 0.07)
            core = math.exp(-(d ** 2) / 0.30) * 0.30
            edge = max(0.0, min(1.0, (reach - d) / 0.35))
            a = min(1.0, rim * 0.75 + core) * edge
            px[x, y] = (255, 255, 255, clamp(a * 255))
    save(img, MISC, "aura.png")


def marker_beam():
    """A column of light: bright core, soft sides, fading out toward the top."""
    w, h = 32, 128
    img = Image.new("RGBA", (w, h))
    px = img.load()
    for y in range(h):
        top = (y + 0.5) / h  # 0 at top, 1 at bottom
        fade = top ** 0.7
        for x in range(w):
            u = (x + 0.5) / w * 2 - 1
            a = (math.exp(-(u ** 2) / 0.05) * 0.9 + math.exp(-(u ** 2) / 0.35) * 0.35) * fade
            px[x, y] = (255, 255, 255, clamp(a * 255))
    save(img, MISC, "marker_beam.png")


if __name__ == "__main__":
    cobbles()
    soot_bricks()
    iron("gas_lamp_iron.png", 31)
    iron("lamp_post.png", 41, fluted=True)
    lamp_glass()
    moon_phases()
    aura()
    marker_beam()
    print("textures written")
