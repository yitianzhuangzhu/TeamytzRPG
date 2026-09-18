package com.teamytz.teamytzrpg.region;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.teamytz.teamytzrpg.TeamytzRPG;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ServerTickEvent;

/**
 * 区域系统服务器 tick：
 * 1) 周期失效自愈；
 * 2) 玩家进出区域 debug 日志（脚部方块坐标 floor(pos)）；
 * 3) 根据所在区域实例的功能刷新顶部 Boss 条（区域功能条）。
 */
@Mod.EventBusSubscriber(modid = TeamytzRPG.MODID)
public final class RegionEvents {

    private static int tickCount = 0;

    /** playerKey = "dim:playerUUID" → 该玩家当前所在区域实例签名集合。 */
    private static final Map<String, Set<String>> INSIDE = new HashMap<>();

    private RegionEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server == null) {
            return;
        }
        tickCount++;
        if (tickCount % 100 == 0) {
            for (World w : server.worlds) {
                RegionManager.prune(w);
            }
        }
        if (tickCount % 200 == 0) {
            RegionSearchTracker.pruneTossed();
        }
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        if (players == null || players.isEmpty()) {
            return;
        }
        Set<String> active = new HashSet<>();
        for (EntityPlayerMP player : players) {
            World world = player.getEntityWorld();
            String playerKey = world.provider.getDimension() + ":" + player.getCachedUniqueIdString();
            active.add(playerKey);
            Set<String> cur = INSIDE.computeIfAbsent(playerKey, k -> new HashSet<>());
            Set<String> next = new HashSet<>();

            List<RegionManager.RegionMatch> matches = RegionManager.matchesAt(world,
                    (int) Math.floor(player.posX),
                    (int) Math.floor(player.posY),
                    (int) Math.floor(player.posZ));
            for (RegionManager.RegionMatch m : matches) {
                String sig = m.entry.pos.getX() + "," + m.entry.pos.getY() + "," + m.entry.pos.getZ()
                        + "|" + m.regionName();
                next.add(sig);
                if (!cur.contains(sig)) {
                    RegionLog.info("玩家 " + player.getName() + " 进入区域: " + m.describe(world));
                }
            }
            for (String gone : cur) {
                if (!next.contains(gone)) {
                    RegionLog.info("玩家 " + player.getName() + " 离开区域: " + gone);
                }
            }
            cur.clear();
            cur.addAll(next);

            // 区域功能条（搜索区域等）
            RegionBarManager.update(player, matches);
            // 搜索区域收集信息栏（HUD）
            RegionSearchTracker.update(player, world, matches);
        }
        INSIDE.keySet().retainAll(active);
    }

    @SubscribeEvent
    public static void onPickup(EntityItemPickupEvent event) {
        EntityPlayer player = event.getEntityPlayer();
        EntityItem item = event.getItem();
        if (player == null || item == null) {
            return;
        }
        // 物品实体位置命中搜索区域 → 累计收集进度（HUD 打 √ 的依据）
        RegionSearchTracker.onPickup(player, item);
    }

    @SubscribeEvent
    public static void onToss(ItemTossEvent event) {
        // 记录玩家丢出的物品实体，用于"丢出再捡"防刷
        RegionSearchTracker.markTossed(event.getEntityItem());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player != null) {
            RegionBarManager.onLogout(event.player.getUniqueID());
            RegionSearchTracker.onLogout(event.player.getUniqueID());
        }
    }
}
