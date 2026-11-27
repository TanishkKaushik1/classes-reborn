package com.tanishk.classesreborn.rpg.classes.swordsman;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.Skill;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import com.tanishk.classesreborn.rpg.skill.SkillTier;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class SwordsmanSkills {
    public static final String QUICKSLASH = "swordsman_quickslash";

    private static final Map<UUID, Long> QUICKSLASH_SURGE_UNTIL_TICK = new HashMap<>();

    private SwordsmanSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        registrar.accept(new Skill(QUICKSLASH, RpgClass.SWORDSMAN, 1, 120, SwordsmanSkills::castQuickslash));
    }

    public static boolean hasQuickslashSurge(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static int getQuickslashSurgeTicksLeft(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime);
    }

    public static void setQuickslashSurgeTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        SkillRegistry.setUntil(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime, ticksLeft);
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        if (!hasQuickslashSurge(player.getUUID(), gameTime)) {
            QUICKSLASH_SURGE_UNTIL_TICK.remove(player.getUUID());
        }
    }

    private static boolean castQuickslash(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        // Extended surge: 10s base + level/tier scaling
        int surgeDuration = (int) Math.round((200 + (skillLevel - 1) * 8) * Math.sqrt(tier.getPowerMultiplier()));
        QUICKSLASH_SURGE_UNTIL_TICK.put(player.getUUID(), gameTime + surgeDuration);

        Vec3 look = player.getLookAngle().normalize();
        AABB hitBox = player.getBoundingBox().inflate(2.5).move(look.scale(1.5));
        float dmg = (float) ((5.0 + (data.getLevel() - 1) * 0.6 + (skillLevel - 1) * 0.4) * tier.getPowerMultiplier());
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, hitBox, e -> e != player && e.isAlive())) {
            target.hurt(player.damageSources().playerAttack(player), dmg);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.15f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX() + look.x, player.getY() + 1.0, player.getZ() + look.z, 4, 0.35, 0.2, 0.35, 0.0);
        player.displayClientMessage(Component.literal("Quickslash! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"), true);
        return true;
    }
}
