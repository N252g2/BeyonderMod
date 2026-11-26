package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.net.BeyonderSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;

/**
 * How a living thing looks to Spirit Vision: how strong its spirit is, and what colour that strength takes.
 *
 * <p>Strength is a log scale: 0 is an ordinary person or zombie, negative is small game, 2-4 is a golem or
 * a beast of note, 5 and up is something you should not be looking at. Once pathways exist, a Beyonder's
 * Sequence will feed in here and dominate the score.</p>
 */
public final class SpiritAura {
    /** Strength thresholds used for colour and behaviour. */
    public static final float FAINT = -1.0F;
    public static final float NOTABLE = 1.5F;
    public static final float DANGEROUS = 4.0F;

    // Colour ramp from faint to overwhelming.
    private static final float[][] RAMP = {
            {-3.0F, 0.72F, 0.78F, 0.90F}, // pale, barely there
            {0.0F, 0.42F, 0.86F, 0.78F},  // living teal
            {2.5F, 0.98F, 0.78F, 0.34F},  // gold
            {5.0F, 0.90F, 0.16F, 0.24F},  // crimson
            {7.5F, 0.58F, 0.22F, 0.92F},  // violet: beyond the ordinary
    };

    private SpiritAura() {}

    public static float strength(LivingEntity entity) {
        double health = Math.max(entity.getMaxHealth(), 1.0F);
        double armor = entity.getArmorValue();
        double attack = entity.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                ? entity.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : 0.0;
        double power = (health / 20.0) * (1.0 + armor / 20.0) * (1.0 + attack / 10.0);
        float score = (float) (Math.log(Math.max(power, 0.05)) / Math.log(2.0));
        if (entity.getType().is(Tags.EntityTypes.BOSSES)) {
            score += 2.0F;
        }
        // Beyonders: every Sequence climbed makes the spirit blaze brighter.
        BeyonderSyncPayload beyonder = ClientInbox.get(entity.getId());
        if (beyonder != null && !"none".equals(beyonder.pathway())) {
            score += (10 - beyonder.sequence()) * 1.2F;
        }
        return score;
    }

    /** RGB colour for a strength, nudged toward red when the creature is hostile. */
    public static float[] color(LivingEntity entity, float strength) {
        float[] c = ramp(strength);
        if (entity instanceof Enemy) {
            c[0] = Mth.lerp(0.30F, c[0], 0.95F);
            c[1] = Mth.lerp(0.30F, c[1], 0.20F);
            c[2] = Mth.lerp(0.30F, c[2], 0.22F);
        } else if (entity instanceof Player) {
            c[2] = Mth.lerp(0.25F, c[2], 1.0F);
        }
        return c;
    }

    public static float[] ramp(float strength) {
        if (strength <= RAMP[0][0]) {
            return new float[] {RAMP[0][1], RAMP[0][2], RAMP[0][3]};
        }
        for (int i = 1; i < RAMP.length; i++) {
            if (strength <= RAMP[i][0]) {
                float t = (strength - RAMP[i - 1][0]) / (RAMP[i][0] - RAMP[i - 1][0]);
                return new float[] {
                        Mth.lerp(t, RAMP[i - 1][1], RAMP[i][1]),
                        Mth.lerp(t, RAMP[i - 1][2], RAMP[i][2]),
                        Mth.lerp(t, RAMP[i - 1][3], RAMP[i][3])};
            }
        }
        float[] last = RAMP[RAMP.length - 1];
        return new float[] {last[1], last[2], last[3]};
    }

    /** Halo size multiplier: stronger spirits spill further out. */
    public static float sizeScale(float strength) {
        return 1.0F + 0.12F * Mth.clamp(strength + 1.0F, 0.0F, 8.0F);
    }

    /** Base opacity: faint for weak things, bolder for strong ones. */
    public static float opacity(float strength) {
        return Mth.clamp(0.16F + 0.05F * strength, 0.08F, 0.55F);
    }

    /** Pulse speed in radians per second: strong spirits throb faster. */
    public static float pulseSpeed(float strength) {
        return 1.2F + 0.45F * Mth.clamp(strength, 0.0F, 8.0F);
    }
}
