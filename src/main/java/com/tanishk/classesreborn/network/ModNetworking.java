package com.tanishk.classesreborn.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetworking {
    private ModNetworking() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.playToServer(
                SelectClassPayload.TYPE,
                SelectClassPayload.STREAM_CODEC,
                SelectClassPayload::handle
        );

        registrar.playToServer(
                CastSkillPayload.TYPE,
                CastSkillPayload.STREAM_CODEC,
                CastSkillPayload::handle
        );

        registrar.playToServer(
                EquipSkillSlotPayload.TYPE,
                EquipSkillSlotPayload.STREAM_CODEC,
                EquipSkillSlotPayload::handle
        );

        registrar.playToServer(
                MoveInputPayload.TYPE,
                MoveInputPayload.STREAM_CODEC,
                MoveInputPayload::handle
        );

        registrar.playToServer(
                ResetStatsPayload.TYPE,
                ResetStatsPayload.STREAM_CODEC,
                ResetStatsPayload::handle
        );

        registrar.playToServer(
                UnlockSkillPayload.TYPE,
                UnlockSkillPayload.STREAM_CODEC,
                UnlockSkillPayload::handle
        );

        registrar.playToServer(
                AllocateStatPayload.TYPE,
                AllocateStatPayload.STREAM_CODEC,
                AllocateStatPayload::handle
        );

        registrar.playToClient(
                SyncRpgDataPayload.TYPE,
                SyncRpgDataPayload.STREAM_CODEC,
                SyncRpgDataPayload::handle
        );
    }
}