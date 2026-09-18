package com.teamytz.teamytzrpg.item;

import com.teamytz.teamytzrpg.TeamytzRPG;
import com.teamytz.teamytzrpg.region.RegionLog;
import com.teamytz.teamytzrpg.region.RegionManager;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * 区域权杖的点击行为：
 * 主手持权杖时——左键方块 = 选点1；右键方块 = 选点2（右键不会触发方块自身的激活）。
 * 每次点击在聊天区输出坐标；两侧都取消事件（防止误拆方块 / 误开容器）。
 */
@Mod.EventBusSubscriber(modid = TeamytzRPG.MODID)
public final class WandEvents {

    private WandEvents() {
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        if (!isWand(player)) {
            return;
        }
        event.setCanceled(true); // 取消挖掘方块
        if (player.getEntityWorld().isRemote) {
            return;
        }
        BlockPos pos = event.getPos();
        RegionManager.selectPoint1(player.getUniqueID(), event.getWorld().provider.getDimension(), pos);
        say(player, "第一个点已选取: " + fmt(pos));
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        EntityPlayer player = event.getEntityPlayer();
        // 玩家右键会先按主手、再按副手尝试两次事件；只处理主手那次，避免提示/选点重复。
        if (event.getHand() != null && event.getHand() != EnumHand.MAIN_HAND) {
            return;
        }
        if (!isWand(player)) {
            return;
        }
        event.setCanceled(true); // 阻止方块被"使用"（容器/按钮等不会触发）
        if (player.getEntityWorld().isRemote) {
            return;
        }
        BlockPos pos = event.getPos();
        RegionManager.selectPoint2(player.getUniqueID(), event.getWorld().provider.getDimension(), pos);
        say(player, "第二个点已选取: " + fmt(pos));
        RegionManager.Selection sel = RegionManager.selectionOf(player.getUniqueID());
        if (sel != null && !sel.a.equals(sel.b)) {
            int sx = Math.abs(sel.a.getX() - sel.b.getX()) + 1;
            int sy = Math.abs(sel.a.getY() - sel.b.getY()) + 1;
            int sz = Math.abs(sel.a.getZ() - sel.b.getZ()) + 1;
            say(player, "区域范围 " + sx + " x " + sy + " x " + sz
                    + " 格 —— 准星对准基准方块后执行 /teamytz area set <区域名> 完成绑定");
            RegionLog.info("玩家 " + player.getName() + " 完成选点: "
                    + fmt(sel.a) + " ~ " + fmt(sel.b));
        }
    }

    private static boolean isWand(EntityPlayer player) {
        return player.getHeldItemMainhand() != null
                && player.getHeldItemMainhand().getItem() instanceof ItemRegionWand;
    }

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }

    private static void say(EntityPlayer player, String msg) {
        player.sendMessage(new TextComponentString("§b[区域权杖] §r" + msg));
    }
}
