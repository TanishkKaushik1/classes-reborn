package com.tanishk.classesreborn.attachment;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {
    public static final String MOD_ID = "classesreborn";

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MOD_ID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<PlayerRpgData>> PLAYER_RPG_DATA =
            ATTACHMENT_TYPES.register("player_rpg_data", () ->
                    AttachmentType.builder(PlayerRpgData::new)
                            .serialize(PlayerRpgData.CODEC)
                            .copyOnDeath()
                            .build()
            );

    public static void register(IEventBus modEventBus) {
        ATTACHMENT_TYPES.register(modEventBus);
    }
}