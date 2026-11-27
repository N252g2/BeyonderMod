package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Equipment;
import io.github.n252g2.beyonder.core.Equipment.Kind;
import io.github.n252g2.beyonder.core.PlayerAnim;
import io.github.n252g2.beyonder.net.ClientInbox;
import io.github.n252g2.beyonder.net.EquipStatePayload;
import io.github.n252g2.beyonder.net.PlayerAnimPayload;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Procedural body animation for players: a combat stance while weapons are drawn, three-step attack
 * combos per weapon type, drawing and sheathing, gun handling (recoil, reload) and ability casting.
 *
 * <p>Poses are arm rotations (radians) blended over vanilla's pose after it is set up each frame,
 * so walking, sneaking and looking still come through. Other players' animations arrive from the
 * server; this player's own start locally the moment the key is pressed.</p>
 */
public final class PlayerAnimator {
    /** A running animation: what, its variant, and when it started (in the entity's tick count). */
    private record Active(PlayerAnim anim, int variant, float start, int ticks) {
        float progress(float now) {
            return Mth.clamp((now - start) / ticks, 0.0F, 1.0F);
        }
    }

    private static final Map<Integer, Active> ACTIVE = new HashMap<>();
    private static final Map<Integer, int[]> COMBO = new HashMap<>(); // entity id -> {step, tick of last swing}

    /** Six angles: right arm x, y, z, then left arm x, y, z. NaN leaves that angle alone. */
    private static final float N = Float.NaN;

    private PlayerAnimator() {}

    // ------------------------------------------------------------------ bookkeeping

    public static void start(Entity player, PlayerAnim anim, int variant) {
        ACTIVE.put(player.getId(), new Active(anim, variant, player.tickCount, anim.ticks(variant)));
    }

