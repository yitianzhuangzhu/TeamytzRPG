package com.teamytz.teamytzrpg.network;

import com.teamytz.teamytzrpg.client.hud.SearchHudRenderer;
import com.teamytz.teamytzrpg.region.SearchHudRow;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：同步搜索区域 HUD 面板（空列表 = 隐藏）。 */
public class PacketSyncSearchHud implements IMessage {

    private final List<SearchHudRow> rows = new ArrayList<>();

    public PacketSyncSearchHud() {
    }

    public PacketSyncSearchHud(List<SearchHudRow> rows) {
        if (rows != null) {
            this.rows.addAll(rows);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        int n = pb.readInt();
        for (int i = 0; i < n; i++) {
            String id = pb.readString(32767);
            int need = pb.readInt();
            boolean done = pb.readBoolean();
            int have = pb.readInt();
            net.minecraft.nbt.NBTTagCompound nbt = null;
            if (pb.readBoolean()) {
                try {
                    nbt = pb.readCompoundTag();
                } catch (java.io.IOException ignored) {
                }
            }
            rows.add(new SearchHudRow(id, need, done, have, nbt));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeInt(rows.size());
        for (SearchHudRow r : rows) {
            pb.writeString(r.id);
            pb.writeInt(r.need);
            pb.writeBoolean(r.done);
            pb.writeInt(r.have);
            pb.writeBoolean(r.nbt != null);
            if (r.nbt != null) {
                pb.writeCompoundTag(r.nbt);
            }
        }
    }

    /** 客户端处理器：注册 HUD 渲染并刷新数据。 */
    public static class HandlerClient implements IMessageHandler<PacketSyncSearchHud, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketSyncSearchHud msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        SearchHudRenderer.register();
                        SearchHudRenderer.setRows(msg.rows);
                    }
                });
            }
            return null;
        }
    }
}
