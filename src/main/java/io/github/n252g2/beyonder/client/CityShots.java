package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.core.Appearance;
import io.github.n252g2.beyonder.core.Background;
import io.github.n252g2.beyonder.core.Characters;
import io.github.n252g2.beyonder.net.CreateCharacterPayload;
import io.github.n252g2.beyonder.server.city.CityBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** CI only: tours a freshly built city and photographs it (see .github/workflows/city.yml). */
final class CityShots {
    private static final Logger LOG = LoggerFactory.getLogger("beyonder-selftest");

    /** name, mark, dx, dy, dz, yaw, pitch, time of day */
    private record View(String name, String mark, double dx, double dy, double dz, float yaw, float pitch, long time) {}

    private static final View[] VIEWS = {
            new View("01_plaza_aerial", "spawn", 0, 45, 75, 180, 28, 6000),
            new View("02_cathedral_front", "cathedral", 0, 1.6, 22, 180, -22, 6000),
            new View("03_cathedral_side", "cathedral", -60, 30, -20, -90, 18, 6000),
            new View("04_overhead", "spawn", 0, 150, 0, 180, 90, 6000),
            new View("05_daffodil_street", "daffodil", 0, 1.6, 0, -90, 2, 6000),
            new View("06_blackthorn", "blackthorn", 0, 1.6, 8, 180, 0, 6000),
            new View("07_confluence", "confluence", -60, 35, 60, -135, 22, 6000),
            new View("08_station", "station", 0, 30, 70, 180, 20, 6000),
            new View("09_iron_cross", "iron_cross", 0, 1.6, 0, 180, 0, 6000),
            new View("10_golden_indus", "golden", 0, 25, 40, 180, 22, 6000),
            new View("11_villas", "villas", 0, 30, 40, 180, 30, 6000),
            new View("12_university", "university", 0, 35, 60, 180, 25, 6000),
            new View("13_market", "market", 0, 20, 30, 180, 20, 6000),
            new View("14_cemetery", "cemetery", 0, 25, 40, 180, 25, 6000),
            new View("15_night_street", "daffodil", 0, 1.6, 0, -90, 2, 18000),
            new View("16_night_plaza", "cathedral", 0, 3, 22, 180, -10, 18000),
            new View("17_sewer", "sewer", 0, 0.1, 0, 90, 0, 6000),
    };
    private static final int SETTLE = 500;

    private CityShots() {}

    static void tick(Minecraft mc, IntegratedServer server, int t) {
        if (t == 40) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                if (sp == null) {
                    return;
                }
                Characters.create(sp, new CreateCharacterPayload("City Test", Background.CONSTABLE.id(), Appearance.defaults()));
                var src = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, "gamemode spectator @a");
                server.getCommands().performPrefixedCommand(src, "gamerule doDaylightCycle false");
                server.getCommands().performPrefixedCommand(src, "weather clear");
                CityBuilder b = CityBuilder.get(server);
                LOG.info("city built={} active={} status={}", b.isBuilt(), b.isActive(), b.status(server));
            });
            return;
        }
        if (t == 60) {
            mc.options.hideGui = true;
        }
        int i = (t - 80) / SETTLE;
        int k = (t - 80) % SETTLE;
        if (t < 80) {
            return;
        }
        if (i >= VIEWS.length) {
            if (k == 0) {
                LOG.info("city tour done");
                mc.stop();
            }
            return;
        }
        View v = VIEWS[i];
        if (k == 0) {
            server.execute(() -> {
                ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
                BlockPos m = CityBuilder.get(server).mark(server, v.mark());
                if (sp == null) {
                    return;
                }
                if (m == null) {
                    LOG.info("no mark {}", v.mark());
                    m = CityBuilder.get(server).mark(server, "spawn");
                    if (m == null) {
                        return;
                    }
                }
                var src = server.createCommandSourceStack().withSuppressedOutput();
                server.getCommands().performPrefixedCommand(src, "time set " + v.time());
                sp.teleportTo(server.overworld(), m.getX() + 0.5 + v.dx(), m.getY() + v.dy(), m.getZ() + 0.5 + v.dz(), v.yaw(), v.pitch());
                LOG.info("view {} at {} {} {}", v.name(), m.getX() + v.dx(), m.getY() + v.dy(), m.getZ() + v.dz());
            });
        }
        if (k == SETTLE - 20) {
            LOG.info("screenshot {}", v.name());
            Screenshot.grab(mc.gameDirectory, v.name() + ".png", mc.getMainRenderTarget(), msg -> {});
        }
    }
}
