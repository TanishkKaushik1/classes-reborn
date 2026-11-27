package com.tanishk.classesreborn.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.tanishk.classesreborn.client.hud.RpgHudOverlay;
import com.tanishk.classesreborn.network.CastSkillPayload;
import com.tanishk.classesreborn.network.MoveInputPayload;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.Skill;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class ClientSetup {
    public static final KeyMapping CAST_SKILL_KEY = new KeyMapping(
            "key.classesreborn.cast_skill",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            "key.categories.classesreborn"
    );

    public static final KeyMapping CAST_SLOT_2_KEY = new KeyMapping(
            "key.classesreborn.cast_slot_2",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            "key.categories.classesreborn"
    );

    public static final KeyMapping CAST_SLOT_3_KEY = new KeyMapping(
            "key.classesreborn.cast_slot_3",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            "key.categories.classesreborn"
    );

    public static final KeyMapping OPEN_SKILLS_KEY = new KeyMapping(
            "key.classesreborn.open_skills",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_K,
            "key.categories.classesreborn"
    );

    public static final KeyMapping OPEN_STATS_KEY = new KeyMapping(
            "key.classesreborn.open_stats",
            KeyConflictContext.IN_GAME,
            KeyModifier.NONE,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.classesreborn"
    );

    @SubscribeEvent
    public void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(CAST_SKILL_KEY);
        event.register(CAST_SLOT_2_KEY);
        event.register(CAST_SLOT_3_KEY);
        event.register(OPEN_SKILLS_KEY);
        event.register(OPEN_STATS_KEY);
    }

    @SubscribeEvent
    public void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath("classesreborn", "rpg_hud"),
                new RpgHudOverlay()
        );
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (OPEN_SKILLS_KEY.consumeClick()) {
            ClientHooks.openSkillTreeScreen();
        }

        while (OPEN_STATS_KEY.consumeClick()) {
            ClientHooks.openStatScreen();
        }

        while (CAST_SKILL_KEY.consumeClick()) {
            triggerActiveSlot(mc, 0, "G");
        }

        while (CAST_SLOT_2_KEY.consumeClick()) {
            triggerActiveSlot(mc, 1, "R");
        }

        while (CAST_SLOT_3_KEY.consumeClick()) {
            triggerActiveSlot(mc, 2, "C");
        }
    }

    private static void triggerActiveSlot(Minecraft mc, int slotIndex, String keyLabel) {
        if (mc.player == null) return;
        PlayerRpgData data = PlayerRpgData.get(mc.player);
        RpgClass rpgClass = data.getSelectedClass();
        if (rpgClass == null) return;

        String skillId = SkillRegistry.getActiveSlotSkillId(data, slotIndex);
        if (skillId == null || skillId.isBlank()) {
            // Fallback if slot hasn't been manually assigned yet, capped within the 3 active slots
            List<Skill> classSkills = SkillRegistry.getForClass(rpgClass);
            int limit = Math.min(PlayerRpgData.MAX_ACTIVE_SLOTS, classSkills.size());
            if (slotIndex < limit) {
                skillId = classSkills.get(slotIndex).getId();
            }
        }

        if (skillId == null || skillId.isBlank()) {
            mc.player.displayClientMessage(
                    Component.literal("No skill assigned to [" + keyLabel + "] (Slot " + (slotIndex + 1) + ")"),
                    true
            );
            return;
        }

        float forward = mc.player.input.forwardImpulse;
        float strafe = mc.player.input.leftImpulse;
        PacketDistributor.sendToServer(new MoveInputPayload(forward, strafe));
        PacketDistributor.sendToServer(new CastSkillPayload(skillId));
    }

    @SubscribeEvent
    public void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        if (!(event.getEntity() instanceof Player player)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        boolean isStealthedAssassin = data.getSelectedClass() == RpgClass.ASSASSIN
                && player.hasEffect(MobEffects.INVISIBILITY);

        if (isStealthedAssassin) {
            event.setCanceled(true);
        }
    }
}