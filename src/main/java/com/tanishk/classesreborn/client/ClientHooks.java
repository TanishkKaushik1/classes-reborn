package com.tanishk.classesreborn.client;

import com.tanishk.classesreborn.client.gui.ClassSelectionScreen;
import com.tanishk.classesreborn.client.gui.SkillTreeScreen;
import com.tanishk.classesreborn.client.gui.StatScreen;
import net.minecraft.client.Minecraft;

public final class ClientHooks {
    private ClientHooks() {
    }

    public static void openClassSelectionScreen() {
        Minecraft.getInstance().setScreen(new ClassSelectionScreen());
    }

    public static void openSkillTreeScreen() {
        Minecraft.getInstance().setScreen(new SkillTreeScreen());
    }

    public static void openStatScreen() {
        Minecraft.getInstance().setScreen(new StatScreen());
    }
}