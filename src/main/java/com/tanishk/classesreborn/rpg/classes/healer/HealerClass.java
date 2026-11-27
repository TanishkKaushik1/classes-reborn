package com.tanishk.classesreborn.rpg.classes.healer;

import com.tanishk.classesreborn.item.ModItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

public final class HealerClass {
    public static final String ID = "healer";
    public static final double MAX_HEALTH_BONUS = 1.0;
    public static final double ARMOR_BONUS = 0.0;
    public static final double ARMOR_TOUGHNESS_BONUS = 0.0;
    public static final double MOVEMENT_SPEED_BONUS = 0.0;
    public static final double ATTACK_DAMAGE_BONUS = -1.5;
    public static final double RANGED_DAMAGE_MULTIPLIER = 0.75;
    public static final double HEALING_MULTIPLIER = 1.50;
    public static final double AGGRO_MULTIPLIER = 0.90;

    private HealerClass() {
    }

    public static List<ItemStack> createStarterKit() {
        return List.of(
                new ItemStack(ModItems.HEALER_STAFF.get()),
                new ItemStack(Items.LEATHER_CHESTPLATE),
                new ItemStack(Items.COOKED_BEEF, 4)
        );
    }
}
