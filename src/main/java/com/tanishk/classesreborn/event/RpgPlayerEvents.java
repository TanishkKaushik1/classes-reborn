package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.network.SyncRpgDataPayload;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class RpgPlayerEvents {

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        if (!data.hasSelectedClass() && !data.hasReceivedClassBook()) {
            ItemStack book = new ItemStack(ModItems.CLASS_BOOK.get());
            if (!player.getInventory().add(book)) {
                player.drop(book, false);
            }
            data.setReceivedClassBook(true);
            player.sendSystemMessage(Component.literal("Use your Class Book to choose your RPG class!"));
        }

        ClassStatManager.applyStats(player, data);
        PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
    }

    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer newPlayer)) return;
        if (!(event.getOriginal() instanceof ServerPlayer oldPlayer)) return;

        PlayerRpgData oldData = PlayerRpgData.get(oldPlayer);
        PlayerRpgData newData = PlayerRpgData.get(newPlayer);
        newData.copyFrom(oldData);

        ClassStatManager.applyStats(newPlayer, newData);
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        ClassStatManager.applyStats(player, data);
        if (!event.isEndConquered()) {
            player.setHealth(player.getMaxHealth());
        }
        PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        ClassStatManager.applyStats(player, data);
        PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
    }

    @SubscribeEvent
    public void onPlayerTickPost(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        PlayerRpgData data = PlayerRpgData.get(player);
        SkillRegistry.tickServerPlayer(player, data);
    }

    @SubscribeEvent
    public void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;

        PlayerRpgData data = PlayerRpgData.get(killer);
        if (!data.hasSelectedClass()) return;

        int xpGain = Math.max(5, Math.round(victim.getMaxHealth() * 0.75f));
        int levelsGained = data.addXp(xpGain);

        if (levelsGained > 0) {
            ClassStatManager.applyStats(killer, data);
            killer.sendSystemMessage(
                    Component.literal("Level Up! You are now Level " + data.getLevel() + " (+1 Skill Point)")
            );
        }

        PacketDistributor.sendToPlayer(killer, SyncRpgDataPayload.from(data));
    }

    @SubscribeEvent
    public void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (event.getSlot() != EquipmentSlot.OFFHAND) return;

        ItemStack newStack = event.getTo();
        if (!(newStack.getItem() instanceof ShieldItem)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        if (data.getSelectedClass() != RpgClass.ARCHER) return;

        ItemStack copy = newStack.copy();
        player.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        if (!player.getInventory().add(copy)) {
            player.drop(copy, false);
        }
        player.displayClientMessage(Component.literal("Archers cannot equip shields!"), true);
    }

    @SubscribeEvent
    public void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(event.getItem().getItem() instanceof ShieldItem)) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        if (data.getSelectedClass() == RpgClass.ARCHER) {
            event.setCanceled(true);
            player.displayClientMessage(Component.literal("Archers cannot use shields!"), true);
        }
    }

    @SubscribeEvent
    public void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        FoodProperties food = event.getItem().getFoodProperties(player);
        if (food == null) return;

        PlayerRpgData data = PlayerRpgData.get(player);
        if (!data.hasSelectedClass()) return;

        double satMult = ClassStatManager.getEffectiveSaturationMultiplier(data);
        if (satMult > 1.0) {
            float bonusSat = (float) (food.saturation() * (satMult - 1.0));
            float newSat = Math.min(player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel() + bonusSat);
            player.getFoodData().setSaturation(newSat);
        }
    }
}