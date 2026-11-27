package com.tanishk.classesreborn.rpg.classes.archer;

import com.tanishk.classesreborn.network.SyncRpgDataPayload;
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
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ArcherSkills {
    public static final String DASH = "archer_dash";
    public static final String NIGHTVISION = "archer_nightvision";
    public static final String LEAP = "archer_leap";

    private static final int TWO_MINUTES_TICKS = 2400; // 120s * 20 ticks
    private static final int ONE_MINUTE_TICKS = 1200;  // 60s * 20 ticks
    private static final int LEAP_RECAST_DELAY_TICKS = 75;

    private static final Map<UUID, Long> DASH_BOOST_UNTIL_TICK = new HashMap<>();

    private ArcherSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        registrar.accept(new Skill(DASH, RpgClass.ARCHER, 1, 70, ArcherSkills::castDash));
        // 2 min active duration (2400 ticks), flat 1 min cooldown (1200 ticks) — changes coming later
        registrar.accept(new Skill(NIGHTVISION, RpgClass.ARCHER, 1, ONE_MINUTE_TICKS, ArcherSkills::castNightVision));
        // 2 min active window (2400 ticks) with multi-leap re-trigger + tiered Slow Falling (up to IV), flat 1 min cooldown after window ends
        registrar.accept(new Skill(LEAP, RpgClass.ARCHER, 1, ONE_MINUTE_TICKS, ArcherSkills::castLeap));
    }

    public static boolean hasDashBoost(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(DASH_BOOST_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static int getDashBoostTicksLeft(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(DASH_BOOST_UNTIL_TICK, playerId, gameTime);
    }

    public static void setDashBoostTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        SkillRegistry.setUntil(DASH_BOOST_UNTIL_TICK, playerId, gameTime, ticksLeft);
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        if (!hasDashBoost(player.getUUID(), gameTime)) {
            DASH_BOOST_UNTIL_TICK.remove(player.getUUID());
        }

        // When Leap's 2-minute active window expires, start its flat 1-minute cooldown (no level/tier reduction)
        long leapEndTick = data.getActiveWindowEndTick(LEAP);
        if (leapEndTick > 0 && gameTime >= leapEndTick) {
            data.clearActiveWindow(LEAP);
            SkillRegistry.clearRecastDelay(LEAP, player.getUUID());
            data.startCooldown(LEAP, gameTime, ONE_MINUTE_TICKS);
            player.displayClientMessage(Component.literal("Leap window ended - 1m cooldown started."), true);
            PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
        }
    }

    private static boolean castNightVision(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        int duration = (int) Math.round((TWO_MINUTES_TICKS + (skillLevel - 1) * 40) * Math.sqrt(tier.getPowerMultiplier()));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false));
        data.startActiveWindow(NIGHTVISION, gameTime, duration);

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.25f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.35, 0.35, 0.35, 0.02);
        player.displayClientMessage(
                Component.literal("Night Vision active for " + (duration / 20) + "s! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"),
                true
        );
        return true;
    }

    private static boolean castLeap(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        boolean firstActivation = !data.isSkillActive(LEAP, gameTime);
        int windowDuration = firstActivation
                ? (int) Math.round((TWO_MINUTES_TICKS + (skillLevel - 1) * 40) * Math.sqrt(tier.getPowerMultiplier()))
                : data.getRemainingActiveTicks(LEAP, gameTime);

        if (firstActivation) {
            data.startActiveWindow(LEAP, gameTime, windowDuration);
        }

        // Slow Falling scales from tier to tier up to Slow Falling IV (amplifier 3)
        int slowFallingAmplifier = Math.min(3, Math.max(0, tier.tierIndex()));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(20, windowDuration), slowFallingAmplifier, false, true));

        SkillRegistry.MoveInput input = SkillRegistry.getLastMoveInput(player.getUUID(), 0.0f, 0.0f);
        float yawRad = (float) Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double dx = forwardX * input.forward() + rightX * input.strafe();
        double dz = forwardZ * input.forward() + rightZ * input.strafe();
        Vec3 horiz = new Vec3(dx, 0.0, dz);
        if (horiz.lengthSqr() > 1.0E-4) {
            horiz = horiz.normalize().scale(0.55);
        }

        // Slight upward jump boost per skill level and tier (no cooldown reduction on level-up)
        double jumpVelocity = Math.min(1.45, 0.92 + (skillLevel - 1) * 0.02 + tier.tierIndex() * 0.035);
        player.setDeltaMovement(horiz.x, Math.max(player.getDeltaMovement().y, jumpVelocity), horiz.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;

        SkillRegistry.setRecastDelay(LEAP, player.getUUID(), gameTime, LEAP_RECAST_DELAY_TICKS);

        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.1f);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.15, 0.3, 0.04);

        int secsLeft = Math.max(1, data.getRemainingActiveTicks(LEAP, gameTime) / 20);
        int slowFallingLevel = slowFallingAmplifier + 1;
        player.displayClientMessage(
                Component.literal("Leap! Slow Falling " + slowFallingLevel + " (" + secsLeft + "s left)"),
                true
        );
        return true;
    }

    private static boolean castDash(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        SkillRegistry.MoveInput input = SkillRegistry.getLastMoveInput(player.getUUID(), 1.0f, 0.0f);
        float yawRad = (float) Math.toRadians(player.getYRot());
        double forwardX = -Math.sin(yawRad);
        double forwardZ = Math.cos(yawRad);
        double rightX = Math.cos(yawRad);
        double rightZ = Math.sin(yawRad);

        double dx = forwardX * input.forward() + rightX * input.strafe();
        double dz = forwardZ * input.forward() + rightZ * input.strafe();

        Vec3 dir = new Vec3(dx, 0.0, dz);
        if (dir.lengthSqr() < 1.0E-4) {
            Vec3 look = player.getLookAngle();
            dir = new Vec3(look.x, 0.0, look.z);
        }
        if (dir.lengthSqr() > 1.0E-4) {
            dir = dir.normalize();
        }

        // Dash distance is intentionally fixed regardless of skill level or tier
        boolean midAir = !player.onGround();
        double horizontalSpeed = midAir ? 1.35 : 1.15;
        double verticalBoost = midAir ? Math.max(player.getDeltaMovement().y, 0.12) : 0.18;

        player.setDeltaMovement(dir.x * horizontalSpeed, verticalBoost, dir.z * horizontalSpeed);
        player.hurtMarked = true;
        DASH_BOOST_UNTIL_TICK.put(player.getUUID(), level.getGameTime() + 80); // 4s mobility window

        if (tier.ordinal() >= SkillTier.EPIC.ordinal()) {
            double rangedBonus = 1.0 + skillLevel * 0.25;
            data.grantTemporaryRangedDamageBonus(rangedBonus, 180, level.getGameTime()); // 9s ranged bonus
            player.displayClientMessage(
                    Component.literal("Dash! +" + String.format("%.1f", rangedBonus) + " Ranged Dmg (" + tier.getDisplayName() + ")"),
                    true
            );
        }

        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 0.9f, 1.35f);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.5, player.getZ(), 8, 0.2, 0.1, 0.2, 0.05);
        return true;
    }
}
