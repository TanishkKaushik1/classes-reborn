package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ResetStatsPayload() implements CustomPacketPayload {
    public static final Type<ResetStatsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "reset_stats"));

    public static final StreamCodec<FriendlyByteBuf, ResetStatsPayload> STREAM_CODEC =
            StreamCodec.unit(new ResetStatsPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ResetStatsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);
            if (!data.hasSelectedClass()) return;

            if (data.resetAllocatedStats() > 0) {
                ClassStatManager.applyStats(serverPlayer, data);
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
            }
        });
    }
}