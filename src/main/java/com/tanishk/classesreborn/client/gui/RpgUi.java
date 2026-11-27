package com.tanishk.classesreborn.client.gui;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Shared drawing helpers for the stone / wood / fire "battle board" look.
 * Swap the TEX_* constants for your own textures to re-skin every screen at once.
 */
public final class RpgUi {
    private RpgUi() {}

    public static final ResourceLocation TEX_STONE  = ResourceLocation.withDefaultNamespace("textures/block/stone_bricks.png");
    public static final ResourceLocation TEX_BOARD  = ResourceLocation.withDefaultNamespace("textures/block/polished_deepslate.png");
    public static final ResourceLocation TEX_PLANKS = ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png");

    /** Seconds, wrapping every 100s, used to animate flames. */
    public static float time() {
        return (Util.getMillis() % 100_000L) / 1000f;
    }

    // ---- Basic helpers ----
    /** Tiles a 16x16 texture over a rectangle, trimming edge tiles (no scissor needed). */
    public static void tile(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        for (int ty = y; ty < y + h; ty += 16) {
            int ch = Math.min(16, y + h - ty);
            for (int tx = x; tx < x + w; tx += 16) {
                int cw = Math.min(16, x + w - tx);
                gg.blit(tex, tx, ty, 0f, 0f, cw, ch, 16, 16);
            }
        }
    }

    public static void bevel(GuiGraphics gg, int x, int y, int w, int h, int light, int dark) {
        gg.fill(x, y, x + w, y + 1, light);
        gg.fill(x, y, x + 1, y + h, light);
        gg.fill(x, y + h - 1, x + w, y + h, dark);
        gg.fill(x + w - 1, y, x + w, y + h, dark);
    }

    public static void nail(GuiGraphics gg, int nx, int ny) {
        gg.fill(nx + 1, ny + 1, nx + 3, ny + 3, 0xAA000000); // shadow
        gg.fill(nx, ny, nx + 2, ny + 2, 0xFFB8B8B8);
    }

    // ---- Composite pieces ----
    /** Fire + stone frame + glowing rim. Draw the plank/board on top afterwards. */
    public static void drawPanel(GuiGraphics gg, int x, int y, int w, int h, float t, float fireIntensity) {
        drawFire(gg, x, y, w, h, t, fireIntensity);

        tile(gg, TEX_STONE, x, y, w, h);
        gg.fill(x, y, x + w, y + h, 0x55000000);
        bevel(gg, x, y, w, h, 0x66FFFFFF, 0xAA000000);

        float pulse = 0.5f + 0.5f * Mth.sin(t * 4f);
        int glow = 0xFF000000 | (255 << 16) | ((int) (100 + 80 * pulse) << 8) | 20;
        gg.renderOutline(x - 1, y - 1, w + 2, h + 2, glow);
        gg.renderOutline(x, y, w, h, (glow & 0x00FFFFFF) | 0x88000000);
    }

    /** Wooden plank strip (headers). */
    public static void drawPlank(GuiGraphics gg, int x, int y, int w, int h) {
        tile(gg, TEX_PLANKS, x, y, w, h);
        gg.fill(x, y, x + w, y + h, 0x66000000);
        bevel(gg, x, y, w, h, 0x55FFFFFF, 0xAA000000);
    }

    /** Sunken dark board (content area). */
    public static void drawBoard(GuiGraphics gg, int x, int y, int w, int h) {
        tile(gg, TEX_BOARD, x, y, w, h);
        gg.fill(x, y, x + w, y + h, 0x80000000);
        bevel(gg, x, y, w, h, 0xAA000000, 0x44FFFFFF); // inverted = sunken
    }

    // ---- Fire ----
    private static int flameHeight(int pos, float t, float phase, float intensity) {
        float a = 0.5f + 0.5f * Mth.sin(pos * 0.35f + t * 7f + phase);
        float b = 0.5f + 0.5f * Mth.sin(pos * 0.9f - t * 11f + phase * 1.7f);
        return Math.max(2, (int) ((5 + 8 * a + 5 * b) * intensity));
    }

    private static void layerV(GuiGraphics gg, int x, int w, int edgeY, int h, int dir, int color) {
        if (dir < 0) gg.fill(x, edgeY - h, x + w, edgeY, color);
        else gg.fill(x, edgeY, x + w, edgeY + h, color);
    }

    private static void layerH(GuiGraphics gg, int y, int h, int edgeX, int len, int dir, int color) {
        if (dir < 0) gg.fill(edgeX - len, y, edgeX, y + h, color);
        else gg.fill(edgeX, y, edgeX + len, y + h, color);
    }

    private static void flameV(GuiGraphics gg, int x, int w, int edgeY, int h, int dir) {
        layerV(gg, x, w, edgeY, h, dir, 0xB0B01808);                 // deep red
        layerV(gg, x, w, edgeY, (int) (h * 0.70f), dir, 0xD8FF5A10); // orange
        layerV(gg, x, w, edgeY, (int) (h * 0.40f), dir, 0xEEFFC830); // yellow core
    }

    private static void flameH(GuiGraphics gg, int y, int h, int edgeX, int len, int dir) {
        layerH(gg, y, h, edgeX, len, dir, 0xB0B01808);
        layerH(gg, y, h, edgeX, (int) (len * 0.70f), dir, 0xD8FF5A10);
        layerH(gg, y, h, edgeX, (int) (len * 0.40f), dir, 0xEEFFC830);
    }

    public static void drawFire(GuiGraphics gg, int x, int y, int w, int h, float t, float intensity) {
        final int step = 3;
        for (int dx = 0; dx < w; dx += step) {
            flameV(gg, x + dx, step, y, flameHeight(dx, t, 0f, intensity), -1);
            flameV(gg, x + dx, step, y + h, (int) (flameHeight(dx, t, 2.1f, intensity) * 0.55f), +1);
        }
        for (int dy = 0; dy < h; dy += step) {
            flameH(gg, y + dy, step, x, (int) (flameHeight(dy, t, 4.2f, intensity) * 0.6f), -1);
            flameH(gg, y + dy, step, x + w, (int) (flameHeight(dy, t, 6.3f, intensity) * 0.6f), +1);
        }

        // Rising embers
        for (int i = 0; i < 14; i++) {
            float seed = i * 0.6180339f;
            float phase = (t * 0.5f + seed) % 1f;
            float px = x + ((seed * 7.13f) % 1f) * w + Mth.sin(t * 3f + i) * 4f;
            float py = y - 6 - phase * 46f;
            int alpha = (int) (255 * (1f - phase));
            gg.fill((int) px, (int) py, (int) px + 2, (int) py + 2, (alpha << 24) | 0xFFB040);
        }
    }
}