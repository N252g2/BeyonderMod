// Vertex stage for lit geometry (terrain, entities, hand, particles, water).
#include "/lib/common.glsl"

#ifdef TERRAIN
attribute vec4 mc_Entity;
#endif

uniform mat4 gbufferModelViewInverse;
uniform mat4 shadowModelView;
uniform mat4 shadowProjection;
uniform vec3 shadowLightPosition;

varying vec2 texcoord;
varying vec2 lmcoord;
varying vec4 glcolor;
varying vec4 shadowPos;   // xyz: shadow clip position, w: N.L
varying float emissive;

void main() {
    texcoord = (gl_TextureMatrix[0] * gl_MultiTexCoord0).xy;
    lmcoord = (gl_TextureMatrix[1] * gl_MultiTexCoord1).xy;
    lmcoord = clamp(lmcoord * (33.05 / 32.0) - (1.05 / 32.0), 0.0, 1.0);
    glcolor = gl_Color;

    vec4 viewPos = gl_ModelViewMatrix * gl_Vertex;
    vec3 normal = normalize(gl_NormalMatrix * gl_Normal);
    vec3 lightDir = normalize(shadowLightPosition);

#ifdef FLAT_LIGHT
    float NdotL = 0.65;
#else
    float NdotL = dot(normal, lightDir);
#endif

    // Normal-offset bias keeps surfaces from shadowing themselves.
    vec4 playerPos = gbufferModelViewInverse * viewPos;
    vec3 worldNormal = mat3(gbufferModelViewInverse) * normal;
    float dist = length(playerPos.xyz);
    playerPos.xyz += worldNormal * (0.04 + dist * 0.003);
    shadowPos = shadowProjection * (shadowModelView * playerPos);
    shadowPos.w = NdotL;

    emissive = 0.0;
#ifdef TERRAIN
    if (abs(mc_Entity.x - 10001.0) < 0.5) {
        emissive = 1.0;
    }
#endif

    gl_Position = gl_ProjectionMatrix * viewPos;
}
