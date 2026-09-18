package com.teamytz.teamytzrpg.network;

import com.teamytz.teamytzrpg.client.gui.GuiAnchorEdit;
import com.teamytz.teamytzrpg.region.SearchItem;

import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/** 服务端 → 客户端：打开基准方块编辑 UI（携带当前名称/功能/搜索收集目标）。 */
public class PacketOpenAnchorGui implements IMessage {

    private BlockPos pos;
    private String anchorName = "";
    private String function = "";
    private final List<SearchItem> items = new ArrayList<>();

    public PacketOpenAnchorGui() {
    }

    public PacketOpenAnchorGui(BlockPos pos, String anchorName, String function, List<SearchItem> items) {
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

    /** 客户端处理器（仅客户端会被调用）。 */
    public static class HandlerClient implements IMessageHandler<PacketOpenAnchorGui, IMessage> {
        @Override
        @SideOnly(Side.CLIENT)
        public IMessage onMessage(final PacketOpenAnchorGui msg, MessageContext ctx) {
            if (ctx.side == Side.CLIENT) {
                Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                    @Override
                    public void run() {
                        Minecraft.getMinecraft().displayGuiScreen(
                                new GuiAnchorEdit(msg.pos, msg.anchorName, msg.function, msg.items));
                    }
                });
            }
            return null;
        }
    }
}
