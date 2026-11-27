package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.*;

public record SyncRpgDataPayload(
        String classId,
        int level,
        int xp,
        int skillPoints,
        int statPoints,
        boolean receivedBook,
        boolean receivedKit,
        List<String> unlockedSkills,
        Map<String, Integer> skillLevels,
        List<String> activeSkillSlots,
        Map<String, Integer> allocatedStats
) implements CustomPacketPayload {

    public static final Type<SyncRpgDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "sync_rpg_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncRpgDataPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeUtf(p.classId);
                        buf.writeVarInt(p.level);
                        buf.writeVarInt(p.xp);
                        buf.writeVarInt(p.skillPoints);
                        buf.writeVarInt(p.statPoints);
                        buf.writeBoolean(p.receivedBook);
                        buf.writeBoolean(p.receivedKit);
                        buf.writeCollection(p.unlockedSkills, FriendlyByteBuf::writeUtf);
                        buf.writeMap(p.skillLevels, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
                        buf.writeCollection(normalizeSlots(p.activeSkillSlots), FriendlyByteBuf::writeUtf);
                        buf.writeMap(p.allocatedStats, FriendlyByteBuf::writeUtf, FriendlyByteBuf::writeVarInt);
                    },
                    buf -> new SyncRpgDataPayload(
                            buf.readUtf(32),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readVarInt(),
                            buf.readBoolean(),
                            buf.readBoolean(),
                            buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf),
                            buf.readMap(LinkedHashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt),
                            normalizeSlots(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf)),
                            buf.readMap(LinkedHashMap::new, FriendlyByteBuf::readUtf, FriendlyByteBuf::readVarInt)
                    )
            );

    private static List<String> normalizeSlots(List<String> rawSlots) {
        List<String> normalized = new ArrayList<>(PlayerRpgData.MAX_ACTIVE_SLOTS);
        for (int i = 0; i < PlayerRpgData.MAX_ACTIVE_SLOTS; i++) {
            String val = (rawSlots != null && i < rawSlots.size() && rawSlots.get(i) != null)
                    ? rawSlots.get(i)
                    : "";
            normalized.add(val);
        }
        return normalized;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static SyncRpgDataPayload from(PlayerRpgData data) {
        Map<String, Integer> levels = new LinkedHashMap<>(data.getAllSkillLevels());
        for (String unlockedId : data.getUnlockedSkills()) {
            levels.putIfAbsent(unlockedId, Math.max(1, data.getSkillLevel(unlockedId)));
        }

        Map<String, Integer> stats = new LinkedHashMap<>();
        for (PlayerRpgData.StatType type : PlayerRpgData.StatType.values()) {
            stats.put(type.getId(), data.getStatPointsAllocated(type));
        }

        return new SyncRpgDataPayload(
                data.getSelectedClass() == null ? "" : data.getSelectedClass().getId(),
                data.getLevel(),
                data.getXp(),
                data.getSkillPoints(),
                data.getStatPoints(),
                data.hasReceivedClassBook(),
                data.hasReceivedStarterKit(),
                new ArrayList<>(data.getUnlockedSkills()),
                levels,
                normalizeSlots(data.getActiveSkillSlots()),
                stats
        );
    }

    public static void handle(SyncRpgDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            var player = context.player();
            if (player == null) return;

            PlayerRpgData updated = new PlayerRpgData();
            updated.setSelectedClass(RpgClass.byId(payload.classId));
            updated.setLevel(payload.level);
            updated.setXp(payload.xp);
            updated.setReceivedClassBook(payload.receivedBook);
            updated.setReceivedStarterKit(payload.receivedKit);

            for (String skill : new HashSet<>(payload.unlockedSkills)) {
                if (skill != null && !skill.isBlank() && updated.getSkillLevel(skill) <= 0) {
                    updated.setSkillLevel(skill, 1);
                }
            }
            for (Map.Entry<String, Integer> entry : payload.skillLevels.entrySet()) {
                String skillId = entry.getKey();
                Integer lvl = entry.getValue();
                if (skillId != null && !skillId.isBlank() && lvl != null && lvl > 0) {
                    updated.setSkillLevel(skillId, lvl);
                }
            }
            updated.setSkillPoints(payload.skillPoints);

            List<String> slots = normalizeSlots(payload.activeSkillSlots);
            for (int i = 0; i < PlayerRpgData.MAX_ACTIVE_SLOTS; i++) {
                String skillId = slots.get(i);
                if (skillId != null && !skillId.isBlank() && updated.isSkillUnlocked(skillId)) {
                    updated.setSkillInSlot(i, skillId);
                }
            }

            for (Map.Entry<String, Integer> entry : payload.allocatedStats.entrySet()) {
                PlayerRpgData.StatType type = PlayerRpgData.StatType.byId(entry.getKey());
                int count = entry.getValue() == null ? 0 : Math.max(0, entry.getValue());
                if (type != null && count > 0) {
                    updated.setStatPoints(count);
                    for (int i = 0; i < count; i++) {
                        updated.allocateStatPoint(type);
                    }
                }
            }
            updated.setStatPoints(payload.statPoints);

            PlayerRpgData.get(player).copyFrom(updated);
        });
    }
}