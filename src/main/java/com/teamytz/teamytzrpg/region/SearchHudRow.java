package com.teamytz.teamytzrpg.region;

import net.minecraft.nbt.NBTTagCompound;

/** HUD 里一行收集目标的显示数据。 */
public final class SearchHudRow {

    public final String id;
    /** 需要数量。 */
    public final int need;
    /** 是否已达成。 */
    public final boolean done;
    /** 当前已收集数量（显示用，如 钻石×5 (4)）。 */
    public final int have;
    /** 目标携带的 NBT（显示用：成书类按 NBT 里的书名显示）。 */
    public final NBTTagCompound nbt;

    public SearchHudRow(String id, int need, boolean done, int have, NBTTagCompound nbt) {
        this.id = id;
        this.need = need;
        this.done = done;
        this.have = have;
        this.nbt = nbt;
    }
}
