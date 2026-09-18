package com.teamytz.teamytzrpg.registry;

import com.teamytz.teamytzrpg.TeamytzRPG;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/** RegistryEvent.Register 时代的事件订阅入口（1.12.2：注册事件走 MinecraftForge.EVENT_BUS）。 */
@Mod.EventBusSubscriber(modid = TeamytzRPG.MODID)
public final class ModRegistry {

    private ModRegistry() {
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        ModBlocks.registerBlocks(event);
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        ModBlocks.registerItems(event);
        ModItems.registerItems(event);
    }
}
