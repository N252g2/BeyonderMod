package io.github.n252g2.beyonder.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.n252g2.beyonder.core.BeyonderData;
import io.github.n252g2.beyonder.core.Beyonders;
import io.github.n252g2.beyonder.core.Pathway;
import java.util.Collection;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /beyonder status [player]                       anyone, for themselves; ops for others
 * /beyonder set <players> <pathway> <sequence>    ops: make players Beyonders (or "none")
 * /beyonder digestion <players> <0-100>           ops
 * /beyonder spirituality <players> <amount>       ops
 * /beyonder calm <players>                        ops: clear loss of control
 * /beyonder build <building>                      ops: place a Tingen building facing you
 */
public final class BeyonderCommands {
    private BeyonderCommands() {}

    static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("beyonder")
                .then(BuildCommand.node())
                .then(io.github.n252g2.beyonder.server.city.CityCommand.node())
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> status(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("set").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("pathway", StringArgumentType.word())
                                        .suggests((ctx, b) -> SharedSuggestionProvider.suggest(new String[] {"seer", "none"}, b))
                                        .executes(ctx -> set(ctx, 9))
                                        .then(Commands.argument("sequence", IntegerArgumentType.integer(0, 9))
                                                .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "sequence")))))))
                .then(Commands.literal("digestion").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("percent", FloatArgumentType.floatArg(0, 100))
                                        .executes(ctx -> each(ctx, p -> Beyonders.get(p).setDigestion(FloatArgumentType.getFloat(ctx, "percent")))))))
                .then(Commands.literal("spirituality").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                        .executes(ctx -> each(ctx, p -> Beyonders.get(p).setSpirituality(FloatArgumentType.getFloat(ctx, "amount")))))))
                .then(Commands.literal("character").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("reset").then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    io.github.n252g2.beyonder.core.Characters.reset(target);
                                    ctx.getSource().sendSuccess(() -> Component.literal(target.getGameProfile().getName()
                                            + " will make their character again."), true);
                                    return 1;
                                }))))
                .then(Commands.literal("calm").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("players", EntityArgument.players())
                                .executes(ctx -> each(ctx, p -> Beyonders.get(p).setLossOfControl(0.0F))))));
    }

    private interface Change {
        void apply(ServerPlayer player);
    }

    private static int each(CommandContext<CommandSourceStack> ctx, Change change) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "players");
        for (ServerPlayer p : players) {
            change.apply(p);
            Beyonders.sync(p);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Updated " + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int sequence) throws CommandSyntaxException {
        Pathway pathway = Pathway.byId(StringArgumentType.getString(ctx, "pathway"));
        return each(ctx, p -> {
            BeyonderData data = Beyonders.get(p);
            data.become(pathway, sequence);
            data.setSpirituality(data.maxSpirituality());
            data.setLossOfControl(0.0F);
        });
    }

    private static int status(CommandSourceStack source, ServerPlayer player) {
        BeyonderData d = Beyonders.get(player);
        String text = d.isBeyonder()
                ? String.format("%s: %s, Sequence %d (%s pathway). Spirituality %.0f/%.0f, digested %.0f%%, loss of control %.0f%%.",
                        player.getName().getString(), d.pathway().sequenceName(d.sequence()), d.sequence(),
                        d.pathway().sequenceName(9), d.spirituality(), d.maxSpirituality(), d.digestion(), d.lossOfControl())
                : player.getName().getString() + " is not a Beyonder.";
        source.sendSuccess(() -> Component.literal(text), false);
        return 1;
    }
}
