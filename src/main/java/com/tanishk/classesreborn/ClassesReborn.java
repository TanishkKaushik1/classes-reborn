package com.tanishk.classesreborn;

import com.mojang.logging.LogUtils;
import com.tanishk.classesreborn.attachment.ModAttachments;
import com.tanishk.classesreborn.command.RpgCommands;
import com.tanishk.classesreborn.event.RpgAggroEvents;
import com.tanishk.classesreborn.event.RpgCombatEvents;
import com.tanishk.classesreborn.event.RpgPlayerEvents;
import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.network.ModNetworking;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import org.slf4j.Logger;

@Mod(ClassesReborn.MODID)
public class ClassesReborn {
    public static final String MODID = "classesreborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ClassesReborn(IEventBus modEventBus, ModContainer modContainer) {
        ModAttachments.register(modEventBus);
        ModItems.register(modEventBus);

        modEventBus.addListener(ModNetworking::registerPayloads);
        modEventBus.addListener(this::addCreative);

        NeoForge.EVENT_BUS.register(new RpgPlayerEvents());
        NeoForge.EVENT_BUS.register(new RpgCombatEvents());
        NeoForge.EVENT_BUS.register(new RpgAggroEvents());
        NeoForge.EVENT_BUS.addListener(RpgCommands::onRegisterCommands);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(ModItems.CLASS_BOOK);
        }
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(ModItems.HEALER_STAFF);
        }
    }
}