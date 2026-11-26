#version 120
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

attribute vec4 mc_Entity;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    glcolor = gl_Color;
    gl_Position = ftransform();
    gl_Position.xyz = distortShadow(gl_Position.xyz);
    // Water does not cast shadows.
    if (abs(mc_Entity.x - 10002.0) < 0.5) {
        gl_Position = vec4(10.0, 10.0, 10.0, 1.0);
    }
}
