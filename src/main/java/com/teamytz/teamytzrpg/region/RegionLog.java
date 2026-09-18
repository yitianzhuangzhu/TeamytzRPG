package com.teamytz.teamytzrpg.region;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.teamytz.teamytzrpg.TeamytzRPG;

/** 区域系统专用日志，统一 [RPG-Region] 前缀，方便在日志里检索研究。 */
public final class RegionLog {

    private RegionLog() {
    }

    public static Logger get() {
        Logger logger = TeamytzRPG.getLogger();
        return logger != null ? logger : LogManager.getLogger(TeamytzRPG.MODID);
    }

    public static void info(String msg) {
        get().info("[RPG-Region] " + msg);
    }

    public static void warn(String msg) {
        get().warn("[RPG-Region] " + msg);
    }

    public static void error(String msg) {
        get().error("[RPG-Region] " + msg);
    }
}
