package com.tanishk.classesreborn.rpg;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.tanishk.classesreborn.attachment.ModAttachments;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerRpgData {
    public enum StatType {
        VITALITY("vitality"),
        GUARD("guard"),
        MIGHT("might"),
        PRECISION("precision"),
        AGILITY("agility"),
        PRESENCE("presence"),
        MEND("mend");

        private final String id;

        StatType(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        public static @Nullable StatType byId(String id) {
            if (id == null) return null;
            for (StatType type : values()) {
                if (type.id.equalsIgnoreCase(id)) return type;
            }
            return null;
        }
    }

    public static final int MAX_ACTIVE_SLOTS = 3;

    public static final Codec<PlayerRpgData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("class_id", "").forGetter(d -> d.selectedClass == null ? "" : d.selectedClass.getId()),
            Codec.INT.optionalFieldOf("level", 1).forGetter(d -> d.level),
            Codec.INT.optionalFieldOf("xp", 0).forGetter(d -> d.xp),
            Codec.INT.optionalFieldOf("skill_points", 0).forGetter(d -> d.skillPoints),
            Codec.INT.optionalFieldOf("stat_points", 0).forGetter(d -> d.statPoints),
            Codec.BOOL.optionalFieldOf("received_book", false).forGetter(d -> d.receivedClassBook),
            Codec.BOOL.optionalFieldOf("received_starter_kit", false).forGetter(d -> d.receivedStarterKit),
            Codec.STRING.listOf().optionalFieldOf("unlocked_skills", List.of()).forGetter(d -> new ArrayList<>(d.getUnlockedSkills())),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("skill_levels", Map.of()).forGetter(d -> new LinkedHashMap<>(d.skillLevels)),
            Codec.STRING.listOf().optionalFieldOf("active_skill_slots", List.of()).forGetter(d -> new ArrayList<>(d.getActiveSkillSlots())),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("allocated_stats", Map.of()).forGetter(d -> {
                Map<String, Integer> map = new LinkedHashMap<>();
                for (StatType type : StatType.values()) {
                    map.put(type.id, d.getStatPointsAllocated(type));
                }
                return map;
            })
    ).apply(instance, (classId, level, xp, skillPoints, statPoints, receivedBook, receivedKit, unlocked, levelsMap, activeSlots, statsMap) -> {
        PlayerRpgData data = new PlayerRpgData();
        data.selectedClass = RpgClass.byId(classId);
        data.level = Math.max(1, level);
        data.xp = Math.max(0, xp);
        data.skillPoints = Math.max(0, skillPoints);
        data.statPoints = Math.max(0, statPoints);
        data.receivedClassBook = receivedBook;
        data.receivedStarterKit = receivedKit;

        for (String skillId : unlocked) {
            if (skillId != null && !skillId.isBlank()) {
                data.skillLevels.putIfAbsent(skillId, 1);
            }
        }
        levelsMap.forEach((k, v) -> {
            if (k != null && !k.isBlank() && v != null && v > 0) {
                data.skillLevels.put(k, v);
            }
        });

        for (int i = 0; i < MAX_ACTIVE_SLOTS && i < activeSlots.size(); i++) {
            String slotSkill = activeSlots.get(i);
            if (slotSkill != null && !slotSkill.isBlank() && data.isSkillUnlocked(slotSkill)) {
                data.setSkillInSlot(i, slotSkill);
            }
        }

        statsMap.forEach((k, v) -> {
            StatType type = StatType.byId(k);
            if (type != null && v != null && v > 0) {
                data.allocatedStats.put(type, v);
            }
        });
        return data;
    }));
    // Inside PlayerRpgData:
    private double bonusRangedDamageBonus = 0.0;
    private long bonusRangedDamageUntilGameTime = 0L;

    public void grantTemporaryRangedDamageBonus(double bonus, int durationTicks, long currentGameTime) {
        this.bonusRangedDamageBonus = Math.max(this.bonusRangedDamageBonus, bonus);
        this.bonusRangedDamageUntilGameTime = Math.max(this.bonusRangedDamageUntilGameTime, currentGameTime + durationTicks);
    }

    public double getActiveRangedDamageBonus(long currentGameTime) {
        if (currentGameTime >= this.bonusRangedDamageUntilGameTime) {
            this.bonusRangedDamageBonus = 0.0;
            return 0.0;
        }
        return this.bonusRangedDamageBonus;
    }
    @Nullable
    private RpgClass selectedClass;
    private int level = 1;
    private int xp = 0;
    private int skillPoints = 0;
    private int statPoints = 0;
    private boolean receivedClassBook = false;
    private boolean receivedStarterKit = false;
    private final Map<String, Integer> skillLevels = new HashMap<>();
    private final EnumMap<StatType, Integer> allocatedStats = new EnumMap<>(StatType.class);

    // Runtime-only cooldowns & temporary buffs (world gameTime)
    private final Map<String, Long> skillCooldownEndTick = new HashMap<>();
    private int bonusPrecisionPoints = 0;
    private long bonusPrecisionUntilGameTime = 0L;

    public @Nullable RpgClass getSelectedClass() {
        return selectedClass;
    }

    public boolean hasSelectedClass() {
        return selectedClass != null;
    }

    public void setSelectedClass(@Nullable RpgClass selectedClass) {
        this.selectedClass = selectedClass;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = Math.max(1, level);
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = Math.max(0, xp);
    }

    public int getSkillPoints() {
        return skillPoints;
    }

    public void setSkillPoints(int skillPoints) {
        this.skillPoints = Math.max(0, skillPoints);
    }

    public int getStatPoints() {
        return statPoints;
    }

    public void setStatPoints(int statPoints) {
        this.statPoints = Math.max(0, statPoints);
    }

    public int getStatPointsAllocated(StatType type) {
        return allocatedStats.getOrDefault(type, 0);
    }

    public boolean allocateStatPoint(StatType type) {
        if (statPoints <= 0 || type == null) return false;
        statPoints--;
        allocatedStats.put(type, getStatPointsAllocated(type) + 1);
        return true;
    }
    // Inside PlayerRpgData:
    private final Map<String, Long> activeSkillUntilTick = new HashMap<>();

    public void startActiveWindow(String skillId, long gameTime, int durationTicks) {
        if (skillId == null || skillId.isBlank() || durationTicks <= 0) return;
        long currentUntil = activeSkillUntilTick.getOrDefault(skillId, 0L);
        activeSkillUntilTick.put(skillId, Math.max(currentUntil, gameTime + durationTicks));
    }

    public long getActiveWindowEndTick(String skillId) {
        return activeSkillUntilTick.getOrDefault(skillId, 0L);
    }

    public boolean isSkillActive(String skillId, long gameTime) {
        return getActiveWindowEndTick(skillId) > gameTime;
    }

    public int getRemainingActiveTicks(String skillId, long gameTime) {
        long diff = getActiveWindowEndTick(skillId) - gameTime;
        return diff > 0 ? (int) Math.min(Integer.MAX_VALUE, diff) : 0;
    }

    public void clearActiveWindow(String skillId) {
        activeSkillUntilTick.remove(skillId);
    }
    public int resetAllocatedStats() {
        int refunded = 0;
        for (StatType type : StatType.values()) {
            refunded += allocatedStats.getOrDefault(type, 0);
            allocatedStats.put(type, 0);
        }
        this.statPoints += refunded;
        return refunded;
    }

    public boolean hasReceivedClassBook() {
        return receivedClassBook;
    }

    public void setReceivedClassBook(boolean receivedClassBook) {
        this.receivedClassBook = receivedClassBook;
    }

    public boolean hasReceivedStarterKit() {
        return receivedStarterKit;
    }

    public void setReceivedStarterKit(boolean receivedStarterKit) {
        this.receivedStarterKit = receivedStarterKit;
    }

    public Map<String, Integer> getAllSkillLevels() {
        return Collections.unmodifiableMap(skillLevels);
    }

    public int getSkillLevel(String skillId) {
        return skillLevels.getOrDefault(skillId, 0);
    }

    public boolean isSkillUnlocked(String skillId) {
        return getSkillLevel(skillId) > 0;
    }

    public Set<String> getUnlockedSkills() {
        Set<String> unlocked = new LinkedHashSet<>();
        for (Map.Entry<String, Integer> entry : skillLevels.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                unlocked.add(entry.getKey());
            }
        }
        return Collections.unmodifiableSet(unlocked);
    }

    public void setSkillLevel(String skillId, int level) {
        if (level <= 0) {
            skillLevels.remove(skillId);
        } else {
            skillLevels.put(skillId, level);
        }
    }

    public int getUnlockedSkillCount() {
        return getUnlockedSkills().size();
    }

    /** Requires 1 skill point and at least 1 player level (plus +1 level per already-unlocked skill). */
    public boolean canUnlockSkill(String skillId, int requiredBaseLevel) {
        if (isSkillUnlocked(skillId)) return false;
        int neededPlayerLevel = Math.max(1, requiredBaseLevel) + getUnlockedSkillCount();
        return this.skillPoints >= 1 && this.level >= neededPlayerLevel;
    }
    // Inside PlayerRpgData:
    private final List<String> activeSkillSlots = new ArrayList<>(Collections.nCopies(MAX_ACTIVE_SLOTS, ""));

    public List<String> getActiveSkillSlots() {
        while (activeSkillSlots.size() < MAX_ACTIVE_SLOTS) {
            activeSkillSlots.add("");
        }
        return Collections.unmodifiableList(activeSkillSlots.subList(0, MAX_ACTIVE_SLOTS));
    }

    public String getSkillInSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= MAX_ACTIVE_SLOTS) return "";
        while (activeSkillSlots.size() < MAX_ACTIVE_SLOTS) {
            activeSkillSlots.add("");
        }
        String id = activeSkillSlots.get(slotIndex);
        return id == null ? "" : id;
    }

    public boolean setSkillInSlot(int slotIndex, String skillId) {
        if (slotIndex < 0 || slotIndex >= MAX_ACTIVE_SLOTS) return false;
        while (activeSkillSlots.size() < MAX_ACTIVE_SLOTS) {
            activeSkillSlots.add("");
        }
        String cleanId = skillId == null ? "" : skillId;
        if (!cleanId.isEmpty() && !isSkillUnlocked(cleanId)) return false;

        // Prevent the same skill from occupying two slots at once
        for (int i = 0; i < MAX_ACTIVE_SLOTS; i++) {
            if (i != slotIndex && cleanId.equals(activeSkillSlots.get(i))) {
                activeSkillSlots.set(i, "");
            }
        }
        activeSkillSlots.set(slotIndex, cleanId);
        return true;
    }

    /** Auto-equips a newly unlocked skill into the first empty slot out of the 3 active slots. */
    public int autoEquipIfSpace(String skillId) {
        if (!isSkillUnlocked(skillId)) return -1;
        for (int i = 0; i < MAX_ACTIVE_SLOTS; i++) {
            if (skillId.equals(getSkillInSlot(i))) return i;
        }
        for (int i = 0; i < MAX_ACTIVE_SLOTS; i++) {
            if (getSkillInSlot(i).isEmpty()) {
                setSkillInSlot(i, skillId);
                return i;
            }
        }
        return -1;
    }
    public boolean tryUnlockSkill(String skillId, int requiredBaseLevel) {
        if (!canUnlockSkill(skillId, requiredBaseLevel)) return false;
        this.skillPoints -= 1;
        this.skillLevels.put(skillId, 1);
        return true;
    }

    /** Backwards-compatible helper when no extra base level gate is passed. */
    public boolean unlockSkill(String skillId) {
        return tryUnlockSkill(skillId, 1);
    }

    public boolean tryUpgradeSkill(String skillId, int maxSkillLevel) {
        int current = getSkillLevel(skillId);
        if (current <= 0 || current >= maxSkillLevel || this.skillPoints < 1) return false;
        this.skillPoints -= 1;
        this.skillLevels.put(skillId, current + 1);
        return true;
    }

    public void grantTemporaryPrecision(int extraPoints, int durationTicks, long currentGameTime) {
        this.bonusPrecisionPoints = Math.max(this.bonusPrecisionPoints, extraPoints);
        this.bonusPrecisionUntilGameTime = Math.max(this.bonusPrecisionUntilGameTime, currentGameTime + durationTicks);
    }

    public int getActiveBonusPrecision(long currentGameTime) {
        if (currentGameTime >= this.bonusPrecisionUntilGameTime) {
            this.bonusPrecisionPoints = 0;
            return 0;
        }
        return this.bonusPrecisionPoints;
    }

    public int getXpForNextLevel() {
        return 100 + (level - 1) * 50;
    }

    public int addXp(int amount) {
        if (amount <= 0) return 0;
        this.xp += amount;
        int levelsGained = 0;
        while (this.xp >= getXpForNextLevel()) {
            this.xp -= getXpForNextLevel();
            this.level++;
            this.skillPoints++;
            this.statPoints += 2; // 2 stat points per level for controlled growth
            levelsGained++;
        }
        return levelsGained;
    }

    public boolean isOnCooldown(String skillId, long currentGameTime) {
        Long readyAt = skillCooldownEndTick.get(skillId);
        return readyAt != null && currentGameTime < readyAt;
    }

    public int getRemainingCooldownTicks(String skillId, long currentGameTime) {
        Long readyAt = skillCooldownEndTick.get(skillId);
        if (readyAt == null || currentGameTime >= readyAt) return 0;
        return (int) (readyAt - currentGameTime);
    }

    public void startCooldown(String skillId, long currentGameTime, int durationTicks) {
        skillCooldownEndTick.put(skillId, currentGameTime + Math.max(1, durationTicks));
    }

    public void copyFrom(PlayerRpgData other) {
        this.selectedClass = other.selectedClass;
        this.level = other.level;
        this.xp = other.xp;
        this.skillPoints = other.skillPoints;
        this.statPoints = other.statPoints;
        this.receivedClassBook = other.receivedClassBook;
        this.receivedStarterKit = other.receivedStarterKit;
        this.skillLevels.clear();
        this.skillLevels.putAll(other.skillLevels);
        this.activeSkillSlots.clear();
        this.activeSkillSlots.addAll(other.getActiveSkillSlots());
        this.allocatedStats.clear();
        this.allocatedStats.putAll(other.allocatedStats);
        this.skillCooldownEndTick.clear();
        this.skillCooldownEndTick.putAll(other.skillCooldownEndTick);
        this.activeSkillUntilTick.clear();
        this.activeSkillUntilTick.putAll(other.activeSkillUntilTick);
        this.bonusPrecisionPoints = other.bonusPrecisionPoints;
        this.bonusPrecisionUntilGameTime = other.bonusPrecisionUntilGameTime;
        this.bonusRangedDamageBonus = other.bonusRangedDamageBonus;
        this.bonusRangedDamageUntilGameTime = other.bonusRangedDamageUntilGameTime;
    }

    public static PlayerRpgData get(Player player) {
        return player.getData(ModAttachments.PLAYER_RPG_DATA.get());
    }
}