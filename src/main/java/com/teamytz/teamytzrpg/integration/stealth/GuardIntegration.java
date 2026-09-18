package com.teamytz.teamytzrpg.integration.stealth;

import com.fantasticsource.dynamicstealth.server.ai.AIDynamicStealth;
import com.fantasticsource.dynamicstealth.server.event.ApplyThreatEvent;
import com.fantasticsource.dynamicstealth.server.threat.Threat;
import com.teamytz.teamytzrpg.config.RegionConfig;
import com.teamytz.teamytzrpg.region.RegionAnchorData;
import com.teamytz.teamytzrpg.region.RegionDesignData;
import com.teamytz.teamytzrpg.region.RegionFunctions;
import com.teamytz.teamytzrpg.region.RegionLog;
import com.teamytz.teamytzrpg.region.RegionManager;

import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 警戒区域 × Dynamic Stealth 联动（软依赖，dynamicstealth 在场才加载）。
 *
 * <p>思路（基于源码研究）：
 * <ul>
 *   <li>Dynamic Stealth 的每次威胁变动都会先广播可取消的 {@link ApplyThreatEvent}
 *       （searcher=提升威胁的实体，target=对象，type=威胁类型，含 damageSource）。</li>
 *   <li>其中 GEN_TARGET_SPOTTED / GEN_TARGET_VISIBLE 是"视线索敌"类；GEN_ATTACKED*、
 *       GEN_DAMAGE_DEALT、GEN_WARNED、GEN_ALLY_KILLED 等是受击/告警类（高优先级，不应拦截）。</li>
 *   <li>警戒区域内的实体被登记为"守卫"（归属某一警戒实例）。守卫只有在
 *       <b>自身与目标玩家同处该警戒区域内</b>时才允许视线索敌/提升威胁度；
 *       守卫离开区域后的视线索敌被拦截（被攻击等高优先类型自动放行）。</li>
 *   <li>越界拉回：周期把已登记的守卫实体导航回其警戒区域（最近内点）。</li>
 * </ul>
 */
public final class GuardIntegration {

    /** 守卫归属记录：实体 UUID → 它最初归属的警戒实例(基准+设计)。 */
    private static final Map<UUID, Origin> ORIGINS = new HashMap<>();

    private static boolean inited = false;
    private static int tick = 0;

    private static final class Origin {
        final BlockPos anchor;
        final RegionDesignData.Design design;

        Origin(BlockPos anchor, RegionDesignData.Design design) {
            this.anchor = anchor;
            this.design = design;
        }
    }

    private GuardIntegration() {
    }

    /** 主类在 dynamicstealth 加载时调用；缺席时不加载本类。 */
    public static void init() {
        if (inited || !Loader.isModLoaded("dynamicstealth")) {
            return;
        }
        inited = true;
        MinecraftForge.EVENT_BUS.register(Handler.class);
        RegionLog.info("Dynamic Stealth 联动已启用（警戒区域：威胁门控 + 越界拉回）");
    }

    // ================= 威胁门控 =================

    private static void registerOriginIfGuard(EntityLivingBase searcher, World world) {
        UUID id = searcher.getUniqueID();
        if (ORIGINS.containsKey(id)) {
            return;
        }
        List<RegionManager.RegionMatch> ms = RegionManager.matchesAt(world,
                (int) Math.floor(searcher.posX),
                (int) Math.floor(searcher.posY),
                (int) Math.floor(searcher.posZ));
        for (RegionManager.RegionMatch m : ms) {
            if (m.design != null && RegionFunctions.GUARD.equals(m.design.function)) {
                ORIGINS.put(id, new Origin(m.entry.pos, m.design));
                break;
            }
        }
    }

    private static boolean inside(Origin o, EntityLivingBase e) {
        return RegionManager.containsWorld(o.design, o.anchor,
                (int) Math.floor(e.posX), (int) Math.floor(e.posY), (int) Math.floor(e.posZ));
    }

