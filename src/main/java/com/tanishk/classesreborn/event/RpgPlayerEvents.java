package com.tanishk.classesreborn.event;

import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.network.SyncRpgDataPayload;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
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
        PacketDistributor.sendToPlayer(newPlayer, SyncRpgDataPayload.from(newData));
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
}