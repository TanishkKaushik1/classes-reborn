package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record MoveInputPayload(float forward, float strafe) implements CustomPacketPayload {
    public static final Type<MoveInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "move_input"));

    public static final StreamCodec<FriendlyByteBuf, MoveInputPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, p) -> {
                        buf.writeFloat(p.forward);
                        buf.writeFloat(p.strafe);
                    },
                    buf -> new MoveInputPayload(buf.readFloat(), buf.readFloat())
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MoveInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                float f = Math.max(-1.0f, Math.min(1.0f, payload.forward));
                float s = Math.max(-1.0f, Math.min(1.0f, payload.strafe));
                SkillRegistry.updateMoveInput(serverPlayer.getUUID(), f, s);
            }
        });
    }
}