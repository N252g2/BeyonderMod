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
            new View("02_cathedral_front", "cathedral", 9, 1.6, 26, 160, -24, 6000),
            new View("03_cathedral_side", "cathedral", -60, 30, -20, -90, 18, 6000),
            new View("04_overhead", "spawn", 0, 70, 40, 180, 60, 6000),
            new View("05_daffodil_street", "daffodil", 0, 1.6, 0, -90, 2, 6000),
            new View("06_blackthorn", "blackthorn", 0, 1.6, 8, 180, 0, 6000),
            new View("07_confluence", "confluence", -60, 35, 60, -135, 22, 6000),
            new View("08_station", "station", 0, 22, 30, 180, 18, 6000),
            new View("09_station_shed", "shed", 0, 1.6, 0, 90, 4, 6000),
            new View("10_khoy_university", "university", 30, 30, 10, 90, 22, 6000),
            new View("11_market", "market_view", 0, 0, 0, 180, 25, 6000),
            new View("12_iron_cross", "iron_cross", 0, 1.6, 0, 180, 0, 6000),
            new View("13_golden_indus", "golden", 0, 25, 40, 180, 22, 6000),
            new View("14_gardens", "gardens", 0, 25, -10, 0, 25, 6000),
            new View("15_cemetery", "cemetery", 0, 22, -12, 0, 25, 6000),
            new View("16_villas", "villas", 0, 30, 40, 180, 30, 6000),
            new View("17_tingen_university", "tingen_university", 0, 25, -12, 0, 22, 6000),
            new View("18_docks", "docks_view", 0, 0, 0, 180, 18, 6000),
            new View("19_works", "works", 0, 30, 30, 180, 25, 6000),
            new View("20a_brickfields", "brickfields", 0, 25, 20, 180, 25, 6000),
            new View("20b_westbrook", "westbrook", 0, 18, 12, 180, 25, 6000),
            new View("20c_eastwharf", "eastwharf", 0, 22, 16, 180, 25, 6000),
            new View("20d_asylum", "asylum", 0, 25, -16, 0, 22, 6000),
            new View("20e_gaol", "gaol", 0, 30, -16, 0, 28, 6000),
            new View("20f_ships", "river", 0, 12, 0, 0, 15, 6000),
            new View("20g_nighthawks", "nighthawks", 0, 0.2, 0, 90, 0, 6000),
            new View("20_night_street", "daffodil", 0, 1.6, 0, -90, 2, 18000),
            new View("21_night_plaza", "cathedral", 9, 3, 26, 160, -10, 18000),
            new View("22_sewer", "sewer", 0, 0.1, 0, 90, 0, 6000),
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
