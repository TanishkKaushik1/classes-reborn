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

public record AllocateStatPayload(String statId) implements CustomPacketPayload {
    public static final Type<AllocateStatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "allocate_stat"));

    public static final StreamCodec<FriendlyByteBuf, AllocateStatPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> buf.writeUtf(p.statId),
                    buf -> new AllocateStatPayload(buf.readUtf(32))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AllocateStatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            PlayerRpgData.StatType stat = PlayerRpgData.StatType.byId(payload.statId);
            if (stat == null) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);
            if (!data.hasSelectedClass()) return;

            if (data.allocateStatPoint(stat)) {
                ClassStatManager.applyStats(serverPlayer, data);
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
            }
        });
    }
}