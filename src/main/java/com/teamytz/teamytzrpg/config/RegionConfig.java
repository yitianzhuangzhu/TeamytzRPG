package com.teamytz.teamytzrpg.config;

import net.minecraft.world.BossInfo;
import net.minecraftforge.common.config.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * mod 配置（config/teamytzrpg.cfg，Forge Configuration，1.12 时代写法）。
 * 客户端与服务端各自读取（单机整合环境同一份文件）。
 */
public final class RegionConfig {

    /** 单个基准(共享设计)最多可写入的搜索收集目标条数。 */
    public static int maxSearchItems = 20;

    /**
     * 防刷：玩家自己丢出(drop/投掷)的物品再被捡起时是否计入收集进度。
     * false(默认) = 不计 —— 阻止"丢一颗钻石再捡 = 收集1次"的刷法；
     * true = 与旧版一致，任何拾取都计。
     */
    public static boolean countTossedPickup = false;

    /** 丢出物品的"去重标记"最长保留时长(毫秒)，超时自动清理。 */
    public static long tossedMarkTtl = 10L * 60L * 1000L;

    // ================= HUD（搜索收集信息栏）显示设置 =================

    /** 是否显示右上角收集信息栏。 */
    public static boolean hudEnabled = true;
    /** 字体缩放倍数(0.5~3.0，1.0=原版字号)。 */
    public static double hudFontScale = 1.0D;
    /** 正文颜色(十进制 RGB，如 16777215=白)。 */
    public static int hudColorText = 0xFFFFFF;
    /** 标题"收集物品"颜色。 */
    public static int hudColorHeader = 0xFFFF55;
    /** 达成(√)行的颜色。 */
    public static int hudColorDone = 0x55FF55;
    /** 背景黑条透明度(0=不画背景, 1~180=alpha)。 */
    public static int hudBgAlpha = 0;
    /** 距离屏幕右侧边距(像素)。 */
    public static int hudMarginRight = 6;
    /** 距离屏幕顶部边距(像素)。 */
    public static int hudMarginTop = 4;

    // ================= 功能顶部条颜色（bars 分组） =================

    /** code → 颜色名(枚举字符串或 NONE)。默认: search=绿, sync=蓝, guard/attack=不显示。 */
    private static final Map<String, String> BAR_COLOR_NAMES = new HashMap<>();

    // ================= 警戒区域（Dynamic Stealth 联动） =================

    /** 警戒门控开关。 */
    public static boolean guardGateEnabled = true;
    /** 越界拉回守卫开关。 */
    public static boolean guardLeashEnabled = true;
    /** 守卫注册扫描间隔(tick)。 */
    public static int guardScanInterval = 10;
    /** 越界拉回判定间隔(tick)。 */
    public static int guardLeashInterval = 20;
    /** 玩家附近扫描半径(方块)。 */
    public static int guardScanRange = 96;
    /** 越界多少格才开始拉回。 */
    public static int guardLeashMargin = 6;

    private static Configuration cfg;

    private RegionConfig() {
    }

    /** preInit 时调用（入参用 FMLPreInitializationEvent.getSuggestedConfigurationFile()）。 */
    public static void init(File suggestedFile) {
        cfg = new Configuration(suggestedFile);
        load();
    }

