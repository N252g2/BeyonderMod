#version 120
// Sun and moon. The red moon glows enough to feed the bloom.
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform int renderStage;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    vec3 col = toLinear(c.rgb);
    if (renderStage == MC_RENDER_STAGE_MOON) {
        col *= 3.0;
    } else {
        col *= 1.6;
    }
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(col, c.a);
}
