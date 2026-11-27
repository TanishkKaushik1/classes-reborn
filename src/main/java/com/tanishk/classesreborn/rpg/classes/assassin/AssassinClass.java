package com.tanishk.classesreborn.rpg.classes.assassin;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class AssassinClass {
    public static final String ID = "assassin";
    public static final double MAX_HEALTH_BONUS = -2.0;
    public static final double ARMOR_BONUS = -2.0;
    public static final double ARMOR_TOUGHNESS_BONUS = 0.0;
    public static final double MOVEMENT_SPEED_BONUS = 0.035;
    public static final double ATTACK_DAMAGE_BONUS = 3.0;
    public static final double RANGED_DAMAGE_MULTIPLIER = 0.70;
    public static final double HEALING_MULTIPLIER = 1.0;
    public static final double AGGRO_MULTIPLIER = 0.55;

    private AssassinClass() {
    }

    public static List<ItemStack> createStarterKit() {
        return List.of(
                new ItemStack(Items.STONE_SWORD),
                new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.LEATHER_BOOTS),
                new ItemStack(Items.ENDER_PEARL, 2)
        );
    }
}
