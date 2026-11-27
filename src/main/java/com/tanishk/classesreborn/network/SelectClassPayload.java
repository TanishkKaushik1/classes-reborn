package com.tanishk.classesreborn.network;

import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

public record SelectClassPayload(String classId) implements CustomPacketPayload {
    public static final Type<SelectClassPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("classesreborn", "select_class"));

    public static final StreamCodec<FriendlyByteBuf, SelectClassPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> buf.writeUtf(payload.classId),
                    buf -> new SelectClassPayload(buf.readUtf(32))
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SelectClassPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer serverPlayer)) return;

            RpgClass chosen = RpgClass.byId(payload.classId);
            if (chosen == null) return;

            PlayerRpgData data = PlayerRpgData.get(serverPlayer);

            // Prevent changing once selected unless reset via admin command
            if (data.hasSelectedClass()) {
                serverPlayer.sendSystemMessage(Component.literal("You have already chosen a class!"));
                PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
                return;
            }

            data.setSelectedClass(chosen);
            data.setLevel(1);
            data.setXp(0);
            ClassStatManager.applyStats(serverPlayer, data);
            serverPlayer.setHealth(serverPlayer.getMaxHealth());

            if (!data.hasReceivedStarterKit()) {
                removeClassBook(serverPlayer);
                giveStarterKit(serverPlayer, chosen);
                data.setReceivedStarterKit(true);
            }

            serverPlayer.sendSystemMessage(
                    Component.literal("You chose ").append(chosen.getDisplayName()).append("!")
            );

            PacketDistributor.sendToPlayer(serverPlayer, SyncRpgDataPayload.from(data));
        });
    }

    private static void removeClassBook(ServerPlayer player) {
        var inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(ModItems.CLASS_BOOK.get())) {
                stack.shrink(1);
                return;
            }
        }
    }

    private static void giveStarterKit(ServerPlayer player, RpgClass rpgClass) {
        List<ItemStack> items = new ArrayList<>();
        switch (rpgClass) {
            case TANK -> {
                items.add(new ItemStack(Items.IRON_SWORD));
                items.add(new ItemStack(Items.SHIELD));
                items.add(new ItemStack(Items.IRON_CHESTPLATE));
                items.add(new ItemStack(Items.COOKED_BEEF, 4));
            }
            case SWORDSMAN -> {
                items.add(new ItemStack(Items.IRON_SWORD));
                items.add(new ItemStack(Items.CHAINMAIL_CHESTPLATE));
                items.add(new ItemStack(Items.IRON_BOOTS));
                items.add(new ItemStack(Items.COOKED_BEEF, 4));
            }
            case ASSASSIN -> {
                items.add(new ItemStack(Items.STONE_SWORD));
                items.add(new ItemStack(Items.LEATHER_CHESTPLATE));
                items.add(new ItemStack(Items.LEATHER_BOOTS));
                items.add(new ItemStack(Items.ENDER_PEARL, 2));
            }
            case ARCHER -> {
                items.add(new ItemStack(Items.BOW));
                items.add(new ItemStack(Items.ARROW, 32));
                items.add(new ItemStack(Items.LEATHER_CHESTPLATE));
                items.add(new ItemStack(Items.LEATHER_HELMET));
            }
            case HEALER -> {
                items.add(new ItemStack(ModItems.HEALER_STAFF.get()));
                items.add(new ItemStack(Items.LEATHER_CHESTPLATE));
                items.add(new ItemStack(Items.COOKED_BEEF, 4));
            }
        }

        for (ItemStack stack : items) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }
}