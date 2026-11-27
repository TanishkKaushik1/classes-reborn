package com.tanishk.classesreborn.rpg.classes.healer;

import com.tanishk.classesreborn.attachment.ModAttachments;
import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.rpg.ClassStatManager;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class HealerSkills {
    public static final String HEAL = "healer_heal";

    private static final Map<UUID, SkillRegistry.PendingCast> PENDING_CASTS = new HashMap<>();

    private HealerSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        // 240 ticks = 12s cooldown (applied only when a teammate is actually healed)
        registrar.accept(new Skill(HEAL, RpgClass.HEALER, 1, 240, HealerSkills::beginHealCast));
    }

    public static int getPendingHealTicksLeft(UUID playerId, long gameTime) {
        SkillRegistry.PendingCast pending = PENDING_CASTS.get(playerId);
        return (pending != null && HEAL.equals(pending.skillId()) && gameTime < pending.completeAtGameTime())
                ? (int) Math.min(Integer.MAX_VALUE, pending.completeAtGameTime() - gameTime)
                : 0;
    }

    public static void setPendingHealTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        if (ticksLeft > 0) {
            PENDING_CASTS.put(playerId, new SkillRegistry.PendingCast(HEAL, gameTime + ticksLeft));
        } else {
            PENDING_CASTS.remove(playerId);
        }
    }

    public static float computeHealOutput(PlayerRpgData data) {
        int skillLevel = Math.max(1, data.getSkillLevel(HEAL));
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        double mult = ClassStatManager.getEffectiveHealingMultiplier(data);
        double skillScale = 1.0 + (skillLevel - 1) * 0.05;
        return (float) ((6.0 + (data.getLevel() - 1) * 0.75) * mult * skillScale * tier.getPowerMultiplier());
    }

    public static boolean tryStartHealCast(ServerPlayer player, PlayerRpgData data) {
        int skillLevel = Math.max(1, data.getSkillLevel(HEAL));
        return beginHealCast(player, data, skillLevel, SkillTier.fromSkillLevel(skillLevel));
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        // Process 1.0s (20-tick) Healer cast completion
        SkillRegistry.PendingCast pending = PENDING_CASTS.get(player.getUUID());
        if (pending != null && gameTime >= pending.completeAtGameTime()) {
            PENDING_CASTS.remove(player.getUUID());
            if (HEAL.equals(pending.skillId())) {
                finishHeal(player, data);
            }
        }
    }

    private static boolean beginHealCast(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (PENDING_CASTS.containsKey(player.getUUID())) {
            return false;
        }
        if (findLookedAtTeammate(player, 10.0) == null) {
            player.displayClientMessage(Component.literal("No teammate in sight to heal!"), true);
            return false;
        }
        // 1.0 second = 20 ticks
        PENDING_CASTS.put(player.getUUID(), new SkillRegistry.PendingCast(HEAL, player.level().getGameTime() + 20));
        player.displayClientMessage(Component.literal("Casting Heal (1.0s)..."), true);
        return true;
    }

    private static void finishHeal(ServerPlayer caster, PlayerRpgData data) {
        if (!(caster.level() instanceof ServerLevel level)) return;

        Player aimedTeammate = findLookedAtTeammate(caster, 10.0);
        if (aimedTeammate == null || aimedTeammate.getUUID().equals(caster.getUUID())) {
            caster.displayClientMessage(Component.literal("No teammate in sight to heal! (No cooldown)"), true);
            return;
        }

        PlayerRpgData currentData = caster.getData(ModAttachments.PLAYER_RPG_DATA.get());
        float healAmount = computeHealOutput(currentData);

        aimedTeammate.heal(healAmount);

        Skill skill = SkillRegistry.get(HEAL);
        int cdTicks = skill != null ? skill.getCooldownTicksForLevel(currentData.getSkillLevel(HEAL)) : 240;
        currentData.startCooldown(HEAL, level.getGameTime(), cdTicks);
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
}
