package com.teamytz.teamytzrpg.network;

import com.teamytz.teamytzrpg.region.RegionSearchTracker;

import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** 客户端 → 服务端：切换"调试模式(忽略拾取来源判定)"开关。默认关。 */
public class PacketToggleDebugIgnore implements IMessage {

    public PacketToggleDebugIgnore() {
    }

    @Override
    public void fromBytes(ByteBuf buf) {
    }

    @Override
    public void toBytes(ByteBuf buf) {
    }

    public static class HandlerServer implements IMessageHandler<PacketToggleDebugIgnore, IMessage> {
        @Override
        public IMessage onMessage(final PacketToggleDebugIgnore msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    boolean on = RegionSearchTracker.toggleDebugIgnoreToss();
                    player.sendMessage(new TextComponentString(
                            "\u00a7e调试模式(忽略丢出再捡的来源判定): " + (on ? "\u00a7a开" : "\u00a77关")));
                }
            });
            return null;
        }
    }
}
