package com.teamytz.teamytzrpg.region;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;

import java.util.ArrayList;
import java.util.List;

/** NBT 比较工具（服务端使用）。 */
public final class NbtCodec {

    private NbtCodec() {
    }

    /**
     * required 是否为 actual 的"子集"（actual 需包含 required 里的每一个键/值）。
     * 用于 NBT 匹配模式的 CONTAIN 档（对齐 FTBQuests/ItemFilters 的 CONTAIN 语义）。
     */
    public static boolean contains(NBTTagCompound required, NBTTagCompound actual) {
        if (required == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        for (String key : required.getKeySet()) {
            if (!actual.hasKey(key)) {
                return false;
            }
            if (!deepContains(required.getTag(key), actual.getTag(key))) {
                return false;
            }
        }
        return true;
    }

    private static boolean deepContains(NBTBase required, NBTBase actual) {
        if (required == null) {
            return true;
        }
        if (actual == null) {
            return false;
        }
        if (required.equals(actual)) {
            return true;
        }
        if (required instanceof NBTTagCompound && actual instanceof NBTTagCompound) {
            return contains((NBTTagCompound) required, (NBTTagCompound) actual);
        }
        if (required instanceof NBTTagList && actual instanceof NBTTagList) {
            return listContains((NBTTagList) required, (NBTTagList) actual);
        }
        return false;
    }

    /** required 列表的每一项都必须在 actual 里能找到相等的元素（多重集子集）。 */
    private static boolean listContains(NBTTagList required, NBTTagList actual) {
        if (required.tagCount() > actual.tagCount()) {
            return false;
        }
        List<NBTBase> pool = new ArrayList<>();
        for (int i = 0; i < actual.tagCount(); i++) {
            pool.add(actual.get(i));
        }
        for (int i = 0; i < required.tagCount(); i++) {
            NBTBase want = required.get(i);
            boolean found = false;
            for (int j = 0; j < pool.size(); j++) {
                if (deepContains(want, pool.get(j))) {
                    pool.remove(j);
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }
}
