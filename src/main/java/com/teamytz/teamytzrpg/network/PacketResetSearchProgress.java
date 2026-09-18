package com.teamytz.teamytzrpg.network;

import com.teamytz.teamytzrpg.region.RegionSearchTracker;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 客户端 → 服务端：重置指定基准方块的收集进度（测试用）。 */
public class PacketResetSearchProgress implements IMessage {

    private BlockPos pos;

    public PacketResetSearchProgress() {
    }

    public PacketResetSearchProgress(BlockPos pos) {
        this.pos = pos;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pos = new BlockPos(pb.readInt(), pb.readInt(), pb.readInt());
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer pb = new PacketBuffer(buf);
        pb.writeInt(pos.getX());
        pb.writeInt(pos.getY());
        pb.writeInt(pos.getZ());
    }

    /** 服务端处理器：切主线程执行重置并回执。 */
    public static class HandlerServer implements IMessageHandler<PacketResetSearchProgress, IMessage> {
        @Override
        public IMessage onMessage(final PacketResetSearchProgress msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    RegionSearchTracker.resetProgress(player, msg.pos);
                    player.sendMessage(new TextComponentString(
                            "\u00a7a已重置该基准方块(" + msg.pos.getX() + "," + msg.pos.getY()
                                    + "," + msg.pos.getZ() + ")的收集进度"));
                }
            });
            return null;
        }
    }
}
