package com.teamytz.teamytzrpg.client.hud;

import com.teamytz.teamytzrpg.config.RegionConfig;
import com.teamytz.teamytzrpg.region.SearchHudRow;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

/**
 * 搜索区域收集信息栏（客户端 HUD，屏幕右上角，从上往下逐行）。
 * 行内容："物品名×数量 (当前收集数)"，达成后在行尾加 √。
 * 字号/颜色/背景/边距/开关 全部由 config（hud.* 分组）控制。
 */
@SideOnly(Side.CLIENT)
public final class SearchHudRenderer {

    private static SearchHudRenderer INSTANCE;
    private static boolean registered = false;

    private final List<SearchHudRow> rows = new ArrayList<>();

    private SearchHudRenderer() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(get());
        }
    }

    private static SearchHudRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new SearchHudRenderer();
        }
        return INSTANCE;
    }

    public static void setRows(List<SearchHudRow> newRows) {
        get().rows.clear();
        if (newRows != null) {
            get().rows.addAll(newRows);
        }
    }

    @SubscribeEvent
    public void onRenderOverlay(RenderGameOverlayEvent.Post event) {
        if (event.getType() != RenderGameOverlayEvent.ElementType.ALL) {
            return;
        }
        if (!RegionConfig.hudEnabled || this.rows.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRenderer;
        ScaledResolution res = event.getResolution();
        if (fr == null || res == null) {
            return;
        }

        // 行文案（无颜色码，颜色按状态用 config 指定）
        List<String> lines = new ArrayList<>();
        List<Boolean> doneFlags = new ArrayList<>();
        int maxWidth = 0;
        for (SearchHudRow r : this.rows) {
            String name = itemDisplayName(r.id, r.nbt);
            String text = name + "\u00d7" + r.need + " (" + r.have + ")";
            if (r.done) {
                text = text + " \u2713";
            }
            doneFlags.add(r.done);
            lines.add(text);
            maxWidth = Math.max(maxWidth, fr.getStringWidth(text));
        }
        String header = "收集物品";
        maxWidth = Math.max(maxWidth, fr.getStringWidth(header));

        float scale = (float) RegionConfig.hudFontScale;
        int widthPx = res.getScaledWidth();
        int anchorX = widthPx - RegionConfig.hudMarginRight;
        int yTop = RegionConfig.hudMarginTop;
        int lineStep = 10; // 每行纵向间距(实际像素)
        int totalLines = lines.size() + 1;

        // 半透明背景(实际像素坐标，画在矩阵缩放前)
        if (RegionConfig.hudBgAlpha > 0) {
            int bgRight = anchorX + 1;
            int bgLeft = anchorX - (int) Math.ceil(maxWidth * scale) - 5;
            int bgBottom = yTop + totalLines * lineStep + 2;
            int argb = (RegionConfig.hudBgAlpha << 24) | 0x000000;
            Gui.drawRect(bgLeft, yTop - 2, bgRight, bgBottom, argb);
        }

        GlStateManager.pushMatrix();
        GlStateManager.translate(anchorX, yTop, 0.0F);
        GlStateManager.scale(scale, scale, 1.0F);
        float ly = 0.0F;
        // 标题
        fr.drawString(header, -fr.getStringWidth(header), (int) ly,
                RegionConfig.hudColorHeader | 0xFF000000);
        ly += lineStep / scale;
        // 各行（右键对齐到 anchorX）
        for (int i = 0; i < lines.size(); i++) {
            boolean done = doneFlags.get(i);
            int color = (done ? RegionConfig.hudColorDone : RegionConfig.hudColorText) | 0xFF000000;
            fr.drawString(lines.get(i), -fr.getStringWidth(lines.get(i)), (int) ly, color);
            ly += lineStep / scale;
        }
        GlStateManager.popMatrix();
    }

    private static String itemDisplayName(String registryId, net.minecraft.nbt.NBTTagCompound nbt) {
        try {
            Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(registryId));
            if (item != null) {
                ItemStack stack = new ItemStack(item);
                if (nbt != null) {
                    stack.setTagCompound(nbt.copy());
                }
                String name = stack.getDisplayName();
                if (name != null) {
                    return name;
                }
            }
        } catch (Exception ignored) {
        }
        return registryId;
    }
}
