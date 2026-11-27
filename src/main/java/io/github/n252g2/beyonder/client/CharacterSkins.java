package io.github.n252g2.beyonder.client;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.n252g2.beyonder.Beyonder;
import io.github.n252g2.beyonder.core.Appearance;
import io.github.n252g2.beyonder.core.Appearance.Slot;
import io.github.n252g2.beyonder.net.CharacterSyncPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;

/**
 * Builds each character's skin from the pixel-art parts in {@code textures/skin/} (made by
 * tools/gen_skin_parts.py): every visible pixel of a part names a material (red channel) and a shade
 * (green channel, 0..5). Palettes come from the character's choices, hue-shifted the way skin
 * artists shade: darker steps richer and cooler, lighter steps warmer. The mixin on
 * AbstractClientPlayer asks {@link #skinFor} for the skin to draw.
 */
public final class CharacterSkins {
    // materials (match gen_skin_parts.py)
    static final int SKIN = 1, HAIR = 2, IRIS = 3, WHITE = 4, LASH = 5, LIP = 6, CLOTH = 7, TROUSER = 8, SHIRT = 9,
            LEATHER = 10, BRASS = 11, SILVER = 12, SILK = 13, STUBBLE = 14;

    static final int[][] SKIN_C = {{250, 226, 210}, {240, 204, 174}, {232, 186, 160}, {206, 166, 122}, {192, 144, 102},
            {158, 108, 72}, {120, 80, 52}, {82, 52, 38}};
    static final int[][] EYES_C = {{118, 134, 148}, {70, 122, 208}, {70, 148, 96}, {142, 112, 58}, {112, 72, 42},
            {52, 38, 32}, {210, 146, 42}, {138, 92, 190}};
    static final int[][] HAIR_C = {{40, 34, 40}, {66, 46, 34}, {104, 70, 44}, {130, 76, 44}, {148, 62, 40},
            {196, 104, 54}, {222, 186, 114}, {236, 228, 206}, {152, 152, 158}, {230, 230, 234}};
    static final int[][] CLOTH_C = {{62, 64, 74}, {40, 52, 94}, {98, 66, 46}, {112, 34, 48}, {40, 80, 58}};
    private static final Map<Integer, int[]> FIXED = Map.of(
            WHITE, new int[] {236, 234, 230}, LASH, new int[] {46, 30, 32}, SHIRT, new int[] {226, 222, 210},
            LEATHER, new int[] {76, 52, 38}, BRASS, new int[] {196, 154, 70}, SILVER, new int[] {196, 202, 212},
            SILK, new int[] {52, 44, 60});

    private static final Map<String, int[]> PARTS = new HashMap<>();      // part name -> 64*64 (mat << 8 | shade), -1 = empty
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();
    private static int[] preview;                                         // the creator's live choices for the local player

    private CharacterSkins() {}

    /** While the creator is open the local player is drawn with these choices. */
    public static void setPreview(int[] appearance) {
        preview = appearance == null ? null : appearance.clone();
    }

    /** The skin to draw for this player, or null to keep their normal one. */
    public static PlayerSkin skinFor(AbstractClientPlayer player, PlayerSkin original) {
        int[] appearance = null;
        Minecraft mc = Minecraft.getInstance();
        if (preview != null && player == mc.player) {
            appearance = preview;
        } else {
            CharacterSyncPayload c = ClientInbox.character(player.getId());
            if (c != null && c.created()) {
                appearance = c.appearance();
            }
        }
        if (appearance == null) {
            return null;
        }
        ResourceLocation texture = texture(appearance);
        if (texture == null) {
            return null;
        }
        return new PlayerSkin(texture, null, original.capeTexture(), original.elytraTexture(),
                Appearance.slim(appearance) ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, true);
    }

    /** Forget built textures and parts (after a resource reload). */
    public static void reset() {
        Minecraft mc = Minecraft.getInstance();
        for (ResourceLocation id : TEXTURES.values()) {
            mc.getTextureManager().release(id);
        }
        TEXTURES.clear();
        PARTS.clear();
    }

    private static ResourceLocation texture(int[] appearance) {
        int[] a = Appearance.sanitize(appearance);
        String key = Appearance.key(a);
        ResourceLocation cached = TEXTURES.get(key);
        if (cached != null) {
            return cached;
        }
        NativeImage image = compose(a);
        if (image == null) {
            return null;
        }
        ResourceLocation id = Beyonder.id("dynamic/character/" + Integer.toHexString(key.hashCode()));
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        TEXTURES.put(key, id);
        return id;
    }

    private static NativeImage compose(int[] a) {
        boolean slim = Appearance.slim(a);
        List<String> parts = new ArrayList<>();
        parts.add(slim ? "base_slim" : "base_wide");
        parts.add("face_" + Appearance.get(a, Slot.EYE_SHAPE));
        int brows = Appearance.get(a, Slot.BROWS);
        if (brows != 3) {          // 3 = none
            parts.add("brows_" + brows);
        }
        int facial = Appearance.get(a, Slot.FACIAL_HAIR);
        if (facial > 0) {
            parts.add("facial_" + facial);
        }
        parts.add("hair_" + Appearance.get(a, Slot.HAIR));

        Map<Integer, int[][]> pal = palettes(a);
        NativeImage image = new NativeImage(64, 64, true);
        image.fillRect(0, 0, 64, 64, 0);
        for (String name : parts) {
            int[] part = part(name);
            if (part == null) {
                continue;
            }
            for (int i = 0; i < part.length; i++) {
                int v = part[i];
                if (v < 0) {
                    continue;
                }
                int[][] ramp = pal.get(v >> 8);
                if (ramp == null) {
                    continue;
                }
                int[] c = ramp[Math.min(5, v & 0xFF)];
                // NativeImage stores ABGR
                image.setPixelRGBA(i % 64, i / 64, 0xFF000000 | (c[2] << 16) | (c[1] << 8) | c[0]);
            }
        }
        return image;
    }

