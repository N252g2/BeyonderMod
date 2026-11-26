// Shared helpers.

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

vec3 toLinear(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(2.2));
}

vec3 toGamma(vec3 c) {
    return pow(max(c, vec3(0.0)), vec3(1.0 / 2.2));
}

// Shadow map distortion: more resolution near the player.
vec3 distortShadow(vec3 p) {
    float f = length(p.xy) * 0.85 + 0.15;
    return vec3(p.xy / f, p.z * 0.2);
}

// 0 at night, 1 in daylight (sunAngle: 0 sunrise, 0.25 noon, 0.5 sunset).
float dayFactor(float angle) {
    return smoothstep(-0.06, 0.12, sin(angle * 6.2831853));
}

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
