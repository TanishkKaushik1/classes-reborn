package com.tanishk.classesreborn.client.gui;

import com.tanishk.classesreborn.network.AllocateStatPayload;
import com.tanishk.classesreborn.network.ResetStatsPayload;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

public class StatScreen extends Screen {
    // ---- Layout ----
    private static final int PANEL_W = 356;
    private static final int PANEL_H = 292;
    private static final int FRAME = 8;
    private static final int PLANK_H = 22;
    private static final int STRIP_H = 22;
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 3;
    private static final int PLUS_SIZE = 16;

    // ---- Colors (ARGB) ----
    private static final int COL_ACCENT   = 0xFFFFD700;
    private static final int COL_TEXT     = 0xFFE0E0E0;
    private static final int COL_TEXT_DIM = 0xFF909090;
    private static final int COL_GOOD     = 0xFF55DD55;
    private static final int COL_ACTIVE   = 0xFFFFAA33;

    private final List<StatRow> rows = new ArrayList<>();
    private int left, top;
    private int boardX, boardY, boardW, boardH;
    private int lastSnapshot = 0;

    // Fire flares up while a combat buff is active
    private float fireIntensity = 0.7f;
    private boolean anyBuffActive = false;

    public StatScreen() {
        super(Component.literal("Character Stats"));
    }

