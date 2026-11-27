"""Write the canvas in the format CityData.java reads, and check every block name exists."""
import os
import re
import struct
import zlib
import numpy as np
from world import P, split

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", ".."))


def utf(s):
    b = s.encode("utf-8")
    return struct.pack(">H", len(b)) + b


def write(canvas, path, marks, sign_colors=None):
    a = canvas.a
    h, d, w = a.shape
    cx, cz = (w + 15) // 16, (d + 15) // 16
    out = bytearray()
    out += utf("TCITY1")
    out += struct.pack(">iiii", w, h, d, canvas.street)
    out += struct.pack(">i", len(P.states))
    for s in P.states:
        out += utf(s)
    out += struct.pack(">ii", cx, cz)
    table = bytearray()
    blob = bytearray()
    for j in range(cz):
        for i in range(cx):
            sub = np.zeros((h, 16, 16), np.uint16)
            part = a[:, j * 16:(j + 1) * 16, i * 16:(i + 1) * 16]
            sub[:, :part.shape[1], :part.shape[2]] = part
            nz = np.nonzero(sub.reshape(h, -1).any(axis=1))[0]
            layers = int(nz.max()) + 1 if len(nz) else 0
            raw = sub[:layers].astype(">u2").tobytes()
            comp = zlib.compress(raw, 9) if layers else b""
            table += struct.pack(">iih", len(blob), len(comp), layers)
            blob += comp
    out += table
    out += struct.pack(">i", len(blob))
    out += blob
    out += struct.pack(">i", len(canvas.signs))
    for x, y, z, lines, *rest in canvas.signs:
        color = rest[0] if rest else "black"
        out += struct.pack(">iii", x, y, z)
        lines = (list(lines) + ["", "", "", ""])[:4]
        for ln in lines:
            out += utf(ln[:30])
        out += utf(color)
    out += struct.pack(">i", len(marks))
    for k, (x, y, z, yaw) in marks.items():
        out += utf(k) + struct.pack(">iiii", x, y, z, yaw)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(out)
    return len(out)


def known_blocks():
    names = set()
    src = "/home/claude/mcsrc3/src/net/minecraft/world/level/block/Blocks.java"
    if os.path.exists(src):
        names |= {"minecraft:" + m for m in re.findall(r"\"([a-z0-9_]+)\"", open(src).read())}
    bs = os.path.join(REPO, "src", "main", "resources", "assets", "beyonder", "blockstates")
    names |= {"beyonder:" + f[:-5] for f in os.listdir(bs) if f.endswith(".json")}
    return names


def check_palette():
    known = known_blocks()
    bad = sorted({split(s)[0] for s in P.states if split(s)[0] not in known})
    return bad
