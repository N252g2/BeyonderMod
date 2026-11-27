package io.github.n252g2.beyonder.client;

import io.github.n252g2.beyonder.brewing.Formula;
import io.github.n252g2.beyonder.net.BrewResultPayload;
import io.github.n252g2.beyonder.net.BrewStartPayload;
import io.github.n252g2.beyonder.net.ClientInbox;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * The brewing minigame, played at an Apothecary Bench.
 *
 * <ol>
 *   <li>Warm the base: hold the bellows (Space or right mouse) and keep the flask's heat inside the
 *       gold band until the water is ready.</li>
 *   <li>For each ingredient a marker sweeps the timing bar: click (or Enter) while it is in the gold
 *       zone. Dead centre is a perfect addition; outside the zone the flask shudders.</li>
 *   <li>After each addition, hold the heat in the (moving) band while it dissolves.</li>
 *   <li>Let it settle: ease off and hold the heat low.</li>
 * </ol>
 *
 * <p>Stability drains while the heat strays from the band (fast if it boils) and with every
 * fumbled addition; at zero the flask cracks and everything is lost. Timing and the stability left
 * at the end decide the potion's quality.</p>
 */
public class BrewingScreen extends Screen {
    private enum Phase { HEAT, ADD, DISSOLVE, SETTLE, DONE, FAILED }

    private record Step(ItemStack stack, int count) {}

    private static final int W = 320, H = 214;
    private static final int GOLD = 0xFFD8B25A, BRASS = 0xFF8A7140, TEXT = 0xFFE6DCC6, MUTED = 0xFF9A9080;

    private final Formula formula;
    private final int nonce;
    private final RandomSource random;
    private final List<Step> steps = new ArrayList<>();

    private Phase phase = Phase.HEAT;
    private int step;
    private int ticks, phaseTicks;
    private float heat = 0.08F, prevHeat = 0.08F, heatVel;
    private float bandCenter, prevBandCenter, bandBase;
    private float stability = 1.0F, prevStability = 1.0F;
    private float progress, prevProgress;
    private float prevLiquidMix;
    private float zoneCenter;
    private int points;
    private boolean bellowsKey, bellowsMouse;
    private boolean sent;
    private int endTicks;
    private int quality;
    private String flash = "";
    private int flashTicks;
    private int flashColor = TEXT;
    private float liquidMix;           // 0 clear water → 1 the potion's colour
    private final List<float[]> bubbles = new ArrayList<>(); // x, y, vy, life
    private final List<float[]> steam = new ArrayList<>();

    public BrewingScreen(Formula formula, int nonce) {
        super(Component.literal("Brewing"));
        this.formula = formula;
        this.nonce = nonce;
        this.random = RandomSource.create(nonce);
        for (Map.Entry<Supplier<? extends Item>, Integer> e : formula.ingredients().entrySet()) {
            steps.add(new Step(new ItemStack(e.getKey().get()), e.getValue()));
        }
        bandBase = 0.45F + random.nextFloat() * 0.15F;
        bandCenter = bandBase;
        prevBandCenter = bandBase;
    }

