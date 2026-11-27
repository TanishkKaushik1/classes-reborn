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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class ArcherSkills {
    public static final String DASH = "archer_dash";
    public static final String MARK = "archer_mark";
    public static final String LEAP = "archer_leap";
    public static final String ARROW_BARRAGE = "archer_arrow_barrage";

    private static final int TWO_MINUTES_TICKS = 2400; // 120s * 20 ticks
    private static final int ONE_MINUTE_TICKS = 1200;  // 60s * 20 ticks
    private static final int LEAP_RECAST_DELAY_TICKS = 75;
    private static final int MARK_DURATION_TICKS = 600; // 30s * 20 ticks
    private static final int MARK_IFRAME_TICKS = 18;    // 0.9s i-frames while sliding
    private static final int BARRAGE_WINDOW_TICKS = 300; // 15s * 20 ticks

    public record MarkedInfo(UUID archerId, long expiresAtTick, float damageMultiplier) {}
    private record PendingTurn(UUID targetId, long turnAtTick) {}

    private static final Map<UUID, Long> DASH_BOOST_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> MARK_IFRAME_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, PendingTurn> MARK_TURN_TARGETS = new HashMap<>();
    private static final Map<UUID, MarkedInfo> MARKED_TARGETS = new HashMap<>();
    private static final Map<UUID, Integer> BARRAGE_BONUS_ARROWS = new HashMap<>();

    private ArcherSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        registrar.accept(new Skill(DASH, RpgClass.ARCHER, 1, 70, ArcherSkills::castDash));
        registrar.accept(new Skill(MARK, RpgClass.ARCHER, 1, 360, ArcherSkills::castMark));
        // 2 min active window (2400 ticks) with multi-leap re-trigger + tiered Slow Falling (up to IV), flat 1 min cooldown after window ends
        registrar.accept(new Skill(LEAP, RpgClass.ARCHER, 1, ONE_MINUTE_TICKS, ArcherSkills::castLeap));
        registrar.accept(new Skill(ARROW_BARRAGE, RpgClass.ARCHER, 1, 400, ArcherSkills::castArrowBarrage));
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

    public static boolean hasMarkIFrame(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(MARK_IFRAME_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static float getMarkedDamageMultiplier(UUID targetId, UUID attackerId, long gameTime) {
        MarkedInfo info = MARKED_TARGETS.get(targetId);
        if (info == null || gameTime >= info.expiresAtTick()) {
            MARKED_TARGETS.remove(targetId);
            return 1.0f;
        }
        return info.archerId().equals(attackerId) ? info.damageMultiplier() : 1.0f;
    }

    public static int consumeArrowBarrageBonusArrows(PlayerRpgData data, UUID playerId, long gameTime) {
        if (!data.isSkillActive(ARROW_BARRAGE, gameTime)) {
            BARRAGE_BONUS_ARROWS.remove(playerId);
            return 0;
        }
        data.clearActiveWindow(ARROW_BARRAGE);
        Integer bonus = BARRAGE_BONUS_ARROWS.remove(playerId);
        return bonus != null ? bonus : 3;
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        if (!hasDashBoost(player.getUUID(), gameTime)) {
            DASH_BOOST_UNTIL_TICK.remove(player.getUUID());
        }
        if (hasMarkIFrame(player.getUUID(), gameTime)) {
            player.setPose(Pose.CROUCHING);
            if (player.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.15, player.getZ(), 2, 0.15, 0.05, 0.15, 0.01);
            }
        } else {
            MARK_IFRAME_UNTIL_TICK.remove(player.getUUID());
        }

        // Auto-turn toward the marked enemy's back right after sliding past them
        PendingTurn pendingTurn = MARK_TURN_TARGETS.get(player.getUUID());
        if (pendingTurn != null && gameTime >= pendingTurn.turnAtTick()) {
            MARK_TURN_TARGETS.remove(player.getUUID());
            if (player.level() instanceof ServerLevel level) {
                Entity target = level.getEntity(pendingTurn.targetId());
                if (target != null && target.isAlive()) {
                    Vec3 diff = target.getEyePosition().subtract(player.getEyePosition());
                    double horiz = Math.sqrt(diff.x * diff.x + diff.z * diff.z);
                    float yaw = (float) (Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90.0);
                    float pitch = (float) -Math.toDegrees(Math.atan2(diff.y, horiz));
                    player.teleportTo(level, player.getX(), player.getY(), player.getZ(), yaw, pitch);
                }
            }
        }

        // Gentle arrow homing toward marked enemies
        if (!MARKED_TARGETS.isEmpty() && player.level() instanceof ServerLevel level) {
            for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, player.getBoundingBox().inflate(48.0),
                    a -> a.isAlive() && !a.onGround() && player.equals(a.getOwner()))) {
                LivingEntity nearestMarked = null;
                double bestDistSq = 12.25; // 3.5 blocks
                for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class, arrow.getBoundingBox().inflate(5.5),
                        e -> e.isAlive() && MARKED_TARGETS.containsKey(e.getUUID()))) {
                    MarkedInfo info = MARKED_TARGETS.get(candidate.getUUID());
                    if (info != null && gameTime < info.expiresAtTick() && info.archerId().equals(player.getUUID())) {
                        double dSq = arrow.distanceToSqr(candidate);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            nearestMarked = candidate;
                        }
                    }
                }
                if (nearestMarked != null) {
                    Vec3 vel = arrow.getDeltaMovement();
                    double speed = vel.length();
                    if (speed > 0.2) {
                        Vec3 toTarget = nearestMarked.getEyePosition().subtract(arrow.position()).normalize().scale(speed);
                        Vec3 steered = vel.lerp(toTarget, 0.22).normalize().scale(speed);
                        arrow.setDeltaMovement(steered);
                    }
                }
            }
        }

        if (!data.isSkillActive(ARROW_BARRAGE, gameTime)) {
            BARRAGE_BONUS_ARROWS.remove(player.getUUID());
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

    private static boolean castMark(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        double range = 20.0;
        Vec3 eyePos = player.getEyePosition(1.0f);
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        AABB searchBox = player.getBoundingBox().expandTowards(lookVec.scale(range)).inflate(1.5);

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player,
                eyePos,
                endPos,
                searchBox,
                e -> e instanceof LivingEntity le && le.isAlive() && e != player && !e.isAlliedTo(player),
                range * range
        );

        if (hit == null || !(hit.getEntity() instanceof LivingEntity target)) {
            player.displayClientMessage(Component.literal("No enemy in sight to Mark!"), true);
            return false;
        }

        // Slide through the target and continue a little forward (no teleport)
        Vec3 toTarget = target.position().subtract(player.position());
        Vec3 horizDir = new Vec3(toTarget.x, 0.0, toTarget.z);
        if (horizDir.lengthSqr() < 1.0E-4) {
            horizDir = new Vec3(lookVec.x, 0.0, lookVec.z);
        }
        Vec3 slideVel = horizDir.normalize().scale(2.25);
        player.setDeltaMovement(slideVel.x, 0.18, slideVel.z);
        player.hurtMarked = true;
        player.fallDistance = 0.0f;

        // Grant i-frames while sliding and schedule auto-turn toward target's back
        SkillRegistry.setUntil(MARK_IFRAME_UNTIL_TICK, player.getUUID(), gameTime, MARK_IFRAME_TICKS);
        MARK_TURN_TARGETS.put(player.getUUID(), new PendingTurn(target.getUUID(), gameTime + 7));

        // Mark target for 30s (+25% base damage from this archer, scaling slightly with level/tier)
        float mult = Math.min(1.50f, 1.25f + (skillLevel - 1) * 0.03f + tier.tierIndex() * 0.03f);
        MARKED_TARGETS.put(target.getUUID(), new MarkedInfo(player.getUUID(), gameTime + MARK_DURATION_TICKS, mult));
        target.addEffect(new MobEffectInstance(MobEffects.GLOWING, MARK_DURATION_TICKS, 0, false, true));

        level.playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8f, 1.4f);
        level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 16, 0.4, 0.4, 0.4, 0.1);
        player.displayClientMessage(
                Component.literal("Marked " + target.getName().getString() + " for 30s! (+" + Math.round((mult - 1.0f) * 100) + "% dmg)"),
                true
        );
        return true;
    }

    private static boolean castArrowBarrage(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        // 4 arrows total at base (1 main + 3 bonus), 5 arrows total at Epic+ or Lv.3+ (1 main + 4 bonus)
        int bonusArrows = (tier.tierIndex() >= 2 || skillLevel >= 3) ? 4 : 3;
        BARRAGE_BONUS_ARROWS.put(player.getUUID(), bonusArrows);
        data.startActiveWindow(ARROW_BARRAGE, gameTime, BARRAGE_WINDOW_TICKS);

        level.playSound(null, player.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 1.35f);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.2, player.getZ(), 12, 0.35, 0.35, 0.35, 0.05);
        player.displayClientMessage(
                Component.literal("Arrow Barrage ready! Next bow shot fires " + (bonusArrows + 1) + " arrows."),
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
