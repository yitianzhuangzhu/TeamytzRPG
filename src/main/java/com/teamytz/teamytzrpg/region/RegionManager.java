package com.teamytz.teamytzrpg.region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.teamytz.teamytzrpg.registry.ModBlocks;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

/**
 * 区域系统总入口（静态门面，全部仅服务端有效）。
 *
 * <p>双层模型：
 * <ul>
 *   <li><b>实例</b>（{@link RegionAnchorData}）：世界里的每个基准方块，只记 坐标 + 名称。</li>
 *   <li><b>设计</b>（{@link RegionDesignData}）：按名称索引的共享模板 —— 区域名 / 功能 / 旋转 /
 *       相对基准的两个局部角点。同名基准共用一份设计，在各自位置实例化区域。</li>
 * </ul>
 * 在 A 处把名为 X 的基准绑定好区域后，B 处放置同名基准 X 会自动得到相同形状（相对 B）的区域。
 * 修改设计（重新划区 / 改功能）会同步到所有同名实例。</p>
 *
 * <p>操作流程：放置基准 → 空手右键(创造)起名/设功能 → 权杖选两点 → /teamytz area set &lt;区域名&gt;。</p>
 */
public final class RegionManager {

    /** 玩家一次"未提交"的区域选择（两个世界坐标点）。 */
    public static final class Selection {
        public final int dim;
        public final BlockPos a;
        public final BlockPos b;

        public Selection(int dim, BlockPos a, BlockPos b) {
            this.dim = dim;
            this.a = a;
            this.b = b;
        }
    }

    /** 单次判定结果：命中的实例 + 它引用的设计 + 该实例的实际世界包围盒。 */
    public static final class RegionMatch {
        public final RegionAnchorData.Entry entry;
        public final RegionDesignData.Design design;
        public final AxisAlignedBB box;

        public RegionMatch(RegionAnchorData.Entry entry, RegionDesignData.Design design, AxisAlignedBB box) {
            this.entry = entry;
            this.design = design;
            this.box = box;
        }

        public String regionName() {
            return design == null ? "" : design.regionName;
        }

        public String function() {
            return design == null ? "" : design.function;
        }

        public String describe(World world) {
            int dim = world.provider.getDimension();
            return "名称=" + entry.anchorName
                    + ", 区域名=" + regionName()
                    + ", 功能=" + (function().isEmpty() ? "(空)" : function())
                    + ", rot=" + (design == null ? 0 : design.rot)
                    + ", 基准=(" + entry.pos.getX() + "," + entry.pos.getY() + "," + entry.pos.getZ() + ")"
                    + ", dim=" + dim
                    + ", box=(" + fmt(box.minX) + "," + fmt(box.minY) + "," + fmt(box.minZ)
                    + ")~(" + fmt(box.maxX) + "," + fmt(box.maxY) + "," + fmt(box.maxZ) + ")";
        }

        private static String fmt(double d) {
            return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
        }
    }

    /** 玩家 UUID → 未提交选择。 */
    private static final Map<UUID, Selection> SELECTIONS = new HashMap<>();

    /** 按维度缓存两类 WorldSavedData。 */
    private static final Map<World, RegionAnchorData> ANCHOR_CACHE = new HashMap<>();
    private static final Map<World, RegionDesignData> DESIGN_CACHE = new HashMap<>();

    private RegionManager() {
    }

    // ---------- 持久化数据 ----------

    public static RegionAnchorData dataFor(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        RegionAnchorData data = ANCHOR_CACHE.get(world);
        if (data == null) {
            MapStorage storage = world.getMapStorage();
            if (storage == null) {
                return null;
            }
            data = (RegionAnchorData) storage.getOrLoadData(RegionAnchorData.class, RegionAnchorData.DATA_NAME);
            if (data == null) {
                data = new RegionAnchorData(RegionAnchorData.DATA_NAME);
                storage.setData(RegionAnchorData.DATA_NAME, data);
            }
            ANCHOR_CACHE.put(world, data);
            RegionLog.info("维度 " + world.provider.getDimension()
                    + " 基准实例数据就绪，共 " + data.getEntries().size() + " 个实例");
        }
        return data;
    }

