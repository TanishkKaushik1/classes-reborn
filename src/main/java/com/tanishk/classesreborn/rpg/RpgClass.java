package com.tanishk.classesreborn.rpg;

import com.tanishk.classesreborn.rpg.classes.archer.ArcherClass;
import com.tanishk.classesreborn.rpg.classes.assassin.AssassinClass;
import com.tanishk.classesreborn.rpg.classes.healer.HealerClass;
import com.tanishk.classesreborn.rpg.classes.swordsman.SwordsmanClass;
import com.tanishk.classesreborn.rpg.classes.tank.TankClass;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

public enum RpgClass {
    TANK(
            TankClass.ID,
            TankClass.MAX_HEALTH_BONUS,
            TankClass.ARMOR_BONUS,
            TankClass.ARMOR_TOUGHNESS_BONUS,
            TankClass.MOVEMENT_SPEED_BONUS,
            TankClass.ATTACK_DAMAGE_BONUS,
            TankClass.RANGED_DAMAGE_MULTIPLIER,
            TankClass.HEALING_MULTIPLIER,
            TankClass.AGGRO_MULTIPLIER,
            TankClass::createStarterKit
    ),
    SWORDSMAN(
            SwordsmanClass.ID,
            SwordsmanClass.MAX_HEALTH_BONUS,
            SwordsmanClass.ARMOR_BONUS,
            SwordsmanClass.ARMOR_TOUGHNESS_BONUS,
            SwordsmanClass.MOVEMENT_SPEED_BONUS,
            SwordsmanClass.ATTACK_DAMAGE_BONUS,
            SwordsmanClass.RANGED_DAMAGE_MULTIPLIER,
            SwordsmanClass.HEALING_MULTIPLIER,
            SwordsmanClass.AGGRO_MULTIPLIER,
            SwordsmanClass::createStarterKit
    ),
    ASSASSIN(
            AssassinClass.ID,
            AssassinClass.MAX_HEALTH_BONUS,
            AssassinClass.ARMOR_BONUS,
            AssassinClass.ARMOR_TOUGHNESS_BONUS,
            AssassinClass.MOVEMENT_SPEED_BONUS,
            AssassinClass.ATTACK_DAMAGE_BONUS,
            AssassinClass.RANGED_DAMAGE_MULTIPLIER,
            AssassinClass.HEALING_MULTIPLIER,
            AssassinClass.AGGRO_MULTIPLIER,
            AssassinClass::createStarterKit
    ),
    ARCHER(
            ArcherClass.ID,
            ArcherClass.MAX_HEALTH_BONUS,
            ArcherClass.ARMOR_BONUS,
            ArcherClass.ARMOR_TOUGHNESS_BONUS,
            ArcherClass.MOVEMENT_SPEED_BONUS,
            ArcherClass.ATTACK_DAMAGE_BONUS,
            ArcherClass.RANGED_DAMAGE_MULTIPLIER,
            ArcherClass.HEALING_MULTIPLIER,
            ArcherClass.AGGRO_MULTIPLIER,
            ArcherClass::createStarterKit
    ),
    HEALER(
            HealerClass.ID,
            HealerClass.MAX_HEALTH_BONUS,
            HealerClass.ARMOR_BONUS,
            HealerClass.ARMOR_TOUGHNESS_BONUS,
            HealerClass.MOVEMENT_SPEED_BONUS,
            HealerClass.ATTACK_DAMAGE_BONUS,
            HealerClass.RANGED_DAMAGE_MULTIPLIER,
            HealerClass.HEALING_MULTIPLIER,
            HealerClass.AGGRO_MULTIPLIER,
            HealerClass::createStarterKit
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
    private final Supplier<List<ItemStack>> starterKitFactory;

    RpgClass(
            String id,
            double maxHealthBonus,
            double armorBonus,
            double armorToughnessBonus,
            double movementSpeedBonus,
            double attackDamageBonus,
            double rangedDamageMultiplier,
            double healingMultiplier,
            double aggroMultiplier,
            Supplier<List<ItemStack>> starterKitFactory
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
        this.starterKitFactory = starterKitFactory;
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

    public List<ItemStack> createStarterKit() {
        return starterKitFactory.get();
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
