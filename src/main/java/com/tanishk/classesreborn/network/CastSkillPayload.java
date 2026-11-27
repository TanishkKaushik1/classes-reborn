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

public record CastSkillPayload(String skillId) implements CustomPacketPayload {
    public static final Type<CastSkillPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "cast_skill"));

    public static final StreamCodec<FriendlyByteBuf, CastSkillPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.skillId),
                    buf -> new CastSkillPayload(buf.readUtf(64))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CastSkillPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);
            if (!data.hasSelectedClass()) return;

            Skill skill = SkillRegistry.get(payload.skillId);
            if (skill == null || skill.getOwnerClass() != data.getSelectedClass()) return;

            if (!data.isSkillUnlocked(skill.getId()) || data.getLevel() < skill.getRequiredLevel()) {
                serverPlayer.sendSystemMessage(Component.literal("Skill not unlocked yet!"));
                return;
            }

            long gameTime = serverPlayer.level().getGameTime();
            boolean alreadyActive = data.isSkillActive(skill.getId(), gameTime);
            if (alreadyActive) {
                if (SkillRegistry.isOnRecastDelay(skill.getId(), serverPlayer.getUUID(), gameTime)) {
                    int remDelay = SkillRegistry.getRemainingRecastDelayTicks(skill.getId(), serverPlayer.getUUID(), gameTime);
                    serverPlayer.displayClientMessage(
                            Component.literal("Ready again in " + String.format("%.1fs", remDelay / 20.0f)),
                            true
                    );
                    return;
                }
            } else if (data.isOnCooldown(skill.getId(), gameTime)) {
                int remTicks = data.getRemainingCooldownTicks(skill.getId(), gameTime);
                serverPlayer.sendSystemMessage(
                        Component.literal("Cooldown: " + String.format("%.1fs", remTicks / 20.0f))
                );
                return;
            }

            int skillLevel = Math.max(1, data.getSkillLevel(skill.getId()));
            if (skill.cast(serverPlayer, data, skillLevel)) {
                // Healer Heal starts cooldown only on successful teammate heal;
                // Archer Leap starts its 1m cooldown when its 2m active window ends.
                if (!SkillRegistry.HEALER_HEAL.equals(skill.getId())
                        && !SkillRegistry.ARCHER_LEAP.equals(skill.getId())) {
                    int cdTicks = skill.getCooldownTicksForLevel(skillLevel);
                    data.startCooldown(skill.getId(), gameTime, cdTicks);
                }
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
            }
        });
    }
}