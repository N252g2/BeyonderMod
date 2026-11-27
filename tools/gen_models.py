"""Builds Beyonder's 3D item models (Blockbench-style element models) as JSON.

Run from the repo root:  python3 tools/gen_models.py
Model space is 16 units per block. Guns are built with the barrel pointing north (-Z) and the top
of the gun up (+Y), so first person needs no rotation: the in-hand placement is done in code
(GunClient), and third person uses the two-handed crossbow stance.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "beyonder")


def face_uvs(frm, to):
    x1, y1, z1 = frm
    x2, y2, z2 = to

    def fix(u):
        u = [max(0.0, min(16.0, v)) for v in u]
        if u[0] == u[2]:
            u[2] = min(16.0, u[0] + 0.5)
        if u[1] == u[3]:
            u[3] = min(16.0, u[1] + 0.5)
        return u
    return {
        "north": fix([16 - x2, 16 - y2, 16 - x1, 16 - y1]),
        "south": fix([x1, 16 - y2, x2, 16 - y1]),
        "east": fix([16 - z2, 16 - y2, 16 - z1, 16 - y1]),
        "west": fix([z1, 16 - y2, z2, 16 - y1]),
        "up": fix([x1, z1, x2, z2]),
        "down": fix([x1, 16 - z2, x2, 16 - z1]),
    }


def box(name, frm, to, tex, rotation=None, faces=None):
    uvs = face_uvs(frm, to)
    tex_for = (lambda f: faces.get(f, tex)) if faces else (lambda f: tex)
    el = {"name": name, "from": frm, "to": to,
          "faces": {f: {"uv": uv, "texture": tex_for(f)} for f, uv in uvs.items()}}
    if rotation:
        el["rotation"] = rotation
    return el


def revolver():
    S, D, W, C, B = "#steel", "#dark", "#wood", "#cylinder", "#brass"
    els = [
        # barrel and its top rib, front sight
        box("barrel", [7.25, 9.0, 0.5], [8.75, 10.5, 9.0], S, faces={"north": D}),
        box("bore", [7.6, 9.35, 0.4], [8.4, 10.15, 0.5], D),
        box("rib", [7.6, 10.5, 0.7], [8.4, 10.8, 9.0], S),
        box("front_sight", [7.8, 10.8, 0.8], [8.2, 11.25, 1.8], B),
        box("ejector_rod", [7.6, 8.3, 3.0], [8.4, 9.0, 9.0], D),
        box("ejector_head", [7.45, 8.2, 2.7], [8.55, 9.05, 3.3], S),
        # cylinder with its frame window
        box("cylinder", [6.4, 7.7, 9.0], [9.6, 10.9, 12.2], C, faces={"north": D, "south": D}),
        box("frame_top", [7.0, 10.9, 8.6], [9.0, 11.25, 13.2], S),
        # top-break latch: its two brass ears form the rear sight notch
        box("latch_l", [6.8, 10.9, 12.3], [7.65, 11.6, 13.4], B),
        box("latch_r", [8.35, 10.9, 12.3], [9.2, 11.6, 13.4], B),
        # brass bands at both ends of the cylinder
        box("band_front", [6.3, 7.6, 9.0], [9.7, 11.0, 9.35], B),
        box("band_back", [6.3, 7.6, 11.85], [9.7, 11.0, 12.2], B),
        box("barrel_lug", [7.1, 8.1, 7.4], [8.9, 9.1, 9.0], S),
        box("frame_bottom", [7.0, 7.2, 8.6], [9.0, 7.7, 12.6], S),
        box("frame_front", [7.0, 7.2, 8.4], [9.0, 10.9, 9.0], S),
        box("recoil_shield", [6.9, 7.2, 12.2], [9.1, 11.0, 13.6], S),
        box("backstrap", [7.2, 6.8, 13.5], [8.8, 10.7, 14.4], S),
        box("hammer", [7.55, 10.4, 13.4], [8.45, 11.1, 14.4], D),
        box("hammer_spur", [7.55, 10.7, 14.3], [8.45, 11.15, 15.2], D),
        # trigger and guard
        box("trigger", [7.75, 6.0, 11.0], [8.25, 7.2, 11.5], B),
        box("guard_front", [7.65, 5.6, 10.0], [8.35, 7.2, 10.5], S),
        box("guard_bottom", [7.65, 5.4, 10.0], [8.35, 5.9, 12.8], S),
        box("guard_back", [7.65, 5.4, 12.4], [8.35, 7.2, 12.9], S),
        # grip frame and walnut grips, raked back
        box("grip_frame", [7.3, 1.8, 12.4], [8.7, 7.4, 14.6], S,
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 7.4, 13.4]}),
        box("grip_wood", [6.8, 2.2, 12.6], [9.2, 7.2, 14.4], W,
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 7.4, 13.4]}),
        box("butt_cap", [7.1, 1.5, 12.5], [8.9, 2.1, 14.5], B,
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 7.4, 13.4]}),
        box("lanyard_ring", [7.75, 0.6, 13.2], [8.25, 1.5, 14.0], B,
            rotation={"angle": -22.5, "axis": "x", "origin": [8, 7.4, 13.4]}),
    ]
    # centre the gun front to back (it spans about z 0.4..15.2 as built)
    for el in els:
        el["from"][2] += 0.2
        el["to"][2] += 0.2
        if "rotation" in el:
            el["rotation"]["origin"][2] += 0.2
    display = {
        "firstperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        "firstperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
        # two-handed stance: the grip (about 3.5 below and 6 behind the centre) sits in the hand
        "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 2.2, -3.4], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 2.2, -3.4], "scale": [0.6, 0.6, 0.6]},
        "gui": {"rotation": [12, -90, 0], "translation": [1.2, 1.4, 0], "scale": [0.92, 0.92, 0.92]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, -90, 0], "translation": [0, 1, 0], "scale": [0.9, 0.9, 0.9]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 6, 0], "scale": [0.8, 0.8, 0.8]},
    }
    model = {
        "credit": "Beyonder: original model, generated by tools/gen_models.py",
        "gui_light": "side",
        "textures": {
            "particle": "beyonder:item/gun/steel",
            "steel": "beyonder:item/gun/steel",
            "dark": "beyonder:item/gun/dark",
            "wood": "beyonder:item/gun/walnut",
            "cylinder": "beyonder:item/gun/cylinder",
            "brass": "beyonder:item/gun/brass",
        },
        "elements": els,
        "display": display,
    }
    path = os.path.join(ROOT, "models", "item", "revolver.json")
    with open(path, "w") as f:
        json.dump(model, f, indent=1)
        f.write("\n")


if __name__ == "__main__":
    revolver()
    print("models written")
