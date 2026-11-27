package io.github.n252g2.beyonder.server.city;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /beyonder city build [x z [streetY]]   ops: build Tingen centred on x z (default 0 0)
 * /beyonder city status                  progress of the build
 * /beyonder city pause | resume          ops
 * /beyonder city goto <place>            ops: go to a named place in the city
 */
public final class CityCommand {
    private CityCommand() {}

    public static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("city")
                .then(Commands.literal("build").requires(s -> s.hasPermission(2))
                        .executes(ctx -> build(ctx, 0, 0, null))
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "x"),
                                                IntegerArgumentType.getInteger(ctx, "z"), null))
                                        .then(Commands.argument("streetY", IntegerArgumentType.integer(-64, 300))
                                                .executes(ctx -> build(ctx, IntegerArgumentType.getInteger(ctx, "x"),
                                                        IntegerArgumentType.getInteger(ctx, "z"),
                                                        IntegerArgumentType.getInteger(ctx, "streetY")))))))
                .then(Commands.literal("status").executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    ctx.getSource().sendSuccess(() -> Component.literal(CityBuilder.get(server).status(server)), false);
                    return 1;
                }))
                .then(Commands.literal("pause").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    CityBuilder.get(ctx.getSource().getServer()).pause();
                    ctx.getSource().sendSuccess(() -> Component.literal("City building paused."), true);
                    return 1;
                }))
                .then(Commands.literal("resume").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    CityBuilder.get(ctx.getSource().getServer()).resume();
                    ctx.getSource().sendSuccess(() -> Component.literal("City building resumed."), true);
                    return 1;
                }))
                .then(Commands.literal("goto").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("place", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(
                                        CityBuilder.get(ctx.getSource().getServer()).marks(ctx.getSource().getServer()), b))
                                .executes(CityCommand::go)));
    }

    /** Default street height: well above the floor of a flat world, or just above sea level otherwise. */
    public static int defaultStreetY(ServerLevel level) {
        return level.isFlat() ? level.getMinBuildHeight() + 17 : level.getSeaLevel() + 2;
    }

    private static int build(CommandContext<CommandSourceStack> ctx, int x, int z, Integer streetY) {
        MinecraftServer server = ctx.getSource().getServer();
        ServerLevel level = server.overworld();
        int y = streetY != null ? streetY : defaultStreetY(level);
        try {
            CityBuilder.get(server).start(server, "tingen", x, y, z);
        } catch (Exception e) {
            ctx.getSource().sendFailure(Component.literal("Could not start the city: " + e.getMessage()));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Building Tingen around " + x + ", " + z + " with its streets at y="
                + y + ". This takes a few minutes; /beyonder city status shows progress."), true);
        return 1;
    }

    private static int go(CommandContext<CommandSourceStack> ctx) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();
        String name = StringArgumentType.getString(ctx, "place");
        BlockPos p = CityBuilder.get(server).mark(server, name);
        if (p == null) {
            ctx.getSource().sendFailure(Component.literal("No place called " + name));
            return 0;
        }
        player.teleportTo(server.overworld(), p.getX() + 0.5, p.getY(), p.getZ() + 0.5, player.getYRot(), player.getXRot());
        return 1;
    }
}
