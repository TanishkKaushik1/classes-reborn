package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.classes.swordsman.SwordsmanSkills;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.projectile.Projectile;
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
        // Swordsman incoming hit parry (manual Parry + Summon Sword 20% guardian parry)
        if (event.getEntity() instanceof ServerPlayer defender) {
            PlayerRpgData defenderData = PlayerRpgData.get(defender);
            SwordsmanSkills.onPlayerIncomingDamage(defender, defenderData, event);
            if (event.isCanceled()) return;
        }

        // Melee attack scaling for Swordsman (Surge + Berserk)
        if (event.getSource().getEntity() instanceof ServerPlayer attacker
                && event.getSource().getDirectEntity() == attacker) {
            PlayerRpgData data = PlayerRpgData.get(attacker);
            SwordsmanSkills.onPlayerMeleeAttack(attacker, data, event);
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