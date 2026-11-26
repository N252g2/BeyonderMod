#version 120
// Lines and untextured shapes (block outline, leash).
#include "/lib/common.glsl"

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(glcolor.rgb), glcolor.a);
}
