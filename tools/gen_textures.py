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
ITEM = os.path.join(ROOT, "beyonder", "textures", "item")


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


AURA_FRAMES = 16
BEAM_FRAMES = 16


def aura():
    """Animated halo: a flipbook of AURA_FRAMES frames stacked vertically (white; tinted in game).

    Wisps stream outward and upward from the body's edge. The loop is seamless: each frame
    cross-fades two noise fields offset by one full cycle.
    """
    size = 64
    img = Image.new("RGBA", (size, size * AURA_FRAMES))
    px = img.load()
    for f in range(AURA_FRAMES):
        t = f / AURA_FRAMES
        for y in range(size):
            for x in range(size):
                u = (x + 0.5) / size * 2 - 1
                v = (y + 0.5) / size * 2 - 1
                d = math.hypot(u, v * 1.05)
                ca, sa = (u / d, v / d) if d > 1e-6 else (1.0, 0.0)
                up = max(0.0, -v)  # v < 0 is up

                def wisps(phase):
                    # flow outward, and faster upward
                    flow = d * 3.0 - phase * (2.4 + 2.0 * up)
                    return value_noise(ca * 2.2 + 10, sa * 2.2 + flow, 5)

                wisp = (1 - t) * wisps(t) + t * wisps(t - 1)
                shimmer = (1 - t) * value_noise(ca * 2.5 + 3, sa * 2.5 + t * 3, 9) + t * value_noise(ca * 2.5 + 3, sa * 2.5 + (t - 1) * 3, 9)
                reach = 0.66 + 0.26 * wisp + 0.20 * up * wisp
                rim = math.exp(-((d - 0.42) ** 2) / 0.07) * (0.85 + 0.2 * shimmer)
                core = math.exp(-(d ** 2) / 0.30) * 0.30
                edge = max(0.0, min(1.0, (reach - d) / 0.35))
                a = min(1.0, rim * 0.75 + core) * edge
                px[x, y + f * size] = (255, 255, 255, clamp(a * 255))
    save(img, MISC, "aura.png")


def marker_beam():
    """Animated light column: bands of light rise through a soft beam that fades toward the top."""
    w, h = 32, 128
    img = Image.new("RGBA", (w, h * BEAM_FRAMES))
    px = img.load()
    for f in range(BEAM_FRAMES):
        t = f / BEAM_FRAMES
        for y in range(h):
            top = (y + 0.5) / h  # 0 at top, 1 at bottom
            fade = top ** 0.7
            band = 0.5 + 0.5 * math.sin((top * 4.0 + t) * 2 * math.pi)  # 4 bands, one cycle per loop
            for x in range(w):
                u = (x + 0.5) / w * 2 - 1
                core = math.exp(-(u ** 2) / 0.05) * (0.65 + 0.35 * band)
                glow = math.exp(-(u ** 2) / 0.35) * (0.25 + 0.2 * band)
                px[x, y + f * h] = (255, 255, 255, clamp((core + glow) * fade * 255))
    save(img, MISC, "marker_beam.png")


