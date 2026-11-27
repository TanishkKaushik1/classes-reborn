package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.skill.Skill;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record EquipSkillSlotPayload(int slotIndex, String skillId) implements CustomPacketPayload {
    public static final Type<EquipSkillSlotPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "equip_skill_slot"));

    public static final StreamCodec<FriendlyByteBuf, EquipSkillSlotPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeVarInt(p.slotIndex);
                        buf.writeUtf(p.skillId == null ? "" : p.skillId);
                    },
                    buf -> new EquipSkillSlotPayload(buf.readVarInt(), buf.readUtf(64))
            );

    private static final String[] SLOT_KEYS = {"G", "R", "C"};

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(EquipSkillSlotPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;
            if (payload.slotIndex < 0 || payload.slotIndex >= PlayerRpgData.MAX_ACTIVE_SLOTS) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);
            if (!data.hasSelectedClass()) return;

            String cleanId = payload.skillId == null ? "" : payload.skillId.trim();
            String keyName = SLOT_KEYS[payload.slotIndex];

            if (cleanId.isEmpty()) {
                data.setSkillInSlot(payload.slotIndex, "");
                serverPlayer.sendSystemMessage(Component.literal("Cleared slot [" + keyName + "]"));
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
                return;
            }

            Skill skill = SkillRegistry.get(cleanId);
            if (skill == null || skill.getOwnerClass() != data.getSelectedClass()) return;
            if (!data.isSkillUnlocked(cleanId)) {
                serverPlayer.sendSystemMessage(Component.literal("Unlock the skill before assigning it to [" + keyName + "]!"));
                return;
            }

            if (data.setSkillInSlot(payload.slotIndex, cleanId)) {
                serverPlayer.sendSystemMessage(
                        Component.literal("Assigned ")
                                .append(skill.getDisplayName())
                                .append(Component.literal(" to [" + keyName + "]"))
                );
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
            }
        });
    }
}