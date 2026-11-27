package com.tanishk.classesreborn.rpg.skill;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Skill {
    @FunctionalInterface
    public interface SkillAction {
        boolean execute(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier);
    }

    private final String id;
    private final Component displayName;
    private final RpgClass ownerClass;
    private final int requiredPlayerLevel;
    private final int baseCooldownTicks;
    private final SkillAction action;

    public Skill(String id, RpgClass ownerClass, int requiredPlayerLevel, int baseCooldownTicks, SkillAction action) {
        this(id, formatDisplayName(id), ownerClass, requiredPlayerLevel, baseCooldownTicks, action);
    }

    public Skill(String id, Component displayName, RpgClass ownerClass, int requiredPlayerLevel, int baseCooldownTicks, SkillAction action) {
        this.id = id;
        this.displayName = displayName;
        this.ownerClass = ownerClass;
        this.requiredPlayerLevel = Math.max(1, requiredPlayerLevel);
        this.baseCooldownTicks = Math.max(0, baseCooldownTicks);
        this.action = action;
    }

    private static Component formatDisplayName(String id) {
        String[] parts = id.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return Component.literal(sb.toString());
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public RpgClass getOwnerClass() {
        return ownerClass;
    }

    public int getRequiredPlayerLevel() {
        return requiredPlayerLevel;
    }

    public int getRequiredLevel() {
        return requiredPlayerLevel;
    }

    public int getBaseCooldownTicks() {
        return baseCooldownTicks;
    }

    public int getCooldownTicksForLevel(int skillLevel) {
        if (baseCooldownTicks <= 0) {
            return 0;
        }
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        if (SkillRegistry.ARCHER_DASH.equals(id)) {
            if (tier.ordinal() < SkillTier.LEGENDARY.ordinal()) {
                return baseCooldownTicks;
            }
            int tiersAboveEpic = tier.ordinal() - SkillTier.EPIC.ordinal();
            int reduction = tiersAboveEpic * 3;
            return Math.max(30, baseCooldownTicks - reduction);
        }

        double factor = Math.max(0.75, 1.0 - tier.tierIndex() * 0.05);
        return Math.max(20, (int) Math.round(baseCooldownTicks * factor));
    }

    public boolean cast(ServerPlayer player, PlayerRpgData data, int skillLevel) {
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        return action.execute(player, data, Math.max(1, skillLevel), tier);
    }
}