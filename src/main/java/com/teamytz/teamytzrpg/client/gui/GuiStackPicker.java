package com.teamytz.teamytzrpg.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.renderer.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.teamytz.teamytzrpg.region.RegionLog;

/**
 * 物品选择器（客户端，MC 风格、仿 FTBQuests/FTBLib 的 GuiSelectItemStack）：
 * 每个候选显示为一行 [物品图标][名称](数量/[NBT] 标记)，可搜索(创造页)、可滚轮滚动；
 * 点任意一行 = 整栈（含 NBT）回填到编辑界面的对应目标行。
 */
@SideOnly(Side.CLIENT)
public class GuiStackPicker extends GuiScreen {

    private final GuiAnchorEdit owner;
    private final int rowIndex;

    private int source = 0; // 0 = 背包, 1 = 创造(全物品)
    private GuiTextField searchField;
    private int scroll = 0;
    private final List<ItemStack> visible = new ArrayList<>();
    private final List<ItemStack> inventoryList = new ArrayList<>();
    private List<ItemStack> creativeList;

    public GuiStackPicker(GuiAnchorEdit owner, int rowIndex) {
        this.owner = owner;
        this.rowIndex = rowIndex;
    }

    private static List<ItemStack> collectInventory(EntityPlayer player) {
        List<ItemStack> list = new ArrayList<>();
        if (player == null || player.inventory == null) {
            return list;
        }
        for (ItemStack s : player.inventory.mainInventory) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
            }
        }
        for (ItemStack s : player.inventory.armorInventory) {
            if (s != null && !s.isEmpty()) {
                list.add(s);
            }
        }
        ItemStack off = player.getHeldItemOffhand();
        if (off != null && !off.isEmpty()) {
            list.add(off);
        }
        return list;
    }

    private static List<ItemStack> collectCreative() {
        List<ItemStack> list = new ArrayList<>();
        for (Item item : ForgeRegistries.ITEMS.getValuesCollection()) {
            try {
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    list.add(stack);
                }
            } catch (Exception ignored) {
            }
        }
        return list;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        this.buttonList.add(new GuiButton(0, cx - 190, this.height - 30, 100, 20, "背包"));
        this.buttonList.add(new GuiButton(1, cx - 80, this.height - 30, 100, 20, "创造(全物品)"));
        this.buttonList.add(new GuiButton(9, cx + 90, this.height - 30, 100, 20, "取消"));

        this.inventoryList.clear();
        this.inventoryList.addAll(collectInventory(Minecraft.getMinecraft().player));
        if (this.creativeList == null) {
            this.creativeList = collectCreative();
        }
        this.searchField = new GuiTextField(0, this.mc.fontRenderer, cx - 190, 58, 380, 16);
        this.searchField.setMaxStringLength(64);
        RegionLog.info("[选择器] 初始化: 目标 row=" + rowIndex
                + " 背包项=" + inventoryList.size() + " 创造项=" + creativeList.size());
        refreshList();
    }

    private void refreshList() {
        visible.clear();
        String q = this.searchField.getText() == null ? "" : this.searchField.getText().trim().toLowerCase();
        List<ItemStack> base = source == 0 ? inventoryList : creativeList;
        for (ItemStack s : base) {
            if (q.isEmpty() || stackMatchesQuery(s, q)) {
                visible.add(s);
            }
        }
        int visRows = listRowsVisible();
        if (scroll > Math.max(0, visible.size() - visRows)) {
            scroll = Math.max(0, visible.size() - visRows);
        }
    }

    private boolean stackMatchesQuery(ItemStack s, String q) {
        try {
            if (s.getItem().getRegistryName() != null
                    && s.getItem().getRegistryName().toString().toLowerCase().contains(q)) {
                return true;
            }
            String name = s.getDisplayName();
            if (name != null && name.toLowerCase().contains(q)) {
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int listRowsVisible() {
        return Math.max(1, (this.height - 104) / 18);
    }

    private int panelX() {
        return this.width / 2 - 190;
    }

    private int panelY() {
        return 82;
    }

    @Override
    public void updateScreen() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            int maxScroll = Math.max(0, visible.size() - listRowsVisible());
            if (wheel > 0) {
                scroll = Math.max(0, scroll - 1);
            } else {
                scroll = Math.min(maxScroll, scroll + 1);
            }
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            int y0 = panelY();
            int rowH = 18;
            int w = 380;
            if (mouseX >= panelX() && mouseX < panelX() + w
                    && mouseY >= y0 && mouseY < y0 + listRowsVisible() * rowH) {
                int idx = scroll + (mouseY - y0) / rowH;
                if (idx >= 0 && idx < visible.size()) {
                    ItemStack picked = visible.get(idx).copy();
                    picked.setCount(1);
                    RegionLog.info("[选择器] 点击候选 idx=" + idx + "/" + visible.size()
                            + " stack=" + (picked.getItem().getRegistryName() == null ? "?"
                            : picked.getItem().getRegistryName().toString())
                            + " nbt=" + picked.hasTagCompound()
                            + " → 回填 row=" + rowIndex);
                    owner.pickRowStack(rowIndex, picked); // 回填并在 owner 里重新显示本界面
                    return;
                }
            }
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.searchField != null) {
            this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            this.mc.displayGuiScreen(this.owner);
            return;
        }
        if (this.searchField != null) {
            String before = this.searchField.getText();
            this.searchField.textboxKeyTyped(typedChar, keyCode);
            if (!before.equals(this.searchField.getText())) {
                refreshList();
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 9) {
            this.mc.displayGuiScreen(this.owner);
        } else if (button.id == 0 || button.id == 1) {
            this.source = button.id;
            this.scroll = 0;
            refreshList();
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        FontRenderer fr = this.mc.fontRenderer;
        int cx = this.width / 2;

        this.drawCenteredString(fr, "\u00a7e选择物品 \u00a77—— 为第 " + (rowIndex + 1) + " 行选择收集目标"
                + " (整栈含 NBT，等同 FTB 任务物品)", cx, 16, 0xFFFFFF);
        if (this.source == 1) {
            this.drawCenteredString(fr, "\u00a77搜索(物品名/注册id):", cx, 44, 0x909090);
        } else {
            this.drawCenteredString(fr, "\u00a77从你的背包中直接点选（携带原 NBT）", cx, 44, 0x909090);
        }
        if (this.searchField != null) {
            this.searchField.drawTextBox();
        }

        // 面板
        int y0 = panelY();
        int rows = listRowsVisible();
        int w = 380;
        Gui.drawRect(panelX() - 2, y0 - 2, panelX() + w + 2, y0 + rows * 18 + 2, 0xFF101010);
        fr.drawString("\u00a77" + visible.size() + " 项" + (visible.size() > rows ? "  (滚轮滚动)" : ""),
                panelX() + 2, y0 - 10, 0x808080);

        RenderItem ri = mc.getRenderItem();
        for (int i = 0; i < rows; i++) {
            int idx = scroll + i;
            int y = y0 + i * 18;
            if (idx >= visible.size()) {
                break;
            }
            ItemStack s = visible.get(idx);
            boolean over = mouseX >= panelX() && mouseX < panelX() + w
                    && mouseY >= y && mouseY < y + 18;
            Gui.drawRect(panelX(), y, panelX() + w, y + 18,
                    over ? 0xFF2A3A5A : 0xFF1E1E28);
            // 物品图标槽
            Gui.drawRect(panelX() + 2, y + 1, panelX() + 18, y + 17, 0xFF303030);
            ri.renderItemAndEffectIntoGUI(s, panelX() + 3, y + 2);
            if (s.getCount() > 1) {
                ri.renderItemOverlayIntoGUI(fr, s, panelX() + 3, y + 2, null);
            }
            fr.drawString(safeName(s), panelX() + 22, y + 5, 0xFFFFFF);
            if (s.hasTagCompound()) {
                fr.drawString("\u00a76[NBT]", panelX() + w - 44, y + 5, 0xFFB000);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static String safeName(ItemStack s) {
        try {
            String n = s.getDisplayName();
            if (n == null || n.isEmpty()) {
                return s.getItem().getRegistryName() != null
                        ? s.getItem().getRegistryName().toString() : "?";
            }
            return n;
        } catch (Exception e) {
            return "?";
        }
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
