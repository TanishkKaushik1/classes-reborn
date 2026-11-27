package com.tanishk.classesreborn.rpg.skill;

import com.tanishk.classesreborn.attachment.ModAttachments;
import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.network.CastSkillPayload;
import com.tanishk.classesreborn.network.SyncRpgDataPayload;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SkillRegistry {
    public static final String TANK_ROAR = "tank_roar";
    public static final String SWORDSMAN_QUICKSLASH = "swordsman_quickslash";
    public static final String ARCHER_DASH = "archer_dash";
    public static final String ARCHER_NIGHTVISION = "archer_nightvision";
    public static final String ARCHER_LEAP = "archer_leap";
    public static final String ASSASSIN_INVISIBILITY = "assassin_invisibility";
    public static final String HEALER_HEAL = "healer_heal";

    private static final int TWO_MINUTES_TICKS = 2400; // 120s * 20 ticks
    private static final int ONE_MINUTE_TICKS = 1200;  // 60s * 20 ticks
    private static final int LEAP_RECAST_DELAY_TICKS = 75;

    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    // Runtime trackers (server authoritative; synced via SyncRpgDataPayload when needed)
    private static final Map<UUID, Long> TAUNT_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> STEALTH_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> QUICKSLASH_SURGE_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> ARCHER_BOOST_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, PendingCast> PENDING_CASTS = new HashMap<>();
    private static final Map<UUID, MoveInput> LAST_MOVE_INPUT = new HashMap<>();
    private static final Map<String, Map<UUID, Long>> RECAST_READY_AT_TICK = new HashMap<>();

    public record MoveInput(float forward, float strafe) {}

    public record PendingCast(String skillId, long completeAtGameTime) {}

    public record ActiveBuffSnapshot(
            int tauntTicksLeft,
            int stealthTicksLeft,
            int quickslashSurgeTicksLeft,
            int archerBoostTicksLeft,
            int pendingHealTicksLeft
    ) {
        public static final ActiveBuffSnapshot EMPTY = new ActiveBuffSnapshot(0, 0, 0, 0, 0);

        public int totalHash() {
            return Objects.hash(
                    tauntTicksLeft > 0,
                    stealthTicksLeft > 0,
                    quickslashSurgeTicksLeft > 0,
                    archerBoostTicksLeft > 0,
                    pendingHealTicksLeft > 0
            );
        }
    }

    static {
        register(new Skill(TANK_ROAR, RpgClass.TANK, 1, 160, SkillRegistry::castTankRoar));
        register(new Skill(SWORDSMAN_QUICKSLASH, RpgClass.SWORDSMAN, 1, 120, SkillRegistry::castSwordsmanQuickslash));
        register(new Skill(ARCHER_DASH, RpgClass.ARCHER, 1, 70, SkillRegistry::castArcherDash));
        // 2 min active duration (2400 ticks), flat 1 min cooldown (1200 ticks) — changes coming later
        register(new Skill(ARCHER_NIGHTVISION, RpgClass.ARCHER, 1, ONE_MINUTE_TICKS, SkillRegistry::castArcherNightVision));
        // 2 min active window (2400 ticks) with multi-leap re-trigger + tiered Slow Falling (up to IV), flat 1 min cooldown after window ends
        register(new Skill(ARCHER_LEAP, RpgClass.ARCHER, 1, ONE_MINUTE_TICKS, SkillRegistry::castArcherLeap));
        register(new Skill(ASSASSIN_INVISIBILITY, RpgClass.ASSASSIN, 1, 180, SkillRegistry::castAssassinInvisibility));
        // 240 ticks = 12s cooldown (applied only when a teammate is actually healed)
        register(new Skill(HEALER_HEAL, RpgClass.HEALER, 1, 240, SkillRegistry::beginHealerHealCast));
    }

    private SkillRegistry() {
    }

    private static void register(Skill skill) {
        SKILLS.put(skill.getId(), skill);
    }

    public static @Nullable Skill get(String id) {
        return SKILLS.get(id);
    }

    public static Collection<Skill> getAll() {
        return Collections.unmodifiableCollection(SKILLS.values());
    }

    public static List<Skill> getForClass(RpgClass rpgClass) {
        List<Skill> list = new ArrayList<>();
        for (Skill skill : SKILLS.values()) {
            if (skill.getOwnerClass() == rpgClass) {
                list.add(skill);
            }
        }
        return list;
    }

    /** Resolves whichever skill is in active slot 0 (G), 1 (R), or 2 (C) for casting. */
    public static @Nullable String getActiveSlotSkillId(PlayerRpgData data, int slotIndex) {
        String id = data.getSkillInSlot(slotIndex);
        return (id == null || id.isBlank()) ? null : id;
    }

    public static void updateMoveInput(UUID playerId, float forward, float strafe) {
        LAST_MOVE_INPUT.put(playerId, new MoveInput(forward, strafe));
    }

    public static boolean isOnRecastDelay(String skillId, UUID playerId, long gameTime) {
        Map<UUID, Long> perPlayer = RECAST_READY_AT_TICK.get(skillId);
        if (perPlayer == null) return false;
        Long readyAt = perPlayer.get(playerId);
        if (readyAt == null) return false;
        if (gameTime >= readyAt) {
            perPlayer.remove(playerId);
            return false;
        }
        return true;
    }

    public static int getRemainingRecastDelayTicks(String skillId, UUID playerId, long gameTime) {
        Map<UUID, Long> perPlayer = RECAST_READY_AT_TICK.get(skillId);
        if (perPlayer == null) return 0;
        Long readyAt = perPlayer.get(playerId);
        if (readyAt == null || gameTime >= readyAt) return 0;
        return (int) Math.min(Integer.MAX_VALUE, readyAt - gameTime);
    }

    public static void setRecastDelay(String skillId, UUID playerId, long gameTime, int delayTicks) {
        if (delayTicks <= 0) return;
        RECAST_READY_AT_TICK
                .computeIfAbsent(skillId, k -> new HashMap<>())
                .put(playerId, gameTime + delayTicks);
    }

    private static int remainingTicks(Map<UUID, Long> map, UUID playerId, long gameTime) {
        Long until = map.get(playerId);
        if (until == null || gameTime >= until) return 0;
        return (int) Math.min(Integer.MAX_VALUE, until - gameTime);
    }

    public static boolean isTaunting(UUID playerId, long gameTime) {
        return remainingTicks(TAUNT_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static boolean isStealthed(UUID playerId, long gameTime) {
        return remainingTicks(STEALTH_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static boolean hasQuickslashSurge(UUID playerId, long gameTime) {
        return remainingTicks(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static boolean hasArcherDashBoost(UUID playerId, long gameTime) {
        return remainingTicks(ARCHER_BOOST_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static ActiveBuffSnapshot getBuffSnapshot(UUID playerId, long gameTime) {
        PendingCast pending = PENDING_CASTS.get(playerId);
        int pendingTicks = (pending != null && HEALER_HEAL.equals(pending.skillId()) && gameTime < pending.completeAtGameTime())
                ? (int) Math.min(Integer.MAX_VALUE, pending.completeAtGameTime() - gameTime)
                : 0;

        return new ActiveBuffSnapshot(
                remainingTicks(TAUNT_UNTIL_TICK, playerId, gameTime),
                remainingTicks(STEALTH_UNTIL_TICK, playerId, gameTime),
                remainingTicks(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime),
                remainingTicks(ARCHER_BOOST_UNTIL_TICK, playerId, gameTime),
                pendingTicks
        );
    }

    public static void applySyncedBuffSnapshot(UUID playerId, long gameTime, ActiveBuffSnapshot snapshot) {
        setUntil(TAUNT_UNTIL_TICK, playerId, gameTime, snapshot.tauntTicksLeft());
        setUntil(STEALTH_UNTIL_TICK, playerId, gameTime, snapshot.stealthTicksLeft());
        setUntil(QUICKSLASH_SURGE_UNTIL_TICK, playerId, gameTime, snapshot.quickslashSurgeTicksLeft());
        setUntil(ARCHER_BOOST_UNTIL_TICK, playerId, gameTime, snapshot.archerBoostTicksLeft());
        if (snapshot.pendingHealTicksLeft() > 0) {
            PENDING_CASTS.put(playerId, new PendingCast(HEALER_HEAL, gameTime + snapshot.pendingHealTicksLeft()));
        } else {
            PENDING_CASTS.remove(playerId);
        }
    }

    private static void setUntil(Map<UUID, Long> map, UUID playerId, long gameTime, int ticksLeft) {
        if (ticksLeft > 0) {
            map.put(playerId, gameTime + ticksLeft);
        } else {
            map.remove(playerId);
        }
    }

    public static float computeHealerHealOutput(PlayerRpgData data) {
        int skillLevel = Math.max(1, data.getSkillLevel(HEALER_HEAL));
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        double mult = ClassStatManager.getEffectiveHealingMultiplier(data);
        double skillScale = 1.0 + (skillLevel - 1) * 0.05;
        return (float) ((6.0 + (data.getLevel() - 1) * 0.75) * mult * skillScale * tier.getPowerMultiplier());
    }

    public static boolean tryStartHealerHealCast(ServerPlayer player, PlayerRpgData data) {
        int skillLevel = Math.max(1, data.getSkillLevel(HEALER_HEAL));
        return beginHealerHealCast(player, data, skillLevel, SkillTier.fromSkillLevel(skillLevel));
    }

    public static void tickServerPlayer(ServerPlayer player, PlayerRpgData data) {
        long gameTime = player.level().getGameTime();

        // Clean up expired entries
        if (!isTaunting(player.getUUID(), gameTime)) TAUNT_UNTIL_TICK.remove(player.getUUID());
        if (!isStealthed(player.getUUID(), gameTime)) STEALTH_UNTIL_TICK.remove(player.getUUID());
        if (!hasQuickslashSurge(player.getUUID(), gameTime)) QUICKSLASH_SURGE_UNTIL_TICK.remove(player.getUUID());
        if (!hasArcherDashBoost(player.getUUID(), gameTime)) ARCHER_BOOST_UNTIL_TICK.remove(player.getUUID());
        data.getActiveRangedDamageBonus(gameTime);

        // When Leap's 2-minute active window expires, start its flat 1-minute cooldown (no level/tier reduction)
        long leapEndTick = data.getActiveWindowEndTick(ARCHER_LEAP);
        if (leapEndTick > 0 && gameTime >= leapEndTick) {
            data.clearActiveWindow(ARCHER_LEAP);
            Map<UUID, Long> leapDelays = RECAST_READY_AT_TICK.get(ARCHER_LEAP);
            if (leapDelays != null) leapDelays.remove(player.getUUID());
            data.startCooldown(ARCHER_LEAP, gameTime, ONE_MINUTE_TICKS);
            player.displayClientMessage(Component.literal("Leap window ended - 1m cooldown started."), true);
            PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
        }

        // Process 1.0s (20-tick) Healer cast completion
        PendingCast pending = PENDING_CASTS.get(player.getUUID());
        if (pending != null && gameTime >= pending.completeAtGameTime()) {
            PENDING_CASTS.remove(player.getUUID());
            if (HEALER_HEAL.equals(pending.skillId())) {
                finishHealerHeal(player, data);
            }
        }
    }

    private static boolean castArcherNightVision(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        int duration = (int) Math.round((TWO_MINUTES_TICKS + (skillLevel - 1) * 40) * Math.sqrt(tier.getPowerMultiplier()));
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, duration, 0, false, false));
        data.startActiveWindow(ARCHER_NIGHTVISION, gameTime, duration);

        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 1.0f, 1.25f);
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.2, player.getZ(), 10, 0.35, 0.35, 0.35, 0.02);
        player.displayClientMessage(
                Component.literal("Night Vision active for " + (duration / 20) + "s! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"),
                true
        );
        return true;
    }

    private static boolean castArcherLeap(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        boolean firstActivation = !data.isSkillActive(ARCHER_LEAP, gameTime);
        int windowDuration = firstActivation
                ? (int) Math.round((TWO_MINUTES_TICKS + (skillLevel - 1) * 40) * Math.sqrt(tier.getPowerMultiplier()))
                : data.getRemainingActiveTicks(ARCHER_LEAP, gameTime);

        if (firstActivation) {
            data.startActiveWindow(ARCHER_LEAP, gameTime, windowDuration);
        }

        // Slow Falling scales from tier to tier up to Slow Falling IV (amplifier 3)
        int slowFallingAmplifier = Math.min(3, Math.max(0, tier.tierIndex()));
        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, Math.max(20, windowDuration), slowFallingAmplifier, false, true));

        MoveInput input = LAST_MOVE_INPUT.getOrDefault(player.getUUID(), new MoveInput(0.0f, 0.0f));
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

        setRecastDelay(ARCHER_LEAP, player.getUUID(), gameTime, LEAP_RECAST_DELAY_TICKS);

        level.playSound(null, player.blockPosition(), SoundEvents.WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.0f, 1.1f);
        level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(), 10, 0.3, 0.15, 0.3, 0.04);

        int secsLeft = Math.max(1, data.getRemainingActiveTicks(ARCHER_LEAP, gameTime) / 20);
        int slowFallingLevel = slowFallingAmplifier + 1;
        player.displayClientMessage(
                Component.literal("Leap! Slow Falling " + slowFallingLevel + " (" + secsLeft + "s left)"),
                true
        );
        return true;
    }

    private static boolean castTankRoar(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
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

    private static boolean castSwordsmanQuickslash(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
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

    private static boolean castArcherDash(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;

        MoveInput input = LAST_MOVE_INPUT.getOrDefault(player.getUUID(), new MoveInput(1.0f, 0.0f));
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
        ARCHER_BOOST_UNTIL_TICK.put(player.getUUID(), level.getGameTime() + 80); // 4s mobility window

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

    private static boolean castAssassinInvisibility(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
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

    private static boolean beginHealerHealCast(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (PENDING_CASTS.containsKey(player.getUUID())) {
            return false;
        }
        if (findLookedAtTeammate(player, 10.0) == null) {
            player.displayClientMessage(Component.literal("No teammate in sight to heal!"), true);
            return false;
        }
        // 1.0 second = 20 ticks
        PENDING_CASTS.put(player.getUUID(), new PendingCast(HEALER_HEAL, player.level().getGameTime() + 20));
        player.displayClientMessage(Component.literal("Casting Heal (1.0s)..."), true);
        return true;
    }

    private static void finishHealerHeal(ServerPlayer caster, PlayerRpgData data) {
        if (!(caster.level() instanceof ServerLevel level)) return;

        Player aimedTeammate = findLookedAtTeammate(caster, 10.0);
        if (aimedTeammate == null || aimedTeammate.getUUID().equals(caster.getUUID())) {
            caster.displayClientMessage(Component.literal("No teammate in sight to heal! (No cooldown)"), true);
            return;
        }

        PlayerRpgData currentData = caster.getData(ModAttachments.PLAYER_RPG_DATA.get());
        float healAmount = computeHealerHealOutput(currentData);

        aimedTeammate.heal(healAmount);

        Skill skill = SKILLS.get(HEALER_HEAL);
        int cdTicks = skill != null ? skill.getCooldownTicksForLevel(currentData.getSkillLevel(HEALER_HEAL)) : 240;
        currentData.startCooldown(HEALER_HEAL, level.getGameTime(), cdTicks);
        caster.getCooldowns().addCooldown(ModItems.HEALER_STAFF.get(), cdTicks);

        level.sendParticles(
                ParticleTypes.HEART,
                aimedTeammate.getX(),
                aimedTeammate.getY() + aimedTeammate.getBbHeight() + 0.25,
                aimedTeammate.getZ(),
                6,
                0.35,
                0.25,
                0.35,
                0.02
        );
        level.playSound(null, aimedTeammate.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.3f);
        caster.displayClientMessage(Component.literal("Healed " + aimedTeammate.getName().getString() + "!"), true);
    }

    private static @Nullable Player findLookedAtTeammate(ServerPlayer caster, double maxDistance) {
        Vec3 eyePos = caster.getEyePosition();
        Vec3 look = caster.getLookAngle().normalize();
        AABB search = caster.getBoundingBox().expandTowards(look.scale(maxDistance)).inflate(1.5);

        Player best = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (Player candidate : caster.level().getEntitiesOfClass(Player.class, search, p -> p != caster && !p.getUUID().equals(caster.getUUID()) && p.isAlive())) {
            Vec3 toCenter = candidate.position().add(0.0, candidate.getBbHeight() * 0.5, 0.0).subtract(eyePos);
            double dist = toCenter.length();
            if (dist < 0.25 || dist > maxDistance) continue;

            Vec3 dirToCandidate = toCenter.scale(1.0 / dist);
            double dot = look.dot(dirToCandidate);
            if (dot < 0.97) continue;

            HitResult hitResult = caster.level().clip(new ClipContext(
                    eyePos,
                    candidate.getEyePosition(),
                    ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE,
                    caster
            ));
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                continue;
            }

            double anglePenalty = (1.0 - dot) * 20.0;
            double score = dist + anglePenalty;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    /** Helper for keybind handlers to cast a specific active slot (0 = G, 1 = R, 2 = C). */
    public static void requestCastActiveSlot(net.minecraft.client.Minecraft mc, int slotIndex) {
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        String skillId = getActiveSlotSkillId(data, slotIndex);
        if (skillId == null) {
            mc.player.displayClientMessage(Component.literal("Slot " + (slotIndex + 1) + " has no active skill equipped."), true);
            return;
        }
        PacketDistributor.sendToServer(new CastSkillPayload(skillId));
    }
}