    public static void load() {
        maxSearchItems = cfg.getInt(
                "search_max_items",
                Configuration.CATEGORY_GENERAL,
                20,
                1,
                512,
                "单个基准方块(共享设计)可写入的搜索收集目标最大条数");
        countTossedPickup = cfg.getBoolean(
                "search_count_tossed_pickup",
                Configuration.CATEGORY_GENERAL,
                false,
                "玩家自己丢出(drop/投掷)的物品再被捡起时是否计入收集进度。false=不计（防止丢出再捡刷进度，推荐），true=任何拾取都计(旧行为)");
        tossedMarkTtl = cfg.getInt(
                "search_tossed_mark_ttl_ms",
                Configuration.CATEGORY_GENERAL,
                600000,
                1000,
                3600000,
                "丢出标记保留时长(毫秒)，用于拦截丢出再捡；超时自动清理");

        String cat = "hud";
        hudEnabled = cfg.getBoolean("hud_enabled", cat, true, "是否显示搜索收集信息栏(HUD)");
        hudFontScale = cfg.getFloat("hud_font_scale", cat, 1.0F, 0.5F, 3.0F, "字体缩放倍数(1.0=原版字号)");
        hudColorText = cfg.getInt("hud_color_text", cat, 0xFFFFFF, 0, 0xFFFFFF,
                "正文颜色(RGB 十进制，如 16777215=白)");
        hudColorHeader = cfg.getInt("hud_color_header", cat, 0xFFFF55, 0, 0xFFFFFF,
                "标题颜色(RGB 十进制)");
        hudColorDone = cfg.getInt("hud_color_done", cat, 0x55FF55, 0, 0xFFFFFF,
                "达成(√)行颜色(RGB 十进制)");
        hudBgAlpha = cfg.getInt("hud_bg_alpha", cat, 0, 0, 180,
                "背景黑条透明度(0=不画背景, 1~180=半透明黑)");
        hudMarginRight = cfg.getInt("hud_margin_right", cat, 6, 0, 200, "HUD 距屏幕右侧边距(像素)");
        hudMarginTop = cfg.getInt("hud_margin_top", cat, 4, 0, 200, "HUD 距屏幕顶部边距(像素)");

        // 功能顶部条颜色：NONE=不显示；可选 PINK/BLUE/RED/GREEN/YELLOW/PURPLE/WHITE
        loadBarColor("search", "GREEN", "搜索区域(绿)");
        loadBarColor("guard", "NONE", "警戒区域(默认不显示)");
        loadBarColor("attack", "NONE", "攻击区域(默认不显示)");
        loadBarColor("sync", "BLUE", "同步区域(蓝)");

        // ================= 警戒区域（Dynamic Stealth 联动） =================
        String gcat = "guard";
        guardGateEnabled = cfg.getBoolean("guard_gate_enabled", gcat, true,
                "警戒门控：警戒区域的实体只在\"它与玩家同处该警戒区域内\"时才允许视线索敌/提升威胁度；"
                        + "离开区域则拦截（被攻击/逃跑等高优先类型不受影响）");
        guardLeashEnabled = cfg.getBoolean("guard_leash_enabled", gcat, true,
                "把注册为警戒守卫的实体拉回警戒区域（防其主动走丢/被引出后不归位）");
        guardScanInterval = cfg.getInt("guard_scan_interval", gcat, 10, 1, 200,
                "警戒守卫注册扫描间隔(tick)：扫描玩家附近的警戒区域，把区域内怪物登记为守卫");
        guardLeashInterval = cfg.getInt("guard_leash_interval", gcat, 20, 1, 200,
                "越界拉回判定间隔(tick)");
        guardScanRange = cfg.getInt("guard_scan_range", gcat, 96, 8, 512,
                "扫描玩家附近警戒区域的半径(方块)");
        guardLeashMargin = cfg.getInt("guard_leash_margin", gcat, 6, 0, 64,
                "守卫越界多少格(超出区域边界)才开始拉回；战斗中/逃跑中/正在循声调查的不拉（兼容枪声警觉等外部触发）");

        if (cfg.hasChanged()) {
            cfg.save();
        }
    }

    private static void loadBarColor(String code, String defaultName, String comment) {
        String name = cfg.getString("bar_color_" + code, "bars", defaultName, comment);
        BAR_COLOR_NAMES.put(code, name.trim().toUpperCase());
    }

    /** 返回某功能顶部条颜色；NONE/无效 → null（不显示条）。 */
    public static BossInfo.Color barColor(String code) {
        String name = BAR_COLOR_NAMES.get(code);
        if (name == null || name.equals("NONE")) {
            return null;
        }
        try {
            return BossInfo.Color.valueOf(name);
        } catch (Exception e) {
            return null;
        }
    }
}
