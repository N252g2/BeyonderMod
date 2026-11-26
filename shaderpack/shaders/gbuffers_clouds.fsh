#version 120
// Low, soot-grey clouds.
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform float sunAngle;
uniform float rainStrength;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    vec4 c = texture2D(gtexture, texcoord) * glcolor;
    if (c.a < 0.01) discard;
    float day = dayFactor(sunAngle);
    vec3 tint = mix(vec3(0.030, 0.022, 0.030), vec3(0.62, 0.64, 0.68), day) * (1.0 - rainStrength * 0.4);
    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(toLinear(vec3(luma(c.rgb))) * tint * 1.4, c.a * 0.85);
}