    public static RegionDesignData designsFor(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        RegionDesignData data = DESIGN_CACHE.get(world);
        if (data == null) {
            MapStorage storage = world.getMapStorage();
            if (storage == null) {
                return null;
            }
            data = (RegionDesignData) storage.getOrLoadData(RegionDesignData.class, RegionDesignData.DATA_NAME);
            if (data == null) {
                data = new RegionDesignData(RegionDesignData.DATA_NAME);
                storage.setData(RegionDesignData.DATA_NAME, data);
            }
            DESIGN_CACHE.put(world, data);
            RegionLog.info("维度 " + world.provider.getDimension()
                    + " 基准设计数据就绪，共 " + data.getDesigns().size() + " 份设计");
        }
        return data;
    }

    // ---------- 生命周期钩子（由 BlockRegionAnchor 调用） ----------

    public static void notifyPlaced(World world, BlockPos pos) {
        RegionAnchorData data = dataFor(world);
        if (data == null) {
            return;
        }
        if (data.get(pos) != null) {
            return;
        }
        data.upsert(new RegionAnchorData.Entry(pos));
        RegionLog.info("基准方块已放置 dim=" + world.provider.getDimension()
                + " pos=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                + ") —— 流程: 空手右键起名/设功能 → 权杖选两点 → /teamytz area set <区域名>");
    }

    public static void notifyRemoved(World world, BlockPos pos) {
        RegionAnchorData data = dataFor(world);
        if (data == null) {
            return;
        }
        RegionAnchorData.Entry old = data.get(pos);
        if (old == null) {
            return;
        }
        data.remove(pos);
        RegionLog.info("基准方块已移除 dim=" + world.provider.getDimension()
                + " pos=(" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")"
                + " 名称=" + old.anchorName + "（设计保留，其他同名实例不受影响）");
    }

    // ---------- 名称/功能（写入共享设计） ----------

    /**
     * 保存基准的名称与功能（UI 保存调用）。
     * 名称是设计的键：写入后同名基准全部引用该设计。功能为空且设计已存在 = 保持设计原功能
     * （方便给新副本"挂名"时不覆盖模板功能）；否则按输入更新。
     */
    public static void applyAnchorMeta(World world, BlockPos pos, String anchorName, String function,
                                       List<SearchItem> searchItems) {
        RegionAnchorData anchors = dataFor(world);
        RegionDesignData designs = designsFor(world);
        if (anchors == null || designs == null) {
            return;
        }
        RegionAnchorData.Entry entry = anchors.get(pos);
        if (entry == null) {
            entry = new RegionAnchorData.Entry(pos);
        }
        String newName = anchorName == null ? "" : anchorName.trim();
        entry.anchorName = newName;
        anchors.upsert(entry);

        if (newName.isEmpty()) {
            if (function != null && !function.isEmpty()) {
                RegionLog.warn("基准 (" + pos.getX() + "," + pos.getY() + "," + pos.getZ()
                        + ") 未命名，无法挂载功能 '" + function + "'——请先输入名称");
            }
            return;
        }
        // 功能三态（下拉选择）：
        //   空 / __keep__ = 不修改（同名已有设计则继承模板功能+收集目标；新设计默认无功能）
        //   __clear__     = 显式清除功能
        //   其他           = 归一化成 code 存储
        String target = null; // null = 不修改
        if (function == null || function.isEmpty() || RegionFunctions.FUNCTION_KEEP.equals(function)) {
            target = null;
        } else if (RegionFunctions.FUNCTION_CLEAR.equals(function)) {
            target = "";
        } else {
            RegionFunctions.Descriptor desc = RegionFunctions.find(function);
            if (desc != null) {
                target = desc.code;
            } else {
                RegionLog.warn("未知区域功能 '" + function + "'（可选: search/guard/attack/sync）—— 忽略");
            }
        }
        boolean wantSearch = RegionFunctions.SEARCH.equals(target);
        RegionDesignData.Design design = designs.get(newName);
        if (design == null) {
            design = new RegionDesignData.Design(newName);
            design.function = target == null ? "" : target;
            if (wantSearch) {
                design.items.addAll(sanitizeItems(searchItems));
            }
            designs.upsert(design);
            RegionLog.info("基准设计已创建 名称=" + newName
                    + " 功能=" + (design.function.isEmpty() ? "(无)" : design.function)
                    + " 收集项=" + design.items.size());
        } else if (target != null) {
            design.function = target;
            design.items.clear();
            if (wantSearch) {
                design.items.addAll(sanitizeItems(searchItems));
            }
            designs.upsert(design);
            RegionLog.info("基准设计功能已更新 名称=" + newName
                    + " 功能=" + (design.function.isEmpty() ? "(无)" : design.function)
                    + " 收集项=" + design.items.size()
                    + "（将同步到所有同名实例）");
        }
        // target==null 且设计已存在：不做改动 —— 同名挂名默认继承模板功能与收集目标
    }

