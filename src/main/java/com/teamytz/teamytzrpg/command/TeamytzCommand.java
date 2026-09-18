package com.teamytz.teamytzrpg.command;

import java.util.List;

import com.teamytz.teamytzrpg.region.RegionAnchorData;
import com.teamytz.teamytzrpg.region.RegionDesignData;
import com.teamytz.teamytzrpg.region.RegionManager;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

/**
 * 唯一命令根：/teamytz（需要权限等级 2，OP）
 * <pre>
 *   area 组（区域设计绑定，WorldEdit 式）：
 *     /teamytz area set &lt;区域名&gt;   准星对着基准方块时，把权杖选出的两点写入它的设计
 *                                  （设计按"基准名称"共享：同名基准全部同步）
 *     /teamytz area clear           清除未提交的选点
 *     /teamytz area status          查看选点与准星基准的状态
 *   debug 组（诊断）：
 *     /teamytz debug query          列出当前位置命中的区域实例
 *     /teamytz debug list           列出本维度全部基准实例 + 共享设计
 *     /teamytz debug info           查看看向的基准方块与它引用的设计
 *     /teamytz debug remove         移除看向基准的记录（不拆方块）
 *     /teamytz debug designdel &lt;名称&gt;  删除一份共享设计（所有引用它的实例变回未绑定）
 * </pre>
 */
public class TeamytzCommand extends CommandBase {

    @Override
    public String getName() {
        return "teamytz";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/teamytz <area|debug> ...";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) {
        if (!(sender instanceof EntityPlayer)) {
            msg(sender, "仅限玩家执行");
            return;
        }
        EntityPlayer player = (EntityPlayer) sender;
        if (args.length < 1) {
            msg(sender, "用法: " + getUsage(sender));
            msg(sender, "  /teamytz area set <区域名> | area clear | area status");
            msg(sender, "  /teamytz debug query | debug list | debug info | debug remove | debug designdel <名称>");
            return;
        }
        String group = args[0];
        if (group.equals("area")) {
            execArea(player, sender, args);
        } else if (group.equals("debug")) {
            execDebug(player, sender, args);
        } else {
            msg(sender, "未知子命令 '" + group + "'，用法: " + getUsage(sender));
        }
    }

    // ================= area 组 =================

