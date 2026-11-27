package com.tanishk.classesreborn.client.gui;

import com.tanishk.classesreborn.network.CastSkillPayload;
import com.tanishk.classesreborn.network.UnlockSkillPayload;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.Skill;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import com.tanishk.classesreborn.rpg.skill.SkillTier;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class SkillTreeScreen extends Screen {
    // ---- Layout ----
    private static final int PANEL_W = 320;
    private static final int PANEL_H = 244;
    private static final int FRAME = 8;          // stone frame thickness
    private static final int PLANK_H = 22;       // header plank height
    private static final int ROW_H = 34;
    private static final int ROW_GAP = 4;
    private static final int PILL_W = 54;
    private static final int PILL_H = 16;
    private static final int PLUS_SIZE = 16;

    /** UI guard only: keep in sync with the server-side max skill level. */
    private static final int MAX_SKILL_LEVEL = 30;

    // ---- Textures (vanilla, so no assets needed). Swap for your own in assets/classesreborn/textures/gui/ ----
    private static final ResourceLocation TEX_STONE  = ResourceLocation.withDefaultNamespace("textures/block/stone_bricks.png");
    private static final ResourceLocation TEX_BOARD  = ResourceLocation.withDefaultNamespace("textures/block/polished_deepslate.png");
    private static final ResourceLocation TEX_PLANKS = ResourceLocation.withDefaultNamespace("textures/block/dark_oak_planks.png");

    // ---- Colors (ARGB) ----
    private static final int COL_ACCENT    = 0xFFFFD700;
    private static final int COL_TEXT      = 0xFFE0E0E0;
    private static final int COL_TEXT_DIM  = 0xFF909090;
    private static final int COL_UNLOCKED  = 0xFF55DD55;
    private static final int COL_AVAILABLE = 0xFFFFD700;
    private static final int COL_LOCKED    = 0xFF6A6A6A;

    private final List<SkillRow> rows = new ArrayList<>();
    private int left, top;
    private int boardX, boardY, boardW, boardH;
    private int viewTop, viewBottom;
    private double scroll = 0;
    private int lastSnapshot = 0;
    private float fireIntensity = 0.6f;

    public SkillTreeScreen() {
        super(Component.translatable("gui.classesreborn.skills.title"));
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        this.boardX = left + FRAME;
        this.boardY = top + FRAME + PLANK_H + 2;
        this.boardW = PANEL_W - FRAME * 2;
        this.boardH = (top + PANEL_H - FRAME - 28) - boardY;
        this.viewTop = boardY + 26;
        this.viewBottom = boardY + boardH - 4;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(left + PANEL_W / 2 - 50, top + PANEL_H - FRAME - 24, 100, 20).build());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();
        if (rpgClass == null) return;

        this.lastSnapshot = snapshot(data, rpgClass);

        int rowW = boardW - 12 - 8; // side padding + scrollbar room
        int maxTier = -1;
        int i = 0;
        for (Skill skill : SkillRegistry.getForClass(rpgClass)) {
            boolean unlocked = data.isSkillUnlocked(skill.getId());
            int level = unlocked ? Math.max(1, data.getSkillLevel(skill.getId())) : 0;
            boolean canUnlock = !unlocked && data.getSkillPoints() > 0
                    && data.getLevel() >= skill.getRequiredLevel();
            boolean canUpgrade = unlocked && data.getSkillPoints() > 0 && level < MAX_SKILL_LEVEL;
            if (unlocked) maxTier = Math.max(maxTier, SkillTier.fromSkillLevel(level).tierIndex());

            SkillRow row = new SkillRow(boardX + 6, rowW, i++, skill, level, unlocked, canUnlock, canUpgrade);
            this.rows.add(row);
            this.addWidget(row); // events only; rendered manually inside a scissor
        }
        // The fire grows stronger as your best skill climbs the tiers
        this.fireIntensity = maxTier < 0 ? 0.6f : 0.75f + 0.1f * maxTier;
        clampScroll();
    }

    /** Cheap fingerprint of the data we display, so we can refresh when the server syncs. */
    private static int snapshot(PlayerRpgData data, RpgClass rpgClass) {
        int h = data.getLevel() * 31 + data.getSkillPoints();
        for (Skill s : SkillRegistry.getForClass(rpgClass)) {
            h = h * 31 + (data.isSkillUnlocked(s.getId()) ? 1 : 0);
            h = h * 31 + data.getSkillLevel(s.getId());
        }
        return h;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();
        if (rpgClass != null && snapshot(data, rpgClass) != lastSnapshot) {
            this.rebuildWidgets();
        }
    }

    // ---- Scrolling ----
    private int contentHeight() {
        return rows.isEmpty() ? 0 : rows.size() * (ROW_H + ROW_GAP) - ROW_GAP;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (viewBottom - viewTop));
    }

    private void clampScroll() {
        this.scroll = Mth.clamp(scroll, 0, maxScroll());
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (maxScroll() > 0) {
            this.scroll -= scrollY * 14;
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mx, my, scrollX, scrollY);
    }

    // ---- Drawing helpers ----
    /** Tiles a 16x16 texture over a rectangle, trimming edge tiles (no scissor needed). */
    private static void tile(GuiGraphics gg, ResourceLocation tex, int x, int y, int w, int h) {
        for (int ty = y; ty < y + h; ty += 16) {
            int ch = Math.min(16, y + h - ty);
            for (int tx = x; tx < x + w; tx += 16) {
                int cw = Math.min(16, x + w - tx);
                gg.blit(tex, tx, ty, 0f, 0f, cw, ch, 16, 16);
            }
        }
    }

    private static void bevel(GuiGraphics gg, int x, int y, int w, int h, int light, int dark) {
        gg.fill(x, y, x + w, y + 1, light);
        gg.fill(x, y, x + 1, y + h, light);
        gg.fill(x, y + h - 1, x + w, y + h, dark);
        gg.fill(x + w - 1, y, x + w, y + h, dark);
    }

    private static int flameHeight(int pos, float t, float phase, float intensity) {
        float a = 0.5f + 0.5f * Mth.sin(pos * 0.35f + t * 7f + phase);
        float b = 0.5f + 0.5f * Mth.sin(pos * 0.9f - t * 11f + phase * 1.7f);
        return Math.max(2, (int) ((5 + 8 * a + 5 * b) * intensity));
    }

    /** Vertical flame column growing up (dir=-1) or down (dir=+1) from edgeY. */
    private static void flameV(GuiGraphics gg, int x, int w, int edgeY, int h, int dir) {
        layerV(gg, x, w, edgeY, h, dir, 0xB0B01808);                 // deep red
        layerV(gg, x, w, edgeY, (int) (h * 0.70f), dir, 0xD8FF5A10); // orange
        layerV(gg, x, w, edgeY, (int) (h * 0.40f), dir, 0xEEFFC830); // yellow core
    }

    private static void layerV(GuiGraphics gg, int x, int w, int edgeY, int h, int dir, int color) {
        if (dir < 0) gg.fill(x, edgeY - h, x + w, edgeY, color);
        else gg.fill(x, edgeY, x + w, edgeY + h, color);
    }

    /** Horizontal flame row growing left (dir=-1) or right (dir=+1) from edgeX. */
    private static void flameH(GuiGraphics gg, int y, int h, int edgeX, int len, int dir) {
        layerH(gg, y, h, edgeX, len, dir, 0xB0B01808);
        layerH(gg, y, h, edgeX, (int) (len * 0.70f), dir, 0xD8FF5A10);
        layerH(gg, y, h, edgeX, (int) (len * 0.40f), dir, 0xEEFFC830);
    }

    private static void layerH(GuiGraphics gg, int y, int h, int edgeX, int len, int dir, int color) {
        if (dir < 0) gg.fill(edgeX - len, y, edgeX, y + h, color);
        else gg.fill(edgeX, y, edgeX + len, y + h, color);
    }

    private void drawFire(GuiGraphics gg, float t) {
        final int step = 3;
        for (int dx = 0; dx < PANEL_W; dx += step) {
            int x = left + dx;
            flameV(gg, x, step, top, flameHeight(dx, t, 0f, fireIntensity), -1);
            flameV(gg, x, step, top + PANEL_H,
                    (int) (flameHeight(dx, t, 2.1f, fireIntensity) * 0.55f), +1);
        }
        for (int dy = 0; dy < PANEL_H; dy += step) {
            int y = top + dy;
            flameH(gg, y, step, left, (int) (flameHeight(dy, t, 4.2f, fireIntensity) * 0.6f), -1);
            flameH(gg, y, step, left + PANEL_W, (int) (flameHeight(dy, t, 6.3f, fireIntensity) * 0.6f), +1);
        }

        // Rising embers
        for (int i = 0; i < 14; i++) {
            float seed = i * 0.6180339f;
            float phase = (t * 0.5f + seed) % 1f;
            float px = left + ((seed * 7.13f) % 1f) * PANEL_W + Mth.sin(t * 3f + i) * 4f;
            float py = top - 6 - phase * 46f;
            int alpha = (int) (255 * (1f - phase));
            gg.fill((int) px, (int) py, (int) px + 2, (int) py + 2, (alpha << 24) | 0xFFB040);
        }
    }

    // ---- Rendering ----
    @Override
    public void renderBackground(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(gg, mouseX, mouseY, partialTick);
        float t = (Util.getMillis() % 100_000L) / 1000f;

        // 1. Fire behind the panel
        drawFire(gg, t);

        // 2. Stone frame
        tile(gg, TEX_STONE, left, top, PANEL_W, PANEL_H);
        gg.fill(left, top, left + PANEL_W, top + PANEL_H, 0x55000000);
        bevel(gg, left, top, PANEL_W, PANEL_H, 0x66FFFFFF, 0xAA000000);

        // 3. Wooden header plank
        int plankX = left + FRAME, plankY = top + FRAME, plankW = PANEL_W - FRAME * 2;
        tile(gg, TEX_PLANKS, plankX, plankY, plankW, PLANK_H);
        gg.fill(plankX, plankY, plankX + plankW, plankY + PLANK_H, 0x66000000);
        bevel(gg, plankX, plankY, plankW, PLANK_H, 0x55FFFFFF, 0xAA000000);
        gg.drawCenteredString(this.font, this.title.copy().withStyle(s -> s.withBold(true)),
                left + PANEL_W / 2, plankY + (PLANK_H - 8) / 2, COL_ACCENT);

        // 4. Recessed board
        tile(gg, TEX_BOARD, boardX, boardY, boardW, boardH);
        gg.fill(boardX, boardY, boardX + boardW, boardY + boardH, 0x80000000);
        bevel(gg, boardX, boardY, boardW, boardH, 0xAA000000, 0x44FFFFFF); // inverted = sunken

        // 5. Glowing rim
        float pulse = 0.5f + 0.5f * Mth.sin(t * 4f);
        int glow = 0xFF000000 | (255 << 16) | ((int) (100 + 80 * pulse) << 8) | 20;
        gg.renderOutline(left - 1, top - 1, PANEL_W + 2, PANEL_H + 2, glow);
        gg.renderOutline(left, top, PANEL_W, PANEL_H, (glow & 0x00FFFFFF) | 0x88000000);
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        super.render(gg, mouseX, mouseY, partialTick); // background + Done button

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();

        if (rpgClass == null) {
            gg.drawCenteredString(this.font, "No class selected yet. Use your Class Book!",
                    left + PANEL_W / 2, boardY + boardH / 2 - 4, 0xFFFF7070);
            return;
        }

        renderStatsStrip(gg, data, rpgClass);

        gg.enableScissor(boardX + 2, viewTop, boardX + boardW - 2, viewBottom);
        for (SkillRow row : rows) {
            row.setY(viewTop + (int) (row.index * (ROW_H + ROW_GAP) - scroll));
            if (row.getY() + ROW_H >= viewTop && row.getY() <= viewBottom) {
                row.render(gg, mouseX, mouseY, partialTick);
            }
        }
        gg.disableScissor();

        renderScrollbar(gg);

        // Tooltip for hovered skill
        for (SkillRow row : rows) {
            if (row.isMouseOver(mouseX, mouseY)) {
                gg.renderComponentTooltip(this.font, tooltipFor(row), mouseX, mouseY);
                break;
            }
        }
    }

    private List<Component> tooltipFor(SkillRow row) {
        List<Component> tip = new ArrayList<>();
        SkillTier tier = row.tier;
        int rgb = tier.getColor() & 0xFFFFFF;

        tip.add(row.skill.getDisplayName().copy().withStyle(s -> s.withBold(true)));
        if (!row.unlocked) {
            tip.add(Component.literal("Locked - requires Lv. " + row.skill.getRequiredLevel())
                    .withStyle(s -> s.withColor(COL_LOCKED & 0xFFFFFF)));
            tip.add(Component.literal("Starts at tier: " + tier.getDisplayName())
                    .withStyle(s -> s.withColor(rgb)));
            return tip;
        }
        tip.add(Component.literal("Tier: " + tier.getDisplayName()).withStyle(s -> s.withColor(rgb)));
        tip.add(Component.literal("Skill Level: " + row.level));
        tip.add(Component.literal(String.format("Power: x%.2f", tier.getPowerMultiplier())));

        int idx = tier.tierIndex();
        if (idx < SkillTier.values().length - 1) {
            SkillTier next = SkillTier.values()[idx + 1];
            int nextLevel = (idx + 1) * 5 + 1;
            tip.add(Component.literal(String.format("Next: %s at Lv. %d (x%.2f)",
                            next.getDisplayName(), nextLevel, next.getPowerMultiplier()))
                    .withStyle(s -> s.withColor(next.getColor() & 0xFFFFFF)));
        } else {
            tip.add(Component.literal("Maximum tier reached").withStyle(s -> s.withColor(COL_AVAILABLE & 0xFFFFFF)));
        }
        return tip;
    }

    private void renderStatsStrip(GuiGraphics gg, PlayerRpgData data, RpgClass rpgClass) {
        int x = boardX + 6;
        int y = boardY + 4;
        int w = boardW - 12;
        gg.fill(x, y, x + w, y + 18, 0x90000000);
        gg.renderOutline(x, y, w, 18, 0x44FFFFFF);

        gg.drawString(this.font, rpgClass.getDisplayName(), x + 6, y + 5, COL_ACCENT, true);

        String lvl = "Lv. " + data.getLevel();
        String sp = "SP: " + data.getSkillPoints();
        int spColor = data.getSkillPoints() > 0 ? COL_UNLOCKED : COL_TEXT_DIM;
        int spW = this.font.width(sp);
        gg.drawString(this.font, sp, x + w - 6 - spW, y + 5, spColor, false);
        gg.drawString(this.font, lvl, x + w - 6 - spW - 14 - this.font.width(lvl), y + 5, COL_TEXT, false);
    }

    private void renderScrollbar(GuiGraphics gg) {
        int max = maxScroll();
        if (max <= 0) return;
        int trackX = boardX + boardW - 8;
        int trackH = viewBottom - viewTop;
        gg.fill(trackX, viewTop, trackX + 3, viewBottom, 0xFF101018);

        int thumbH = Math.max(14, (int) ((float) trackH * trackH / contentHeight()));
        int thumbY = viewTop + (int) ((trackH - thumbH) * (scroll / max));
        gg.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, 0xFFFF9030);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Skill plaque widget ----
    private class SkillRow extends AbstractButton {
        final int index;
        final Skill skill;
        final int level;
        final SkillTier tier;
        final boolean unlocked, canUnlock, canUpgrade;

        SkillRow(int x, int w, int index, Skill skill, int level,
                 boolean unlocked, boolean canUnlock, boolean canUpgrade) {
            super(x, 0, w, ROW_H, skill.getDisplayName());
            this.index = index;
            this.skill = skill;
            this.level = level;
            this.tier = SkillTier.fromSkillLevel(Math.max(1, level));
            this.unlocked = unlocked;
            this.canUnlock = canUnlock;
            this.canUpgrade = canUpgrade;
            this.active = unlocked || canUnlock; // locked plaques aren't clickable
        }

        private boolean inViewport(double my) {
            return my >= viewTop && my < viewBottom;
        }

        private int pillX() { return getX() + width - PILL_W - 8; }
        private int plusX() { return pillX() - PLUS_SIZE - 4; }
        private int plusY() { return getY() + (height - PLUS_SIZE) / 2; }

        private boolean overPlus(double mx, double my) {
            return unlocked && mx >= plusX() && mx < plusX() + PLUS_SIZE
                    && my >= plusY() && my < plusY() + PLUS_SIZE;
        }

        @Override
        public boolean isMouseOver(double mx, double my) {
            return super.isMouseOver(mx, my) && inViewport(my);
        }

        @Override
        protected boolean clicked(double mx, double my) {
            return super.clicked(mx, my) && inViewport(my);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && inViewport(my) && overPlus(mx, my)) {
                if (canUpgrade) {
                    this.playDownSound(Minecraft.getInstance().getSoundManager());
                    PacketDistributor.sendToServer(new UnlockSkillPayload(skill.getId()));
                }
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public void onPress() {
            if (unlocked) {
                PacketDistributor.sendToServer(new CastSkillPayload(skill.getId()));
                SkillTreeScreen.this.onClose();
            } else if (canUnlock) {
                PacketDistributor.sendToServer(new UnlockSkillPayload(skill.getId()));
            }
        }

        @Override
        protected void renderWidget(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
            int x = getX(), y = getY();
            boolean hover = this.active && this.isHovered();
            int tierColor = unlocked ? tier.getColor() : (canUnlock ? COL_AVAILABLE : COL_LOCKED);

            // Wooden plaque
            tile(gg, TEX_PLANKS, x, y, width, height);
            gg.fill(x, y, x + width, y + height, unlocked || canUnlock ? 0x88000000 : 0xB0000000);
            if (hover) gg.fill(x, y, x + width, y + height, 0x22FFFFFF);
            gg.renderOutline(x, y, width, height, (tierColor & 0x00FFFFFF) | (hover ? 0xFF000000 : 0xCC000000));
            bevel(gg, x + 1, y + 1, width - 2, height - 2, 0x22FFFFFF, 0x66000000);

            // Pinned nails
            nail(gg, x + 4, y + 4);
            nail(gg, x + width - 6, y + 4);

            // Name
            int nameColor = this.active ? 0xFFFFFFFF : COL_TEXT_DIM;
            gg.drawString(font, getMessage(), x + 12, y + 6, nameColor, true);

            // Subline
            if (unlocked) {
                String sub = String.format("%s  Lv.%d  x%.2f", tier.getDisplayName(), level, tier.getPowerMultiplier());
                gg.drawString(font, sub, x + 12, y + 18, tier.getColor(), false);

                // Progress through the current tier (5 levels per tier)
                float progress = tier == SkillTier.MYTHIC ? 1f : ((level - 1) % 5 + 1) / 5f;
                int barW = 90;
                gg.fill(x + 12, y + 28, x + 12 + barW, y + 31, 0xAA000000);
                gg.fill(x + 12, y + 28, x + 12 + (int) (barW * progress), y + 31, tier.getColor());
            } else if (canUnlock) {
                gg.drawString(font, "Ready to unlock (1 SP)", x + 12, y + 18, COL_AVAILABLE, false);
            } else {
                gg.drawString(font, "Requires Lv. " + skill.getRequiredLevel(), x + 12, y + 18, COL_LOCKED, false);
            }

            // Upgrade "+" / MAX (unlocked only)
            if (unlocked) {
                if (level >= MAX_SKILL_LEVEL) {
                    String max = "MAX";
                    gg.drawString(font, max, plusX() + PLUS_SIZE - font.width(max), plusY() + 4, COL_AVAILABLE, true);
                } else {
                    boolean plusHover = canUpgrade && mouseX >= plusX() && mouseX < plusX() + PLUS_SIZE
                            && mouseY >= plusY() && mouseY < plusY() + PLUS_SIZE && inViewport(mouseY);
                    int pbg = !canUpgrade ? 0xFF2A2A2A : (plusHover ? 0xFF3C8F3C : 0xFF2E6B2E);
                    gg.fill(plusX(), plusY(), plusX() + PLUS_SIZE, plusY() + PLUS_SIZE, pbg);
                    gg.renderOutline(plusX(), plusY(), PLUS_SIZE, PLUS_SIZE, canUpgrade ? 0x88FFFFFF : 0x33FFFFFF);
                    gg.drawCenteredString(font, "+", plusX() + PLUS_SIZE / 2, plusY() + 4,
                            canUpgrade ? 0xFFFFFFFF : COL_TEXT_DIM);
                }
            }

            // Action pill
            String label = unlocked ? "Cast" : (canUnlock ? "Unlock" : "Locked");
            int pillY = y + (height - PILL_H) / 2;
            int pillBg = unlocked ? (hover ? 0xFF3C8F3C : 0xFF2E6B2E)
                    : canUnlock ? (hover ? 0xFFB39700 : 0xFF8A7500)
                    : 0xFF2A2A2A;
            gg.fill(pillX(), pillY, pillX() + PILL_W, pillY + PILL_H, pillBg);
            gg.renderOutline(pillX(), pillY, PILL_W, PILL_H, 0x55FFFFFF);
            gg.drawCenteredString(font, label, pillX() + PILL_W / 2, pillY + 4,
                    this.active ? 0xFFFFFFFF : COL_TEXT_DIM);
        }

        private void nail(GuiGraphics gg, int nx, int ny) {
            gg.fill(nx + 1, ny + 1, nx + 3, ny + 3, 0xAA000000); // shadow
            gg.fill(nx, ny, nx + 2, ny + 2, 0xFFB8B8B8);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}