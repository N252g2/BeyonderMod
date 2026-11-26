#version 120
// Rain and snow, kept faint.
#include "/lib/common.glsl"

uniform sampler2D gtexture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(c.rgb) * 0.5, c.a * 0.55);
}
