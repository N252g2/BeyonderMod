#version 120
// Bloom: soft halos around lamps, flames and the moon.
#include "/lib/settings.glsl"

const bool colortex1MipmapEnabled = true;

uniform sampler2D colortex0;
uniform sampler2D colortex1;
uniform float viewWidth;
uniform float viewHeight;

varying vec2 texcoord;

void main() {
    vec3 color = texture2D(colortex0, texcoord).rgb;
    vec2 texel = 1.0 / vec2(viewWidth, viewHeight);
    vec3 bloom = vec3(0.0);
    float total = 0.0;
    for (int i = 2; i <= 6; i++) {
        float lod = float(i);
        float scale = exp2(lod);
        float weight = 1.0 / (1.0 + lod * 0.5);
        vec3 s = texture2D(colortex1, texcoord + vec2( 0.5,  0.5) * texel * scale, lod).rgb;
        s += texture2D(colortex1, texcoord + vec2(-0.5,  0.5) * texel * scale, lod).rgb;
        s += texture2D(colortex1, texcoord + vec2( 0.5, -0.5) * texel * scale, lod).rgb;
        s += texture2D(colortex1, texcoord + vec2(-0.5, -0.5) * texel * scale, lod).rgb;
        bloom += s * 0.25 * weight;
        total += weight;
    }
    color += bloom / total * BLOOM_STRENGTH * 2.0;

    /* DRAWBUFFERS:0 */
    gl_FragData[0] = vec4(color, 1.0);
}
