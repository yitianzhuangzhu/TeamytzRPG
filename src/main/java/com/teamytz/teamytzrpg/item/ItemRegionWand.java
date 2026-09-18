package com.teamytz.teamytzrpg.item;

import com.teamytz.teamytzrpg.TeamytzRPG;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;

/**
 * 区域权杖：WorldEdit 式划分长方体区域的工具。
 * 交互逻辑见 {@link WandEvents}（左键=点1、右键=点2，取消原方块动作）。
 */
public class ItemRegionWand extends Item {

    public ItemRegionWand() {
        setUnlocalizedName("region_wand");
        setRegistryName(TeamytzRPG.MODID, "region_wand");
        setCreativeTab(CreativeTabs.MISC);
        setMaxStackSize(1);
    }
}
