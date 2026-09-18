package com.teamytz.teamytzrpg.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.teamytz.teamytzrpg.config.RegionConfig;
import com.teamytz.teamytzrpg.network.NetworkHandler;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * 搜索区域收集进度（服务端，内存态；登出清除）。
 *
 * <p>按 (玩家, 区域实例基准坐标) 各自记账：玩家在搜索区域内拾取到设计里列出的物品，
 * 数量累加；达到目标后在 HUD 行打 √。</p>
 *
 * <p>判定口径：拾取时以"物品实体位置"命中搜索区域为准。
 * 目标匹配：物品注册 id（+ 可选 NBT，见 {@link SearchItem#nbtMode}，对齐 FTBQuests/ItemFilters
 * 的 MATCH/CONTAIN/IGNORE 语义）。
 * 防刷：默认丢弃的"丢出再捡"不计数（config: search_count_tossed_pickup）。</p>
 */
public final class RegionSearchTracker {

    private static final Map<String, Map<String, Integer>> COLLECTED = new HashMap<>();
    /** 每个玩家当前 HUD 面板签名（避免每 tick 重复发包）。 */
    private static final Map<UUID, String> PANEL_SIG = new HashMap<>();
    /** 玩家丢出过、尚未被捡起的物品实体 id → 时间戳（防丢出再捡刷进度）。 */
    private static final Map<UUID, Long> TOSSED = new HashMap<>();
    /** 调试开关（默认关）：开启后忽略"丢出再捡"来源判定，任何拾取都计（用于测试成书等）。 */
    private static boolean debugIgnoreToss = false;

    private RegionSearchTracker() {
    }

    /** 当前调试(忽略来源)开关状态。 */
    public static boolean isDebugIgnoreToss() {
        return debugIgnoreToss;
    }

    /** 切换调试开关，返回切换后的状态（服务端调用）。 */
    public static boolean toggleDebugIgnoreToss() {
        debugIgnoreToss = !debugIgnoreToss;
        RegionLog.info("调试模式(忽略拾取来源判定) = " + (debugIgnoreToss ? "开" : "关")
                + "（不影响 config: search_count_tossed_pickup 的持久默认值）");
        return debugIgnoreToss;
    }

    // ---------- tick：刷新玩家 HUD ----------

    public static void update(EntityPlayerMP player, World world, List<RegionManager.RegionMatch> matches) {
        RegionManager.RegionMatch panel = null;
        for (RegionManager.RegionMatch m : matches) {
            if (m.design != null && m.design.isSearch() && !m.design.items.isEmpty()) {
                panel = m;
                break;
            }
        }
        List<SearchHudRow> rows = new ArrayList<>();
        if (panel != null) {
            Map<String, Integer> got = collected(player, panel.entry.pos);
            for (SearchItem s : panel.design.items) {
                int have = got.containsKey(bucket(s)) ? got.get(bucket(s)) : 0;
                rows.add(new SearchHudRow(s.id, s.qty, have >= s.qty, have, s.nbt));
            }
        }
        StringBuilder sig = new StringBuilder();
        for (SearchHudRow r : rows) {
            sig.append(r.id).append(':').append(r.need).append(':').append(r.done ? 1 : 0)
                    .append(':').append(r.have).append(';');
        }
        String sigStr = sig.toString();
        String prev = PANEL_SIG.get(player.getUniqueID());
        if (sigStr.equals(prev)) {
            return;
        }
        PANEL_SIG.put(player.getUniqueID(), sigStr);
        NetworkHandler.sendSearchHud(player, rows);
    }

    // ---------- 拾取：累加进度 ----------

    /** EntityItemPickupEvent 服务端回调。 */
    public static void onPickup(EntityPlayer player, EntityItem itemEntity) {
        World world = player.getEntityWorld();
        if (world == null || world.isRemote) {
            return;
        }
        // 防刷：该物品实体是玩家丢出的（且没超时）→ 不计（config 可关；调试模式开启时忽略该判定）
        if (!RegionConfig.countTossedPickup && !debugIgnoreToss) {
            if (TOSSED.remove(itemEntity.getUniqueID()) != null) {
                return;
            }
        }
        ItemStack stack = itemEntity.getItem();
        if (stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return;
        }
        BlockPos itemPos = itemEntity.getPosition();
        List<RegionManager.RegionMatch> matches = RegionManager.matchesAt(world,
                itemPos.getX(), itemPos.getY(), itemPos.getZ());
        for (RegionManager.RegionMatch m : matches) {
            if (m.design == null || !m.design.isSearch() || m.design.items.isEmpty()) {
                continue;
            }
            for (SearchItem s : m.design.items) {
                if (!stackMatches(s, stack)) {
                    continue;
                }
                Map<String, Integer> got = collected(player, m.entry.pos);
                int before = got.containsKey(bucket(s)) ? got.get(bucket(s)) : 0;
                int now = before + stack.getCount();
                got.put(bucket(s), now);
                if (before < s.qty && now >= s.qty) {
                    RegionLog.info("搜索目标达成 玩家=" + player.getName()
                            + " 名称=" + m.entry.anchorName
                            + " 基准=(" + m.entry.pos.getX() + "," + m.entry.pos.getY() + "," + m.entry.pos.getZ() + ")"
                            + " 物品=" + s.id + " 需要=" + s.qty + " 已收集=" + now);
                }
            }
        }
    }

    // ---------- 防刷：丢出标记 ----------

    /** ItemTossEvent 回调：记录被玩家丢出的物品实体（若 config 开启拦截才有意义）。 */
    public static void markTossed(EntityItem itemEntity) {
        if (itemEntity == null || itemEntity.getEntityWorld() == null
                || itemEntity.getEntityWorld().isRemote) {
            return;
        }
        if (RegionConfig.countTossedPickup) {
            return;
        }
        TOSSED.put(itemEntity.getUniqueID(), System.currentTimeMillis());
    }

    /** 周期清理超时标记。 */
    public static void pruneTossed() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = TOSSED.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() > RegionConfig.tossedMarkTtl) {
                it.remove();
            }
        }
    }

    // ---------- 生命周期 ----------

    /**
     * FTB 联动：名称为 anchorName 的基准(设计)的全部搜索收集目标是否已被该玩家完成。
     * 跨同名单例取"最大计数"合并（防多副本把同一次收集重复计）。
     */
    public static boolean isDesignCompleted(EntityPlayer player, String anchorName) {
        World world = player.getEntityWorld();
        if (world == null || world.isRemote || anchorName == null || anchorName.isEmpty()) {
            return false;
        }
        RegionAnchorData anchors = RegionManager.dataFor(world);
        RegionDesignData designs = RegionManager.designsFor(world);
        if (anchors == null || designs == null) {
            return false;
        }
        RegionDesignData.Design d = designs.get(anchorName);
        if (d == null || !d.isSearch() || d.items.isEmpty()) {
            return false;
        }
        List<BlockPos> instances = new ArrayList<>();
        for (RegionAnchorData.Entry e : anchors.getEntries()) {
            if (anchorName.equals(e.anchorName)) {
                instances.add(e.pos);
            }
        }
        if (instances.isEmpty()) {
            return false;
        }
        for (SearchItem s : d.items) {
            int best = 0;
            for (BlockPos pos : instances) {
                Map<String, Integer> got = collected(player, pos);
                best = Math.max(best, got.containsKey(bucket(s)) ? got.get(bucket(s)) : 0);
            }
            if (best < s.qty) {
                return false;
            }
        }
        return true;
    }

    /** 重置某个基准方块的收集进度（测试按钮调用）。 */
    public static void resetProgress(EntityPlayer player, BlockPos pos) {
        String key = progKey(player, pos);
        boolean existed = COLLECTED.remove(key) != null;
        PANEL_SIG.remove(player.getUniqueID());
        RegionLog.info("已重置收集进度 玩家=" + player.getName()
                + " 基准=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"
                + (existed ? "（原进度已清空）" : "（本无进度）"));
    }

    public static void onLogout(UUID uuid) {
        PANEL_SIG.remove(uuid);
        List<String> keys = new ArrayList<>();
        String prefix = uuid.toString() + "|";
        for (String k : COLLECTED.keySet()) {
            if (k.startsWith(prefix)) {
                keys.add(k);
            }
        }
        for (String k : keys) {
            COLLECTED.remove(k);
        }
    }

    // ---------- 匹配 ----------

    /** 实物栈是否满足目标条：id 相等；带 NBT(来自选择器)时要求整栈 NBT 完全相等(FTB MATCH 语义)。 */
    private static boolean stackMatches(SearchItem s, ItemStack stack) {
        if (s == null || stack == null || stack.isEmpty() || stack.getItem().getRegistryName() == null) {
            return false;
        }
        if (!s.id.equals(stack.getItem().getRegistryName().toString())) {
            return false;
        }
        if (s.nbt == null) {
            return true; // 手输 id：仅按 id 匹配
        }
        return s.nbt.equals(stack.getTagCompound());
    }

    // ---------- 内部 ----------

    private static String progKey(EntityPlayer player, BlockPos pos) {
        return player.getUniqueID() + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** 一行目标一个独立计数桶（同一 id 不同 NBT 的行互不串账）。 */
    private static String bucket(SearchItem s) {
        return s.id + "#" + Integer.toHexString(System.identityHashCode(s));
    }

    private static Map<String, Integer> collected(EntityPlayer player, BlockPos pos) {
        String key = progKey(player, pos);
        Map<String, Integer> got = COLLECTED.get(key);
        if (got == null) {
            got = new HashMap<>();
            COLLECTED.put(key, got);
        }
        return got;
    }
}
