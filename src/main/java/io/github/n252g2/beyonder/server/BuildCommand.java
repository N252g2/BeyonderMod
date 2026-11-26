package io.github.n252g2.beyonder.server;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.n252g2.beyonder.Beyonder;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * /beyonder build <building>: places a Tingen building in front of you, its front facing you.
 * The buildings are designed in tools/gen_structures.py. Ops only.
 */
public final class BuildCommand {
    static final List<String> BUILDINGS = List.of(
            "terrace_house", "terrace_house_b", "corner_shop", "street", "chapel", "tingen_street");

    private BuildCommand() {}

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("build").requires(s -> s.hasPermission(2))
                .then(Commands.argument("building", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(BUILDINGS, builder))
                        .executes(BuildCommand::build));
    }

    private static int build(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "building");
        ServerLevel level = player.serverLevel();
        Optional<StructureTemplate> found = level.getStructureManager().get(Beyonder.id("tingen/" + name));
        if (found.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("No building called \"" + name + "\". Try: " + String.join(", ", BUILDINGS)));
            return 0;
        }
        StructureTemplate template = found.get();

        // Building fronts face north in their files; turn them so the front faces the player.
        Direction facing = player.getDirection();
        Rotation rotation = switch (facing) {
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            case WEST -> Rotation.CLOCKWISE_90;
            default -> Rotation.NONE;
        };
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation).setIgnoreEntities(true);
        BoundingBox box = template.getBoundingBox(settings, BlockPos.ZERO);
        int width = box.getXSpan();
        int depth = box.getZSpan();

        // Start two blocks ahead of the player, centred on them, with the ground floor at their feet.
        BlockPos feet = player.blockPosition();
        int minX;
        int minZ;
        switch (facing) {
            case NORTH -> { minX = feet.getX() - width / 2; minZ = feet.getZ() - 2 - (depth - 1); }
            case EAST -> { minX = feet.getX() + 2; minZ = feet.getZ() - depth / 2; }
            case WEST -> { minX = feet.getX() - 2 - (width - 1); minZ = feet.getZ() - depth / 2; }
            default -> { minX = feet.getX() - width / 2; minZ = feet.getZ() + 2; }
        }
        BlockPos origin = new BlockPos(minX - box.minX(), feet.getY() - 1 - box.minY(), minZ - box.minZ());
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), 2);
        ctx.getSource().sendSuccess(() -> Component.literal("Built " + name + " (" + width + " x " + depth + ")."), true);
        return 1;
    }
}