    private void execArea(EntityPlayer player, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "用法: /teamytz area set <区域名> | area clear | area status");
            return;
        }
        switch (args[1]) {
            case "set":
                areaSet(player, sender, args);
                break;
            case "clear":
                RegionManager.clearSelection(player.getUniqueID());
                msg(sender, "已清除未提交的选点");
                break;
            case "status":
                areaStatus(player, sender);
                break;
            default:
                msg(sender, "用法: /teamytz area set <区域名> | area clear | area status");
        }
    }

    private void areaSet(EntityPlayer player, ICommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "缺少区域名: /teamytz area set <区域名>");
            return;
        }
        String name = args[2];
        if (name.contains(" ") || name.isEmpty()) {
            msg(sender, "区域名不能为空且不能含空格");
            return;
        }
        BlockPos anchor = RegionManager.lookedAnchor(player);
        if (anchor == null) {
            msg(sender, "请把准星对准一个基准方块（6 格内）");
            return;
        }
        String result = RegionManager.bindRegion(player.getEntityWorld(), anchor,
                player.getUniqueID(), name);
        msg(sender, result);
    }

    private void areaStatus(EntityPlayer player, ICommandSender sender) {
        RegionManager.Selection sel = RegionManager.selectionOf(player.getUniqueID());
        if (sel != null) {
            msg(sender, "选点: " + fmt(sel.a) + " ~ " + fmt(sel.b));
        } else {
            msg(sender, "选点: 无（手持区域权杖，左键/右键点两个方块）");
        }
        BlockPos anchor = RegionManager.lookedAnchor(player);
        if (anchor == null) {
            msg(sender, "准星基准: 无（请对准基准方块）");
            return;
        }
        RegionAnchorData anchors = RegionManager.dataFor(player.getEntityWorld());
        RegionAnchorData.Entry e = anchors != null ? anchors.get(anchor) : null;
        RegionDesignData.Design d = null;
        if (e != null && e.hasName()) {
            RegionDesignData designs = RegionManager.designsFor(player.getEntityWorld());
            d = designs != null ? designs.get(e.anchorName) : null;
        }
        StringBuilder sb = new StringBuilder("准星基准: " + fmt(anchor));
        sb.append(e == null ? "（无实例记录）" : " 名称=" + (e.hasName() ? e.anchorName : "(未命名)"));
        if (d == null) {
            sb.append(" —— 无设计");
        } else {
            sb.append(" 区域名=" + (d.isRegionBound() ? d.regionName : "(未绑定)"));
            sb.append(" 功能=" + (d.function.isEmpty() ? "(空)" : d.function));
            if (d.isRegionBound()) {
                sb.append(" box=" + RegionManager.worldBoxOf(d, anchor));
            }
        }
        msg(sender, sb.toString());
    }

    // ================= debug 组 =================

    private void execDebug(EntityPlayer player, ICommandSender sender, String[] args) {
        if (args.length < 2) {
            msg(sender, "用法: /teamytz debug query | debug list | debug info | debug remove | debug designdel <名称>");
            return;
        }
        switch (args[1]) {
            case "query":
                debugQuery(player, sender);
                break;
            case "list":
                debugList(player, sender);
                break;
            case "info":
                debugInfo(player, sender);
                break;
            case "remove":
                debugRemove(player, sender);
                break;
            case "designdel":
                debugDesignDel(player, sender, args);
                break;
            default:
                msg(sender, "用法: /teamytz debug query | debug list | debug info | debug remove | debug designdel <名称>");
        }
    }

    private void debugQuery(EntityPlayer player, ICommandSender sender) {
        World world = player.getEntityWorld();
        List<RegionManager.RegionMatch> matches = RegionManager.matchesAt(world,
                (int) Math.floor(player.posX), (int) Math.floor(player.posY), (int) Math.floor(player.posZ));
        if (matches.isEmpty()) {
            msg(sender, "当前位置不在任何已绑定的区域内 (" + fmt(new BlockPos(player)) + ")");
            return;
        }
        msg(sender, "当前位置 (" + fmt(new BlockPos(player)) + ") 命中 " + matches.size() + " 个区域:");
        for (RegionManager.RegionMatch m : matches) {
            msg(sender, "  - " + m.describe(world));
        }
    }

    private void debugList(EntityPlayer player, ICommandSender sender) {
        World world = player.getEntityWorld();
        RegionAnchorData anchors = RegionManager.dataFor(world);
        RegionDesignData designs = RegionManager.designsFor(world);
        if (anchors == null || designs == null) {
            msg(sender, "数据不可用");
            return;
        }
        msg(sender, "本维度基准实例 " + anchors.getEntries().size() + " 个:");
        for (RegionAnchorData.Entry e : anchors.getEntries()) {
            RegionDesignData.Design d = e.hasName() ? designs.get(e.anchorName) : null;
            String state = (d == null)
                    ? "无设计"
                    : (d.isRegionBound()
                        ? "区域=" + d.regionName + " 功能=" + (d.function.isEmpty() ? "(空)" : d.function)
                          + " box=" + RegionManager.worldBoxOf(d, e.pos)
                        : "设计未绑定区域");
            msg(sender, "  - " + fmt(e.pos) + " 名称=" + (e.hasName() ? e.anchorName : "(未命名)")
                    + " [" + state + "]");
        }
        msg(sender, "本维度共享设计 " + designs.getDesigns().size() + " 份:");
        for (RegionDesignData.Design d : designs.getDesigns()) {
            msg(sender, "  - 名称=" + d.anchorName
                    + " 区域名=" + (d.isRegionBound() ? d.regionName : "(未绑定)")
                    + " 功能=" + (d.function.isEmpty() ? "(空)" : d.function)
                    + (d.isRegionBound()
                        ? " 局部角点=(" + d.local1.getX() + "," + d.local1.getY() + "," + d.local1.getZ()
                          + ")~(" + d.local2.getX() + "," + d.local2.getY() + "," + d.local2.getZ() + ")"
                        : ""));
        }
    }

    private void debugInfo(EntityPlayer player, ICommandSender sender) {
        BlockPos anchor = RegionManager.lookedAnchor(player);
        if (anchor == null) {
            msg(sender, "请把准星对准一个基准方块（6 格内）");
            return;
        }
        World world = player.getEntityWorld();
        RegionAnchorData anchors = RegionManager.dataFor(world);
        RegionAnchorData.Entry e = anchors != null ? anchors.get(anchor) : null;
        if (e == null) {
            msg(sender, "该位置有基准方块但无实例记录（重新放置一次即可）: " + fmt(anchor));
            return;
        }
        if (!e.hasName()) {
            msg(sender, "基准 " + fmt(anchor) + ": 未命名（空手右键起名后可引用共享设计）");
            return;
        }
        RegionDesignData.Design d = RegionManager.designsFor(world).get(e.anchorName);
        if (d == null) {
            msg(sender, "基准 " + fmt(anchor) + ": 名称=" + e.anchorName + "（尚无该名称的设计）");
            return;
        }
        msg(sender, "基准 " + fmt(anchor) + ": 名称=" + e.anchorName
                + ", 区域名=" + (d.isRegionBound() ? d.regionName : "(未绑定)")
                + ", 功能=" + (d.function.isEmpty() ? "(空)" : d.function)
                + (d.isRegionBound()
                        ? ", box=" + RegionManager.worldBoxOf(d, anchor)
                        : "（先 /teamytz area set 绑定区域）"));
    }

    private void debugRemove(EntityPlayer player, ICommandSender sender) {
        BlockPos anchor = RegionManager.lookedAnchor(player);
        if (anchor == null) {
            msg(sender, "请把准星对准一个基准方块（6 格内）");
            return;
        }
        RegionManager.removeAnchor(player.getEntityWorld(), anchor);
        msg(sender, "已移除基准记录 " + fmt(anchor) + "（共享设计保留）");
    }

    private void debugDesignDel(EntityPlayer player, ICommandSender sender, String[] args) {
        if (args.length < 3) {
            msg(sender, "用法: /teamytz debug designdel <名称>");
            return;
        }
        String name = args[2];
        RegionDesignData designs = RegionManager.designsFor(player.getEntityWorld());
        boolean removed = designs != null && designs.remove(name);
        msg(sender, removed
                ? "已删除共享设计 '" + name + "'（引用它的基准实例已变回未绑定）"
                : "未找到设计 '" + name + "'");
    }

    // ================= 工具 =================

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + ", " + p.getY() + ", " + p.getZ() + ")";
    }

    private static void msg(ICommandSender sender, String text) {
        sender.sendMessage(new TextComponentString("§3[RPG-Region] §r" + text));
    }
}
