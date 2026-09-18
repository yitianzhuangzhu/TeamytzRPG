package com.teamytz.teamytzrpg.region;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 区域功能注册表：UI 下拉菜单的候选即来自这里（显示名 = display）。
 *
 * <p>功能码（code）为稳定英文小写，落库用 code；玩家选择在 GUI 完成。</p>
 *
 * <p>各功能顶部条的颜色由 config（bars 分组，bar_color_&lt;code&gt;）配置：
 * 填 NONE 表示该功能不显示顶部条；默认 search=绿(GREEN)、sync=蓝(BLUE)、
 * guard/attack=无(NONE)。</p>
 */
public final class RegionFunctions {

    public static final String SEARCH = "search";
    public static final String GUARD = "guard";
    public static final String ATTACK = "attack";
    public static final String SYNC = "sync";

    /** 下拉菜单内部指令：不修改功能（同名挂名时继承模板已有功能）。 */
    public static final String FUNCTION_KEEP = "__keep__";
    /** 下拉菜单内部指令：显式清除功能。 */
    public static final String FUNCTION_CLEAR = "__clear__";

    /** 单个功能的描述。 */
    public static final class Descriptor {
        /** 稳定代码（落库值）。 */
        public final String code;
        /** UI 显示名。 */
        public final String display;
        private final String[] aliases;

        private Descriptor(String code, String display, String... aliases) {
            this.code = code;
            this.display = display;
            this.aliases = aliases;
        }

        public boolean matches(String value) {
            if (value == null) {
                return false;
            }
            String v = value.trim();
            for (String a : aliases) {
                if (a.equalsIgnoreCase(v)) {
                    return true;
                }
            }
            return false;
        }
    }

    private static final List<Descriptor> ALL = new ArrayList<>();

    static {
        ALL.add(new Descriptor(SEARCH, "搜索区域", SEARCH, "搜索", "搜索区域"));
        ALL.add(new Descriptor(GUARD, "警戒区域", GUARD, "警戒", "警戒区域"));
        ALL.add(new Descriptor(ATTACK, "攻击区域", ATTACK, "攻击", "攻击区域"));
        ALL.add(new Descriptor(SYNC, "同步区域", SYNC, "同步", "同步区域"));
    }

    private RegionFunctions() {
    }

    /** 按输入值（code / 显示名 / 别名）查功能；不认识返回 null。 */
    public static Descriptor find(String function) {
        if (function == null) {
            return null;
        }
        for (Descriptor d : ALL) {
            if (d.matches(function)) {
                return d;
            }
        }
        return null;
    }

    public static List<Descriptor> all() {
        return Collections.unmodifiableList(ALL);
    }
}
