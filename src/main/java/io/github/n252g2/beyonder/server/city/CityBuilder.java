package io.github.n252g2.beyonder.server.city;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * Places a city into the overworld a slice at a time, so the server keeps ticking. Progress is saved with the
 * world, so a restart carries on where it stopped. Pass 1 lays every block (and clears what is above), pass 2
 * pours the water and lets stairs, fences, panes and walls join up, pass 3 writes the signs and sets the spawn.
 * Natural monster spawns are refused in the streets of a finished city (the sewers below are left to them).
 */
public final class CityBuilder extends SavedData {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String KEY = "beyonder_city";
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
    /** Milliseconds of each server tick the builder may use. */
    public static long budgetMs = Long.getLong("beyonder.city.budget", 30L);

    String city = "tingen";
    boolean active;
    int phase;              // 1 blocks, 2 water and shapes, 3 signs, 4 done
    int chunk;
    int voxel;
    int ox, oy, oz;         // world position of the city's [0, 0, 0]
    boolean built;          // a finished city exists (for the spawn rule)
    long placed;

    private static CityBuilder cached;
    private short[] data;
    private int dataChunk = -1;

    public static CityBuilder get(MinecraftServer server) {
        ServerLevel level = server.overworld();
        CityBuilder b = level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(CityBuilder::new, CityBuilder::load), KEY);
        cached = b;
        return b;
    }

    private static CityBuilder load(CompoundTag tag, HolderLookup.Provider lookup) {
        CityBuilder b = new CityBuilder();
        b.city = tag.getString("city").isEmpty() ? "tingen" : tag.getString("city");
        b.active = tag.getBoolean("active");
        b.phase = tag.getInt("phase");
        b.chunk = tag.getInt("chunk");
        b.voxel = tag.getInt("voxel");
        b.ox = tag.getInt("ox");
        b.oy = tag.getInt("oy");
        b.oz = tag.getInt("oz");
        b.built = tag.getBoolean("built");
        b.placed = tag.getLong("placed");
        return b;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putString("city", city);
        tag.putBoolean("active", active);
        tag.putInt("phase", phase);
        tag.putInt("chunk", chunk);
        tag.putInt("voxel", voxel);
        tag.putInt("ox", ox);
        tag.putInt("oy", oy);
        tag.putInt("oz", oz);
        tag.putBoolean("built", built);
        tag.putLong("placed", placed);
        return tag;
    }

    /** Start (or restart) building with the city's centre at (cx, cz) and its streets at y = streetY. */
    public void start(MinecraftServer server, String name, int cx, int streetY, int cz) throws java.io.IOException {
        CityData c = CityData.load(server, name);
        city = name;
        ox = Math.floorDiv(cx - c.w / 2, 16) * 16;
        oz = Math.floorDiv(cz - c.d / 2, 16) * 16;
        ServerLevel level = server.overworld();
        oy = Math.max(level.getMinBuildHeight(), streetY - c.street);
        active = true;
        phase = 1;
        chunk = 0;
        voxel = 0;
        placed = 0;
        dataChunk = -1;
        setDirty();
    }

    public String status(MinecraftServer server) {
        try {
            CityData c = CityData.load(server, city);
            int total = c.chunksX * c.chunksZ;
            if (!active) {
                return built ? "The city of " + c.name + " is built (streets at y=" + (oy + c.street) + ")."
                        : "No city is being built.";
            }
            String what = switch (phase) {
                case 1 -> "laying blocks";
                case 2 -> "pouring water and joining stairs, fences and walls";
                default -> "writing signs";
            };
            return String.format("Building %s: %s, %d%% (%d of %d columns, %,d blocks placed).", c.name, what,
                    chunk * 100 / Math.max(1, total), chunk, total, placed);
        } catch (Exception e) {
            return "City data could not be read: " + e.getMessage();
        }
    }

    public static void onTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        CityBuilder b = cached != null ? cached : get(server);
        if (!b.active) {
            return;
        }
        try {
            b.work(server, System.nanoTime() + budgetMs * 1_000_000L);
        } catch (Exception e) {
            LOG.error("City building stopped", e);
            b.active = false;
            b.setDirty();
            server.getPlayerList().broadcastSystemMessage(Component.literal("City building stopped: " + e), false);
        }
    }

    private void work(MinecraftServer server, long deadline) throws java.io.IOException {
        CityData c = CityData.load(server, city);
        ServerLevel level = server.overworld();
        int total = c.chunksX * c.chunksZ;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();
        while (System.nanoTime() < deadline) {
            if (phase == 3) {
                finish(server, level, c);
                return;
            }
            if (chunk >= total) {
                phase++;
                chunk = 0;
                voxel = 0;
                setDirty();
                LOG.info("City {}: starting pass {}", c.name, phase);
                continue;
            }
            int ccx = chunk % c.chunksX, ccz = chunk / c.chunksX;
            if (dataChunk != chunk) {
                data = c.chunk(chunk);
                dataChunk = chunk;
            }
            int baseX = ox + ccx * 16, baseZ = oz + ccz * 16;
            LevelChunk lc = level.getChunk(baseX >> 4, baseZ >> 4);
            int stored = c.layers(chunk);
            int top;
            if (phase == 1) {
                // clear anything standing above the city's own layers (hills, trees)
                int hm = 0;
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        hm = Math.max(hm, lc.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1);
                    }
                }
                top = Math.min(level.getMaxBuildHeight() - oy, Math.max(c.h, hm - oy));
            } else {
                top = stored;
            }
            int end = top * 256;
            int checked = 0;
            while (voxel < end) {
                int y = voxel >> 8, z = (voxel >> 4) & 15, x = voxel & 15;
                int canvasX = ccx * 16 + x, canvasZ = ccz * 16 + z;
                int id = voxel < stored * 256 ? data[voxel] & 0xFFFF : 0;
                voxel++;
                if (canvasX >= c.w || canvasZ >= c.d || oy + y < level.getMinBuildHeight()) {
                    continue;
                }
                pos.set(baseX + x, oy + y, baseZ + z);
                if (phase == 1) {
                    BlockState want = c.fluid[id] ? air : c.palette[id];
                    if (lc.getBlockState(pos) != want) {
                        level.setBlock(pos, want, FLAGS);
                        placed++;
                    }
                } else if (c.fluid[id]) {
                    if (lc.getBlockState(pos) != c.palette[id]) {
                        level.setBlock(pos, c.palette[id], Block.UPDATE_CLIENTS);
                        placed++;
                    }
                } else if (c.shaped[id]) {
                    BlockState cur = lc.getBlockState(pos);
                    BlockState shaped = Block.updateFromNeighbourShapes(cur, level, pos);
                    if (shaped != cur) {
                        level.setBlock(pos, shaped, FLAGS);
                    }
                }
                if ((++checked & 255) == 0 && System.nanoTime() >= deadline) {
                    setDirty();
                    return;
                }
            }
            chunk++;
            voxel = 0;
            setDirty();
        }
    }

    private void finish(MinecraftServer server, ServerLevel level, CityData c) {
        for (CityData.Sign s : c.signs) {
            BlockPos p = new BlockPos(ox + s.x(), oy + s.y(), oz + s.z());
            if (level.getBlockEntity(p) instanceof SignBlockEntity sign) {
                DyeColor color = DyeColor.byName(s.color(), DyeColor.BLACK);
                SignText text = new SignText();
                for (int i = 0; i < 4; i++) {
                    text = text.setMessage(i, Component.literal(s.lines()[i]));
                }
                text = text.setColor(color).setHasGlowingText(color != DyeColor.BLACK);
                sign.setText(text, true);
                sign.setText(text, false);
                sign.setWaxed(true);
                sign.setChanged();
                level.sendBlockUpdated(p, sign.getBlockState(), sign.getBlockState(), Block.UPDATE_ALL);
            }
        }
        int[] spawn = c.marks.get("spawn");
        if (spawn != null) {
            level.setDefaultSpawnPos(new BlockPos(ox + spawn[0], oy + spawn[1], oz + spawn[2]), spawn[3]);
        }
        active = false;
        built = true;
        phase = 4;
        setDirty();
        LOG.info("City {} finished: {} blocks placed", c.name, placed);
        if (CITY_TEST) {
            LOG.info("City test: done, stopping the server");
            server.halt(false);
        }
        server.getPlayerList().broadcastSystemMessage(Component.literal("The city of " + c.name + " is finished."), false);
    }

    public BlockPos mark(MinecraftServer server, String name) {
        try {
            int[] m = CityData.load(server, city).marks.get(name);
            return m == null ? null : new BlockPos(ox + m[0], oy + m[1], oz + m[2]);
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public java.util.Set<String> marks(MinecraftServer server) {
        try {
            return CityData.load(server, city).marks.keySet();
        } catch (java.io.IOException e) {
            return java.util.Set.of();
        }
    }

    /** CI only (-Dbeyonder.citytest=true): build the city as soon as the server starts and stop it when done. */
    public static final boolean CITY_TEST = Boolean.getBoolean("beyonder.citytest");

    public static void onServerStarted(net.neoforged.neoforge.event.server.ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        CityBuilder b = get(server);
        if (CITY_TEST && !b.built && !b.active) {
            try {
                b.start(server, "tingen", 0, CityCommand.defaultStreetY(server.overworld()), 0);
                LOG.info("City test: building");
            } catch (java.io.IOException e) {
                LOG.error("City test: could not start", e);
                server.halt(false);
            }
        }
    }

    public static void onServerStopped(net.neoforged.neoforge.event.server.ServerStoppedEvent event) {
        cached = null;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isBuilt() {
        return built;
    }

    public void pause() {
        active = false;
        setDirty();
    }

    public void resume() {
        if (phase >= 1 && phase < 4) {
            active = true;
            dataChunk = -1;
            setDirty();
        }
    }

    // ------------------------------------------------------------------ the spawn rule

    /** No natural monsters in a finished city's streets and houses; the sewers keep theirs. */
    public static void onSpawnCheck(MobSpawnEvent.PositionCheck event) {
        if (!(event.getEntity() instanceof Enemy) || cached == null || !cached.built) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION) {
            return;
        }
        if (!(event.getLevel() instanceof Level lvl) || lvl.dimension() != Level.OVERWORLD) {
            return;
        }
        CityData c;
        try {
            c = CityData.load(lvl.getServer(), cached.city);
        } catch (java.io.IOException e) {
            return;
        }
        double x = event.getX() - cached.ox, y = event.getY() - cached.oy, z = event.getZ() - cached.oz;
        if (x >= 0 && z >= 0 && x < c.w && z < c.d && y >= c.street - 2) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);
        }
    }
}
