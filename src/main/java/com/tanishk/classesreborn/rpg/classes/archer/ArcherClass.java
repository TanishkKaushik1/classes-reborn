package com.tanishk.classesreborn.rpg.classes.archer;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class ArcherClass {
    public static final String ID = "archer";
    public static final double MAX_HEALTH_BONUS = 0.0;
    public static final double ARMOR_BONUS = 0.0;
    public static final double ARMOR_TOUGHNESS_BONUS = 0.0;
    public static final double MOVEMENT_SPEED_BONUS = 0.02;
    public static final double ATTACK_DAMAGE_BONUS = -1.0;
    public static final double RANGED_DAMAGE_MULTIPLIER = 1.45;
    public static final double HEALING_MULTIPLIER = 1.0;
    public static final double AGGRO_MULTIPLIER = 0.80;

    private ArcherClass() {
    }

    public static List<ItemStack> createStarterKit() {
        return List.of(
                new ItemStack(Items.BOW),
                new ItemStack(Items.ARROW, 32),
                new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.LEATHER_HELMET)
        );
    }
}
