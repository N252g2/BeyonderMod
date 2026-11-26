// Fragment stage for lit geometry: hazy sun, red moonlight, gaslight and shadows.
#include "/lib/settings.glsl"
#include "/lib/common.glsl"

uniform sampler2D gtexture;
uniform sampler2D shadowtex0;
uniform float sunAngle;
uniform float frameTimeCounter;
uniform float rainStrength;
uniform float nightVision;
#ifdef ENTITY
uniform vec4 entityColor;
#endif

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec4 shadowPos;
varying float emissive;

float shadowAt(vec4 sp) {
    vec3 p = distortShadow(sp.xyz) * 0.5 + 0.5;
    if (p.x <= 0.0 || p.x >= 1.0 || p.y <= 0.0 || p.y >= 1.0 || p.z >= 1.0) {
        return 1.0;
    }
    float texel = 1.0 / float(shadowMapResolution);
    float lit = 0.0;
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            float depth = texture2D(shadowtex0, p.xy + vec2(float(x), float(y)) * texel).r;
            lit += step(p.z - 0.0004, depth);
        }
    }
    return lit / 9.0;
}

void main() {
    vec4 albedo = texture2D(gtexture, texcoord) * glcolor;
#ifdef ENTITY
    albedo.rgb = mix(albedo.rgb, entityColor.rgb, entityColor.a);
#endif
#ifdef TRANSLUCENT
    if (albedo.a < 0.01) discard;
#else
    if (albedo.a < 0.1) discard;
#endif

    vec3 base = toLinear(albedo.rgb);
    float day = dayFactor(sunAngle);
    float sky = lmcoord.y;
    float block = lmcoord.x;

    // Tingen sun: soft and hazy. The moon: dim and red.
    vec3 sunColor = vec3(1.00, 0.90, 0.78) * 1.6;
    vec3 moonColor = vec3(0.60, 0.14, 0.13) * 0.45;
    vec3 directColor = mix(moonColor, sunColor, day) * (1.0 - rainStrength * 0.7);
    vec3 ambientColor = mix(vec3(0.030, 0.034, 0.060), vec3(0.42, 0.47, 0.56), day) * (1.0 - rainStrength * 0.3);

    float NdotL = max(shadowPos.w, 0.0);
    float shadow = 0.0;
#ifdef NO_SHADOW
    shadow = 1.0;
#else
    if (NdotL > 0.0) {
        shadow = shadowAt(shadowPos);
    }
#endif
    float skyMask = smoothstep(0.05, 0.40, sky);
    vec3 direct = directColor * NdotL * shadow * skyMask;
    vec3 ambient = ambientColor * sky * sky + vec3(0.018, 0.018, 0.024);

    // Gaslight: warm, close, with a faint breath.
    float t = frameTimeCounter;
    float flicker = 1.0 - GASLIGHT_FLICKER * (0.5 + 0.5 * sin(t * 11.0 + sin(t * 3.7) * 2.0));
    vec3 gas = vec3(1.00, 0.58, 0.26) * pow(block, 2.4) * 2.2 * flicker;

    vec3 light = ambient + gas + direct + vec3(nightVision * 0.5);
    vec3 color = base * light;

    // Lamp glass, flames and glowstone glow on their own.
    color += base * emissive * smoothstep(0.45, 0.9, luma(albedo.rgb)) * 6.0;

#ifdef WATER
    color = mix(color, vec3(0.010, 0.022, 0.024) + color * 0.4, 0.35);
    albedo.a = max(albedo.a, 0.55);
#endif

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, albedo.a);
}