    @Override
    protected void init() {
        this.rows.clear();
        this.left = (this.width - PANEL_W) / 2;
        this.top = Math.max(18, (this.height - PANEL_H) / 2); // leave room for the flames

        this.boardX = left + FRAME;
        this.boardY = top + FRAME + PLANK_H + 2;
        this.boardW = PANEL_W - FRAME * 2;
        this.boardH = (top + PANEL_H - FRAME - 28) - boardY;

        int footerY = top + PANEL_H - FRAME - 24;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose())
                .bounds(left + PANEL_W / 2 + 6, footerY, 96, 20).build());

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);

        int totalAllocated = 0;
        if (data.hasSelectedClass()) {
            for (PlayerRpgData.StatType t : PlayerRpgData.StatType.values()) {
                totalAllocated += data.getStatPointsAllocated(t);
            }
        }

        Button resetBtn = this.addRenderableWidget(Button.builder(
                Component.literal("Reset Stats"),
                b -> PacketDistributor.sendToServer(new ResetStatsPayload())
        ).bounds(left + PANEL_W / 2 - 102, footerY, 96, 20).build());
        resetBtn.active = data.hasSelectedClass() && totalAllocated > 0;

        if (!data.hasSelectedClass()) return;

        this.lastSnapshot = snapshot(mc, data);

        int rowsTop = boardY + 4 + STRIP_H + 6;
        int rowW = boardW - 12;
        PlayerRpgData.StatType[] stats = PlayerRpgData.StatType.values();
        for (int i = 0; i < stats.length; i++) {
            int y = rowsTop + i * (ROW_H + ROW_GAP);
            StatRow row = new StatRow(boardX + 6, y, rowW, stats[i]);
            rows.add(row);
            this.addRenderableWidget(new PlusButton(row.getX() + row.getWidth() - PLUS_SIZE - 4,
                    y + (ROW_H - PLUS_SIZE) / 2, stats[i], data.getStatPoints() > 0));
        }
    }

    private static int snapshot(Minecraft mc, PlayerRpgData data) {
        int h = data.getStatPoints() * 31 + data.getLevel();
        for (PlayerRpgData.StatType t : PlayerRpgData.StatType.values()) {
            h = h * 31 + data.getStatPointsAllocated(t);
        }
        if (mc.player != null && mc.level != null) {
            long gameTime = mc.level.getGameTime();
            var id = mc.player.getUUID();
            SkillRegistry.ActiveBuffSnapshot buffs = SkillRegistry.getBuffSnapshot(id, gameTime);
            boolean stealth = buffs.stealthTicksLeft() > 0 || mc.player.hasEffect(MobEffects.INVISIBILITY);
            ItemStack held = mc.player.getMainHandItem();
            boolean holdingSword = held.is(ItemTags.SWORDS);
            boolean holdingBow = held.getItem() instanceof BowItem;

            int hpRounded = (int) Math.round(mc.player.getAttributeValue(Attributes.MAX_HEALTH) * 10);
            int atkRounded = (int) Math.round(mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 10);
            int spdRounded = (int) Math.round(mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED) * 1000);

            h = h * 31 + hpRounded + atkRounded + spdRounded + buffs.totalHash();
            h = h * 31 + (stealth ? 1 : 0) + (holdingSword ? 2 : 0) + (holdingBow ? 4 : 0);
        }
        return h;
    }

    @Override
    public void tick() {
        super.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        if (data.hasSelectedClass() && snapshot(mc, data) != lastSnapshot) {
            this.rebuildWidgets();
        }
    }

    private static double computeLiveSwordDamage(PlayerRpgData data, double baseMelee, boolean surgeActive) {
        double dmg = baseMelee + 2.0 + (data.getLevel() - 1) * 0.4;
        if (surgeActive) {
            dmg += 3.5 + (data.getLevel() - 1) * 0.35;
        }
        return dmg;
    }

    // ---- Stat metadata ----
    private static int colorFor(PlayerRpgData.StatType t) {
        return switch (t.name()) {
            case "VITALITY"  -> 0xFFFF5555;
            case "GUARD"     -> 0xFF55AAFF;
            case "MIGHT"     -> 0xFFFF9944;
            case "PRECISION" -> 0xFFDDDD55;
            case "AGILITY"   -> 0xFF55EEDD;
            case "PRESENCE"  -> 0xFFCC77FF;
            case "MEND"      -> 0xFF55DD88;
            default          -> 0xFFAAAAAA;
        };
    }

    private static ItemStack iconFor(PlayerRpgData.StatType t) {
        return new ItemStack(switch (t.name()) {
            case "VITALITY"  -> Items.GOLDEN_APPLE;
            case "GUARD"     -> Items.SHIELD;
            case "MIGHT"     -> Items.IRON_SWORD;
            case "PRECISION" -> Items.BOW;
            case "AGILITY"   -> Items.LEATHER_BOOTS;
            case "PRESENCE"  -> Items.GOLDEN_HELMET;
            case "MEND"      -> Items.GLISTERING_MELON_SLICE;
            default          -> Items.PAPER;
        });
    }

    private static String nameFor(PlayerRpgData.StatType t) {
        String n = t.name();
        return n.charAt(0) + n.substring(1).toLowerCase();
    }

    private static String descFor(PlayerRpgData.StatType t) {
        return switch (t.name()) {
            case "VITALITY"  -> "+1.0 Max HP per pt. Tank Roar adds Absorption & Resistance.";
            case "GUARD"     -> "+0.75 Armor & +0.25 Toughness per pt.";
            case "MIGHT"     -> "+0.5 Melee Dmg per pt. Swordsman boosts sword hits & Quickslash surge.";
            case "PRECISION" -> "+3% Ranged Mult per pt. Scales bow/crossbow damage.";
            case "AGILITY"   -> "+0.004 Move Speed per pt. Archer Dash boosts mobility.";
            case "PRESENCE"  -> "+4% Aggro Mult per pt. Tank Taunt & Assassin Stealth modify threat.";
            case "MEND"      -> "+3% Heal Mult per pt. Healer scales incoming & aimed heal output.";
            default          -> "";
        };
    }

    private String valueFor(Minecraft mc, PlayerRpgData data, PlayerRpgData.StatType t) {
        RpgClass rpgClass = data.getSelectedClass();
        long gameTime = mc.level != null ? mc.level.getGameTime() : 0L;
        var uuid = mc.player.getUUID();

        SkillRegistry.ActiveBuffSnapshot buffs = SkillRegistry.getBuffSnapshot(uuid, gameTime);
        boolean tauntActive = buffs.tauntTicksLeft() > 0;
        boolean stealthActive = buffs.stealthTicksLeft() > 0 || mc.player.hasEffect(MobEffects.INVISIBILITY);
        boolean surgeActive = buffs.quickslashSurgeTicksLeft() > 0;
        boolean dashActive = buffs.archerBoostTicksLeft() > 0;
        boolean healingCast = buffs.pendingHealTicksLeft() > 0;
        ItemStack held = mc.player.getMainHandItem();

        double maxHp = mc.player.getAttributeValue(Attributes.MAX_HEALTH);
        double armor = mc.player.getAttributeValue(Attributes.ARMOR);
        double toughness = mc.player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double baseMelee = mc.player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        double moveSpeed = mc.player.getAttributeValue(Attributes.MOVEMENT_SPEED);

        return switch (t.name()) {
            case "VITALITY" -> {
                String tag = (rpgClass == RpgClass.TANK && tauntActive) ? " [ROAR]" : "";
                yield "HP " + fmt(maxHp, 1) + tag;
            }
            case "GUARD" -> "Armor " + fmt(armor, 1) + " / Tough " + fmt(toughness, 1);
            case "MIGHT" -> {
                if (rpgClass == RpgClass.SWORDSMAN) {
                    double swordDmg = computeLiveSwordDamage(data, baseMelee, surgeActive);
                    String tag = surgeActive ? " [SURGE]" : (held.is(ItemTags.SWORDS) ? " [SWORD]" : "");
                    yield "Base " + fmt(baseMelee, 1) + " | Sword " + fmt(swordDmg, 1) + tag;
                }
                yield "Melee " + fmt(baseMelee, 1);
            }
            case "PRECISION" -> {
                double mult = ClassStatManager.getEffectiveRangedMultiplier(data);
                String tag = (rpgClass == RpgClass.ARCHER && held.getItem() instanceof BowItem) ? " [BOW]" : "";
                yield "Ranged " + String.format("%.2fx", mult) + tag;
            }
            case "AGILITY" -> {
                String tag = (rpgClass == RpgClass.ARCHER && dashActive) ? " [DASH]" : "";
                yield "Speed " + fmt(moveSpeed, 3) + tag;
            }
            case "PRESENCE" -> {
                double mult = ClassStatManager.getEffectiveAggroMultiplier(data);
                String tag = (rpgClass == RpgClass.TANK && tauntActive) ? " [TAUNT]"
                        : (rpgClass == RpgClass.ASSASSIN && stealthActive) ? " [STEALTH]" : "";
                yield "Aggro " + String.format("%.2fx", mult) + tag;
            }
            case "MEND" -> {
                double mult = ClassStatManager.getEffectiveHealingMultiplier(data);
                if (rpgClass == RpgClass.HEALER) {
                    float healOut = SkillRegistry.computeHealerHealOutput(data);
                    String tag = healingCast ? " [CASTING]" : "";
                    yield "Heal " + String.format("%.2fx", mult) + " (" + fmt(healOut, 1) + " HP)" + tag;
                }
                yield "Heal " + String.format("%.2fx", mult);
            }
            default -> "";
        };
    }

    private static boolean hasActiveTag(String value) {
        return value.contains("[SURGE]") || value.contains("[TAUNT]")
                || value.contains("[STEALTH]") || value.contains("[DASH]")
                || value.contains("[ROAR]") || value.contains("[CASTING]");
    }

    private static String fmt(double v, int decimals) {
        return String.format("%." + decimals + "f", v);
    }

    // ---- Rendering ----
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        float t = RpgUi.time();

        // Fire flares up while any combat buff is active, then settles back down
        float target = anyBuffActive ? 1.3f : 0.7f;
        fireIntensity += (target - fireIntensity) * 0.08f;

        RpgUi.drawPanel(g, left, top, PANEL_W, PANEL_H, t, fireIntensity);

        // Header plank with crossed weapons either side of the title
        int plankX = left + FRAME, plankY = top + FRAME, plankW = PANEL_W - FRAME * 2;
        RpgUi.drawPlank(g, plankX, plankY, plankW, PLANK_H);
        g.renderItem(new ItemStack(Items.IRON_SWORD), plankX + 6, plankY + 3);
        g.renderItem(new ItemStack(Items.IRON_AXE), plankX + plankW - 22, plankY + 3);
        g.drawCenteredString(this.font, this.title.copy().withStyle(s -> s.withBold(true)),
                left + PANEL_W / 2, plankY + (PLANK_H - 8) / 2, COL_ACCENT);

        RpgUi.drawBoard(g, boardX, boardY, boardW, boardH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();

        if (rpgClass == null) {
            g.drawCenteredString(this.font, "No class selected yet. Use your Class Tome!",
                    left + PANEL_W / 2, boardY + boardH / 2 - 4, 0xFFFF7070);
            anyBuffActive = false;
            return;
        }

        renderStatsStrip(g, data, rpgClass);

        boolean anyActive = false;
        StatRow hovered = null;
        for (StatRow row : rows) {
            if (row.renderRow(g, mc, data, mouseX, mouseY)) anyActive = true;
            if (row.isOverRow(mouseX, mouseY)) hovered = row;
        }
        this.anyBuffActive = anyActive;

        if (hovered != null) {
            List<FormattedCharSequence> tip = new ArrayList<>();
            int rgb = colorFor(hovered.type) & 0xFFFFFF;
            tip.add(Component.literal(nameFor(hovered.type))
                    .withStyle(s -> s.withBold(true).withColor(rgb)).getVisualOrderText());
            tip.addAll(this.font.split(Component.literal(descFor(hovered.type))
                    .withStyle(s -> s.withColor(0xBBBBBB)), 200));
            g.renderTooltip(this.font, tip, mouseX, mouseY);
        }
    }

    private void renderStatsStrip(GuiGraphics g, PlayerRpgData data, RpgClass rpgClass) {
        int x = boardX + 6;
        int y = boardY + 4;
        int w = boardW - 12;

        // Crimson war-banner strip
        g.fill(x, y, x + w, y + STRIP_H, 0xB0300A0A);
        g.fill(x, y + STRIP_H - 1, x + w, y + STRIP_H, 0xFFB02020);
        g.renderOutline(x, y, w, STRIP_H, 0x55FF5533);

        g.drawString(this.font, rpgClass.getDisplayName(), x + 8, y + 7, COL_ACCENT, true);

        int points = data.getStatPoints();
        String pts = "Stat Points: " + points;
        String lvl = "Lv. " + data.getLevel();
        int ptsW = this.font.width(pts);
        g.drawString(this.font, pts, x + w - 8 - ptsW, y + 7, points > 0 ? COL_GOOD : COL_TEXT_DIM, false);
        g.drawString(this.font, lvl, x + w - 8 - ptsW - 14 - this.font.width(lvl), y + 7, COL_TEXT, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Stat plaque (drawn manually; the + button is a separate widget) ----
    private class StatRow {
        final int x, y, width;
        final PlayerRpgData.StatType type;
        final ItemStack icon;

        StatRow(int x, int y, int width, PlayerRpgData.StatType type) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.type = type;
            this.icon = iconFor(type);
        }

        int getX() { return x; }
        int getWidth() { return width; }

        boolean isOverRow(int mx, int my) {
            return mx >= x && mx < x + width - PLUS_SIZE - 8 && my >= y && my < y + ROW_H;
        }

        /** @return true if this row currently shows an active combat buff tag. */
        boolean renderRow(GuiGraphics g, Minecraft mc, PlayerRpgData data, int mx, int my) {
            int color = colorFor(type);
            boolean hover = isOverRow(mx, my);
            String value = valueFor(mc, data, type);
            boolean active = hasActiveTag(value);

            // Wooden plaque
            RpgUi.tile(g, RpgUi.TEX_PLANKS, x, y, width, ROW_H);
            g.fill(x, y, x + width, y + ROW_H, 0xA0000000);
            if (hover) g.fill(x, y, x + width, y + ROW_H, 0x22FFFFFF);

            // Outline: stat color, pulsing orange while a buff is active
            int outline;
            if (active) {
                float pulse = 0.5f + 0.5f * Mth.sin(RpgUi.time() * 6f);
                int a = (int) (140 + 115 * pulse);
                outline = (a << 24) | (COL_ACTIVE & 0x00FFFFFF);
            } else {
                outline = (color & 0x00FFFFFF) | (hover ? 0xFF000000 : 0x99000000);
            }
            g.renderOutline(x, y, width, ROW_H, outline);
            RpgUi.bevel(g, x + 1, y + 1, width - 2, ROW_H - 2, 0x22FFFFFF, 0x66000000);

            // Icon socket
            g.fill(x + 3, y + 2, x + 23, y + ROW_H - 2, 0xCC000000);
            g.renderOutline(x + 3, y + 2, 20, ROW_H - 4, (color & 0x00FFFFFF) | 0xAA000000);
            g.renderItem(icon, x + 5, y + 4);

            int ty = y + (ROW_H - 8) / 2;
            g.drawString(font, nameFor(type), x + 29, ty, color, true);

            int allocated = data.getStatPointsAllocated(type);
            String pts = "(" + allocated + " pts)";
            int nameW = font.width(nameFor(type));
            g.drawString(font, pts, x + 29 + nameW + 5, ty, allocated > 0 ? COL_TEXT : COL_TEXT_DIM, false);

            int valueRight = x + width - PLUS_SIZE - 12;
            g.drawString(font, value, valueRight - font.width(value), ty, active ? COL_ACTIVE : COL_TEXT, false);
            return active;
        }
    }

    // ---- "+" button ----
    private class PlusButton extends AbstractButton {
        private final PlayerRpgData.StatType type;

        PlusButton(int x, int y, PlayerRpgData.StatType type, boolean enabled) {
            super(x, y, PLUS_SIZE, PLUS_SIZE, Component.literal("+"));
            this.type = type;
            this.active = enabled;
        }

        @Override
        public void onPress() {
            PacketDistributor.sendToServer(new AllocateStatPayload(type.getId()));
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int bg = !active ? 0xFF2A2A2A : (isHoveredOrFocused() ? 0xFF3C8F3C : 0xFF2E6B2E);
            g.fill(getX(), getY(), getX() + width, getY() + height, bg);
            g.renderOutline(getX(), getY(), width, height, active ? 0x88FFFFFF : 0x33FFFFFF);
            g.drawCenteredString(font, "+", getX() + width / 2, getY() + (height - 8) / 2,
                    active ? 0xFFFFFFFF : COL_TEXT_DIM);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}