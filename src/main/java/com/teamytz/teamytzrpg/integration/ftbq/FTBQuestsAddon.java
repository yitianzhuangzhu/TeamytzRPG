package com.teamytz.teamytzrpg.integration.ftbq;

import com.feed_the_beast.ftblib.lib.icon.Icon;
import com.feed_the_beast.ftbquests.quest.task.TaskType;
import com.teamytz.teamytzrpg.TeamytzRPG;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.Loader;

/**
 * FTB Quests 联动入口（软依赖）：仅当 ftbquests 加载时由主类调用 init()。
 * 通过 FTB 在 postInit 广播的 RegistryEvent.Register&lt;TaskType&gt; 注册"区域任务"类型。
 */
public final class FTBQuestsAddon {

    /** 区域任务类型实例（注册后非空）。 */
    public static TaskType REGION;

    private static boolean inited = false;

    private FTBQuestsAddon() {
    }

    /** 主类 preInit/init 调用：FTB 在场才注册事件订阅（注册事件在其 postInit 广播）。 */
    public static void init() {
        if (inited || !Loader.isModLoaded("ftbquests")) {
            return;
        }
        inited = true;
        MinecraftForge.EVENT_BUS.register(Handler.class);
    }

    /** 事件订阅类（静态方法，FTB 的任务注册表创建时广播）。 */
    public static class Handler {
        @SubscribeEvent
        public static void registerTaskType(RegistryEvent.Register<TaskType> event) {
            if (REGION != null) {
                return;
            }
            ResourceLocation id = new ResourceLocation(TeamytzRPG.MODID, "region");
            if (event.getRegistry().getValue(id) != null) {
                REGION = event.getRegistry().getValue(id);
                return;
            }
            REGION = new TaskType(RegionTask::new)
                    .setRegistryName(id)
                    .setDisplayName("区域任务")
                    .setIcon(Icon.getIcon("minecraft:blocks/beacon"));
            event.getRegistry().register(REGION);
            TeamytzRPG.getLogger().info("[FTBQ] 已注册任务类型 区域任务 (" + id + ")");
        }
    }
}
