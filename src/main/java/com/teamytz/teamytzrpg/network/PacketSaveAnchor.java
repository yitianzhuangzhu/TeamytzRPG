package com.teamytz.teamytzrpg.network;

import com.teamytz.teamytzrpg.region.RegionManager;
import com.teamytz.teamytzrpg.region.SearchItem;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/** 客户端 → 服务端：保存基准方块编辑后的名称/功能/搜索收集目标。 */
public class PacketSaveAnchor implements IMessage {

    private BlockPos pos;
    private String anchorName = "";
    private String function = "";
    private final List<SearchItem> items = new ArrayList<>();

    public PacketSaveAnchor() {
    }

    public PacketSaveAnchor(BlockPos pos, String anchorName, String function, List<SearchItem> items) {
        this.pos = pos;
        this.anchorName = anchorName == null ? "" : anchorName;
        this.function = function == null ? "" : function;
        if (items != null) {
            this.items.addAll(items);
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pos = new BlockPos(pb.readInt(), pb.readInt(), pb.readInt());
        anchorName = pb.readString(32767);
        function = pb.readString(32767);
        int n = pb.readInt();
        for (int i = 0; i < n; i++) {
            SearchItem si = new SearchItem(pb.readString(32767), pb.readInt());
            si.nbtMode = (char) pb.readByte();
            if (pb.readBoolean()) {
                try {
                    si.nbt = pb.readCompoundTag();
                } catch (java.io.IOException ignored) {
                    si.nbt = null;
                }
            }
            items.add(si);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeInt(pos.getX());
        pb.writeInt(pos.getY());
        pb.writeInt(pos.getZ());
        pb.writeString(anchorName);
        pb.writeString(function);
        pb.writeInt(items.size());
        for (SearchItem s : items) {
            pb.writeString(s.id);
            pb.writeInt(s.qty);
            pb.writeByte(s.nbtMode);
            pb.writeBoolean(s.nbt != null);
            if (s.nbt != null) {
                pb.writeCompoundTag(s.nbt);
            }
        }
    }

    /** 服务端处理器：切到主线程写数据。 */
    public static class HandlerServer implements IMessageHandler<PacketSaveAnchor, IMessage> {
        @Override
        public IMessage onMessage(final PacketSaveAnchor msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    RegionManager.applyAnchorMeta(player.getEntityWorld(), msg.pos,
                            msg.anchorName, msg.function, msg.items);
                    player.sendMessage(new TextComponentString("§a基准方块信息已保存: 名称='"
                            + msg.anchorName + "', 功能='" + msg.function + "'"));
                }
            });
            return null;
        }
    }
}
