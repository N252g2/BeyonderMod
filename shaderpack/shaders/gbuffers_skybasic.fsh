#version 120
// The sky dome: smoky grey-blue by day; by night, deep navy with a crimson glow on the horizon.
#include "/lib/common.glsl"

uniform int renderStage;
uniform mat4 gbufferProjectionInverse;
uniform mat4 gbufferModelViewInverse;
uniform vec3 skyColor;
uniform vec3 fogColor;
uniform float viewWidth;
uniform float viewHeight;
uniform float sunAngle;
uniform float rainStrength;
uniform int moonPhase;

varying vec2 texcoord;
varying vec4 glcolor;

void main() {
    /* DRAWBUFFERS:0 */
    if (renderStage == MC_RENDER_STAGE_STARS) {
        gl_FragData[0] = vec4(toLinear(glcolor.rgb) * 0.7, glcolor.a);
        return;
    }
    if (renderStage == MC_RENDER_STAGE_SUNSET) {
        // Muted, sooty sunsets.
        vec3 c = mix(vec3(luma(glcolor.rgb)), glcolor.rgb, 0.6);
        gl_FragData[0] = vec4(toLinear(c) * 0.7, glcolor.a * 0.8);
        return;
    }

    vec4 ndc = vec4(gl_FragCoord.xy / vec2(viewWidth, viewHeight) * 2.0 - 1.0, 1.0, 1.0);
    vec4 viewPos = gbufferProjectionInverse * ndc;
    vec3 dir = normalize(mat3(gbufferModelViewInverse) * normalize(viewPos.xyz / viewPos.w));
    float up = dir.y;

    float day = dayFactor(sunAngle);
    vec3 dayHorizon = toLinear(fogColor);
    vec3 dayZenith = toLinear(mix(skyColor, vec3(luma(skyColor)), 0.35)) * 0.85;
    vec3 nightHorizon = moonPhase == 0 ? vec3(0.110, 0.012, 0.016) : vec3(0.050, 0.016, 0.020);
    vec3 nightZenith = vec3(0.003, 0.004, 0.010);

    vec3 horizon = mix(nightHorizon, dayHorizon, day);
    vec3 zenith = mix(nightZenith, dayZenith, day);
    vec3 sky = mix(horizon, zenith, smoothstep(-0.02, 0.55, up));
    sky = mix(sky, vec3(luma(sky)), rainStrength * 0.6);
    sky *= mix(1.0, 0.35, smoothstep(0.0, -0.3, up)); // darker below the horizon

    gl_FragData[0] = vec4(sky, 1.0);
}
