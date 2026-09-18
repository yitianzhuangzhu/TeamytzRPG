package com.teamytz.teamytzrpg.region;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 基准方块实例数据（每维度一份，WorldSavedData，服务器权威）。
 *
 * <p>实例只保存：世界坐标（实例身份）+ 名称（跨实例共享设计的键）。
 * 区域形状 / 功能 / 旋转全部存于按名称索引的 {@link RegionDesignData}：
 * 同名基准方块引用同一份设计，并在各自位置实例化出各自的世界区域。</p>
 */
public class RegionAnchorData extends WorldSavedData {

    public static final String DATA_NAME = "teamytzrpg_regions";

    private final List<Entry> entries = new ArrayList<>();

    public RegionAnchorData() {
        super(DATA_NAME);
    }

    public RegionAnchorData(String name) {
        super(name);
    }

    public List<Entry> getEntries() {
        return entries;
    }

    /** 基准方块实例：世界坐标 + 名称。 */
    public static class Entry {
        public BlockPos pos;
        /** 名称（设计键）；留空 = 未命名实例，无设计引用。 */
        public String anchorName = "";

        public Entry(BlockPos pos) {
            this.pos = pos;
        }

        public boolean hasName() {
            return anchorName != null && !anchorName.isEmpty();
        }
    }

    public Entry get(BlockPos pos) {
        for (Entry e : entries) {
            if (e.pos.equals(pos)) {
                return e;
            }
        }
        return null;
    }

    public void upsert(Entry entry) {
        Entry old = get(entry.pos);
        if (old != null) {
            entries.remove(old);
        }
        entries.add(entry);
        markDirty();
    }

    public boolean remove(BlockPos pos) {
        Entry old = get(pos);
        if (old != null) {
            entries.remove(old);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        entries.clear();
        NBTTagList list = nbt.getTagList("anchors", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Entry e = new Entry(new BlockPos(tag.getInteger("x"), tag.getInteger("y"), tag.getInteger("z")));
            e.anchorName = tag.getString("anchorName");
            // 0.1.x 旧档：把旧的 group/__unset__ 字段兜底（不迁移区域数据，仅保留位置+名字）
            if (e.anchorName.isEmpty() && tag.hasKey("group")) {
                String old = tag.getString("group");
                e.anchorName = "__unset__".equals(old) ? "" : old;
            }
            entries.add(e);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Entry e : entries) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setInteger("x", e.pos.getX());
            tag.setInteger("y", e.pos.getY());
            tag.setInteger("z", e.pos.getZ());
            tag.setString("anchorName", e.anchorName == null ? "" : e.anchorName);
            list.appendTag(tag);
        }
        nbt.setTag("anchors", list);
        return nbt;
    }
}
