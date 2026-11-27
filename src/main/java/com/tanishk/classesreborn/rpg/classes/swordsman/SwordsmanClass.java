package com.tanishk.classesreborn.rpg.classes.swordsman;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class SwordsmanClass {
    public static final String ID = "swordsman";
    public static final double MAX_HEALTH_BONUS = 2.0;
    public static final double ARMOR_BONUS = 2.0;
    public static final double ARMOR_TOUGHNESS_BONUS = 0.0;
    public static final double MOVEMENT_SPEED_BONUS = 0.01;
    public static final double ATTACK_DAMAGE_BONUS = 2.5;
    public static final double RANGED_DAMAGE_MULTIPLIER = 0.90;
    public static final double HEALING_MULTIPLIER = 1.0;
    public static final double AGGRO_MULTIPLIER = 1.0;

    private SwordsmanClass() {
    }

    public static List<ItemStack> createStarterKit() {
        return List.of(
                new ItemStack(Items.IRON_SWORD),
                new ItemStack(Items.CHAINMAIL_CHESTPLATE),
                new ItemStack(Items.IRON_BOOTS),
                new ItemStack(Items.COOKED_BEEF, 4)
        );
    }
}
