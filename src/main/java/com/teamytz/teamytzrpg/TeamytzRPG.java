package com.teamytz.teamytzrpg;

import com.teamytz.teamytzrpg.command.TeamytzCommand;
import com.teamytz.teamytzrpg.config.RegionConfig;
import com.teamytz.teamytzrpg.network.NetworkHandler;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.Logger;

@Mod(modid = TeamytzRPG.MODID, name = TeamytzRPG.NAME, version = TeamytzRPG.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class TeamytzRPG {

    public static final String MODID = "teamytzrpg";
    public static final String NAME = "异天庄的RPG";
    // 与 build.gradle 的 version 保持一致（0.<major>.<minor>：大更新/小更新与bug修复）。
    public static final String VERSION = "0.1.22";

    private static Logger logger;

    public static Logger getLogger() {
        return logger;
    }

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        logger = event.getModLog();
        RegionConfig.init(event.getSuggestedConfigurationFile());
        // FTB Quests 联动（可选）：FTB 在场才加载对应类，缺席不影响本 mod。
        if (net.minecraftforge.fml.common.Loader.isModLoaded("ftbquests")) {
            com.teamytz.teamytzrpg.integration.ftbq.FTBQuestsAddon.init();
            logger.info("{} preInit: 已启用 FTB Quests 联动(区域任务)", NAME);
        }
        // Dynamic Stealth 联动（可选）：警戒区域需要它的威胁度/AI 体系。
        if (net.minecraftforge.fml.common.Loader.isModLoaded("dynamicstealth")) {
            com.teamytz.teamytzrpg.integration.stealth.GuardIntegration.init();
        }
        logger.info("{} preInit: 配置已加载(搜索目标上限={})", NAME, RegionConfig.maxSearchItems);
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        NetworkHandler.init();
        logger.info("{} init: 网络通道就绪", NAME);
    }

    @EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        event.registerServerCommand(new TeamytzCommand());
        logger.info("{} serverStarting: 已注册命令 /teamytz (area / debug)", NAME);
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        logger.info("{} postInit", NAME);
    }
}
