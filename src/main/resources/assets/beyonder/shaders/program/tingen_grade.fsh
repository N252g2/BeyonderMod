#version 150

// Beyonder: the Tingen look.
//  - Grade: muted Victorian palette, cool shadows, warm gaslight highlights, vignette, grain.
//  - Spirit: the Seer's Spirit Vision, a watercolor wash with ink edges and paper grain.

uniform sampler2D DiffuseSampler;

uniform vec2 InSize;
uniform float Strength;   // 0..1 color grade amount
uniform float Night;      // 0 day .. 1 midnight
uniform float Spirit;     // 0..1 Spirit Vision amount
uniform float Clock;      // seconds, for slow animation
uniform float Crimson;    // 0..1 crimson full-moon night
uniform float Rain;       // 0..1 rain
uniform float Glow;       // 1 = soft glow around lamps and flames

in vec2 texCoord;
in vec2 oneTexel;

out vec4 fragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += a * noise(p);
        p *= 2.03;
        a *= 0.5;
    }
    return v;
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

float aspectDistance(vec2 uv) {
    vec2 d = uv - 0.5;
    d.x *= InSize.x / InSize.y;
    return length(d);
}

vec3 grade(vec3 c, vec2 uv) {
    float l = luma(c);

    // Muted, murky Victorian palette: pull saturation down, more so at night.
    c = mix(vec3(l), c, mix(0.72, 0.55, Night));

    // Split tone: blue-grey shadows, warm gaslight highlights.
    vec3 shadowTint = vec3(0.82, 0.93, 1.08);
    vec3 highlightTint = vec3(1.10, 0.98, 0.82);
    c *= mix(shadowTint, highlightTint, smoothstep(0.25, 0.85, l));

    // A touch of murky green in the midtones.
    float mid = 1.0 - abs(l - 0.5) * 2.0;
    c = mix(c, c * vec3(0.95, 1.02, 0.94), 0.5 * mid);

    // Soft filmic contrast.
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.35);

    // Rain: greyer, darker, wetter.
    c = mix(c, vec3(luma(c)) * vec3(0.92, 0.96, 1.02), Rain * 0.35);
    c *= 1.0 - 0.12 * Rain;

    // Night: deeper blues, slightly lifted blacks so shapes still read in the fog.
    c = mix(c, c * vec3(0.82, 0.88, 1.08) + vec3(0.010, 0.012, 0.022), Night * 0.8);

    // Crimson full moon: everything takes on the moon's colour.
    c = mix(c, c * vec3(1.30, 0.55, 0.55) + vec3(0.035, 0.0, 0.004), Crimson * 0.75);

    // Vignette.
    c *= 1.0 - smoothstep(0.35, 1.05, aspectDistance(uv)) * mix(0.30, 0.45, max(Night, Crimson));

    // Fine film grain.
    c += (hash(uv * InSize + fract(Clock) * 37.0) - 0.5) * 0.025;

    return clamp(c, 0.0, 1.0);
}

vec3 watercolor(vec2 uv) {
    // Paint bleeds along slow-moving noise.
    vec2 wobble = vec2(fbm(uv * 6.0 + Clock * 0.05), fbm(uv * 6.0 + 17.3 - Clock * 0.04)) - 0.5;
    vec2 suv = uv + wobble * oneTexel * 7.0;

    // Soft pigment: a few blended taps.
    vec3 c = texture(DiffuseSampler, suv).rgb * 0.40;
    c += texture(DiffuseSampler, suv + vec2(oneTexel.x * 2.0, 0.0)).rgb * 0.15;
    c += texture(DiffuseSampler, suv - vec2(oneTexel.x * 2.0, 0.0)).rgb * 0.15;
    c += texture(DiffuseSampler, suv + vec2(0.0, oneTexel.y * 2.0)).rgb * 0.15;
    c += texture(DiffuseSampler, suv - vec2(0.0, oneTexel.y * 2.0)).rgb * 0.15;

    // Ink outlines from luminance edges.
    vec2 ox = vec2(oneTexel.x * 1.5, 0.0);
    vec2 oy = vec2(0.0, oneTexel.y * 1.5);
    float gx = luma(texture(DiffuseSampler, uv + ox).rgb) - luma(texture(DiffuseSampler, uv - ox).rgb);
    float gy = luma(texture(DiffuseSampler, uv + oy).rgb) - luma(texture(DiffuseSampler, uv - oy).rgb);
    float edge = clamp(length(vec2(gx, gy)) * 3.0, 0.0, 1.0);

    // Flatten into washes.
    float source = luma(c);
    float l = mix(source, floor(source * 5.0 + 0.5) / 5.0, 0.55);

    // Palette: indigo ink, teal, pale aqua, cream paper.
    vec3 p0 = vec3(0.10, 0.09, 0.25);
    vec3 p1 = vec3(0.16, 0.42, 0.52);
    vec3 p2 = vec3(0.62, 0.82, 0.80);
    vec3 p3 = vec3(0.96, 0.92, 0.82);
    vec3 pal;
    if (l < 0.33) {
        pal = mix(p0, p1, l / 0.33);
    } else if (l < 0.66) {
        pal = mix(p1, p2, (l - 0.33) / 0.33);
    } else {
        pal = mix(p2, p3, clamp((l - 0.66) / 0.34, 0.0, 1.0));
    }

    // Keep a hint of the real hue so lamps stay warm and blood stays red.
    vec3 hue = c / max(source, 0.001) * l;
    vec3 col = mix(pal, clamp(hue, 0.0, 1.0), 0.30);

    // Bright sources bloom gold.
    col += vec3(1.0, 0.75, 0.35) * smoothstep(0.75, 1.0, source) * 0.35;

    // Paper fibre and pigment pooling.
    float paper = fbm(uv * InSize / 3.0);
    float pool = fbm(uv * 4.0 + 3.1);
    col *= 0.88 + paper * 0.18;
    col = mix(col, col * 0.82, smoothstep(0.55, 0.80, pool) * 0.5);

    // Ink edges in deep indigo.
    col = mix(col, vec3(0.08, 0.06, 0.18), edge * 0.6);

    // The wash fades to bare paper at the frame.
    col = mix(col, p3, smoothstep(0.45, 1.0, aspectDistance(uv)) * 0.55);

    return clamp(col, 0.0, 1.0);
}

// Soft halation around bright lights: gaslight blooms warmly into the fog.
vec3 halation(vec2 uv) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 12; i++) {
        float a = float(i) * 0.5235988;
        vec2 dir = vec2(cos(a), sin(a));
        vec3 near = texture(DiffuseSampler, uv + dir * oneTexel * 5.0).rgb;
        vec3 far = texture(DiffuseSampler, uv + dir * oneTexel * 12.0).rgb;
        sum += max(near - 0.72, 0.0) * 0.6 + max(far - 0.72, 0.0) * 0.4;
    }
    return sum / 12.0 * vec3(1.0, 0.72, 0.45) * mix(1.4, 2.2, Night);
}

void main() {
    vec3 src = texture(DiffuseSampler, texCoord).rgb;
    if (Glow > 0.5 && Strength > 0.001) {
        src = min(src + halation(texCoord) * Strength, vec3(1.0));
    }
    vec3 col = mix(src, grade(src, texCoord), Strength);
    if (Spirit > 0.001) {
        col = mix(col, watercolor(texCoord), Spirit);
    }
    fragColor = vec4(col, 1.0);
}
