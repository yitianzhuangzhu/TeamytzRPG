package com.teamytz.teamytzrpg.network;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 简单网络通道（频道名 ≤20 字符）。注册顺序固定，两侧编号必须一致。
 * <ul>
 *   <li>PacketOpenAnchorGui —— 服务端→客户端，打开基准方块编辑 UI</li>
 *   <li>PacketSaveAnchor    —— 客户端→服务端，保存名称/功能/收集目标</li>
 *   <li>PacketSyncSearchHud —— 服务端→客户端，搜索区域收集信息栏</li>
 * </ul>
 */
public final class NetworkHandler {

    public static SimpleNetworkWrapper NET;
    private static int nextId = 0;

    private NetworkHandler() {
    }

    public static void init() {
        NET = NetworkRegistry.INSTANCE.newSimpleChannel("teamytzrpg");
        register(PacketOpenAnchorGui.class, PacketOpenAnchorGui.HandlerClient.class, Side.CLIENT);
        register(PacketSaveAnchor.class, PacketSaveAnchor.HandlerServer.class, Side.SERVER);
        register(PacketSyncSearchHud.class, PacketSyncSearchHud.HandlerClient.class, Side.CLIENT);
        register(PacketResetSearchProgress.class, PacketResetSearchProgress.HandlerServer.class, Side.SERVER);
        register(PacketToggleDebugIgnore.class, PacketToggleDebugIgnore.HandlerServer.class, Side.SERVER);
    }

    private static <REQ extends IMessage, REPLY extends IMessage> void register(
            Class<REQ> msg, Class<? extends IMessageHandler<REQ, REPLY>> handler, Side side) {
        NET.registerMessage(handler, msg, nextId++, side);
    }

    /** 服务端 → 客户端：请求打开基准方块编辑 UI。 */
    public static void sendOpenAnchorGui(EntityPlayer player, BlockPos pos, String anchorName,
                                         String function,
                                         java.util.List<com.teamytz.teamytzrpg.region.SearchItem> items) {
        if (NET == null || player == null || player.getEntityWorld().isRemote) {
            return;
        }
        NET.sendTo(new PacketOpenAnchorGui(pos, anchorName, function, items), (EntityPlayerMP) player);
    }

    /** 服务端 → 客户端：刷新搜索收集信息栏（空列表 = 隐藏）。 */
    public static void sendSearchHud(EntityPlayerMP player,
                                     java.util.List<com.teamytz.teamytzrpg.region.SearchHudRow> rows) {
        if (NET == null || player == null) {
            return;
        }
        NET.sendTo(new PacketSyncSearchHud(rows), player);
    }
}