    /** Opens the minigame when the server says the ingredients are in the flask. */
    static void onClientTick(ClientTickEvent.Post event) {
        BrewStartPayload start = ClientInbox.pollBrew();
        if (start == null) {
            return;
        }
        Formula formula = Formula.byIndex(start.formula());
        if (formula != null) {
            Minecraft.getInstance().setScreen(new BrewingScreen(formula, start.nonce()));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ------------------------------------------------------------------ simulation (20 per second)

    @Override
    public void tick() {
        ticks++;
        phaseTicks++;
        prevHeat = heat;
        prevStability = stability;
        prevBandCenter = bandCenter;
        prevProgress = progress;
        prevLiquidMix = liquidMix;
        for (float[] b : bubbles) {
            b[4] = b[1];
        }
        for (float[] st : steam) {
            st[4] = st[1];
        }
        if (flashTicks > 0) {
            flashTicks--;
        }
        if (phase == Phase.DONE || phase == Phase.FAILED) {
            if (++endTicks > 45) {
                onClose();
            }
            tickParticles();
            return;
        }

        // Bellows push the heat up with a little momentum; the burner cools when left alone.
        boolean pumping = bellowsKey || bellowsMouse;
        heatVel += pumping ? 0.0036F : -0.0022F;
        heatVel *= 0.86F;
        heat = Mth.clamp(heat + heatVel, 0.0F, 1.0F);
        if (heat <= 0.0F || heat >= 1.0F) {
            heatVel = 0.0F;
        }
        if (pumping && ticks % 6 == 0) {
            sound(SoundEvents.FIRECHARGE_USE, 0.6F + heat * 0.3F, 0.12F);
        }

        // The band wanders a little once ingredients are in.
        float drift = phase == Phase.SETTLE ? 0.0F : 0.035F * Math.min(step, 3) * Mth.sin(ticks * 0.04F);
        bandCenter = Mth.clamp(bandBase + drift, 0.12F, 0.86F);
        float half = formula.bandHalfWidth();
        float off = Math.abs(heat - bandCenter) - half;
        boolean inBand = off <= 0.0F;

        // Stability.
        if (!inBand) {
            stability -= 0.0024F + off * 0.035F;
        } else {
            stability = Math.min(1.0F, stability + 0.0008F);
        }
        if (heat > 0.96F) {
            stability -= 0.009F; // boiling over
            if (ticks % 4 == 0) {
                sound(SoundEvents.BUBBLE_COLUMN_WHIRLPOOL_INSIDE, 1.3F, 0.25F);
            }
        }
        if (stability <= 0.0F) {
            stability = 0.0F;
            fail();
            return;
        }

        switch (phase) {
            case HEAT -> {
                if (inBand) {
                    progress += 1.0F / Formula.HEAT_TICKS;
                }
                if (progress >= 1.0F) {
                    say("The water is ready. Add the first ingredient.", GOLD);
                    sound(SoundEvents.BREWING_STAND_BREW, 1.2F, 0.5F);
                    startAdd();
                }
            }
            case ADD -> { /* waiting for the click */ }
            case DISSOLVE -> {
                if (inBand) {
                    progress += 1.0F / Formula.DISSOLVE_TICKS;
                }
                liquidMix = Math.min((step + progress) / (float) steps.size(), liquidMix + 0.01F);
                if (progress >= 1.0F) {
                    step++;
                    if (step < steps.size()) {
                        startAdd();
                    } else {
                        phase = Phase.SETTLE;
                        phaseTicks = 0;
                        progress = 0.0F;
                        bandBase = 0.2F;
                        say("Ease off the bellows. Let it settle.", GOLD);
                    }
                }
            }
            case SETTLE -> {
                if (inBand) {
                    progress += 1.0F / Formula.SETTLE_TICKS;
                }
                if (progress >= 1.0F) {
                    succeed();
                }
            }
            default -> { }
        }
        tickParticles();
    }

    private void startAdd() {
        phase = Phase.ADD;
        phaseTicks = 0;
        progress = 0.0F;
        zoneCenter = 0.22F + random.nextFloat() * 0.56F;
    }

    /** Where the timing marker is, 0..1, sweeping back and forth. */
    private float marker(float partial) {
        float period = formula.sweepTicks();
        float t = ((phaseTicks + partial) % period) / period;
        return t < 0.5F ? t * 2.0F : 2.0F - t * 2.0F;
    }

    private void addIngredient() {
        if (phase != Phase.ADD) {
            return;
        }
        float d = Math.abs(marker(0.0F) - zoneCenter);
        float half = formula.zoneWidth() / 2.0F;
        boolean inBand = Math.abs(heat - bandCenter) <= formula.bandHalfWidth();
        int gained;
        if (d <= half * 0.35F) {
            gained = 2;
        } else if (d <= half) {
            gained = 1;
        } else {
            stability -= 0.16F;
            say("Too early or too late. The flask shudders!", 0xFFE0604A);
            sound(SoundEvents.GLASS_HIT, 0.6F, 0.9F);
            zoneCenter = 0.22F + random.nextFloat() * 0.56F;
            if (stability <= 0.0F) {
                fail();
            }
            return;
        }
        if (!inBand) {
            gained--;
            say(heat > bandCenter ? "In it goes, but the flask was too hot." : "In it goes, but the flask was too cold.", 0xFFE0A84A);
        } else if (gained == 2) {
            say("Perfect!", 0xFFB894FF);
            sound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F, 0.9F);
        } else {
            say("Good.", 0xFFB8E0A0);
        }
        points += Math.max(0, gained);
        sound(SoundEvents.BOTTLE_FILL, 1.0F + step * 0.1F, 0.8F);
        for (int i = 0; i < 12; i++) {
            bubbles.add(new float[] {random.nextFloat() * 2 - 1, 0.0F, 0.6F + random.nextFloat() * 0.8F, 1.0F, 0.0F});
        }
        phase = Phase.DISSOLVE;
        phaseTicks = 0;
        progress = 0.0F;
        bandBase = Mth.clamp(bandBase + (random.nextBoolean() ? 1 : -1) * (0.08F + random.nextFloat() * 0.12F), 0.3F, 0.78F);
    }

    private void succeed() {
        float timing = steps.isEmpty() ? 1.0F : points / (2.0F * steps.size());
        float score = 0.65F * timing + 0.35F * stability;
        quality = score >= 0.8F ? 3 : score >= 0.55F ? 2 : score >= 0.3F ? 1 : 0;
        phase = Phase.DONE;
        liquidMix = 1.0F;
        sound(SoundEvents.BREWING_STAND_BREW, 1.0F, 1.0F);
        sound(SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F + quality * 0.15F, 1.0F);
        send(quality);
    }

    private void fail() {
        phase = Phase.FAILED;
        sound(SoundEvents.GLASS_BREAK, 0.9F, 1.0F);
        sound(SoundEvents.FIRE_EXTINGUISH, 0.8F, 0.6F);
        send(BrewResultPayload.RUINED);
    }

    private void send(int result) {
        if (!sent) {
            sent = true;
            PacketDistributor.sendToServer(new BrewResultPayload(nonce, result));
        }
    }

    private void say(String text, int color) {
        flash = text;
        flashColor = color;
        flashTicks = 40;
    }

    private void sound(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, volume));
    }

    private void tickParticles() {
        float rate = phase == Phase.FAILED ? 0.0F : heat * heat * 1.6F;
        while (random.nextFloat() < rate) {
            bubbles.add(new float[] {random.nextFloat() * 2 - 1, 0.0F, 0.4F + heat * 1.2F, 1.0F, 0.0F});
            rate -= 1.0F;
        }
        if (heat > 0.65F && random.nextFloat() < heat - 0.55F) {
            steam.add(new float[] {random.nextFloat() * 2 - 1, 0.0F, 0.5F + random.nextFloat() * 0.5F, 1.0F, 0.0F});
        }
        for (Iterator<float[]> it = bubbles.iterator(); it.hasNext(); ) {
            float[] b = it.next();
            b[1] += b[2] * 0.035F;
            b[3] -= 0.03F;
            if (b[1] > 1.0F || b[3] <= 0.0F) {
                it.remove();
            }
        }
        for (Iterator<float[]> it = steam.iterator(); it.hasNext(); ) {
            float[] s = it.next();
            s[1] += s[2] * 0.04F;
            s[0] += (random.nextFloat() - 0.5F) * 0.1F;
            s[3] -= 0.025F;
            if (s[3] <= 0.0F) {
                it.remove();
            }
        }
        if (bubbles.size() > 80) {
            bubbles.subList(0, bubbles.size() - 80).clear();
        }
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) {
            bellowsKey = true;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            addIngredient();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE) {
            bellowsKey = false;
            return true;
        }
        return super.keyReleased(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            addIngredient();
            return true;
        }
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            bellowsMouse = true;
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            bellowsMouse = false;
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override
    public void onClose() {
        if (!sent) {
            send(BrewResultPayload.RUINED); // walking away from a flask on the flame ruins it
        }
        super.onClose();
    }

    @Override
    public void removed() {
        if (!sent) {
            send(BrewResultPayload.RUINED);
        }
        super.removed();
    }

    // ------------------------------------------------------------------ drawing

    /** A plain dark veil: the blurred world behind a screen costs too much every frame. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {
        g.fill(0, 0, width, height, 0x90080610);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        super.render(g, mouseX, mouseY, partial);
        // Draw every rectangle in one batch; GuiGraphics otherwise flushes after each fill.
        g.drawManaged(() -> drawScene(g, partial));
        drawIngredients(g, (width - W) / 2, (height - H) / 2);
    }

    private void drawScene(GuiGraphics g, float partial) {
        int x0 = (width - W) / 2;
        int y0 = (height - H) / 2;
        float h = Mth.lerp(partial, prevHeat, heat);
        float st = Mth.lerp(partial, prevStability, stability);
        float band = Mth.lerp(partial, prevBandCenter, bandCenter);
        float prog = Mth.lerp(partial, prevProgress, progress);
        float mix = Mth.lerp(partial, prevLiquidMix, liquidMix);

        // Panel: dark glass with brass edges, like the HUD.
        g.fill(x0, y0, x0 + W, y0 + H, 0xF0141018);
        g.fill(x0, y0, x0 + W, y0 + 1, BRASS);
        g.fill(x0, y0 + H - 1, x0 + W, y0 + H, BRASS);
        g.fill(x0, y0, x0 + 1, y0 + H, BRASS);
        g.fill(x0 + W - 1, y0, x0 + W, y0 + H, BRASS);
        g.fill(x0 + 3, y0 + 3, x0 + W - 3, y0 + 4, 0x608A7140);

        String title = "Brewing: " + Component.translatable(formula.result().getDescriptionId()).getString();
        g.drawCenteredString(font, title, x0 + W / 2, y0 + 8, GOLD);
        g.drawCenteredString(font, phaseText(), x0 + W / 2, y0 + 20, MUTED);

        drawThermometer(g, x0 + 22, y0 + 54, h, band);
        drawStability(g, x0 + W - 34, y0 + 54, st);
        drawFlask(g, x0 + W / 2, y0 + 150, h, partial, mix);

        // Steady-phase progress under the flask.
        if (phase == Phase.HEAT || phase == Phase.DISSOLVE || phase == Phase.SETTLE) {
            int bx = x0 + W / 2 - 50;
            g.fill(bx, y0 + 168, bx + 100, y0 + 171, 0xFF1B1622);
            g.fill(bx, y0 + 168, bx + (int) (100 * Mth.clamp(prog, 0, 1)), y0 + 171, GOLD);
        }
        if (phase == Phase.ADD) {
            drawTimingBar(g, x0 + 50, y0 + 165, W - 100, partial);
        }

        if (flashTicks > 0 || phase == Phase.DONE || phase == Phase.FAILED) {
            String text = phase == Phase.DONE ? qualityText() : phase == Phase.FAILED ? "The flask cracks!" : flash;
            int color = phase == Phase.DONE ? 0xFFB894FF : phase == Phase.FAILED ? 0xFFE0604A : flashColor;
            int alpha = phase == Phase.DONE || phase == Phase.FAILED ? 255 : (int) (255 * Math.min(1.0F, flashTicks / 10.0F));
            g.drawCenteredString(font, text, x0 + W / 2, y0 + 58, (alpha << 24) | (color & 0xFFFFFF));
        }

        g.drawCenteredString(font, "Hold SPACE / right mouse: bellows   ·   Click / ENTER: add   ·   ESC: abandon",
                x0 + W / 2, y0 + H - 14, 0xFF6E6658);
    }

    private String phaseText() {
        return switch (phase) {
            case HEAT -> "Warm the water. Keep the heat in the gold band.";
            case ADD -> "Add " + steps.get(step).count() + " × " + steps.get(step).stack().getHoverName().getString()
                    + " when the marker is in the gold.";
            case DISSOLVE -> "Hold the heat while it dissolves.";
            case SETTLE -> "Let it settle. Keep the heat low.";
            case DONE -> "The potion is ready.";
            case FAILED -> "The ingredients are lost.";
        };
    }

    private String qualityText() {
        return switch (quality) {
            case 0 -> "Rough brew";
            case 2 -> "Fine brew";
            case 3 -> "Perfect brew!";
            default -> "Ordinary brew";
        };
    }

    private void drawIngredients(GuiGraphics g, int x0, int y0) {
        int n = steps.size();
        int startX = x0 + W / 2 - (n * 26) / 2;
        for (int i = 0; i < n; i++) {
            int ix = startX + i * 26 + 4;
            int iy = y0 + 32;
            boolean current = i == step && (phase == Phase.ADD || phase == Phase.DISSOLVE);
            boolean done = i < step || phase == Phase.SETTLE || phase == Phase.DONE;
            g.fill(ix - 2, iy - 2, ix + 18, iy + 18, current ? GOLD : done ? 0xFF3C5A3C : 0xFF2A2430);
            g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0xFF15121A);
            g.renderItem(steps.get(i).stack(), ix, iy);
            if (steps.get(i).count() > 1) {
                g.drawString(font, "×" + steps.get(i).count(), ix + 10, iy + 10, TEXT, true);
            }
            if (done) {
                g.fill(ix - 1, iy - 1, ix + 17, iy + 17, 0x70102010);
                g.drawString(font, "✔", ix + 5, iy + 4, 0xFF9BE08C, true);
            }
        }
    }

    private void drawThermometer(GuiGraphics g, int x, int y, float h, float band) {
        int height = 96;
        g.drawCenteredString(font, "Heat", x + 5, y - 11, MUTED);
        g.fill(x - 1, y - 1, x + 11, y + height + 1, BRASS);
        g.fill(x, y, x + 10, y + height, 0xFF0E0C12);
        float half = formula.bandHalfWidth();
        int bandTop = y + height - Math.round(height * Math.min(1.0F, band + half));
        int bandBottom = y + height - Math.round(height * Math.max(0.0F, band - half));
        g.fill(x, bandTop, x + 10, bandBottom, 0x60D8B25A);
        g.fill(x, bandTop, x + 10, bandTop + 1, GOLD);
        g.fill(x, bandBottom - 1, x + 10, bandBottom, GOLD);
        int top = y + height - (int) (height * h);
        int mercury = h > 0.96F ? 0xFFFF5030 : 0xFFC8302C;
        g.fill(x + 3, top, x + 7, y + height, mercury);
        g.fill(x + 3, top, x + 4, y + height, 0x60FFFFFF);
        // the bulb
        g.fill(x - 2, y + height, x + 12, y + height + 9, BRASS);
        g.fill(x, y + height + 1, x + 10, y + height + 8, mercury);
        for (int i = 1; i < 5; i++) {
            g.fill(x + 10, y + height * i / 5, x + 13, y + height * i / 5 + 1, BRASS);
        }
    }

    private void drawStability(GuiGraphics g, int x, int y, float st) {
        int height = 96;
        g.drawCenteredString(font, "Stability", x + 5, y - 11, MUTED);
        g.fill(x - 1, y - 1, x + 11, y + height + 1, BRASS);
        g.fill(x, y, x + 10, y + height, 0xFF0E0C12);
        int color = st > 0.6F ? 0xFF6FB36A : st > 0.3F ? 0xFFD8A23A : 0xFFC8302C;
        int top = y + height - (int) (height * st);
        g.fill(x + 1, top, x + 9, y + height, color);
        g.fill(x + 1, top, x + 2, y + height, 0x50FFFFFF);
    }

    private void drawTimingBar(GuiGraphics g, int x, int y, int width, float partial) {
        g.fill(x - 1, y - 1, x + width + 1, y + 11, BRASS);
        g.fill(x, y, x + width, y + 10, 0xFF0E0C12);
        float half = formula.zoneWidth() / 2.0F;
        int z0 = x + (int) (width * (zoneCenter - half));
        int z1 = x + (int) (width * (zoneCenter + half));
        g.fill(z0, y, z1, y + 10, 0xA0D8B25A);
        int p0 = x + (int) (width * (zoneCenter - half * 0.35F));
        int p1 = x + (int) (width * (zoneCenter + half * 0.35F));
        g.fill(p0, y, p1, y + 10, 0xFFF0D890);
        int m = x + (int) (width * marker(partial));
        g.fill(m - 1, y - 3, m + 1, y + 13, 0xFFFFFFFF);
    }

    private void drawFlask(GuiGraphics g, int cx, int bottom, float h, float partial, float mix) {
        int r = 30;
        int cy = bottom - r - 8;
        int water = 0xFF6E9CC0;
        int potion = 0xFF000000 | formula.smokeColor();
        int liquid = lerpColor(water, potion, mix);
        if (phase == Phase.FAILED) {
            liquid = 0xFF3A3236;
        }
        float surface = 0.32F + 0.04F * Mth.sin((ticks + partial) * 0.25F) * h;

        // Burner: brass stand and a flame that grows with the heat.
        g.fill(cx - 22, bottom + 4, cx + 22, bottom + 8, BRASS);
        g.fill(cx - 18, bottom - 2, cx - 15, bottom + 4, BRASS);
        g.fill(cx + 15, bottom - 2, cx + 18, bottom + 4, BRASS);
        if (phase != Phase.FAILED && phase != Phase.DONE) {
            float time = ticks + partial;
            int flame = 3 + (int) (h * 12 + 1.5F + 1.5F * Mth.sin(time * 0.9F) * Mth.sin(time * 0.37F));
            for (int i = 0; i < flame; i++) {
                int w = Math.max(1, (flame - i) / 2);
                int color = i < flame / 3 ? 0xFF3A6CF0 : i < flame * 2 / 3 ? 0xFFFFB040 : 0xFFFFE890;
                g.fill(cx - w, bottom + 3 - i, cx + w, bottom + 4 - i, color);
            }
        }

        // Neck.
        int neckW = 7;
        g.fill(cx - neckW - 1, cy - r - 30, cx + neckW + 1, cy - r + 4, 0x90C8DCE6);
        g.fill(cx - neckW, cy - r - 30, cx + neckW, cy - r + 4, 0x30C8DCE6);
        g.fill(cx - neckW - 2, cy - r - 32, cx + neckW + 2, cy - r - 30, 0xB0C8DCE6);

        // Round body, row by row, with the liquid in it.
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.sqrt(r * r - dy * dy);
            int y = cy + dy;
            float fromTop = (dy + r) / (2.0F * r);
            boolean wet = fromTop >= surface;
            int fill = wet ? liquid : 0x30C8DCE6;
            if (wet) {
                float depth = (fromTop - surface) / (1.0F - surface);
                fill = shade(liquid, 1.1F - depth * 0.35F);
            }
            g.fill(cx - half, y, cx + half, y + 1, fill);
            g.fill(cx - half - 1, y, cx - half, y + 1, 0x90C8DCE6);
            g.fill(cx + half, y, cx + half + 1, y + 1, 0x90C8DCE6);
        }
        g.fill(cx - 18, cy - 14, cx - 15, cy + 2, 0x60FFFFFF); // glare
        if (phase == Phase.FAILED) {
            for (int i = 0; i < 14; i++) {
                g.fill(cx - 4 + i, cy - 10 + (i * 7) % 18, cx - 3 + i, cy - 8 + (i * 7) % 18, 0xFFEEEEEE);
            }
        }

        // Bubbles rise through the liquid; steam rises from the neck.
        for (float[] b : bubbles) {
            float yy = 1.0F - Mth.lerp(partial, b[4], b[1]);
            float top = surface;
            if (yy < top) {
                continue;
            }
            int bx = cx + (int) (b[0] * r * 0.75F * Math.sqrt(1 - Math.pow((yy - 0.5F) * 2, 2)));
            int by = cy - r + (int) (yy * 2 * r);
            g.fill(bx, by, bx + 2, by + 2, 0xB0FFFFFF);
        }
        for (float[] s : steam) {
            int sx = cx + (int) (s[0] * 6);
            int sy = cy - r - 32 - (int) (Mth.lerp(partial, s[4], s[1]) * 30);
            int a = (int) (110 * s[3]);
            g.fill(sx - 2, sy - 2, sx + 2, sy + 2, (a << 24) | 0xD0D0D8);
        }
    }

    private static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int r = (int) Mth.lerp(t, (a >> 16) & 255, (b >> 16) & 255);
        int gg = (int) Mth.lerp(t, (a >> 8) & 255, (b >> 8) & 255);
        int bb = (int) Mth.lerp(t, a & 255, b & 255);
        return 0xFF000000 | (r << 16) | (gg << 8) | bb;
    }

    private static int shade(int c, float k) {
        int r = Mth.clamp((int) (((c >> 16) & 255) * k), 0, 255);
        int gg = Mth.clamp((int) (((c >> 8) & 255) * k), 0, 255);
        int b = Mth.clamp((int) ((c & 255) * k), 0, 255);
        return 0xFF000000 | (r << 16) | (gg << 8) | b;
    }
}
