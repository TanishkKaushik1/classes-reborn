package com.tanishk.classesreborn.rpg;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

public final class ClassStatManager {
    private static final String MOD_ID = "classesreborn";

    private static final ResourceLocation MAX_HEALTH_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "class_max_health");
    private static final ResourceLocation ARMOR_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "class_armor");
    private static final ResourceLocation ARMOR_TOUGHNESS_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "class_armor_toughness");
    private static final ResourceLocation MOVEMENT_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "class_movement_speed");
    private static final ResourceLocation ATTACK_DAMAGE_ID =
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "class_attack_damage");

    private ClassStatManager() {
    }

    public static void applyStats(Player player, PlayerRpgData data) {
        if (player.level().isClientSide()) return;

        RpgClass rpgClass = data.getSelectedClass();
        if (rpgClass == null) {
            clearStats(player);
            return;
        }

        int vitPts = data.getStatPointsAllocated(PlayerRpgData.StatType.VITALITY);
        int guardPts = data.getStatPointsAllocated(PlayerRpgData.StatType.GUARD);
        int mightPts = data.getStatPointsAllocated(PlayerRpgData.StatType.MIGHT);
        int agilityPts = data.getStatPointsAllocated(PlayerRpgData.StatType.AGILITY);

        boolean isArcher = rpgClass == RpgClass.ARCHER;
        double vitPerPt = isArcher ? 0.5 : 1.0;
        double armorPerPt = isArcher ? 0.35 : 0.75;
        double toughnessPerPt = isArcher ? 0.10 : 0.25;
        double mightPerPt = isArcher ? 0.20 : 0.50;
        double agilityPerPt = isArcher ? 0.006 : 0.004;

        double maxHealthValue = rpgClass.getMaxHealthBonus() + (vitPts * vitPerPt);
        double armorValue = rpgClass.getArmorBonus() + (guardPts * armorPerPt);
        double toughnessValue = rpgClass.getArmorToughnessBonus() + (guardPts * toughnessPerPt);
        double speedBonus = Math.min(0.08, agilityPts * agilityPerPt);
        double speedValue = rpgClass.getMovementSpeedBonus() + speedBonus;
        double attackValue = rpgClass.getAttackDamageBonus() + (mightPts * mightPerPt);

        setModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID, maxHealthValue, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ARMOR, ARMOR_ID, armorValue, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_ID, toughnessValue, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_ID, speedValue, AttributeModifier.Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID, attackValue, AttributeModifier.Operation.ADD_VALUE);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    public static double getEffectiveRangedMultiplier(PlayerRpgData data) {
        RpgClass rpgClass = data.getSelectedClass();
        double base = rpgClass == null ? 1.0 : rpgClass.getRangedDamageMultiplier();
        int precisionPts = data.getStatPointsAllocated(PlayerRpgData.StatType.PRECISION);
        double perPt = rpgClass == RpgClass.ARCHER ? 0.05 : 0.03;
        return Math.max(0.1, base + precisionPts * perPt);
    }

    public static double getEffectiveHealingMultiplier(PlayerRpgData data) {
        RpgClass rpgClass = data.getSelectedClass();
        double base = rpgClass == null ? 1.0 : rpgClass.getHealingMultiplier();
        int mendPts = data.getStatPointsAllocated(PlayerRpgData.StatType.MEND);
        if (rpgClass == RpgClass.HEALER) {
            return Math.max(0.1, base + mendPts * 0.04);
        }
        return Math.max(0.1, base + Math.min(0.25, mendPts * 0.015));
    }

    public static double getEffectiveSaturationMultiplier(PlayerRpgData data) {
        int mendPts = data.getStatPointsAllocated(PlayerRpgData.StatType.MEND);
        return 1.0 + Math.min(0.35, mendPts * 0.02);
    }

    public static double getEffectiveAggroMultiplier(PlayerRpgData data) {
        RpgClass rpgClass = data.getSelectedClass();
        double base = rpgClass == null ? 1.0 : rpgClass.getAggroMultiplier();
        int presencePts = data.getStatPointsAllocated(PlayerRpgData.StatType.PRESENCE);
        if (rpgClass == RpgClass.ARCHER) {
            return Math.max(0.25, base - Math.min(0.45, presencePts * 0.03));
        }
        return Math.max(0.1, base + presencePts * 0.04);
    }

    public static void clearStats(Player player) {
        if (player.level().isClientSide()) return;

        removeModifier(player, Attributes.MAX_HEALTH, MAX_HEALTH_ID);
        removeModifier(player, Attributes.ARMOR, ARMOR_ID);
        removeModifier(player, Attributes.ARMOR_TOUGHNESS, ARMOR_TOUGHNESS_ID);
        removeModifier(player, Attributes.MOVEMENT_SPEED, MOVEMENT_SPEED_ID);
        removeModifier(player, Attributes.ATTACK_DAMAGE, ATTACK_DAMAGE_ID);

        if (player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void setModifier(
            Player player,
            Holder<Attribute> attribute,
            ResourceLocation id,
            double amount,
            AttributeModifier.Operation operation
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;

        instance.removeModifier(id);
        if (Math.abs(amount) > 1.0E-6) {
            instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        }
    }

    private static void removeModifier(
            Player player,
            Holder<Attribute> attribute,
            ResourceLocation id
    ) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            instance.removeModifier(id);
        }
    }
}