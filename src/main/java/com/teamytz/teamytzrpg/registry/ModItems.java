package com.teamytz.teamytzrpg.registry;

import com.teamytz.teamytzrpg.item.ItemRegionWand;

import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;

/** 本 mod 全部非方块物品。 */
public final class ModItems {

    public static final ItemRegionWand REGION_WAND = new ItemRegionWand();

    private ModItems() {
    }

    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(REGION_WAND);
    }
}
