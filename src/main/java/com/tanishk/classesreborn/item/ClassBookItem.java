package com.tanishk.classesreborn.item;

import com.tanishk.classesreborn.client.ClientHooks;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class ClassBookItem extends Item {
    public ClassBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);
        if (level.isClientSide()) {
            ClientHooks.openClassSelectionScreen();
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}