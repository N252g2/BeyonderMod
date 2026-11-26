#version 120
// Enchantment glint.
#include "/lib/common.glsl"

uniform sampler2D gtexture;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(c.rgb) * 1.2, c.a);
}
