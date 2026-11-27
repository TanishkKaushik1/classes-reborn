package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public class RpgCombatEvents {

    @SubscribeEvent
    public void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity().level().isClientSide()) return;
// Scale ranged projectile damage based on attacking player's class + Precision points + Dash Epic+ bonus
        if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer shooter) {
            PlayerRpgData data = PlayerRpgData.get(shooter);
            if (data.hasSelectedClass()) {
                double mult = ClassStatManager.getEffectiveRangedMultiplier(data);
                double flatBonus = data.getActiveRangedDamageBonus(shooter.level().getGameTime());
                float scaled = (float) (event.getAmount() * mult + flatBonus);
                event.setAmount(scaled);
            }
        }
        // Melee sword damage scaling for Swordsman (+ Quickslash surge)
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker) {
            PlayerRpgData data = PlayerRpgData.get(attacker);
            if (data.getSelectedClass() == RpgClass.SWORDSMAN) {
                ItemStack weapon = attacker.getMainHandItem();
                if (weapon.is(ItemTags.SWORDS)) {
                    float baseSwordBonus = 2.0f + (data.getLevel() - 1) * 0.4f;
                    float quickslashBonus = SkillRegistry.hasQuickslashSurge(
                            attacker.getUUID(), attacker.level().getGameTime()
                    ) ? 3.5f + (data.getLevel() - 1) * 0.35f : 0.0f;
                    event.setAmount(event.getAmount() + baseSwordBonus + quickslashBonus);
                }
            }
        }

        // Scale ranged projectile damage based on attacking player's class + Precision points
        if (event.getSource().getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() instanceof ServerPlayer shooter) {
            PlayerRpgData data = PlayerRpgData.get(shooter);
            if (data.hasSelectedClass()) {
                float scaled = (float) (event.getAmount() * ClassStatManager.getEffectiveRangedMultiplier(data));
                event.setAmount(scaled);
            }
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