package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.classes.archer.ArcherSkills;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;

public class RpgCombatEvents {

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        long gameTime = event.getEntity().level().getGameTime();

        // Archer Mark i-frames while sliding
        if (event.getEntity() instanceof ServerPlayer victim && ArcherSkills.hasMarkIFrame(victim.getUUID(), gameTime)) {
            event.setCanceled(true);
            return;
        }

        // Scale ranged projectile damage based on attacking player's class + Precision points + Dash Epic+ bonus
        if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer shooter) {
            PlayerRpgData data = PlayerRpgData.get(shooter);
            if (data.hasSelectedClass()) {
                double mult = ClassStatManager.getEffectiveRangedMultiplier(data);
                double flatBonus = data.getActiveRangedDamageBonus(gameTime);
                float markMult = ArcherSkills.getMarkedDamageMultiplier(event.getEntity().getUUID(), shooter.getUUID(), gameTime);
                float scaled = (float) ((event.getAmount() * mult + flatBonus) * markMult);
                event.setAmount(scaled);
            }
        }
        // Melee sword damage scaling for Swordsman (+ Quickslash surge) and penalty for Archer
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker) {
            PlayerRpgData data = PlayerRpgData.get(attacker);
            ItemStack weapon = attacker.getMainHandItem();
            if (data.getSelectedClass() == RpgClass.SWORDSMAN && weapon.is(ItemTags.SWORDS)) {
                float baseSwordBonus = 2.0f + (data.getLevel() - 1) * 0.4f;
                float quickslashBonus = SkillRegistry.hasQuickslashSurge(
                        attacker.getUUID(), gameTime
                ) ? 3.5f + (data.getLevel() - 1) * 0.35f : 0.0f;
                event.setAmount(event.getAmount() + baseSwordBonus + quickslashBonus);
            } else if (data.getSelectedClass() == RpgClass.ARCHER && weapon.is(ItemTags.SWORDS)) {
                event.setAmount(Math.max(1.0f, event.getAmount() * 0.65f));
            }
            if (data.getSelectedClass() == RpgClass.ARCHER) {
                float markMult = ArcherSkills.getMarkedDamageMultiplier(event.getEntity().getUUID(), attacker.getUUID(), gameTime);
                if (markMult > 1.0f) {
                    event.setAmount(event.getAmount() * markMult);
                }
            }
        }
    }

    @SubscribeEvent
    public void onArrowLoose(ArrowLooseEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (!event.hasAmmo()) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        if (data.getSelectedClass() != RpgClass.ARCHER) return;

        int bonusArrows = ArcherSkills.consumeArrowBarrageBonusArrows(data, player.getUUID(), level.getGameTime());
        if (bonusArrows <= 0) return;

        float power = BowItem.getPowerForTime(event.getCharge());
        if (power < 0.1f) return;

        float[] yawOffsets = bonusArrows >= 4
                ? new float[]{-6.0f, -3.0f, 3.0f, 6.0f}
                : new float[]{-5.0f, 2.5f, 5.0f};

        for (float yawOffset : yawOffsets) {
            Arrow arrow = new Arrow(level, player, new ItemStack(Items.ARROW), event.getBow());
            arrow.shootFromRotation(player, player.getXRot(), player.getYRot() + yawOffset, 0.0f, power * 3.0f, 1.0f);
            arrow.setCritArrow(power == 1.0f);
            arrow.setBaseDamage(arrow.getBaseDamage() * 0.65);
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            level.addFreshEntity(arrow);
        }
    }

    @SubscribeEvent
    public void onLivingHeal(LivingHealEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof ServerPlayer healedPlayer) {
            PlayerRpgData data = PlayerRpgData.get(healedPlayer);
            if (data.hasSelectedClass()) {
                event.setAmount((float) (event.getAmount() * ClassStatManager.getEffectiveHealingMultiplier(data)));
            }
        }
    }
}