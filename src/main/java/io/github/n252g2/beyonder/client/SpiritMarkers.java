package io.github.n252g2.beyonder.client;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/**
 * Waypoints that only Spirit Vision can see: a column of light and a label.
 *
 * <p>Quests, divination results and party pings will add markers through {@link #add}. For testing
 * there is a client command: {@code /spiritmark [name]} marks where you stand,
 * {@code /spiritmark clear} removes them.</p>
 */
public final class SpiritMarkers {
    public record Marker(ResourceKey<Level> dimension, Vec3 position, String label, float red, float green, float blue) {}

    private static final List<Marker> MARKERS = new ArrayList<>();

    private SpiritMarkers() {}

    public static void add(Marker marker) {
        MARKERS.add(marker);
    }

    public static void clear() {
        MARKERS.clear();
    }

    public static List<Marker> all() {
        return Collections.unmodifiableList(MARKERS);
    }

    static void onRegisterCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("spiritmark")
                .then(Commands.literal("clear").executes(ctx -> {
                    clear();
                    ctx.getSource().sendSuccess(() -> Component.translatable("message.beyonder.marker.cleared"), false);
                    return 1;
                }))
                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(ctx ->
                        mark(StringArgumentType.getString(ctx, "name"))))
                .executes(ctx -> mark("Waypoint")));
    }

    private static int mark(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return 0;
        }
        // Gold, like the gaslight.
        add(new Marker(mc.level.dimension(), mc.player.position(), name, 1.0F, 0.78F, 0.38F));
        mc.player.displayClientMessage(Component.translatable("message.beyonder.marker.added", name), false);
        return 1;
    }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }
}
