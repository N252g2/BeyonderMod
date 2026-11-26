#version 120
// Tone mapping and the Tingen color grade (same look as the Beyonder mod's own grade).
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler2D colortex0;
uniform float viewWidth;
uniform float viewHeight;
uniform float sunAngle;
uniform float frameTimeCounter;

varying vec2 texcoord;

vec3 tonemap(vec3 c) {
    // Filmic curve (ACES fit).
    return clamp((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0.0, 1.0);
}

vec3 grade(vec3 c, vec2 uv, float night) {
    float l = luma(c);
    c = mix(vec3(l), c, mix(0.72, 0.55, night));
    c *= mix(vec3(0.82, 0.93, 1.08), vec3(1.10, 0.98, 0.82), smoothstep(0.25, 0.85, l));
    float mid = 1.0 - abs(l - 0.5) * 2.0;
    c = mix(c, c * vec3(0.95, 1.02, 0.94), 0.5 * mid);
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.25);
    c = mix(c, c * vec3(0.82, 0.88, 1.08) + vec3(0.010, 0.012, 0.022), night * 0.8);
    vec2 d = uv - 0.5;
    d.x *= viewWidth / viewHeight;
    c *= 1.0 - smoothstep(0.35, 1.05, length(d)) * mix(0.30, 0.45, night);
    c += (hash12(uv * vec2(viewWidth, viewHeight) + fract(frameTimeCounter) * 37.0) - 0.5) * 0.02;
    return clamp(c, 0.0, 1.0);
}

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb * EXPOSURE;
    color = toGamma(tonemap(color));
    float night = 1.0 - dayFactor(sunAngle);
    color = mix(color, grade(color, texcoord, night), GRADE_STRENGTH);
    gl_FragColor = vec4(color, 1.0);
}
