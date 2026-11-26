// Beyonder Shaders: options (shown in Iris > Shader Pack Settings)

const int shadowMapResolution = 2048; // [1024 2048 3072 4096]
const float shadowDistance = 96.0; // [64.0 96.0 128.0 160.0 192.0]
const float sunPathRotation = -25.0;
const float shadowDistanceRenderMul = 1.0;

#define FOG_DENSITY 1.0 // [0.0 0.5 0.75 1.0 1.25 1.5 2.0]
#define BLOOM_STRENGTH 0.6 // [0.0 0.2 0.4 0.6 0.8 1.0 1.5]
#define BLOOM_THRESHOLD 0.9 // [0.6 0.75 0.9 1.1 1.4]
#define EXPOSURE 1.0 // [0.6 0.8 1.0 1.2 1.4 1.7 2.0]
#define GASLIGHT_FLICKER 0.04 // [0.0 0.02 0.04 0.08]
#define GRADE_STRENGTH 1.0 // [0.0 0.25 0.5 0.75 1.0]
