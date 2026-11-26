"""Compile-checks the shader pack with glslangValidator the way Iris sees it.

Usage: python3 tools/check_shaders.py [path/to/glslangValidator]
"""
import os
import re
import subprocess
import sys
import tempfile

ROOT = os.path.join(os.path.dirname(__file__), "..", "shaderpack", "shaders")
VALIDATOR = sys.argv[1] if len(sys.argv) > 1 else "glslangValidator"
IRIS_MACROS = """
#define MC_RENDER_STAGE_NONE 0
#define MC_RENDER_STAGE_SKY 1
#define MC_RENDER_STAGE_SUNSET 2
#define MC_RENDER_STAGE_CUSTOM_SKY 3
#define MC_RENDER_STAGE_SUN 4
#define MC_RENDER_STAGE_MOON 5
#define MC_RENDER_STAGE_STARS 6
#define MC_RENDER_STAGE_VOID 7
"""


def expand(path, seen=()):
    out = []
    for line in open(path):
        m = re.match(r'\s*#include\s+"(/[^"]+)"', line)
        if m:
            inc = os.path.join(ROOT, m.group(1).lstrip("/"))
            out.append(expand(inc, seen + (path,)))
        else:
            out.append(line)
    return "".join(out)


failed = 0
for name in sorted(os.listdir(ROOT)):
    if not name.endswith((".vsh", ".fsh")):
        continue
    src = expand(os.path.join(ROOT, name))
    lines = src.split("\n")
    assert lines[0].startswith("#version")
    src = lines[0] + "\n" + IRIS_MACROS + "\n".join(lines[1:])
    stage = "vert" if name.endswith(".vsh") else "frag"
    with tempfile.NamedTemporaryFile("w", suffix="." + stage, delete=False) as f:
        f.write(src)
    r = subprocess.run([VALIDATOR, f.name], capture_output=True, text=True)
    os.unlink(f.name)
    if r.returncode != 0:
        failed += 1
        print(f"FAIL {name}\n{r.stdout}{r.stderr}")
print("all shaders compile" if not failed else f"{failed} shader(s) failed")
sys.exit(1 if failed else 0)
