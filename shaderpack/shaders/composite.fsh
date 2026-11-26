#version 120
// Fog over Tingen, then the bright pass for bloom.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

/*
const int colortex0Format = RGBA16F;
const int colortex1Format = RGBA16F;
*/

uniform sampler2D colortex0;
uniform sampler2D depthtex0;
uniform mat4 gbufferProjectionInverse;
uniform vec3 fogColor;
uniform float fogStart;
uniform float fogEnd;
uniform float sunAngle;
uniform float rainStrength;
uniform int isEyeInWater;

varying vec2 texcoord;

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
    float depth = texture2D(depthtex0, texcoord).r;

    if (depth < 1.0) {
        vec4 viewPos = gbufferProjectionInverse * vec4(vec3(texcoord, depth) * 2.0 - 1.0, 1.0);
        float dist = length(viewPos.xyz / viewPos.w);
        float night = 1.0 - dayFactor(sunAngle);

        // The game's own fog distances (the Beyonder mod pulls them in at night)...
        float linearFog = clamp((dist - fogStart) / max(fogEnd - fogStart, 1.0), 0.0, 1.0);
        // ...plus a coal-smoke haze that thickens at night and in rain.
        float density = mix(0.004, 0.011, night) * (1.0 + rainStrength) * FOG_DENSITY;
        float haze = 1.0 - exp(-dist * density);
        float fog = 1.0 - (1.0 - linearFog * linearFog) * (1.0 - haze);

        vec3 fogLinear = toLinear(fogColor);
        if (isEyeInWater == 1) {
            fogLinear = vec3(0.010, 0.030, 0.034);
            fog = 1.0 - exp(-dist * 0.08);
        }
        color = mix(color, fogLinear, clamp(fog, 0.0, 1.0));
    }

    /* DRAWBUFFERS:01 */
    gl_FragData[0] = vec4(color, 1.0);
    gl_FragData[1] = vec4(max(color - vec3(BLOOM_THRESHOLD), vec3(0.0)), 1.0);
}
