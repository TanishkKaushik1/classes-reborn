package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.skill.Skill;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import com.tanishk.classesreborn.rpg.skill.SkillTier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record UnlockSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<UnlockSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "unlock_skill"));

    public static final StreamCodec<FriendlyByteBuf, UnlockSkillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.skillId),
                    buf -> new UnlockSkillPayload(buf.readUtf(64))
            );

    private static final int MAX_SKILL_LEVEL = 30;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(UnlockSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);
            if (!data.hasSelectedClass()) return;

            Skill skill = SkillRegistry.get(payload.skillId);
            if (skill == null || skill.getOwnerClass() != data.getSelectedClass()) return;

            if (!data.isSkillUnlocked(skill.getId())) {
                if (data.getLevel() < skill.getRequiredLevel()) return;
                if (data.unlockSkill(skill.getId())) {
                    serverPlayer.sendSystemMessage(
                            Component.literal("Unlocked skill: ").append(skill.getDisplayName())
                    );
                    PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
                }
            } else {
                if (data.tryUpgradeSkill(skill.getId(), MAX_SKILL_LEVEL)) {
                    int newLevel = data.getSkillLevel(skill.getId());
                    SkillTier newTier = SkillTier.fromSkillLevel(newLevel);
                    serverPlayer.sendSystemMessage(
                            Component.literal("Upgraded ")
                                    .append(skill.getDisplayName())
                                    .append(Component.literal(" to Lv. " + newLevel + " (" + newTier.getDisplayName() + ")"))
                    );
                    PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
                }
            }
        });
    }
}