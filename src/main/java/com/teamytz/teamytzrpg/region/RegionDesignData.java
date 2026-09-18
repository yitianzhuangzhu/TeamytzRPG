package com.teamytz.teamytzrpg.region;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.storage.WorldSavedData;

import java.util.ArrayList;
import java.util.List;

/**
 * 基准"设计"数据（每维度一份，WorldSavedData，服务器权威）。
 *
 * <p>按名称（anchorName）索引的共享设计：一组同名基准方块实例共用一份设计——
 * 区域形状 / 区域名 / 功能 / 旋转 / 搜索收集目标（search 功能时）。
 * 任意一处编辑都会同步到所有同名实例（结构复制场景）。</p>
 */
public class RegionDesignData extends WorldSavedData {

    public static final String DATA_NAME = "teamytzrpg_designs";

    private final List<Design> designs = new ArrayList<>();

    public RegionDesignData() {
        super(DATA_NAME);
    }

    public RegionDesignData(String name) {
        super(name);
    }

    public List<Design> getDesigns() {
        return designs;
    }

    /** 一份共享设计。 */
    public static class Design {
        public String anchorName = "";
        public String regionName = "";
        public String function = "";
        public int rot = 0;
        /** 两个对角点的局部坐标（相对基准方块；未绑定时为 0,0,0..0,0,0）。 */
        public BlockPos local1 = BlockPos.ORIGIN;
        public BlockPos local2 = BlockPos.ORIGIN;
        /** 搜索收集目标列表（仅 search 功能有意义）。 */
        public final List<SearchItem> items = new ArrayList<>();

        public Design(String anchorName) {
            this.anchorName = anchorName;
        }

        public boolean isRegionBound() {
            return regionName != null && !regionName.isEmpty();
        }

        public boolean isSearch() {
            return RegionFunctions.SEARCH.equals(function);
        }
    }

    public Design get(String anchorName) {
        if (anchorName == null || anchorName.isEmpty()) {
            return null;
        }
        for (Design d : designs) {
            if (d.anchorName.equals(anchorName)) {
                return d;
            }
        }
        return null;
    }

    public void upsert(Design design) {
        Design old = get(design.anchorName);
        if (old != null) {
            designs.remove(old);
        }
        designs.add(design);
        markDirty();
    }

    public boolean remove(String anchorName) {
        Design old = get(anchorName);
        if (old != null) {
            designs.remove(old);
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        designs.clear();
        NBTTagList list = nbt.getTagList("designs", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound tag = list.getCompoundTagAt(i);
            Design d = new Design(tag.getString("name"));
            d.regionName = tag.getString("regionName");
            d.function = tag.getString("function");
            d.rot = tag.getInteger("rot");
            d.local1 = new BlockPos(tag.getInteger("l1x"), tag.getInteger("l1y"), tag.getInteger("l1z"));
            d.local2 = new BlockPos(tag.getInteger("l2x"), tag.getInteger("l2y"), tag.getInteger("l2z"));
            NBTTagList items = tag.getTagList("searchItems", 10);
            for (int j = 0; j < items.tagCount(); j++) {
                NBTTagCompound it = items.getCompoundTagAt(j);
                SearchItem si = new SearchItem(it.getString("id"), it.getInteger("qty"),
                        it.hasKey("nbtMode") ? it.getString("nbtMode").charAt(0) : SearchItem.MODE_MATCH,
                        null);
                if (it.hasKey("nbt")) {
                    si.nbt = it.getCompoundTag("nbt");
                }
                d.items.add(si);
            }
            designs.add(d);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        NBTTagList list = new NBTTagList();
        for (Design d : designs) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("name", d.anchorName);
            tag.setString("regionName", d.regionName);
            tag.setString("function", d.function);
            tag.setInteger("rot", d.rot);
            tag.setInteger("l1x", d.local1.getX());
            tag.setInteger("l1y", d.local1.getY());
            tag.setInteger("l1z", d.local1.getZ());
            tag.setInteger("l2x", d.local2.getX());
            tag.setInteger("l2y", d.local2.getY());
            tag.setInteger("l2z", d.local2.getZ());
            NBTTagList items = new NBTTagList();
            for (SearchItem s : d.items) {
                NBTTagCompound it = new NBTTagCompound();
                it.setString("id", s.id);
                it.setInteger("qty", s.qty);
                it.setString("nbtMode", String.valueOf(s.nbtMode));
                if (s.nbt != null) {
                    it.setTag("nbt", s.nbt);
                }
                items.appendTag(it);
            }
            tag.setTag("searchItems", items);
            list.appendTag(tag);
        }
        nbt.setTag("designs", list);
        return nbt;
    }
}