def potion(name, liquid, glow):
    """A squat apothecary bottle with a wax-sealed cork and a glowing liquid."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    glass = (190, 205, 215)
    edge = (70, 78, 92)
    # body: rows 6..14, rounded
    for y in range(5, 15):
        half = {5: 2, 6: 4, 7: 5, 8: 5, 9: 5, 10: 5, 11: 5, 12: 5, 13: 4, 14: 3}[y]
        for x in range(8 - half, 8 + half):
            border = x in (8 - half, 8 + half - 1) or y in (5, 14)
            if border:
                px[x, y] = edge + (255,)
            elif y >= 8:
                k = 0.75 + 0.35 * (1 - (y - 8) / 6)
                c = tuple(clamp(v * k) for v in liquid)
                if x == 8 - half + 1 and y < 12:
                    c = tuple(clamp(v * 0.6 + 255 * 0.4) for v in liquid)  # highlight
                px[x, y] = c + (255,)
            else:
                px[x, y] = glass + (110,)
    # neck and cork
    for y in (3, 4):
        for x in (7, 8):
            px[x, y] = edge + (255,)
    for x in (6, 7, 8, 9):
        px[x, 2] = (140, 34, 38, 255)   # wax seal
    for x in (7, 8):
        px[x, 1] = (110, 26, 30, 255)
    # a glint of the liquid's glow
    px[9, 10] = glow + (255,)
    px[7, 12] = glow + (255,)
    save(img, ITEM, name)


def pendulum():
    """A brass chain ending in a pointed crystal."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    brass, dark = (201, 160, 70), (120, 90, 40)
    for i, y in enumerate(range(1, 9)):
        x = 4 + i // 2
        px[x, y] = (brass if i % 2 == 0 else dark) + (255,)
    px[3, 1] = brass + (255,)
    px[2, 1] = dark + (255,)
    # crystal: diamond shape centred at (9, 11)
    crystal = [(9, 8), (8, 9), (9, 9), (10, 9), (7, 10), (8, 10), (9, 10), (10, 10), (11, 10),
               (8, 11), (9, 11), (10, 11), (8, 12), (9, 12), (10, 12), (9, 13), (9, 14)]
    for (x, y) in crystal:
        light = (x + (14 - y)) % 3
        c = [(150, 120, 235), (110, 85, 200), (190, 170, 250)][light]
        px[x, y] = c + (255,)
    px[9, 8] = brass + (255,)
    px[8, 9] = (225, 215, 255, 255)
    save(img, ITEM, "spirit_pendulum.png")


def sun():
    """A round, smog-hazed sun (vanilla's is square). Drawn additively, so black is transparent."""
    size = 32
    img = Image.new("RGBA", (size, size), (0, 0, 0, 255))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = math.hypot(x - 15.5, y - 15.5)
            core = max(0.0, min(1.0, 7.0 - d))           # crisp round disc
            halo = math.exp(-(d / 7.5) ** 2) * 0.55      # haze around it
            hazy = halo * (1 - core)
            r = 255 * core + 255 * hazy
            g = 236 * core + 214 * hazy
            b = 200 * core + 160 * hazy
            px[x, y] = (clamp(r), clamp(g), clamp(b), 255)
    save(img, ENV, "sun.png")


def lit_window():
    """A sash window with warm lamplight behind it."""
    rnd = random.Random(5)
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    frame, dark = (52, 38, 30), (34, 25, 20)
    for y in range(16):
        for x in range(16):
            on_frame = x in (0, 15) or y in (0, 15) or x in (7, 8) or y == 7
            if on_frame:
                px[x, y] = (dark if x in (0, 15) or y in (0, 15) else frame) + (255,)
                continue
            # lamplight: brighter low and centre, a curtain edge on the left pane
            glow = 0.75 + 0.25 * (y / 15) - 0.15 * abs(x - 7.5) / 7.5 + (rnd.random() - 0.5) * 0.06
            c = (255 * glow, 190 * glow, 95 * glow)
            if x in (1, 2) and y < 7:
                c = (150 * glow, 70 * glow, 50 * glow)  # curtain
            px[x, y] = tuple(clamp(v) for v in c) + (255,)
    save(img, BLOCK, "lit_window.png")


def chimney_top():
    img = Image.new("RGBA", (16, 16))
    px = img.load()
    for y in range(16):
        for x in range(16):
            rim = x < 3 or x > 12 or y < 3 or y > 12
            px[x, y] = ((96, 54, 44, 255) if rim else (20, 16, 16, 255))
    save(img, BLOCK, "chimney_top.png")


if __name__ == "__main__":
    cobbles()
    soot_bricks()
    iron("gas_lamp_iron.png", 31)
    iron("lamp_post.png", 41, fluted=True)
    lamp_glass()
    moon_phases()
    aura()
    marker_beam()
    potion("seer_potion.png", (88, 70, 200), (190, 170, 255))
    potion("clown_potion.png", (190, 40, 55), (255, 170, 120))
    potion("magician_potion.png", (40, 130, 150), (150, 240, 230))
    pendulum()
    sun()
    lit_window()
    chimney_top()
    print("textures written")
