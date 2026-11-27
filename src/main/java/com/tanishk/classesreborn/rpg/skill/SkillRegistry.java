package com.tanishk.classesreborn.rpg.skill;

import com.tanishk.classesreborn.network.CastSkillPayload;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.classes.archer.ArcherSkills;
import com.tanishk.classesreborn.rpg.classes.assassin.AssassinSkills;
import com.tanishk.classesreborn.rpg.classes.healer.HealerSkills;
import com.tanishk.classesreborn.rpg.classes.swordsman.SwordsmanSkills;
import com.tanishk.classesreborn.rpg.classes.tank.TankSkills;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class SkillRegistry {
    public static final String TANK_ROAR = TankSkills.ROAR;
    public static final String SWORDSMAN_QUICKSLASH = SwordsmanSkills.SURGE;
    public static final String ARCHER_DASH = ArcherSkills.DASH;
    public static final String ARCHER_NIGHTVISION = ArcherSkills.NIGHTVISION;
    public static final String ARCHER_LEAP = ArcherSkills.LEAP;
    public static final String ASSASSIN_INVISIBILITY = AssassinSkills.INVISIBILITY;
    public static final String HEALER_HEAL = HealerSkills.HEAL;

    private static final Map<String, Skill> SKILLS = new LinkedHashMap<>();

    // Shared runtime trackers (server authoritative; synced via SyncRpgDataPayload when needed)
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
        TankSkills.register(SkillRegistry::register);
        SwordsmanSkills.register(SkillRegistry::register);
        ArcherSkills.register(SkillRegistry::register);
        AssassinSkills.register(SkillRegistry::register);
        HealerSkills.register(SkillRegistry::register);
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

    public static MoveInput getLastMoveInput(UUID playerId, float defaultForward, float defaultStrafe) {
        return LAST_MOVE_INPUT.getOrDefault(playerId, new MoveInput(defaultForward, defaultStrafe));
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

    public static void clearRecastDelay(String skillId, UUID playerId) {
        Map<UUID, Long> perPlayer = RECAST_READY_AT_TICK.get(skillId);
        if (perPlayer != null) {
            perPlayer.remove(playerId);
        }
    }

    public static int remainingTicks(Map<UUID, Long> map, UUID playerId, long gameTime) {
        Long until = map.get(playerId);
        if (until == null || gameTime >= until) return 0;
        return (int) Math.min(Integer.MAX_VALUE, until - gameTime);
    }

    public static void setUntil(Map<UUID, Long> map, UUID playerId, long gameTime, int ticksLeft) {
        if (ticksLeft > 0) {
            map.put(playerId, gameTime + ticksLeft);
        } else {
            map.remove(playerId);
        }
    }

    public static boolean isTaunting(UUID playerId, long gameTime) {
        return TankSkills.isTaunting(playerId, gameTime);
    }

    public static boolean isStealthed(UUID playerId, long gameTime) {
        return AssassinSkills.isStealthed(playerId, gameTime);
    }

    public static boolean hasQuickslashSurge(UUID playerId, long gameTime) {
        return SwordsmanSkills.hasSurge(playerId, gameTime);
    }

    public static boolean hasArcherDashBoost(UUID playerId, long gameTime) {
        return ArcherSkills.hasDashBoost(playerId, gameTime);
    }

    public static ActiveBuffSnapshot getBuffSnapshot(UUID playerId, long gameTime) {
        return new ActiveBuffSnapshot(
                TankSkills.getTauntTicksLeft(playerId, gameTime),
                AssassinSkills.getStealthTicksLeft(playerId, gameTime),
                SwordsmanSkills.getSurgeTicksLeft(playerId, gameTime),
                ArcherSkills.getDashBoostTicksLeft(playerId, gameTime),
                HealerSkills.getPendingHealTicksLeft(playerId, gameTime)
        );
    }

    public static void applySyncedBuffSnapshot(UUID playerId, long gameTime, ActiveBuffSnapshot snapshot) {
        TankSkills.setTauntTicksLeft(playerId, gameTime, snapshot.tauntTicksLeft());
        AssassinSkills.setStealthTicksLeft(playerId, gameTime, snapshot.stealthTicksLeft());
        SwordsmanSkills.setSurgeTicksLeft(playerId, gameTime, snapshot.quickslashSurgeTicksLeft());
        ArcherSkills.setDashBoostTicksLeft(playerId, gameTime, snapshot.archerBoostTicksLeft());
        HealerSkills.setPendingHealTicksLeft(playerId, gameTime, snapshot.pendingHealTicksLeft());
    }

    public static float computeHealerHealOutput(PlayerRpgData data) {
        return HealerSkills.computeHealOutput(data);
    }

    public static boolean tryStartHealerHealCast(ServerPlayer player, PlayerRpgData data) {
        return HealerSkills.tryStartHealCast(player, data);
    }

    public static void tickServerPlayer(ServerPlayer player, PlayerRpgData data) {
        long gameTime = player.level().getGameTime();
        data.getActiveRangedDamageBonus(gameTime);

        TankSkills.tick(player, data, gameTime);
        SwordsmanSkills.tick(player, data, gameTime);
        ArcherSkills.tick(player, data, gameTime);
        AssassinSkills.tick(player, data, gameTime);
        HealerSkills.tick(player, data, gameTime);
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
