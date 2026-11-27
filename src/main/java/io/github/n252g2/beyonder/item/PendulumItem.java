package io.github.n252g2.beyonder.item;

import io.github.n252g2.beyonder.core.BeyonderData;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Pathway;
import io.github.n252g2.beyonder.net.SpiritMarkerPayload;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Dowsing with a spirit pendulum, the Seer's first divination. Rename the pendulum in an anvil to
 * ask your question: a player's name, "village", "home", or an ore such as "diamond". The pendulum
 * swings toward the answer and leaves a spirit marker you can follow with Spirit Vision.
 */
public class PendulumItem extends Item {
    private static final float COST = 15.0F;
    private static final int COOLDOWN_TICKS = 60;
    private static final int ORE_RADIUS = 16;

    public PendulumItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.success(stack);
        }
        BeyonderData data = Beyonders.get(sp);
        if (data.pathway() != Pathway.SEER && !sp.isCreative()) {
            sp.displayClientMessage(Component.literal("The pendulum hangs still. Divination needs a Seer's spirit.")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        Component customName = stack.get(DataComponents.CUSTOM_NAME);
        if (customName == null) {
            sp.displayClientMessage(Component.literal("Ask a question first: rename the pendulum in an anvil "
                    + "(a player's name, \"village\", \"home\" or an ore like \"diamond\").").withStyle(ChatFormatting.GRAY), false);
            return InteractionResultHolder.fail(stack);
        }
        if (!Beyonders.trySpend(sp, COST)) {
            return InteractionResultHolder.fail(stack);
        }
        sp.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        String question = customName.getString().trim();
        divine(sp, data, question);
        Beyonders.act(sp, 9, 6.0F, "pendulum"); // divining is a Seer's act
        io.github.n252g2.beyonder.core.EquipmentRules.anim(sp, io.github.n252g2.beyonder.core.PlayerAnim.CAST, io.github.n252g2.beyonder.core.PlayerAnim.CAST_DIVINE, true);
        return InteractionResultHolder.consume(stack);
    }

    private static void divine(ServerPlayer player, BeyonderData data, String question) {
        ServerLevel level = player.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.4, 0.4, 0.5);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.8F, 1.4F);

        // Lower Sequences are clearer seers.
        int sequence = data.isBeyonder() ? data.sequence() : 9;
        float failChance = 0.04F * (sequence - 6); // 12% at Sequence 9, 4% at Sequence 7
        float errorDegrees = 6.0F * (sequence - 5); // +-24 deg at Sequence 9, +-12 at Sequence 7
        if (player.getRandom().nextFloat() < failChance) {
            player.displayClientMessage(Component.literal("The pendulum circles without purpose. Your spirit is clouded.")
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return;
        }

        Vec3 target = find(player, level, question.toLowerCase(Locale.ROOT));
        if (target == null) {
            player.displayClientMessage(Component.literal("The pendulum hangs still. \"" + question + "\" is beyond its reach.")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }

        Vec3 from = player.position();
        double dx = target.x - from.x;
        double dz = target.z - from.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        double angle = Math.atan2(dz, dx) + Math.toRadians((player.getRandom().nextFloat() * 2 - 1) * errorDegrees);

        // Near answers are marked above where they are; far ones as a signpost along the way.
        // Markers always stand on the surface so the beam is visible; the message says how deep the answer is.
        double markDistance = distance < 160 ? distance : 64;
        double mx = from.x + Math.cos(angle) * markDistance;
        double mz = from.z + Math.sin(angle) * markDistance;
        double surface = surfaceY(level, mx, mz, from.y);
        PacketDistributor.sendToPlayer(player, new SpiritMarkerPayload(mx, surface, mz, question, 0xFFC860));

        String depth = "";
        if (distance < 160) {
            int below = (int) Math.round(surfaceY(level, target.x, target.z, target.y) - target.y);
            if (below > 4) {
                depth = " It lies " + below + " blocks below the surface.";
            }
        }
        player.displayClientMessage(Component.literal("The pendulum swings " + compass(angle) + ". The answer lies "
                + howFar(distance) + "." + depth + " Open your Spirit Vision to follow it.").withStyle(ChatFormatting.GOLD), false);
    }

    /** The surface height at a spot, if that chunk is loaded; otherwise the fallback. */
    private static double surfaceY(ServerLevel level, double x, double z, double fallback) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        if (!level.hasChunk(bx >> 4, bz >> 4)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
    }

    private static Vec3 find(ServerPlayer player, ServerLevel level, String q) {
        // A person
        ServerPlayer other = level.getServer().getPlayerList().getPlayerByName(q);
        if (other != null && other != player && other.level() == level) {
            return other.position();
        }
        // Home
        if (q.equals("home") || q.equals("bed")) {
            BlockPos respawn = player.getRespawnPosition();
            return respawn != null && player.getRespawnDimension() == level.dimension() ? Vec3.atCenterOf(respawn) : null;
        }
        // A village
        if (q.equals("village") || q.equals("town")) {
            BlockPos found = level.findNearestMapStructure(StructureTags.VILLAGE, player.blockPosition(), 64, false);
            return found == null ? null : Vec3.atCenterOf(found);
        }
        // An ore
        String ore = q.replace(' ', '_') + "_ore";
        BlockPos origin = player.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-ORE_RADIUS, -ORE_RADIUS, -ORE_RADIUS),
                origin.offset(ORE_RADIUS, ORE_RADIUS, ORE_RADIUS))) {
            String path = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath();
            if (path.equals(ore) || path.equals("deepslate_" + ore) || path.equals("nether_" + ore)) {
                double dist = pos.distSqr(origin);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    best = pos.immutable();
                }
            }
        }
        return best == null ? null : Vec3.atCenterOf(best);
    }

    private static String compass(double angle) {
        // Minecraft: +x east, +z south.
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int index = Math.floorMod(Math.round((float) (angle / (Math.PI / 4))), 8);
        return names[index];
    }

    private static String howFar(double distance) {
        if (distance < 24) {
            return "very close";
        }
        if (distance < 100) {
            return "nearby";
        }
        if (distance < 500) {
            return "some way off";
        }
        return "far away";
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("Seer divination. Costs " + Mth.floor(COST) + " Spirituality.").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.literal("Rename it in an anvil to ask: a player, \"village\", \"home\", \"diamond\"...")
                .withStyle(ChatFormatting.GRAY));
    }
}
