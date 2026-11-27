package com.tanishk.classesreborn.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.tanishk.classesreborn.item.ModItems;
import com.tanishk.classesreborn.network.SyncRpgDataPayload;
import com.tanishk.classesreborn.rpg.ClassStatManager;
import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Arrays;

public class RpgCommands {

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rpg")
                .then(Commands.literal("info")
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PlayerRpgData data = PlayerRpgData.get(player);
                            String cls = data.hasSelectedClass() ? data.getSelectedClass().getId() : "none";
                            player.sendSystemMessage(Component.literal(
                                    "Class: " + cls +
                                            " | Level: " + data.getLevel() +
                                            " | XP: " + data.getXp() + "/" + data.getXpForNextLevel() +
                                            " | Skill Points: " + data.getSkillPoints()
                            ));
                            return 1;
                        }))
                .then(Commands.literal("setclass")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("class", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(
                                        Arrays.stream(RpgClass.values()).map(RpgClass::getId), builder
                                ))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "class");
                                    RpgClass chosen = RpgClass.byId(id);
                                    if (chosen == null) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown class: " + id));
                                        return 0;
                                    }
                                    PlayerRpgData data = PlayerRpgData.get(player);
                                    data.setSelectedClass(chosen);
                                    ClassStatManager.applyStats(player, data);
                                    PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
                                    player.sendSystemMessage(Component.literal("Set class to " + chosen.getId()));
                                    return 1;
                                })))
                .then(Commands.literal("addxp")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    int amount = IntegerArgumentType.getInteger(ctx, "amount");
                                    PlayerRpgData data = PlayerRpgData.get(player);
                                    int gained = data.addXp(amount);
                                    if (gained > 0) {
                                        ClassStatManager.applyStats(player, data);
                                    }
                                    PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
                                    player.sendSystemMessage(Component.literal(
                                            "Added " + amount + " XP (Level " + data.getLevel() + ")"
                                    ));
                                    return 1;
                                })))
                .then(Commands.literal("reset")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            PlayerRpgData data = PlayerRpgData.get(player);
                            data.copyFrom(new PlayerRpgData());
                            ClassStatManager.clearStats(player);
                            ItemStack book = new ItemStack(ModItems.CLASS_BOOK.get());
                            if (!player.getInventory().add(book)) {
                                player.drop(book, false);
                            }
                            data.setReceivedClassBook(true);
                            PacketDistributor.sendToPlayer(player, SyncRpgDataPayload.from(data));
                            player.sendSystemMessage(Component.literal("RPG class reset. Use your Class Book!"));
                            return 1;
                        }))
        );
    }
}