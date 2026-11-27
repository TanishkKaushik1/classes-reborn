package com.tanishk.classesreborn.rpg;

import net.minecraft.network.chat.Component;

public enum RpgClass {
    TANK(
            "tank",
            6.0,    // +3 hearts max health
            6.0,    // +6 armor
            2.0,    // +2 armor toughness
            -0.015, // slightly slower movement
            0.0,    // melee damage bonus
            0.75,   // ranged damage multiplier
            1.0,    // healing multiplier
            1.65    // high hostile mob aggro
    ),
    SWORDSMAN(
            "swordsman",
            2.0,
            2.0,
            0.0,
            0.01,
            2.5,
            0.90,
            1.0,
            1.0
    ),
    ASSASSIN(
            "assassin",
            -2.0,
            -2.0,
            0.0,
            0.035,
            3.0,
            0.70,
            1.0,
            0.55    // lower passive hostile mob aggro
    ),
    ARCHER(
            "archer",
            0.0,
            0.0,
            0.0,
            0.02,
            -1.0,
            1.45,   // high ranged projectile multiplier
            1.0,
            0.80
    ),
    HEALER(
            "healer",
            1.0,
            0.0,
            0.0,
            0.0,
            -1.5,
            0.75,
            1.50,   // boosted outgoing healing
            0.90
    );

    private final String id;
    private final double maxHealthBonus;
    private final double armorBonus;
    private final double armorToughnessBonus;
    private final double movementSpeedBonus;
    private final double attackDamageBonus;
    private final double rangedDamageMultiplier;
    private final double healingMultiplier;
    private final double aggroMultiplier;

    RpgClass(
            String id,
            double maxHealthBonus,
            double armorBonus,
            double armorToughnessBonus,
            double movementSpeedBonus,
            double attackDamageBonus,
            double rangedDamageMultiplier,
            double healingMultiplier,
            double aggroMultiplier
    ) {
        this.id = id;
        this.maxHealthBonus = maxHealthBonus;
        this.armorBonus = armorBonus;
        this.armorToughnessBonus = armorToughnessBonus;
        this.movementSpeedBonus = movementSpeedBonus;
        this.attackDamageBonus = attackDamageBonus;
        this.rangedDamageMultiplier = rangedDamageMultiplier;
        this.healingMultiplier = healingMultiplier;
        this.aggroMultiplier = aggroMultiplier;
    }

    public String getId() {
        return id;
    }

    public Component getDisplayName() {
        return Component.translatable("class.classesreborn." + id);
    }

    public double getMaxHealthBonus() {
        return maxHealthBonus;
    }

    public double getArmorBonus() {
        return armorBonus;
    }

    public double getArmorToughnessBonus() {
        return armorToughnessBonus;
    }

    public double getMovementSpeedBonus() {
        return movementSpeedBonus;
    }

    public double getAttackDamageBonus() {
        return attackDamageBonus;
    }

    public double getRangedDamageMultiplier() {
        return rangedDamageMultiplier;
    }

    public double getHealingMultiplier() {
        return healingMultiplier;
    }

    public double getAggroMultiplier() {
        return aggroMultiplier;
    }

    public static RpgClass byId(String id) {
        if (id == null) return null;
        for (RpgClass rpgClass : values()) {
            if (rpgClass.id.equalsIgnoreCase(id)) {
                return rpgClass;
            }
        }
        return null;
    }
}