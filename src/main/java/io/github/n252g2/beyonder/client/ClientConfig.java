package io.github.n252g2.beyonder.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-side visual settings (config/beyonder-client.toml). */
public final class ClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.comment("The look of Tingen: color grading, fog and night tint.").push("atmosphere");
    }

    public static final ModConfigSpec.BooleanValue BEYONDER_HUD = BUILDER
            .comment("Beyonder's hotbar, weapon and belt panels, and health, armour, hunger and air bars in place of vanilla's")
            .define("beyonderHud", true);

    public static final ModConfigSpec.BooleanValue COLOR_GRADING = BUILDER
            .comment("Apply the muted Victorian color grade to the world.")
            .define("colorGrading", true);

    public static final ModConfigSpec.DoubleValue GRADING_STRENGTH = BUILDER
            .comment("How strong the color grade is (0 = off, 1 = full).")
            .defineInRange("gradingStrength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue TINGEN_FOG = BUILDER
            .comment("Thicker, closer fog, heaviest at night.")
            .define("tingenFog", true);

    public static final ModConfigSpec.DoubleValue FOG_STRENGTH = BUILDER
            .comment("How thick the fog gets (0 = vanilla, 1 = full).")
            .defineInRange("fogStrength", 1.0, 0.0, 1.0);

    public static final ModConfigSpec.BooleanValue NIGHT_TINT = BUILDER
            .comment("Tint the night sky and fog toward the red moon.")
            .define("nightTint", true);

    public static final ModConfigSpec.BooleanValue CRIMSON_NIGHTS = BUILDER
            .comment("Full-moon nights turn crimson: redder light and fog, spores in the air.")
            .define("crimsonNights", true);

    public static final ModConfigSpec.BooleanValue AMBIENT_PARTICLES = BUILDER
            .comment("Drifting soot, ash and dust outdoors.")
            .define("ambientParticles", true);

    public static final ModConfigSpec.BooleanValue CHURCH_BELLS = BUILDER
            .comment("Distant church bells at dawn, dusk and midnight.")
            .define("churchBells", true);

    public static final ModConfigSpec.BooleanValue LAMP_GLOW = BUILDER
            .comment("Soft glow around bright lights (Lite; Beyonder Shaders has its own).")
            .define("lampGlow", true);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    static boolean bool(ModConfigSpec.BooleanValue value, boolean fallback) {
        try {
            return value.get();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }

    static float number(ModConfigSpec.DoubleValue value, float fallback) {
        try {
            return value.get().floatValue();
        } catch (IllegalStateException notLoadedYet) {
            return fallback;
        }
    }
}