    /** Progress 0-1 of a running animation of this kind, or -1. */
    public static float progress(Entity player, PlayerAnim anim, float partialTick) {
        Active a = ACTIVE.get(player.getId());
        if (a == null || a.anim != anim) {
            return -1.0F;
        }
        float p = a.progress(player.tickCount + partialTick);
        return p >= 1.0F ? -1.0F : p;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            COMBO.clear();
            return;
        }
        PlayerAnimPayload p;
        while ((p = ClientInbox.pollAnim()) != null) {
            Entity e = mc.level.getEntity(p.entityId());
            PlayerAnim anim = PlayerAnim.byId(p.anim());
            if (e != null && anim != null) {
                start(e, anim, p.variant());
            }
        }
        // A swing that starts soon after the last one is the next step of the combo.
        for (Player player : mc.level.players()) {
            if (player.swinging && player.swingTime == 0) {
                int[] c = COMBO.computeIfAbsent(player.getId(), k -> new int[] {2, -100});
                c[0] = player.tickCount - c[1] < 22 ? (c[0] + 1) % 3 : 0;
                c[1] = player.tickCount;
            }
        }
        ACTIVE.entrySet().removeIf(en -> {
            Entity e = mc.level.getEntity(en.getKey());
            return e == null || e.tickCount - en.getValue().start > en.getValue().ticks + 2;
        });
    }

    // ------------------------------------------------------------------ what a player has drawn

    static boolean drawn(Player player) {
        if (player.isLocalPlayer()) {
            return Equipment.isDrawn(player);
        }
        EquipStatePayload s = ClientInbox.equipment(player.getId());
        return s != null && s.drawn();
    }

    /** The main weapon, whether drawn or on the body. */
    static ItemStack weapon(Player player) {
        if (player.isLocalPlayer()) {
            return Equipment.item(player, Equipment.Slot.WEAPON);
        }
        EquipStatePayload s = ClientInbox.equipment(player.getId());
        return s == null ? ItemStack.EMPTY : s.item(EquipStatePayload.WEAPON);
    }

    // ------------------------------------------------------------------ posing (called from the model)

    public static void pose(HumanoidModel<?> model, LivingEntity entity, float limbSwing, float limbAmount, float ageInTicks) {
        if (!(entity instanceof AbstractClientPlayer player) || ageInTicks == 0.0F) {
            return; // 0 = the first-person arm, which poses itself
        }
        if (player.isVisuallySwimming() || player.isFallFlying() || player.isSleeping() || player.isPassenger()) {
            return;
        }
        float now = ageInTicks;
        Active active = ACTIVE.get(player.getId());
        float p = active == null ? 1.0F : active.progress(now);
        if (active != null && p >= 1.0F) {
            active = null;
        }
        boolean drawn = drawn(player);
        Kind kind = Kind.of(drawn ? player.getMainHandItem() : weapon(player));
        boolean using = player.isUsingItem();

        // 1. the stance while drawn (arms keep a little of the walk's swing)
        if (drawn && !using) {
            float w = player.isSprinting() ? 0.45F : 1.0F;
            if (active != null && active.anim == PlayerAnim.DRAW) {
                w *= smooth(Mth.clamp((p - 0.55F) / 0.45F, 0.0F, 1.0F));
            }
            float sway = Mth.cos(limbSwing * 0.6662F) * 0.12F * limbAmount;
            float[] stance = stance(kind, player.getOffhandItem());
            stance[0] += sway;
            stance[3] -= sway;
            blend(model, stance, w);
        }

        // 2. attack combos with melee weapons (and fists) while drawn
        if (drawn && model.attackTime > 0.0F && kind != Kind.GUN && kind != Kind.BOW && !using) {
            int[] c = COMBO.get(player.getId());
            int step = c == null ? 0 : c[0];
            float[] swing = attack(kind, step, model.attackTime, stance(kind, player.getOffhandItem()));
            blend(model, swing, 1.0F);
        }

        // 3. the running animation on top
        if (active != null) {
            float env = envelope(active.anim, p);
            float[] target = switch (active.anim) {
                case DRAW -> draw(Kind.values()[Mth.clamp(active.variant, 0, Kind.values().length - 1)], p,
                        stance(Kind.values()[Mth.clamp(active.variant, 0, Kind.values().length - 1)], player.getOffhandItem()));
                case SHEATHE -> draw(Kind.values()[Mth.clamp(active.variant, 0, Kind.values().length - 1)], 1.0F - p,
                        stance(Kind.values()[Mth.clamp(active.variant, 0, Kind.values().length - 1)], player.getOffhandItem()));
                case SWAP -> keys(p, new float[][] {
                        {0.0F, -0.6F, -0.3F, 0.0F, -0.6F, 0.3F, 0.0F},
                        {0.5F, -0.7F, -0.9F, 0.0F, -0.7F, 0.9F, 0.0F},
                        {1.0F, -0.6F, -0.3F, 0.0F, -0.6F, 0.3F, 0.0F}});
                case RECOIL -> null;
                case RELOAD -> reload(p, active.ticks);
                case CAST -> cast(active.variant, p);
                case DRINK -> new float[] {-1.95F, -0.55F, 0.0F, N, N, N};
            };
            if (active.anim == PlayerAnim.RECOIL) {
                float kick = (1.0F - p) * (1.0F - p) * 0.45F;
                model.rightArm.xRot -= kick;
                if (using) {
                    model.leftArm.xRot -= kick;
                }
            } else if (target != null) {
                blend(model, target, env);
            }
            if (active.anim == PlayerAnim.DRINK) {
                model.head.xRot = Mth.lerp(env, model.head.xRot, model.head.xRot - 0.35F);
            }
        }
    }

    // ------------------------------------------------------------------ poses

    /** The ready stance for each weapon; a gun or blade in the off hand changes the left arm. */
    static float[] stance(Kind kind, ItemStack offhand) {
        float[] s = switch (kind) {
            case BLADE -> new float[] {-0.75F, -0.25F, 0.05F, -0.35F, 0.35F, -0.1F};
            case HEAVY -> new float[] {-0.9F, -0.5F, 0.0F, -1.05F, 0.75F, 0.0F};
            case STAFF -> new float[] {-0.55F, -0.15F, 0.0F, -1.0F, 0.55F, 0.0F};
            case GUN -> new float[] {-0.45F, -0.15F, 0.0F, -0.2F, 0.1F, -0.05F};
            case BOW -> new float[] {-0.25F, 0.0F, 0.05F, -0.15F, 0.0F, -0.05F};
            case FISTS -> new float[] {-1.25F, -0.35F, 0.0F, -1.35F, 0.45F, 0.0F};
        };
        Kind side = Kind.of(offhand);
        if (!offhand.isEmpty() && Equipment.isWeapon(offhand) && kind != Kind.HEAVY && kind != Kind.STAFF) {
            if (side == Kind.GUN) {
                s[3] = -0.55F; s[4] = 0.1F; s[5] = -0.05F;
            } else if (side == Kind.BLADE) {
                s[3] = -0.7F; s[4] = 0.25F; s[5] = -0.05F;
            }
        }
        return s;
    }

    /** One swing of a three-step combo; t is vanilla's 0-1 attack progress. */
    static float[] attack(Kind kind, int step, float t, float[] stance) {
        if (kind == Kind.HEAVY) {
            // every step is a two-handed overhead blow, alternating sides
            float side = step % 2 == 0 ? 1.0F : -1.0F;
            return keys(t, new float[][] {
                    {0.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]},
                    {0.3F, -2.8F, -0.3F * side, 0.1F, -2.8F, 0.4F * side, -0.1F},
                    {0.6F, -0.35F, -0.45F, 0.0F, -0.45F, 0.65F, 0.0F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
        }
        if (kind == Kind.FISTS) {
            return switch (step) {
                case 0 -> keys(t, new float[][] {   // right jab
                        {0.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F},
                        {0.35F, -1.6F, -0.1F, 0.0F, stance[3], stance[4], 0.0F},
                        {1.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F}});
                case 1 -> keys(t, new float[][] {   // left jab
                        {0.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F},
                        {0.35F, stance[0], stance[1], 0.0F, -1.6F, 0.1F, 0.0F},
                        {1.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F}});
                default -> keys(t, new float[][] {  // hook
                        {0.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F},
                        {0.25F, -1.4F, 0.6F, 0.3F, stance[3], stance[4], 0.0F},
                        {0.55F, -1.5F, -0.7F, 0.0F, stance[3], stance[4], 0.0F},
                        {1.0F, stance[0], stance[1], 0.0F, stance[3], stance[4], 0.0F}});
            };
        }
        return switch (step) {
            case 0 -> keys(t, new float[][] {       // diagonal cut, high right to low left
                    {0.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]},
                    {0.25F, -2.5F, 0.35F, 0.45F, -0.5F, 0.3F, -0.1F},
                    {0.6F, -0.55F, -0.75F, -0.1F, -0.3F, 0.2F, -0.2F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
            case 1 -> keys(t, new float[][] {       // horizontal sweep, right to left
                    {0.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]},
                    {0.25F, -1.4F, 0.9F, 0.35F, -0.4F, 0.3F, -0.1F},
                    {0.6F, -1.5F, -0.95F, 0.0F, -0.4F, 0.2F, -0.3F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
            default -> keys(t, new float[][] {      // thrust
                    {0.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]},
                    {0.3F, -0.55F, 0.15F, 0.05F, -0.6F, 0.4F, 0.0F},
                    {0.55F, -1.62F, -0.05F, 0.0F, -0.25F, 0.2F, -0.15F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
        };
    }

    /**
     * Drawing, from rest to the weapon's sheath to the stance (sheathing runs it backwards).
     * Blades sit on the left hip, guns in a holster on the right hip, heavy weapons and staves on the back.
     */
    static float[] draw(Kind kind, float t, float[] stance) {
        return switch (kind) {
            case BLADE -> keys(t, new float[][] {
                    {0.0F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, -0.05F},
                    {0.35F, -0.3F, -0.95F, -0.15F, -0.2F, 0.1F, -0.1F},
                    {0.7F, -1.9F, -0.25F, 0.2F, -0.3F, 0.2F, -0.1F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
            case GUN -> keys(t, new float[][] {
                    {0.0F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, -0.05F},
                    {0.35F, 0.3F, 0.05F, 0.12F, 0.0F, 0.0F, -0.05F},
                    {0.65F, -0.95F, -0.1F, 0.05F, -0.15F, 0.1F, -0.05F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
            case FISTS -> keys(t, new float[][] {
                    {0.0F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, -0.05F},
                    {0.5F, -1.5F, -0.6F, 0.0F, -1.5F, 0.6F, 0.0F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
            default -> keys(t, new float[][] {      // over the shoulder
                    {0.0F, 0.0F, 0.0F, 0.05F, 0.0F, 0.0F, -0.05F},
                    {0.4F, -2.9F, -0.35F, -0.2F, -0.2F, 0.1F, -0.1F},
                    {0.7F, -1.6F, -0.3F, 0.0F, -0.8F, 0.4F, 0.0F},
                    {1.0F, stance[0], stance[1], stance[2], stance[3], stance[4], stance[5]}});
        };
    }

    /** Swing the cylinder out, thumb rounds in, snap it shut. */
    static float[] reload(float t, int ticks) {
        float thumb = t > 0.2F && t < 0.8F ? Mth.sin(t * Mth.PI * 10.0F) * 0.08F : 0.0F;
        return keys(t, new float[][] {
                {0.0F, -0.45F, -0.15F, 0.0F, -0.2F, 0.1F, 0.0F},
                {0.15F, -1.0F, -0.6F, 0.0F, -1.1F + thumb, 0.55F, 0.0F},
                {0.85F, -1.0F, -0.6F, 0.0F, -1.15F + thumb, 0.6F, 0.0F},
                {1.0F, -0.45F, -0.15F, 0.0F, -0.2F, 0.1F, 0.0F}});
    }

    static float[] cast(int variant, float t) {
        return switch (variant) {
            case PlayerAnim.CAST_CARD -> keys(t, new float[][] {   // a flick from across the chest
                    {0.0F, -1.2F, -0.9F, 0.0F, N, N, N},
                    {0.35F, -1.3F, -1.0F, 0.0F, N, N, N},
                    {0.55F, -1.55F, 0.65F, 0.2F, N, N, N},
                    {1.0F, -1.4F, 0.5F, 0.1F, N, N, N}});
            case PlayerAnim.CAST_AIR_BULLET -> keys(t, new float[][] { // a finger pointed like a pistol
                    {0.0F, -1.45F, -0.05F, 0.0F, -0.35F, 0.2F, 0.0F},
                    {0.45F, -1.57F, -0.05F, 0.0F, -0.35F, 0.2F, 0.0F},
                    {0.55F, -1.85F, -0.05F, 0.0F, -0.35F, 0.2F, 0.0F},
                    {1.0F, -1.57F, -0.05F, 0.0F, -0.35F, 0.2F, 0.0F}});
            case PlayerAnim.CAST_FLAME -> keys(t, new float[][] {  // arms swept back, then flung up
                    {0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F},
                    {0.3F, 0.7F, 0.0F, 0.15F, 0.7F, 0.0F, -0.15F},
                    {0.6F, -2.6F, 0.0F, 0.45F, -2.6F, 0.0F, -0.45F},
                    {1.0F, -2.4F, 0.0F, 0.4F, -2.4F, 0.0F, -0.4F}});
            case PlayerAnim.CAST_PAPER -> new float[] {-1.2F, -0.75F, 0.0F, -1.2F, 0.75F, 0.0F}; // arms crossed
            case PlayerAnim.CAST_DIVINE -> new float[] {-1.0F, -0.3F, 0.0F, -0.9F, 0.3F, 0.0F}; // hands held out together
            default -> new float[] {-2.0F, -0.2F, 0.1F, N, N, N};
        };
    }

    // ------------------------------------------------------------------ helpers

    /** Fade in and out so animations meet the pose around them smoothly. */
    private static float envelope(PlayerAnim anim, float p) {
        return switch (anim) {
            case DRAW -> 1.0F - smooth(Mth.clamp((p - 0.8F) / 0.2F, 0.0F, 1.0F)); // the stance takes over
            case SHEATHE -> 1.0F - smooth(Mth.clamp((p - 0.75F) / 0.25F, 0.0F, 1.0F));
            default -> smooth(Mth.clamp(p / 0.15F, 0.0F, 1.0F)) * (1.0F - smooth(Mth.clamp((p - 0.8F) / 0.2F, 0.0F, 1.0F)));
        };
    }

    /** Interpolates keyframes {t, rx, ry, rz, lx, ly, lz} with an ease between each. */
    static float[] keys(float t, float[][] frames) {
        float[] out = new float[6];
        if (t <= frames[0][0]) {
            System.arraycopy(frames[0], 1, out, 0, 6);
            return out;
        }
        for (int i = 1; i < frames.length; i++) {
            if (t <= frames[i][0] || i == frames.length - 1) {
                float[] a = frames[i - 1], b = frames[i];
                float f = smooth(Mth.clamp((t - a[0]) / Math.max(1.0E-4F, b[0] - a[0]), 0.0F, 1.0F));
                for (int k = 0; k < 6; k++) {
                    float x = a[k + 1], y = b[k + 1];
                    out[k] = Float.isNaN(x) || Float.isNaN(y) ? (Float.isNaN(y) ? N : y) : Mth.lerp(f, x, y);
                }
                return out;
            }
        }
        System.arraycopy(frames[frames.length - 1], 1, out, 0, 6);
        return out;
    }

    private static void blend(HumanoidModel<?> model, float[] pose, float w) {
        if (w <= 0.0F) {
            return;
        }
        set(model.rightArm, pose[0], pose[1], pose[2], w);
        set(model.leftArm, pose[3], pose[4], pose[5], w);
    }

    private static void set(ModelPart part, float x, float y, float z, float w) {
        if (!Float.isNaN(x)) {
            part.xRot = Mth.lerp(w, part.xRot, x);
        }
        if (!Float.isNaN(y)) {
            part.yRot = Mth.lerp(w, part.yRot, y);
        }
        if (!Float.isNaN(z)) {
            part.zRot = Mth.lerp(w, part.zRot, z);
        }
    }

    static float smooth(float t) {
        return t * t * (3.0F - 2.0F * t);
    }
}
