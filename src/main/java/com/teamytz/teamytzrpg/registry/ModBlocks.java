package com.teamytz.teamytzrpg.registry;

import com.teamytz.teamytzrpg.block.BlockRegionAnchor;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraftforge.event.RegistryEvent;

/** 本 mod 全部方块与对应 Item 的持有者。 */
public final class ModBlocks {

    public static final BlockRegionAnchor REGION_ANCHOR = new BlockRegionAnchor();

    private ModBlocks() {
    }

    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().registerAll(REGION_ANCHOR);
    }

    public static void registerItems(RegistryEvent.Register<Item> event) {
        event.getRegistry().registerAll(
                new ItemBlock(REGION_ANCHOR).setRegistryName(REGION_ANCHOR.getRegistryName()));
    }
}