    private static int[] part(String name) {
        int[] cached = PARTS.get(name);
        if (cached != null) {
            return cached;
        }
        ResourceLocation id = Beyonder.id("textures/skin/" + name + ".png");
        Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(id);
        if (res.isEmpty()) {
            Beyonder.LOGGER.warn("Missing character skin part {}", id);
            return null;
        }
        try (InputStream in = res.get().open(); NativeImage img = NativeImage.read(in)) {
            int[] out = new int[64 * 64];
            for (int y = 0; y < 64; y++) {
                for (int x = 0; x < 64; x++) {
                    int abgr = img.getPixelRGBA(x, y);
                    int alpha = (abgr >>> 24) & 0xFF;
                    int r = abgr & 0xFF;
                    int g = (abgr >> 8) & 0xFF;
                    out[y * 64 + x] = alpha == 0 ? -1 : (r << 8) | g;
                }
            }
            PARTS.put(name, out);
            return out;
        } catch (Exception e) {
            Beyonder.LOGGER.warn("Could not read character skin part {}", id, e);
            return null;
        }
    }

    // ------------------------------------------------------------------ palettes (mirror gen_skin_parts.py)

    private static Map<Integer, int[][]> palettes(int[] a) {
        int[] skin = SKIN_C[Appearance.get(a, Slot.SKIN)];
        int[] hair = HAIR_C[Appearance.get(a, Slot.HAIR_COLOR)];
        int[] cloth = CLOTH_C[Appearance.get(a, Slot.CLOTHES)];
        int[] trousers = {(int) (cloth[0] * 0.6 + 24 * 0.4), (int) (cloth[1] * 0.6 + 24 * 0.4), (int) (cloth[2] * 0.6 + 24 * 0.4)};
        int[] lip = {(int) (skin[0] * 0.72 + 170 * 0.28), (int) (skin[1] * 0.72 + 60 * 0.28), (int) (skin[2] * 0.72 + 64 * 0.28)};
        int[] stubble = {(skin[0] + hair[0]) / 2, (skin[1] + hair[1]) / 2, (skin[2] + hair[2]) / 2};
        Map<Integer, int[][]> pal = new HashMap<>();
        pal.put(SKIN, ramp(skin, 340.0));
        pal.put(HAIR, ramp(hair, 250.0));
        pal.put(IRIS, ramp(EYES_C[Appearance.get(a, Slot.EYES)], 250.0));
        pal.put(CLOTH, ramp(cloth, 250.0));
        pal.put(TROUSER, ramp(trousers, 250.0));
        pal.put(LIP, ramp(lip, 340.0));
        pal.put(STUBBLE, ramp(stubble, 340.0));
        for (Map.Entry<Integer, int[]> e : FIXED.entrySet()) {
            pal.put(e.getKey(), ramp(e.getValue(), 250.0));
        }
        return pal;
    }

    /** Six hue-shifted shades around a base colour (index 3). */
    static int[][] ramp(int[] rgb, double darkHue) {
        double[] hsv = toHsv(rgb);
        double h = hsv[0], s = hsv[1], v = hsv[2];
        int[][] out = new int[6][];
        for (int k = -3; k <= 2; k++) {
            double vv, ss, hh;
            if (k < 0) {
                vv = v * (1 + 0.17 * k);
                ss = Math.min(1.0, s * (1 - 0.12 * k) + 0.02 * -k);
                hh = shift(h, darkHue, 8.0 * -k * Math.min(1.0, s * 3));
            } else if (k > 0) {
                vv = v + (1 - v) * 0.30 * k;
                ss = s * (1 - 0.18 * k);
                hh = shift(h, 55.0, 6.0 * k * Math.min(1.0, s * 3));
            } else {
                vv = v;
                ss = s;
                hh = h;
            }
            out[k + 3] = toRgb(((hh % 360) + 360) % 360, Math.max(0, Math.min(1, ss)), Math.max(0, Math.min(1, vv)));
        }
        return out;
    }

    /** HSV with hue in degrees (no AWT: it must stay out of the game client). */
    private static double[] toHsv(int[] rgb) {
        double r = rgb[0] / 255.0, g = rgb[1] / 255.0, b = rgb[2] / 255.0;
        double max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        double h;
        if (d == 0) {
            h = 0;
        } else if (max == r) {
            h = 60 * (((g - b) / d) % 6);
        } else if (max == g) {
            h = 60 * ((b - r) / d + 2);
        } else {
            h = 60 * ((r - g) / d + 4);
        }
        if (h < 0) {
            h += 360;
        }
        return new double[] {h, max == 0 ? 0 : d / max, max};
    }

    private static int[] toRgb(double h, double s, double v) {
        double c = v * s, x = c * (1 - Math.abs((h / 60.0) % 2 - 1)), m = v - c;
        double r, g, b;
        int sector = (int) (h / 60.0) % 6;
        switch (sector) {
            case 0 -> { r = c; g = x; b = 0; }
            case 1 -> { r = x; g = c; b = 0; }
            case 2 -> { r = 0; g = c; b = x; }
            case 3 -> { r = 0; g = x; b = c; }
            case 4 -> { r = x; g = 0; b = c; }
            default -> { r = c; g = 0; b = x; }
        }
        return new int[] {(int) ((r + m) * 255), (int) ((g + m) * 255), (int) ((b + m) * 255)};
    }

    private static double shift(double h, double target, double amount) {
        double d = ((target - h + 540) % 360) - 180;
        double step = Math.min(Math.abs(d), amount) * (d >= 0 ? 1 : -1);
        return h + step;
    }
}
