package com.tanishk.classesreborn.item;

import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final String MOD_ID = "classesreborn";

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MOD_ID);

    public static final DeferredItem<ClassBookItem> CLASS_BOOK = ITEMS.register(
            "class_book",
            () -> new ClassBookItem(new Item.Properties().stacksTo(1))
    );

    public static final DeferredItem<HealerStaffItem> HEALER_STAFF = ITEMS.register(
            "healer_staff",
            () -> new HealerStaffItem(new Item.Properties().stacksTo(1))
    );

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}