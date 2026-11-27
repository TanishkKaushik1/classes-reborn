package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;

public class RpgAggroEvents {

    @SubscribeEvent
    public void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (!(mob instanceof Enemy)) return;
        if (!(event.getNewAboutToBeSetTarget() instanceof ServerPlayer targetPlayer)) return;

        long gameTime = mob.level().getGameTime();

        // If Tank is actively taunting, only allow targeting the Tank (or non-taunted mobs)
        if (SkillRegistry.isTaunting(targetPlayer.getUUID(), gameTime)) {
            return;
        }

        // Assassin Invisibility lowers aggro / breaks lock at distance
        if (SkillRegistry.isStealthed(targetPlayer.getUUID(), gameTime)) {
            double distSq = mob.distanceToSqr(targetPlayer);
            if (distSq > 9.0) { // > 3 blocks away
                event.setCanceled(true);
                return;
            }
        }

        PlayerRpgData data = PlayerRpgData.get(targetPlayer);
        if (!data.hasSelectedClass()) return;

        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        double effectiveRange = followRange * ClassStatManager.getEffectiveAggroMultiplier(data);
        double distSq = mob.distanceToSqr(targetPlayer);

        if (distSq > effectiveRange * effectiveRange) {
            event.setCanceled(true);
        }
    }
}