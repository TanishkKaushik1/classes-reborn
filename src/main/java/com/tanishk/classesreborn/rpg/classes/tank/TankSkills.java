package com.tanishk.classesreborn.rpg.classes.tank;

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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class TankSkills {
    public static final String ROAR = "tank_roar";

    private static final Map<UUID, Long> TAUNT_UNTIL_TICK = new HashMap<>();

    private TankSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        registrar.accept(new Skill(ROAR, RpgClass.TANK, 1, 160, TankSkills::castRoar));
    }

    public static boolean isTaunting(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(TAUNT_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static int getTauntTicksLeft(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(TAUNT_UNTIL_TICK, playerId, gameTime);
    }

    public static void setTauntTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        SkillRegistry.setUntil(TAUNT_UNTIL_TICK, playerId, gameTime, ticksLeft);
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        if (!isTaunting(player.getUUID(), gameTime)) {
            TAUNT_UNTIL_TICK.remove(player.getUUID());
        }
    }

    private static boolean castRoar(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        // Extended duration: 12s base + level/tier scaling
        int duration = (int) Math.round((240 + (skillLevel - 1) * 8) * Math.sqrt(tier.getPowerMultiplier()));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, duration, 0, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, duration + 40, 1, false, true));
        TAUNT_UNTIL_TICK.put(player.getUUID(), gameTime + duration + 40);

        double radius = 10.0 + tier.tierIndex() * 0.75;
        AABB box = player.getBoundingBox().inflate(radius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m instanceof Enemy && m.isAlive())) {
            mob.setTarget(player);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.85f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, player.getX(), player.getY() + 1.0, player.getZ(), 1, 0, 0, 0, 0);
        player.displayClientMessage(Component.literal("Roar! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"), true);
        return true;
    }
}
