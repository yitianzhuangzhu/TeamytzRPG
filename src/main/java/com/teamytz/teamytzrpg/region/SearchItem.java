package com.teamytz.teamytzrpg.region;

import net.minecraft.nbt.NBTTagCompound;

/**
 * 搜索区域的一条收集目标。
 * UI 每行 = 物品ID + 数量（+ 可选"从背包/物品列表选取"得到的 NBT 与 NBT 匹配模式）。
 */
public final class SearchItem {

    /** NBT 匹配模式（对齐 FTBQuests/ItemFilters 的 NBTMatchingMode 语义）。 */
    public static final char MODE_MATCH = 'M';   // 整个 NBT 必须完全相等
    public static final char MODE_CONTAIN = 'C'; // 实物 NBT 需包含本条记录的标签(子集)
    public static final char MODE_IGNORE = 'I';  // 不看 NBT，仅按物品 id

    /** 物品注册 id，如 minecraft:diamond。 */
    public String id = "";
    /** 需要收集的数量。 */
    public int qty = 1;
    /** NBT 匹配模式，默认 'M'。 */
    public char nbtMode = MODE_MATCH;
    /** 携带的 NBT（null = 仅按 id 匹配）。 */
    public NBTTagCompound nbt;

    public SearchItem() {
    }

    public SearchItem(String id, int qty) {
        this.id = id == null ? "" : id;
        this.qty = qty < 1 ? 1 : qty;
    }

    public SearchItem(String id, int qty, char mode, NBTTagCompound nbt) {
        this(id, qty);
        this.nbtMode = normalizeMode(mode);
        this.nbt = nbt;
    }

    public static char normalizeMode(char c) {
        return (c == MODE_CONTAIN || c == MODE_IGNORE) ? c : MODE_MATCH;
    }
}