    /** 校验并规整搜索收集目标：id 必须能在物品注册表里找到；数量至少 1。 */
    private static List<SearchItem> sanitizeItems(List<SearchItem> raw) {
        List<SearchItem> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (SearchItem s : raw) {
            if (s == null || s.id == null) {
                continue;
            }
            String id = s.id.trim().toLowerCase(java.util.Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            ResourceLocation rl;
            try {
                rl = new ResourceLocation(id);
            } catch (Exception ex) {
                RegionLog.warn("无效物品ID '" + s.id + "' 已忽略（应为 命名空间:物品名，如 minecraft:diamond）");
                continue;
            }
            if (!ForgeRegistries.ITEMS.containsKey(rl)) {
                RegionLog.warn("物品不存在 '" + rl + "' 已忽略");
                continue;
            }
            if (out.size() >= com.teamytz.teamytzrpg.config.RegionConfig.maxSearchItems) {
                RegionLog.warn("收集目标条数已达上限 "
                        + com.teamytz.teamytzrpg.config.RegionConfig.maxSearchItems
                        + "（config: search_max_items，默认 20），多余条目已忽略");
                break;
            }
            out.add(new SearchItem(rl.toString(), s.qty < 1 ? 1 : s.qty,
                    SearchItem.normalizeMode(s.nbtMode), s.nbt));
        }
        return out;
    }

    // ---------- 权杖选点 ----------

    public static Selection selectionOf(UUID uuid) {
        return SELECTIONS.get(uuid);
    }

    public static void selectPoint1(UUID uuid, int dim, BlockPos pos) {
        Selection prev = SELECTIONS.get(uuid);
        SELECTIONS.put(uuid, new Selection(dim, pos, prev != null ? prev.b : pos));
    }

    public static void selectPoint2(UUID uuid, int dim, BlockPos pos) {
        Selection prev = SELECTIONS.get(uuid);
        SELECTIONS.put(uuid, new Selection(dim, prev != null ? prev.a : pos, pos));
    }

    public static void clearSelection(UUID uuid) {
        SELECTIONS.remove(uuid);
    }

    // ---------- 绑定（/teamytz area set 调用） ----------

    /** 把选点绑到"准星对着的基准"的设计上（按该基准的名称）。返回反馈消息。 */
    public static String bindRegion(World world, BlockPos anchorPos, UUID uuid, String regionName) {
        RegionAnchorData anchors = dataFor(world);
        RegionDesignData designs = designsFor(world);
        if (anchors == null || designs == null) {
            return "错误: 世界数据不可用";
        }
        RegionAnchorData.Entry entry = anchors.get(anchorPos);
        if (entry == null || !entry.hasName()) {
            return "该基准还没有名称：创造模式空手右键它，先输入名称（同名基准将共享同一区域设计）";
        }
        Selection sel = SELECTIONS.get(uuid);
        if (sel == null) {
            return "还没有用权杖选点: 手持区域权杖，左键点一个方块(点1)，右键点另一个(点2)";
        }
        if (sel.dim != world.provider.getDimension()) {
            return "错误: 选点所在维度与当前基准不一致";
        }
        String name = entry.anchorName;
        RegionDesignData.Design design = designs.get(name);
        if (design == null) {
            design = new RegionDesignData.Design(name);
        }
        design.regionName = regionName;
        design.local1 = sel.a.subtract(anchorPos);
        design.local2 = sel.b.subtract(anchorPos);
        design.rot = 0;
        designs.upsert(design);
        SELECTIONS.remove(uuid);

        int instances = 0;
        for (RegionAnchorData.Entry e : anchors.getEntries()) {
            if (name.equals(e.anchorName)) {
                instances++;
            }
        }
        RegionLog.info("区域设计已绑定 dim=" + world.provider.getDimension()
                + " 名称=" + name + " 区域名=" + regionName
                + " 局部角点=(" + design.local1.getX() + "," + design.local1.getY() + "," + design.local1.getZ()
                + ")~(" + design.local2.getX() + "," + design.local2.getY() + "," + design.local2.getZ() + ")"
                + " 当前同名实例数=" + instances);
        return "已绑定区域设计 '" + regionName + "' → 名称 '" + name + "'"
                + "（" + instances + " 个同名基准已同步，含本处）\n"
                + "基准 " + fmt(anchorPos) + " 的世界范围 " + boxStr(worldBoxOf(design, anchorPos));
    }

    public static void removeAnchor(World world, BlockPos pos) {
        notifyRemoved(world, pos);
    }

    // ---------- 判定 API（底层原语） ----------

    /** 位置落在哪些"有名称且其设计已绑定区域"的实例区域中。 */
    public static List<RegionMatch> matchesAt(World world, int x, int y, int z) {
        List<RegionMatch> out = new ArrayList<>();
        RegionAnchorData anchors = dataFor(world);
        RegionDesignData designs = designsFor(world);
        if (anchors == null || designs == null) {
            return out;
        }
        for (RegionAnchorData.Entry e : anchors.getEntries()) {
            if (!e.hasName()) {
                continue;
            }
            RegionDesignData.Design d = designs.get(e.anchorName);
            if (d == null || !d.isRegionBound()) {
                continue;
            }
            if (containsWorld(d, e.pos, x, y, z)) {
                out.add(new RegionMatch(e, d, worldBoxOf(d, e.pos)));
            }
        }
        return out;
    }

    /** 是否位于指定区域名（regionName）的任意实例内。 */
    public static boolean inRegion(World world, int x, int y, int z, String regionName) {
        for (RegionMatch m : matchesAt(world, x, y, z)) {
            if (m.regionName().equals(regionName)) {
                return true;
            }
        }
        return false;
    }

    /** 是否位于指定名称（模板键）的任意实例内。 */
    public static boolean inAnchorGroup(World world, int x, int y, int z, String anchorName) {
        for (RegionMatch m : matchesAt(world, x, y, z)) {
            if (m.entry.anchorName.equals(anchorName)) {
                return true;
            }
        }
        return false;
    }

    // ---------- 几何（设计局部角点 + 旋转 ↔ 世界） ----------

    private static int[] rotate(int x, int z, int turns) {
        turns = ((turns % 4) + 4) % 4;
        int lx = x, lz = z;
        for (int i = 0; i < turns; i++) {
            int tmp = lx;
            lx = -lz;
            lz = tmp;
        }
        return new int[]{lx, lz};
    }

    private static int[] unrotate(int x, int z, int rot) {
        return rotate(x, z, (4 - ((rot % 4) + 4) % 4) % 4);
    }

    /** 世界坐标是否落在"该设计 + 该基准位置"区域内。 */
    public static boolean containsWorld(RegionDesignData.Design d, BlockPos anchorPos, int x, int y, int z) {
        if (d == null || !d.isRegionBound()) {
            return false;
        }
        int dx = x - anchorPos.getX();
        int dy = y - anchorPos.getY();
        int dz = z - anchorPos.getZ();
        int[] local = unrotate(dx, dz, d.rot);
        int minX = Math.min(d.local1.getX(), d.local2.getX());
        int maxX = Math.max(d.local1.getX(), d.local2.getX());
        int minY = Math.min(d.local1.getY(), d.local2.getY());
        int maxY = Math.max(d.local1.getY(), d.local2.getY());
        int minZ = Math.min(d.local1.getZ(), d.local2.getZ());
        int maxZ = Math.max(d.local1.getZ(), d.local2.getZ());
        return local[0] >= minX && local[0] <= maxX
                && dy >= minY && dy <= maxY
                && local[1] >= minZ && local[1] <= maxZ;
    }

    /** 该设计落在指定基准位置时的真实世界包围盒。 */
    public static AxisAlignedBB worldBoxOf(RegionDesignData.Design d, BlockPos anchorPos) {
        if (d == null || !d.isRegionBound()) {
            return new AxisAlignedBB(anchorPos, anchorPos);
        }
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int lx : new int[]{d.local1.getX(), d.local2.getX()}) {
            for (int lz : new int[]{d.local1.getZ(), d.local2.getZ()}) {
                int[] w = rotate(lx, lz, d.rot);
                minX = Math.min(minX, anchorPos.getX() + w[0]);
                maxX = Math.max(maxX, anchorPos.getX() + w[0]);
                minZ = Math.min(minZ, anchorPos.getZ() + w[1]);
                maxZ = Math.max(maxZ, anchorPos.getZ() + w[1]);
            }
        }
        minY = anchorPos.getY() + Math.min(d.local1.getY(), d.local2.getY());
        maxY = anchorPos.getY() + Math.max(d.local1.getY(), d.local2.getY());
        return new AxisAlignedBB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    // ---------- 工具 ----------

    /** 玩家视线 6 格内是否对着基准方块；是则返回其坐标。 */
    public static BlockPos lookedAnchor(EntityPlayer player) {
        World world = player.getEntityWorld();
        Vec3d eye = player.getPositionEyes(1.0F);
        Vec3d look = player.getLook(1.0F);
        Vec3d end = eye.addVector(look.x * 6.0D, look.y * 6.0D, look.z * 6.0D);
        RayTraceResult hit = world.rayTraceBlocks(eye, end);
        if (hit == null || hit.getBlockPos() == null) {
            return null;
        }
        BlockPos pos = hit.getBlockPos();
        return world.getBlockState(pos).getBlock() == ModBlocks.REGION_ANCHOR ? pos : null;
    }

    /** 周期自愈：基准方块已消失（且区块已加载）的记录清理掉（设计保留）。 */
    public static void prune(World world) {
        RegionAnchorData data = dataFor(world);
        if (data == null) {
            return;
        }
        List<RegionAnchorData.Entry> stale = new ArrayList<>();
        for (RegionAnchorData.Entry e : data.getEntries()) {
            if (world.isBlockLoaded(e.pos)
                    && world.getBlockState(e.pos).getBlock() != ModBlocks.REGION_ANCHOR) {
                stale.add(e);
            }
        }
        for (RegionAnchorData.Entry e : stale) {
            data.remove(e.pos);
            RegionLog.warn("基准方块失效自愈: dim=" + world.provider.getDimension()
                    + " pos=(" + e.pos.getX() + "," + e.pos.getY() + "," + e.pos.getZ() + ")"
                    + " 名称=" + e.anchorName + "（记录已清除，设计保留）");
        }
    }

    private static String fmt(BlockPos p) {
        return "(" + p.getX() + "," + p.getY() + "," + p.getZ() + ")";
    }

    private static String boxStr(AxisAlignedBB box) {
        return "(" + (long) box.minX + "," + (long) box.minY + "," + (long) box.minZ + ")~("
                + (long) box.maxX + "," + (long) box.maxY + "," + (long) box.maxZ + ")";
    }
}
