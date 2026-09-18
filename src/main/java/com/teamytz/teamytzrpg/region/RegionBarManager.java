package com.teamytz.teamytzrpg.region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.teamytz.teamytzrpg.config.RegionConfig;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.BossInfo;
import net.minecraft.world.BossInfoServer;

/**
 * 区域功能顶部条管理器（服务端，袭击/末影龙同款 Boss 条）。
 *
 * <p>规则：玩家站在"功能已识别"的区域实例内时，为其显示一条顶部进度条；
 * 功能由所在基准方块的 function 字段决定（见 {@link RegionFunctions}）。
 * 条颜色按 config（bars.bar_color_&lt;code&gt;）解析，NONE=不显示。
 * 同一时刻每位玩家最多一条，离开区域即移除。</p>
 */
public final class RegionBarManager {

    private static final Map<UUID, BossInfoServer> BARS = new HashMap<>();
    /** 当前显示的功能码（用于检测切换，避免每 tick 重发包）。 */
    private static final Map<UUID, String> SHOWN_CODE = new HashMap<>();

    private RegionBarManager() {
    }

    /** 每个服务端 tick 调用一次（服务器主线程）。 */
    public static void update(EntityPlayerMP player, List<RegionManager.RegionMatch> matches) {
        RegionFunctions.Descriptor desc = null;
        BossInfo.Color color = null;
        for (RegionManager.RegionMatch m : matches) {
            RegionFunctions.Descriptor d = RegionFunctions.find(m.function());
            if (d != null) {
                BossInfo.Color c = RegionConfig.barColor(d.code);
                if (c != null) {
                    desc = d;
                    color = c;
                    break;
                }
            }
        }
        UUID id = player.getUniqueID();
        if (desc == null) {
            hide(player, id);
            return;
        }
        BossInfoServer bar = BARS.get(id);
        String shown = SHOWN_CODE.get(id);
        if (bar == null || !desc.code.equals(shown)) {
            if (bar != null) {
                bar.removePlayer(player);
            }
            bar = new BossInfoServer(new TextComponentString(desc.display),
                    color, BossInfo.Overlay.PROGRESS);
            bar.setPercent(1.0F);
            bar.setVisible(true);
            BARS.put(id, bar);
            SHOWN_CODE.put(id, desc.code);
            bar.addPlayer(player);
            RegionLog.info("区域功能条已显示 玩家=" + player.getName()
                    + " 功能=" + desc.code + " 文本=" + desc.display);
        }
    }

    /** 玩家离开（登出/换维度）时清理。 */
    public static void onLogout(UUID playerId) {
        BossInfoServer bar = BARS.remove(playerId);
        SHOWN_CODE.remove(playerId);
        if (bar != null) {
            for (EntityPlayerMP p : bar.getPlayers()) {
                bar.removePlayer(p);
            }
        }
    }

    private static void hide(EntityPlayerMP player, UUID id) {
        BossInfoServer bar = BARS.remove(id);
        SHOWN_CODE.remove(id);
        if (bar != null) {
            bar.removePlayer(player);
        }
    }
}
