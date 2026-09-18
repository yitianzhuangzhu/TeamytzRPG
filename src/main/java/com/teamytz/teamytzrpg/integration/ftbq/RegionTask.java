package com.teamytz.teamytzrpg.integration.ftbq;

import com.feed_the_beast.ftblib.lib.config.ConfigGroup;
import com.feed_the_beast.ftblib.lib.io.DataIn;
import com.feed_the_beast.ftblib.lib.io.DataOut;
import com.feed_the_beast.ftbquests.quest.Quest;
import com.feed_the_beast.ftbquests.quest.QuestData;
import com.feed_the_beast.ftbquests.quest.task.BooleanTaskData;
import com.feed_the_beast.ftbquests.quest.task.Task;
import com.feed_the_beast.ftbquests.quest.task.TaskData;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.teamytz.teamytzrpg.region.RegionSearchTracker;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * FTB 任务类型：区域任务。
 * 填写"基准方块名字(模板名)"——判定内容(如搜索收集目标)存在该名字的共享设计里，
 * 全部目标完成后本任务即完成（BooleanTaskData：0/1）。
 */
public class RegionTask extends Task {

    /** 基准方块(共享设计)的名字——注意是方块名(模板键)，不是区域名。 */
    public String anchorName = "";

    public RegionTask(Quest quest) {
        super(quest);
    }

    @Override
    public TaskType getType() {
        return FTBQuestsAddon.REGION;
    }

    @Override
    public void writeData(NBTTagCompound nbt) {
        super.writeData(nbt);
        nbt.setString("anchor_name", anchorName);
    }

    @Override
    public void readData(NBTTagCompound nbt) {
        super.readData(nbt);
        anchorName = nbt.getString("anchor_name");
    }

    @Override
    public void writeNetData(DataOut data) {
        super.writeNetData(data);
        data.writeString(anchorName == null ? "" : anchorName);
    }

    @Override
    public void readNetData(DataIn data) {
        super.readNetData(data);
        anchorName = data.readString();
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getConfig(ConfigGroup config) {
        super.getConfig(config);
        config.addString("anchor_name", () -> anchorName, v -> anchorName = v, "");
    }

    /** 每 3 秒自动提交一次判定（同 LocationTask）。 */
    @Override
    public int autoSubmitOnPlayerTick() {
        return 3;
    }

    @Override
    public TaskData createData(QuestData data) {
        return new Data(this, data);
    }

    public static class Data extends BooleanTaskData<RegionTask> {
        private Data(RegionTask task, QuestData data) {
            super(task, data);
        }

        @Override
        public boolean canSubmit(EntityPlayerMP player) {
            return RegionSearchTracker.isDesignCompleted(player, task.anchorName);
        }

        @Override
        public String getProgressString() {
            return progress > 0 ? "1" : "0";
        }
    }
}
