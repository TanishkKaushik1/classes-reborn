package com.tanishk.classesreborn.rpg.classes.assassin;

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
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class AssassinSkills {
    public static final String INVISIBILITY = "assassin_invisibility";

    private static final Map<UUID, Long> STEALTH_UNTIL_TICK = new HashMap<>();

    private AssassinSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        registrar.accept(new Skill(INVISIBILITY, RpgClass.ASSASSIN, 1, 180, AssassinSkills::castInvisibility));
    }

    public static boolean isStealthed(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(STEALTH_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static int getStealthTicksLeft(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(STEALTH_UNTIL_TICK, playerId, gameTime);
    }

    public static void setStealthTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        SkillRegistry.setUntil(STEALTH_UNTIL_TICK, playerId, gameTime, ticksLeft);
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        if (!isStealthed(player.getUUID(), gameTime)) {
            STEALTH_UNTIL_TICK.remove(player.getUUID());
        }
    }

    private static boolean castInvisibility(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        // Extended stealth: 12s base + level/tier scaling
        int duration = (int) Math.round((240 + Math.min(100, (data.getLevel() - 1) * 6) + (skillLevel - 1) * 8) * Math.sqrt(tier.getPowerMultiplier()));
        STEALTH_UNTIL_TICK.put(player.getUUID(), gameTime + duration);

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, duration, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, duration, 0, false, false));

        double dropRadius = 14.0 + tier.tierIndex() * 1.0;
        AABB box = player.getBoundingBox().inflate(dropRadius);
        for (Mob mob : level.getEntitiesOfClass(Mob.class, box, m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }

        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7f, 1.4f);
        level.sendParticles(ParticleTypes.SMOKE, player.getX(), player.getY() + 1.0, player.getZ(), 12, 0.3, 0.4, 0.3, 0.02);
        player.displayClientMessage(Component.literal("Vanished! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"), true);
        return true;
    }
}
