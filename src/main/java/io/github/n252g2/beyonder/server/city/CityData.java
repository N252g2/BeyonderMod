package io.github.n252g2.beyonder.server.city;

import com.mojang.logging.LogUtils;
import io.github.n252g2.beyonder.Beyonder;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * The city as exported by tools/city: a block palette and, per 16x16 column, deflated layers of palette indices
 * from the bottom of the city up. Also its signs and named places ("marks").
 */
public final class CityData {
    private static final Logger LOG = LogUtils.getLogger();

    public record Sign(int x, int y, int z, String[] lines, String color) {}

    public final String name;
    public final int w, h, d, street;
    public final BlockState[] palette;
    /** Placed in the second pass: water, so it has walls to sit in. */
    public final boolean[] fluid;
    /** Re-shaped from their neighbours in the second pass: stairs, fences, panes, bars, walls, gates. */
    public final boolean[] shaped;
    public final int chunksX, chunksZ;
    private final int[] offset, length;
    private final short[] layers;
    private final byte[] blob;
    public final List<Sign> signs = new ArrayList<>();
    public final Map<String, int[]> marks = new LinkedHashMap<>();
    public final List<String> unknown = new ArrayList<>();

    private CityData(MinecraftServer server, String name, DataInputStream in) throws IOException {
        this.name = name;
        String magic = in.readUTF();
        if (!magic.equals("TCITY1")) {
            throw new IOException("not a city file: " + magic);
        }
        w = in.readInt();
        h = in.readInt();
        d = in.readInt();
        street = in.readInt();
        int n = in.readInt();
        palette = new BlockState[n];
        fluid = new boolean[n];
        shaped = new boolean[n];
        var blocks = server.registryAccess().lookupOrThrow(Registries.BLOCK);
        for (int i = 0; i < n; i++) {
            String s = in.readUTF();
            BlockState state;
            try {
                state = BlockStateParser.parseForBlock(blocks, s, false).blockState();
            } catch (Exception e) {
                unknown.add(s);
                state = Blocks.AIR.defaultBlockState();
            }
            palette[i] = state;
            Block b = state.getBlock();
            fluid[i] = b instanceof LiquidBlock;
            shaped[i] = b instanceof StairBlock || b instanceof CrossCollisionBlock || b instanceof WallBlock
                    || b instanceof FenceGateBlock;
        }
        if (!unknown.isEmpty()) {
            LOG.warn("City {}: {} block states could not be read and will be air: {}", name, unknown.size(), unknown);
        }
        chunksX = in.readInt();
        chunksZ = in.readInt();
        int c = chunksX * chunksZ;
        offset = new int[c];
        length = new int[c];
        layers = new short[c];
        for (int i = 0; i < c; i++) {
            offset[i] = in.readInt();
            length[i] = in.readInt();
            layers[i] = in.readShort();
        }
        int blobLen = in.readInt();
        blob = new byte[blobLen];
        in.readFully(blob);
        int signCount = in.readInt();
        for (int i = 0; i < signCount; i++) {
            int x = in.readInt(), y = in.readInt(), z = in.readInt();
            String[] lines = new String[4];
            for (int k = 0; k < 4; k++) {
                lines[k] = in.readUTF();
            }
            signs.add(new Sign(x, y, z, lines, in.readUTF()));
        }
        int markCount = in.readInt();
        for (int i = 0; i < markCount; i++) {
            String key = in.readUTF();
            marks.put(key, new int[] {in.readInt(), in.readInt(), in.readInt(), in.readInt()});
        }
    }

    /** Layers stored for a column chunk (everything above is air). */
    public int layers(int chunk) {
        return layers[chunk];
    }

    /** Palette indices for one column chunk, laid out [y][z][x]. */
    public short[] chunk(int chunk) {
        int n = layers[chunk] * 256;
        short[] out = new short[n];
        if (n == 0) {
            return out;
        }
        byte[] raw = new byte[n * 2];
        Inflater inf = new Inflater();
        try {
            inf.setInput(blob, offset[chunk], length[chunk]);
            int got = 0;
            while (got < raw.length) {
                int k = inf.inflate(raw, got, raw.length - got);
                if (k == 0 && (inf.finished() || inf.needsInput())) {
                    break;
                }
                got += k;
            }
        } catch (DataFormatException e) {
            throw new IllegalStateException("bad city chunk " + chunk, e);
        } finally {
            inf.end();
        }
        for (int i = 0; i < n; i++) {
            out[i] = (short) (((raw[2 * i] & 0xFF) << 8) | (raw[2 * i + 1] & 0xFF));
        }
        return out;
    }

    private static final Map<String, CityData> CACHE = new java.util.HashMap<>();

    public static synchronized CityData load(MinecraftServer server, String name) throws IOException {
        CityData cached = CACHE.get(name);
        if (cached != null) {
            return cached;
        }
        ResourceLocation id = Beyonder.id("city/" + name + ".bin");
        var res = server.getResourceManager().getResource(id);
        if (res.isEmpty()) {
            throw new IOException("no city data at " + id);
        }
        byte[] bytes;
        try (InputStream in = res.get().open()) {
            bytes = in.readAllBytes();
        }
        CityData data = new CityData(server, name, new DataInputStream(new ByteArrayInputStream(bytes)));
        CACHE.put(name, data);
        return data;
    }
}