    /** 事件订阅类（静态方法）。 */
    public static class Handler {
        @SubscribeEvent
        public static void onThreatApply(ApplyThreatEvent event) {
            if (!RegionConfig.guardGateEnabled) {
                return;
            }
            EntityLivingBase searcher = event.searcher;
            if (searcher == null || event.target == null || !(event.target instanceof EntityPlayer)) {
                return;
            }
            World world = searcher.getEntityWorld();
            if (world == null || world.isRemote) {
                return;
            }
            // 只拦截"视线索敌"型；受击/告警/逃跑等类型是高优先级，放行
            Threat.THREAT_TYPE type = event.type;
            if (type != Threat.THREAT_TYPE.GEN_TARGET_SPOTTED
                    && type != Threat.THREAT_TYPE.GEN_TARGET_VISIBLE) {
                return;
            }
            registerOriginIfGuard(searcher, world);
            Origin o = ORIGINS.get(searcher.getUniqueID());
            if (o == null) {
                return; // 非警戒守卫：不干预世界正常行为
            }
            boolean searcherIn = inside(o, searcher);
            boolean targetIn = inside(o, event.target);
            if (searcherIn && targetIn) {
                return; // 双方同在区域内：正常视线索敌
            }
            // 已被锁定(枪声警觉 GEN_WARNED / 受击等更高优先触发已 Threat.set 过目标) → 放行维持，
            // 避免与外部触发互相覆盖/掐断追击。仅拦"闲时/新锁敌"。
            if (Threat.getTarget(searcher) == event.target) {
                return;
            }
            event.setCanceled(true);
        }

        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) {
                return;
            }
            MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
            if (server == null) {
                return;
            }
            tick++;
            if (tick % RegionConfig.guardScanInterval == 0) {
                scanAndRegister(server);
            }
            if (tick % RegionConfig.guardLeashInterval == 0) {
                leashAndPrune(server);
            }
        }
    }

    // ================= 守卫注册（玩家附近扫描） =================

    private static void scanAndRegister(MinecraftServer server) {
        for (World world : server.worlds) {
            if (world == null || world.playerEntities.isEmpty()) {
                continue;
            }
            RegionAnchorData anchors = RegionManager.dataFor(world);
            RegionDesignData designs = RegionManager.designsFor(world);
            if (anchors == null || designs == null) {
                continue;
            }
            double range = RegionConfig.guardScanRange;
            for (RegionAnchorData.Entry e : anchors.getEntries()) {
                if (!e.hasName()) {
                    continue;
                }
                RegionDesignData.Design d = designs.get(e.anchorName);
                if (d == null || !RegionFunctions.GUARD.equals(d.function)) {
                    continue;
                }
                AxisAlignedBB box = RegionManager.worldBoxOf(d, e.pos);
                boolean near = false;
                for (Object o : world.playerEntities) {
                    EntityPlayer p = (EntityPlayer) o;
                    if (box.grow(range, range, range).contains(new Vec3d(p.posX, p.posY, p.posZ))) {
                        near = true;
                        break;
                    }
                }
                if (!near || !world.isBlockLoaded(e.pos)) {
                    continue;
                }
                List<EntityLivingBase> candidates =
                        world.getEntitiesWithinAABB(EntityLivingBase.class, box);
                for (EntityLivingBase cand : candidates) {
                    if (cand instanceof EntityPlayer || !(cand instanceof EntityLiving)
                            || ORIGINS.containsKey(cand.getUniqueID())) {
                        continue;
                    }
                    ORIGINS.put(cand.getUniqueID(), new Origin(e.pos, d));
                }
            }
        }
    }

    // ================= 越界拉回 =================

    private static void leashAndPrune(MinecraftServer server) {
        if (ORIGINS.isEmpty()) {
            return;
        }
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Origin> me : ORIGINS.entrySet()) {
            UUID id = me.getKey();
            Origin o = me.getValue();
            boolean found = false;
            for (World world : server.worlds) {
                if (world == null) {
                    continue;
                }
                net.minecraft.entity.Entity entBase = world instanceof WorldServer
                        ? ((WorldServer) world).getEntityFromUuid(id) : null;
                EntityLivingBase ent = (entBase instanceof EntityLivingBase)
                        ? (EntityLivingBase) entBase : null;
                if (ent == null || ent.isDead) {
                    continue;
                }
                found = true;
                if (RegionConfig.guardLeashEnabled && ent instanceof EntityLiving) {
                    EntityLiving el = (EntityLiving) ent;
                    if (!el.isRiding()) {
                        AxisAlignedBB box = RegionManager.worldBoxOf(o.design, o.anchor);
                        // 只在"冷静"时才允许返回：
                        // 威胁值=0 且 无锁定目标 且 非逃跑/溃逃 —— 战斗/循声调查/警戒中(威胁>0)
                        // 一律不拉回；溃逃期间无视返回，待冷静后才回区。
                        boolean fleeing = false;
                        AIDynamicStealth ai = AIDynamicStealth.getStealthAI(el);
                        if (ai != null) {
                            fleeing = ai.isFleeing();
                        }
                        boolean calm = Threat.getThreat(el) <= 0.0F
                                && Threat.getTarget(el) == null && !fleeing;
                        if (calm) {
                            double margin = RegionConfig.guardLeashMargin;
                            if (!box.grow(margin, margin, margin)
                                    .contains(new Vec3d(el.posX, el.posY, el.posZ))) {
                                int nx = clamp((int) Math.floor(el.posX),
                                        (int) Math.floor(box.minX), (int) Math.ceil(box.maxX) - 1);
                                int ny = clamp((int) Math.floor(el.posY),
                                        (int) Math.floor(box.minY), (int) Math.ceil(box.maxY) - 1);
                                int nz = clamp((int) Math.floor(el.posZ),
                                        (int) Math.floor(box.minZ), (int) Math.ceil(box.maxZ) - 1);
                                el.getNavigator().tryMoveToXYZ(nx + 0.5D, ny, nz + 0.5D, 1.0D);
                            }
                        }
                    }
                }
                break;
            }
            if (!found) {
                gone.add(id); // 实体不在任何已加载世界：清记录（重载后按需重注册）
            }
        }
        for (UUID id : gone) {
            ORIGINS.remove(id);
        }
    }

    private static int clamp(int v, int min, int max) {
        return v < min ? min : (v > max ? max : v);
    }
}
