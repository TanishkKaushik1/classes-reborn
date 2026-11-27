package com.tanishk.classesreborn;

import com.tanishk.classesreborn.client.ClientSetup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ClassesReborn.MODID, dist = Dist.CLIENT)
public class ClassesRebornClient {
    public ClassesRebornClient(IEventBus modEventBus, ModContainer container) {
        ClientSetup clientSetup = new ClientSetup();
        modEventBus.addListener(clientSetup::onRegisterKeys);
        modEventBus.addListener(clientSetup::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(clientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(clientSetup::onClientTick);
        NeoForge.EVENT_BUS.addListener(clientSetup::onRenderLivingPre);
    }
}