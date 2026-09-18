package com.teamytz.teamytzrpg.block;

import com.teamytz.teamytzrpg.TeamytzRPG;
import com.teamytz.teamytzrpg.item.ItemRegionWand;
import com.teamytz.teamytzrpg.network.NetworkHandler;
import com.teamytz.teamytzrpg.region.RegionAnchorData;
import com.teamytz.teamytzrpg.region.RegionDesignData;
import com.teamytz.teamytzrpg.region.RegionManager;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;

/**
 * 区域基准方块（结构里每处实例放置的"界碑"/基准）。
 *
 * <p>方块本身不携带数据（无 TileEntity）：实例身份 = 世界坐标，其余数据
 * （区域名/标签/功能/局部角点）存于该维度的 {@link RegionManager} 持久化数据里。</p>
 *
 * <p>交互：创造模式空手右键 → 打开编辑 UI（改标签名与功能，功能暂未启用）。</p>
 */
public class BlockRegionAnchor extends Block {

    public BlockRegionAnchor() {
        super(Material.ROCK);
        setUnlocalizedName("region_anchor");
        setRegistryName(TeamytzRPG.MODID, "region_anchor");
        setCreativeTab(CreativeTabs.MISC);
        setHardness(-1.0F);      // 不可被生存模式破坏
        setResistance(6000000.0F);
        setSoundType(SoundType.STONE);
    }

    @Override
    public boolean onBlockActivated(World worldIn, BlockPos pos, IBlockState state,
                                    EntityPlayer playerIn, EnumHand hand, EnumFacing facing,
                                    float hitX, float hitY, float hitZ) {
        // 创造模式 + 空手主手 且不是权杖 → 打开编辑 UI
        if (hand == EnumHand.MAIN_HAND && playerIn.isCreative()
                && playerIn.getHeldItemMainhand().isEmpty()
                && !(playerIn.getHeldItemOffhand().getItem() instanceof ItemRegionWand)) {
            if (!worldIn.isRemote) {
                RegionAnchorData data = RegionManager.dataFor(worldIn);
                RegionAnchorData.Entry e = data != null ? data.get(pos) : null;
                String name = e != null && e.hasName() ? e.anchorName : "";
                String function = "";
                java.util.List<com.teamytz.teamytzrpg.region.SearchItem> items =
                        java.util.Collections.emptyList();
                RegionDesignData designs = RegionManager.designsFor(worldIn);
                if (designs != null && !name.isEmpty()) {
                    RegionDesignData.Design d = designs.get(name);
                    if (d != null) {
                        function = d.function;
                        items = d.items;
                    }
                }
                NetworkHandler.sendOpenAnchorGui(playerIn, pos, name, function, items);
            }
            return true;
        }
        return false;
    }

    @Override
    public void onBlockAdded(World worldIn, BlockPos pos, IBlockState state) {
        super.onBlockAdded(worldIn, pos, state);
        RegionManager.notifyPlaced(worldIn, pos);
    }

    @Override
    public void breakBlock(World worldIn, BlockPos pos, IBlockState state) {
        RegionManager.notifyRemoved(worldIn, pos);
        super.breakBlock(worldIn, pos, state);
    }

    @Override
    public void onBlockDestroyedByExplosion(World worldIn, BlockPos pos, Explosion explosionIn) {
        RegionManager.notifyRemoved(worldIn, pos);
        super.onBlockDestroyedByExplosion(worldIn, pos, explosionIn);
    }
}
