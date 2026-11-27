package com.tanishk.classesreborn.client.gui;

import com.tanishk.classesreborn.network.SelectClassPayload;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ClassSelectionScreen extends Screen {
    // ---- Layout ----
    private static final int PANEL_W = 340;
    private static final int PANEL_H = 210;
    private static final int PAD = 10;
    private static final int LIST_W = 100;
    private static final int ENTRY_H = 24;
    private static final int ENTRY_GAP = 4;

    // ---- Colors (ARGB) ----
    private static final int COL_PANEL_BG    = 0xE6101018;
    private static final int COL_PANEL_EDGE  = 0xFF3A3A55;
    private static final int COL_ACCENT      = 0xFFFFD700;
    private static final int COL_SECTION_BG  = 0x80000000;
    private static final int COL_TEXT        = 0xFFE0E0E0;
    private static final int COL_TEXT_DIM    = 0xFF909090;
    private static final int COL_BAR_BG      = 0xFF202030;

    // ---- Stat bar scaling (tweak to match your balance) ----
    private static final double MAX_HP_BONUS = 20.0;
    private static final double MAX_ARMOR_BONUS = 10.0;
    private static final double MAX_AGGRO = 3.0;

    private RpgClass selected = RpgClass.TANK;
    private int left, top;

    public ClassSelectionScreen() {
        super(Component.translatable("gui.classesreborn.select_class.title"));
    }

    @Override
    protected void init() {
        this.left = (this.width - PANEL_W) / 2;
        this.top = (this.height - PANEL_H) / 2;

        // Class list
        RpgClass[] classes = RpgClass.values();
        int listX = left + PAD;
        int listY = top + 34;
        for (int i = 0; i < classes.length; i++) {
            RpgClass c = classes[i];
            this.addRenderableWidget(new ClassEntry(
                    listX, listY + i * (ENTRY_H + ENTRY_GAP), LIST_W, ENTRY_H, c));
        }

        // Bottom buttons
        int btnY = top + PANEL_H - PAD - 20;
        int infoX = left + PAD + LIST_W + PAD;
        int infoW = PANEL_W - (infoX - left) - PAD;
        int half = (infoW - 6) / 2;

        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose())
                .bounds(infoX, btnY, half, 20).build());
        this.addRenderableWidget(Button.builder(
                        Component.translatable("gui.classesreborn.select_class.confirm"),
                        b -> confirm())
                .bounds(infoX + half + 6, btnY, half, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new SelectClassPayload(this.selected.getId()));
        this.onClose();
    }

    // Drawn inside renderBackground so the blur only happens once per frame (required in 1.21+).
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);

        // Panel with border
        g.fill(left - 1, top - 1, left + PANEL_W + 1, top + PANEL_H + 1, COL_PANEL_EDGE);
        g.fill(left, top, left + PANEL_W, top + PANEL_H, COL_PANEL_BG);

        // Title bar
        g.fill(left, top, left + PANEL_W, top + 26, 0x40FFFFFF & 0x40FFD700);
        g.fill(left, top + 26, left + PANEL_W, top + 27, COL_ACCENT);

        // Info section background
        int infoX = left + PAD + LIST_W + PAD;
        int infoW = PANEL_W - (infoX - left) - PAD;
        g.fill(infoX, top + 34, infoX + infoW, top + PANEL_H - PAD - 26, COL_SECTION_BG);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick); // background + widgets

        // Title
        g.drawCenteredString(this.font, this.title.copy().withStyle(s -> s.withBold(true)),
                left + PANEL_W / 2, top + 9, COL_ACCENT);

        renderInfo(g);
    }

    private void renderInfo(GuiGraphics g) {
        int infoX = left + PAD + LIST_W + PAD;
        int infoW = PANEL_W - (infoX - left) - PAD;
        int x = infoX + 8;
        int innerW = infoW - 16;
        int y = top + 42;

        // Class name
        Component name = Component.translatable("class.classesreborn." + selected.getId())
                .withStyle(s -> s.withBold(true));
        g.drawString(this.font, name, x, y, COL_ACCENT, true);
        y += 14;

        // Divider
        g.fill(x, y, x + innerW, y + 1, 0x55FFFFFF);
        y += 6;

        // Wrapped description
        Component desc = Component.translatable("class.classesreborn." + selected.getId() + ".desc");
        List<FormattedCharSequence> lines = this.font.split(desc, innerW);
        for (FormattedCharSequence line : lines) {
            g.drawString(this.font, line, x, y, COL_TEXT, false);
            y += 10;
        }
        y += 8;

        // Stat bars
        y = drawStat(g, x, y, innerW, "Max HP", formatSigned(selected.getMaxHealthBonus()),
                selected.getMaxHealthBonus() / MAX_HP_BONUS, 0xFF55DD55);
        y = drawStat(g, x, y, innerW, "Armor", formatSigned(selected.getArmorBonus()),
                selected.getArmorBonus() / MAX_ARMOR_BONUS, 0xFF55AAFF);
        drawStat(g, x, y, innerW, "Aggro", String.format("%.2fx", selected.getAggroMultiplier()),
                selected.getAggroMultiplier() / MAX_AGGRO, 0xFFFF9944);
    }

    private int drawStat(GuiGraphics g, int x, int y, int w, String label, String value,
                         double fraction, int color) {
        g.drawString(this.font, label, x, y, COL_TEXT_DIM, false);
        g.drawString(this.font, value, x + w - this.font.width(value), y, color, false);
        y += 10;

        int filled = (int) (w * Mth.clamp(fraction, 0.0, 1.0));
        g.fill(x, y, x + w, y + 5, COL_BAR_BG);
        g.fill(x, y, x + filled, y + 5, color);
        g.fill(x, y, x + filled, y + 1, 0x55FFFFFF); // subtle highlight
        return y + 11;
    }

    private static String formatSigned(double val) {
        return (val >= 0 ? "+" : "") + String.format("%.1f", val);
    }

    // ---- Keyboard support: up/down to browse, Enter to confirm ----
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        RpgClass[] all = RpgClass.values();
        if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_UP) {
            int dir = key == GLFW.GLFW_KEY_DOWN ? 1 : -1;
            this.selected = all[Math.floorMod(this.selected.ordinal() + dir, all.length)];
            return true;
        }
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Custom list entry widget ----
    private class ClassEntry extends AbstractButton {
        private final RpgClass rpgClass;

        ClassEntry(int x, int y, int w, int h, RpgClass rpgClass) {
            super(x, y, w, h, rpgClass.getDisplayName());
            this.rpgClass = rpgClass;
        }

        @Override
        public void onPress() {
            ClassSelectionScreen.this.selected = this.rpgClass;
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            boolean isSelected = ClassSelectionScreen.this.selected == this.rpgClass;
            int x = getX(), y = getY();

            int bg = isSelected ? 0xFF2E2E4A : (isHoveredOrFocused() ? 0xFF22223A : 0xFF16161F);
            g.fill(x, y, x + width, y + height, bg);

            // Left accent stripe + outline when selected
            if (isSelected) {
                g.fill(x, y, x + 3, y + height, COL_ACCENT);
                g.renderOutline(x, y, width, height, 0xFF6A6A99);
            } else if (isHoveredOrFocused()) {
                g.renderOutline(x, y, width, height, 0xFF44445F);
            }

            int textColor = isSelected ? COL_ACCENT : (isHoveredOrFocused() ? 0xFFFFFFFF : COL_TEXT);
            g.drawString(font, getMessage(), x + 10, y + (height - 8) / 2, textColor, isSelected);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}