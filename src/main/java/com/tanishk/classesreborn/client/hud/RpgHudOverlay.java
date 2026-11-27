package com.tanishk.classesreborn.client.hud;

import com.tanishk.classesreborn.client.gui.RpgUi;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.util.Mth;

public class RpgHudOverlay implements LayeredDraw.Layer {
    // ---- Layout ----
    private static final int MARGIN = 14;   // leaves room for the flames above the card
    private static final int FRAME = 3;     // stone frame thickness
    private static final int PAD = 5;
    private static final int PLANK_H = 14;
    private static final int BAR_W = 100;
    private static final int BAR_H = 6;

    // ---- Colors (ARGB) ----
    private static final int COL_ACCENT   = 0xFFFFD700;
    private static final int COL_TEXT     = 0xFFE0E0E0;
    private static final int COL_TEXT_DIM = 0xFFA0A0A0;

    // Smoothed state
    private float shownFraction = 0f;
    private int lastLevel = -1;
    private float levelUpFlare = 0f; // 1.0 right after a level-up, decays to 0

    @Override
    public void render(GuiGraphics g, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) return;
        if (mc.getDebugOverlay().showDebugScreen()) return; // don't overlap F3

        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();
        if (rpgClass == null) return;

        Font font = mc.font;
        float dt = deltaTracker.getGameTimeDeltaTicks();

        int level = data.getLevel();
        int xp = data.getXp();
        int needed = data.getXpForNextLevel();
        float target = needed > 0 ? Mth.clamp((float) xp / needed, 0f, 1f) : 1f;

        // Ease the XP bar toward the real value; snap + flare the fire on level change
        if (level != lastLevel) {
            if (lastLevel != -1 && level > lastLevel) levelUpFlare = 1f;
            shownFraction = target;
            lastLevel = level;
        } else {
            shownFraction = Mth.lerp(Math.min(1f, 0.15f * dt), shownFraction, target);
        }
        levelUpFlare = Math.max(0f, levelUpFlare - 0.02f * dt);

        String name = rpgClass.getDisplayName().getString();
        String lvl = "Lv." + level;
        String xpText = xp + " / " + needed + " XP";
        int sp = data.getSkillPoints();
        String spText = sp > 0 ? "+" + sp + " SP [K]" : null; // change the key hint to your keybind

        // ---- Sizes ----
        int line1W = font.width(name) + 10 + font.width(lvl);
        int innerW = Math.max(BAR_W, line1W);
        int w = innerW + (FRAME + PAD) * 2;
        int boardH = PAD + BAR_H + 3 + 9 + 4 + (spText != null ? 11 : 0);
        int h = FRAME + PLANK_H + 1 + boardH + FRAME;

        int x = MARGIN, y = MARGIN;
        float t = RpgUi.time();

        // Small fire normally, brighter with unspent points, big burst on level-up
        float intensity = 0.35f + (sp > 0 ? 0.2f : 0f) + 0.7f * levelUpFlare;

        // ---- Frame: fire + stone + glowing rim ----
        RpgUi.drawPanel(g, x, y, w, h, t, intensity);

        // ---- Wooden header plank: class name + level ----
        int plankX = x + FRAME, plankY = y + FRAME, plankW = w - FRAME * 2;
        RpgUi.drawPlank(g, plankX, plankY, plankW, PLANK_H);
        int textY = plankY + (PLANK_H - 8) / 2;
        g.drawString(font, name, plankX + PAD, textY, COL_ACCENT, true);
        g.drawString(font, lvl, plankX + plankW - PAD - font.width(lvl), textY, COL_TEXT, true);

        // ---- Sunken board ----
        int boardX = x + FRAME;
        int boardY = plankY + PLANK_H + 1;
        RpgUi.drawBoard(g, boardX, boardY, w - FRAME * 2, boardH);

        int cx = boardX + PAD;
        int cy = boardY + PAD;
        int barW = w - (FRAME + PAD) * 2;

        // XP bar: ember gradient with segment ticks and a flickering leading edge
        g.fill(cx - 1, cy - 1, cx + barW + 1, cy + BAR_H + 1, 0xFF000000);
        g.fill(cx, cy, cx + barW, cy + BAR_H, 0xFF1A0A0A);
        int filled = (int) (barW * shownFraction);
        if (filled > 0) {
            g.fillGradient(cx, cy, cx + filled, cy + BAR_H, 0xFFFFC830, 0xFFD04010);
            g.fill(cx, cy, cx + filled, cy + 1, 0x66FFFFFF); // highlight
            if (filled < barW) {
                int flicker = (int) (160 + 95 * (0.5f + 0.5f * Mth.sin(t * 12f)));
                g.fill(cx + filled - 1, cy, cx + filled, cy + BAR_H, (flicker << 24) | 0xFFEE88);
            }
        }
        for (int i = 1; i <= 3; i++) { // quarter marks
            g.fill(cx + barW * i / 4, cy, cx + barW * i / 4 + 1, cy + BAR_H, 0x66000000);
        }
        cy += BAR_H + 3;

        // XP text
        g.drawString(font, xpText, cx, cy, COL_TEXT_DIM, false);
        cy += 13;

        // Pulsing skill point hint
        if (spText != null) {
            float pulse = 0.65f + 0.35f * Mth.sin(
                    (mc.player.tickCount + deltaTracker.getGameTimeDeltaPartialTick(false)) * 0.2f);
            int alpha = (int) (255 * pulse);
            g.drawString(font, spText, cx, cy - 2, (alpha << 24) | 0x55DD55, true);
        }
    }
}