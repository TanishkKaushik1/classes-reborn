package com.tanishk.classesreborn.item;

import com.tanishk.classesreborn.rpg.PlayerRpgData;
import com.tanishk.classesreborn.rpg.RpgClass;
import com.tanishk.classesreborn.rpg.skill.SkillRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class HealerStaffItem extends Item {

    public HealerStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand usedHand) {
        ItemStack stack = player.getItemInHand(usedHand);

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(stack);
        }

        PlayerRpgData data = PlayerRpgData.get(serverPlayer);
        if (data.getSelectedClass() != RpgClass.HEALER) {
            serverPlayer.displayClientMessage(Component.literal("Only Healers can channel the Healer Staff."), true);
            return InteractionResultHolder.fail(stack);
        }

        if (data.isOnCooldown(SkillRegistry.HEALER_HEAL, level.getGameTime())) {
            int remSec = Math.max(1, (data.getRemainingCooldownTicks(SkillRegistry.HEALER_HEAL, level.getGameTime()) + 19) / 20);
            serverPlayer.displayClientMessage(Component.literal("Heal on cooldown (" + remSec + "s)."), true);
            return InteractionResultHolder.fail(stack);
        }

        if (SkillRegistry.tryStartHealerHealCast(serverPlayer, data)) {
            level.playSound(
                    null,
                    serverPlayer.blockPosition(),
                    SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS,
                    0.9f,
                    1.2f
            );
            return InteractionResultHolder.consume(stack);
        }

        return InteractionResultHolder.fail(stack);
    }
}