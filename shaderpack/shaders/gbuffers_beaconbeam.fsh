#version 120
// Things that glow in the dark.
#include "/lib/common.glsl"

uniform sampler2D gtexture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    if (c.a < 0.01) discard;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(c.rgb) * 2.5, c.a);
}
