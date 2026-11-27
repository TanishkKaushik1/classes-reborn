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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public final class SwordsmanSkills {
    public static final String SURGE = "swordsman_surge";
    public static final String PARRY = "swordsman_parry";
    public static final String SUMMON_SWORD = "swordsman_summon_sword";
    public static final String BERSERK = "swordsman_berserk";

    private static final int ONE_MINUTE_TICKS = 1200;
    private static final int TWO_MINUTES_TICKS = 2400;
    private static final int THREE_MINUTES_TICKS = 3600;
    private static final int PARRY_WINDOW_TICKS = 10; // 0.5s Sekiro-style deflect window
    private static final int SUMMON_STRIKE_INTERVAL_TICKS = 15; // 4 swords strike every 0.75s

    private static final Map<UUID, Long> SURGE_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> PARRY_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> SUMMON_SWORD_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Long> BERSERK_UNTIL_TICK = new HashMap<>();
    private static final Map<UUID, Integer> LAST_TARGET_ENTITY_ID = new HashMap<>();

    private SwordsmanSkills() {
    }

    public static void register(Consumer<Skill> registrar) {
        // 1) Surge: attack boost (2 min duration, 2 min cooldown)
        registrar.accept(new Skill(SURGE, Component.translatable("skill.classesreborn.swordsman_surge"),
                RpgClass.SWORDSMAN, 1, TWO_MINUTES_TICKS, SwordsmanSkills::castSurge));
        // 2) Parry: Sekiro-style deflect (no cooldown)
        registrar.accept(new Skill(PARRY, Component.translatable("skill.classesreborn.swordsman_parry"),
                RpgClass.SWORDSMAN, 1, 0, SwordsmanSkills::castParry));
        // 3) Summon Sword: 4 spirit swords (2 min duration, 1 min cooldown)
        registrar.accept(new Skill(SUMMON_SWORD, Component.translatable("skill.classesreborn.swordsman_summon_sword"),
                RpgClass.SWORDSMAN, 1, ONE_MINUTE_TICKS, SwordsmanSkills::castSummonSword));
        // 4) Berserk: rage mode (2 min duration, 3 min cooldown)
        registrar.accept(new Skill(BERSERK, Component.translatable("skill.classesreborn.swordsman_berserk"),
                RpgClass.SWORDSMAN, 1, THREE_MINUTES_TICKS, SwordsmanSkills::castBerserk));
    }

    public static boolean hasSurge(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(SURGE_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static int getSurgeTicksLeft(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(SURGE_UNTIL_TICK, playerId, gameTime);
    }

    public static void setSurgeTicksLeft(UUID playerId, long gameTime, int ticksLeft) {
        SkillRegistry.setUntil(SURGE_UNTIL_TICK, playerId, gameTime, ticksLeft);
    }

    public static boolean isParrying(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(PARRY_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static boolean hasSummonSword(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(SUMMON_SWORD_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static boolean isBerserk(UUID playerId, long gameTime) {
        return SkillRegistry.remainingTicks(BERSERK_UNTIL_TICK, playerId, gameTime) > 0;
    }

    public static void tick(ServerPlayer player, PlayerRpgData data, long gameTime) {
        UUID id = player.getUUID();
        if (!hasSurge(id, gameTime)) SURGE_UNTIL_TICK.remove(id);
        if (!isParrying(id, gameTime)) PARRY_UNTIL_TICK.remove(id);
        if (!hasSummonSword(id, gameTime)) SUMMON_SWORD_UNTIL_TICK.remove(id);
        if (!isBerserk(id, gameTime)) BERSERK_UNTIL_TICK.remove(id);

        if (isBerserk(id, gameTime) && gameTime % 10 == 0 && player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                    3, 0.35, 0.5, 0.35, 0.02);
        }

        if (hasSummonSword(id, gameTime) && player.level() instanceof ServerLevel level) {
            tickSummonedSwords(player, data, level, gameTime);
        }
    }

    /** Called from RpgCombatEvents when a player deals direct melee damage. */
    public static void onPlayerMeleeAttack(ServerPlayer attacker, PlayerRpgData data, LivingIncomingDamageEvent event) {
        if (data.getSelectedClass() != RpgClass.SWORDSMAN) return;

        long gameTime = attacker.level().getGameTime();
        LAST_TARGET_ENTITY_ID.put(attacker.getUUID(), event.getEntity().getId());

        float amount = event.getAmount();
        if (attacker.getMainHandItem().is(ItemTags.SWORDS)) {
            float baseSwordBonus = 2.0f + (data.getLevel() - 1) * 0.4f;
            float surgeBonus = hasSurge(attacker.getUUID(), gameTime)
                    ? getSurgeBonus(data)
                    : 0.0f;
            amount += baseSwordBonus + surgeBonus;
        }

        // Berserk: +200% damage dealt (3.0x total)
        if (isBerserk(attacker.getUUID(), gameTime)) {
            amount *= 3.0f;
        }

        event.setAmount(amount);
    }

    /** Called from RpgCombatEvents when a player is about to take damage (handles Parry + Guardian Sword 20% parry). */
    public static void onPlayerIncomingDamage(ServerPlayer defender, PlayerRpgData data, LivingIncomingDamageEvent event) {
        if (data.getSelectedClass() != RpgClass.SWORDSMAN) return;
        if (!(defender.level() instanceof ServerLevel level)) return;

        long gameTime = level.getGameTime();
        UUID id = defender.getUUID();

        // 1) Active Sekiro-style manual Parry
        if (isParrying(id, gameTime)) {
            PARRY_UNTIL_TICK.remove(id);
            performParryDeflect(defender, data, level, event, false);
            return;
        }

        // 2) Summon Sword: the 2 guardian swords have a 20% chance to parry incoming hits
        if (hasSummonSword(id, gameTime) && level.random.nextFloat() < 0.20f) {
            performParryDeflect(defender, data, level, event, true);
        }
    }

    private static float getSurgeBonus(PlayerRpgData data) {
        int skillLevel = Math.max(1, data.getSkillLevel(SURGE));
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        return (float) ((4.0 + (data.getLevel() - 1) * 0.4 + (skillLevel - 1) * 0.5) * tier.getPowerMultiplier());
    }

    private static void performParryDeflect(
            ServerPlayer defender,
            PlayerRpgData data,
            ServerLevel level,
            LivingIncomingDamageEvent event,
            boolean fromGuardianSword
    ) {
        event.setCanceled(true);

        long gameTime = level.getGameTime();
        level.playSound(null, defender.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.7f, 1.85f);
        level.sendParticles(ParticleTypes.CRIT, defender.getX(), defender.getY() + 1.1, defender.getZ(),
                12, 0.45, 0.35, 0.45, 0.15);

        if (event.getSource().getEntity() instanceof LivingEntity attacker && attacker.isAlive() && attacker != defender) {
            Vec3 push = attacker.position().subtract(defender.position());
            Vec3 horiz = new Vec3(push.x, 0.0, push.z);
            if (horiz.lengthSqr() > 1.0E-4) {
                horiz = horiz.normalize().scale(0.65);
                attacker.push(horiz.x, 0.22, horiz.z);
            }

            int parryLvl = Math.max(1, data.getSkillLevel(PARRY));
            SkillTier tier = SkillTier.fromSkillLevel(parryLvl);
            float counterDamage = (float) ((4.0 + (parryLvl - 1) * 0.6) * tier.getPowerMultiplier());
            if (isBerserk(defender.getUUID(), gameTime)) {
                counterDamage *= 3.0f;
            }
            attacker.hurt(defender.damageSources().playerAttack(defender), counterDamage);
        }

        defender.displayClientMessage(
                Component.literal(fromGuardianSword ? "Guardian Sword Parried!" : "PARRY!"),
                true
        );
    }

    private static void tickSummonedSwords(ServerPlayer player, PlayerRpgData data, ServerLevel level, long gameTime) {
        // Orbit particles every 4 ticks so the 4 floating swords are visible around the player
        if (gameTime % 4 == 0) {
            double baseAngle = (gameTime * 0.25) % (Math.PI * 2.0);
            for (int i = 0; i < 4; i++) {
                double angle = baseAngle + i * (Math.PI / 2.0);
                double px = player.getX() + Math.cos(angle) * 1.4;
                double py = player.getY() + 1.15 + Math.sin(baseAngle * 2.0 + i) * 0.15;
                double pz = player.getZ() + Math.sin(angle) * 1.4;
                level.sendParticles(ParticleTypes.ENCHANTED_HIT, px, py, pz, 1, 0, 0, 0, 0);
            }
        }

        if (gameTime % SUMMON_STRIKE_INTERVAL_TICKS != 0) return;

        double bestSwordAttack = getBestSwordAttackDamage(player);
        int skillLevel = Math.max(1, data.getSkillLevel(SUMMON_SWORD));
        SkillTier tier = SkillTier.fromSkillLevel(skillLevel);
        float swordDamage = (float) (bestSwordAttack * 0.60 * tier.getPowerMultiplier());
        if (isBerserk(player.getUUID(), gameTime)) {
            // Berserk increases Summon Sword damage as well
            swordDamage *= 2.0f;
        }

        // 2 swords attack whatever the player is currently targeting
        LivingEntity primaryTarget = findPrimaryTarget(player, level);
        if (primaryTarget != null) {
            for (int i = 0; i < 2; i++) {
                strikeWithSpiritSword(player, level, primaryTarget, swordDamage);
            }
        }

        // Other 2 swords attack nearby hostile mobs
        AABB box = player.getBoundingBox().inflate(8.0);
        List<LivingEntity> nearbyEnemies = level.getEntitiesOfClass(
                LivingEntity.class,
                box,
                e -> e instanceof Enemy && e.isAlive() && e != player
        );
        if (!nearbyEnemies.isEmpty()) {
            for (int i = 0; i < 2; i++) {
                LivingEntity enemy = nearbyEnemies.get(i % nearbyEnemies.size());
                strikeWithSpiritSword(player, level, enemy, swordDamage);
            }
        }
    }

    private static void strikeWithSpiritSword(ServerPlayer owner, ServerLevel level, LivingEntity target, float damage) {
        if (!target.isAlive()) return;
        target.hurt(owner.damageSources().playerAttack(owner), damage);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                1, 0.2, 0.2, 0.2, 0.0);
    }

    private static @Nullable LivingEntity findPrimaryTarget(ServerPlayer player, ServerLevel level) {
        Integer entityId = LAST_TARGET_ENTITY_ID.get(player.getUUID());
        if (entityId != null && level.getEntity(entityId) instanceof LivingEntity tracked
                && tracked.isAlive() && tracked.distanceToSqr(player) <= 144.0) {
            return tracked;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();
        AABB search = player.getBoundingBox().expandTowards(look.scale(12.0)).inflate(1.5);
        LivingEntity best = null;
        double bestDot = 0.90;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, search,
                ent -> ent != player && ent.isAlive() && ent instanceof Enemy)) {
            Vec3 to = e.getEyePosition().subtract(eye).normalize();
            double dot = look.dot(to);
            if (dot > bestDot) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    private static double getBestSwordAttackDamage(ServerPlayer player) {
        double best = 4.0; // fallback if no sword is carried
        for (ItemStack stack : player.getInventory().items) {
            best = Math.max(best, getSwordAttackDamage(stack));
        }
        best = Math.max(best, getSwordAttackDamage(player.getOffhandItem()));
        return best;
    }

    private static double getSwordAttackDamage(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(ItemTags.SWORDS)) return 0.0;
        final double[] total = {1.0}; // base player fist damage
        stack.forEachModifier(EquipmentSlotGroup.MAINHAND, (attr, mod) -> {
            if (attr.is(Attributes.ATTACK_DAMAGE) && mod.operation() == AttributeModifier.Operation.ADD_VALUE) {
                total[0] += mod.amount();
            }
        });
        return total[0];
    }

    private static boolean castSurge(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        SURGE_UNTIL_TICK.put(player.getUUID(), gameTime + TWO_MINUTES_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 1.15f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(), player.getY() + 1.0, player.getZ(),
                4, 0.4, 0.25, 0.4, 0.0);
        player.displayClientMessage(
                Component.literal("Surge active for 2m! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"),
                true
        );
        return true;
    }

    private static boolean castParry(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        PARRY_UNTIL_TICK.put(player.getUUID(), gameTime + PARRY_WINDOW_TICKS);
        player.swing(InteractionHand.MAIN_HAND, true);
        level.playSound(null, player.blockPosition(), SoundEvents.TRIDENT_THROW.value(), SoundSource.PLAYERS, 0.6f, 1.7f);
        return true;
    }

    private static boolean castSummonSword(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        SUMMON_SWORD_UNTIL_TICK.put(player.getUUID(), gameTime + TWO_MINUTES_TICKS);
        level.playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0f, 1.25f);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, player.getX(), player.getY() + 1.2, player.getZ(),
                16, 0.6, 0.4, 0.6, 0.05);
        player.displayClientMessage(
                Component.literal("4 Spirit Swords summoned for 2m! (" + tier.getDisplayName() + " Lv." + skillLevel + ")"),
                true
        );
        return true;
    }

    private static boolean castBerserk(ServerPlayer player, PlayerRpgData data, int skillLevel, SkillTier tier) {
        if (!(player.level() instanceof ServerLevel level)) return false;
        long gameTime = level.getGameTime();

        BERSERK_UNTIL_TICK.put(player.getUUID(), gameTime + TWO_MINUTES_TICKS);

        int absorptionAmp = Math.min(4, 2 + tier.tierIndex() / 2); // Absorption III+ (+12+ absorption HP)
        int resistanceAmp = tier.ordinal() >= SkillTier.EPIC.ordinal() ? 1 : 0; // Tougher damage reduction
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, TWO_MINUTES_TICKS, absorptionAmp, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, TWO_MINUTES_TICKS, resistanceAmp, false, true));

        level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 0.9f, 1.1f);
        level.sendParticles(ParticleTypes.FLAME, player.getX(), player.getY() + 1.0, player.getZ(),
                24, 0.5, 0.6, 0.5, 0.06);
        player.displayClientMessage(
                Component.literal("BERSERK! +200% Damage & Toughness for 2m!"),
                true
        );
        return true;
    }
}
