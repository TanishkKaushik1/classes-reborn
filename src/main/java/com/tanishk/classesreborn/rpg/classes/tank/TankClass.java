package com.tanishk.classesreborn.rpg.classes.tank;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class TankClass {
    public static final String ID = "tank";
    public static final double MAX_HEALTH_BONUS = 6.0;      // +3 hearts max health
    public static final double ARMOR_BONUS = 6.0;           // +6 armor
    public static final double ARMOR_TOUGHNESS_BONUS = 2.0; // +2 armor toughness
    public static final double MOVEMENT_SPEED_BONUS = -0.015;
    public static final double ATTACK_DAMAGE_BONUS = 0.0;
    public static final double RANGED_DAMAGE_MULTIPLIER = 0.75;
    public static final double HEALING_MULTIPLIER = 1.0;
    public static final double AGGRO_MULTIPLIER = 1.65;

    private TankClass() {
    }

    public static List<ItemStack> createStarterKit() {
        return List.of(
                new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.SHIELD),
                new ItemStack(Items.IRON_CHESTPLATE),
                new ItemStack(Items.COOKED_BEEF, 4)
        );
    }
}
