package com.teamytz.teamytzrpg.client.gui;

import com.teamytz.teamytzrpg.config.RegionConfig;
import com.teamytz.teamytzrpg.network.NetworkHandler;
import com.teamytz.teamytzrpg.network.PacketResetSearchProgress;
import com.teamytz.teamytzrpg.network.PacketSaveAnchor;
import com.teamytz.teamytzrpg.network.PacketToggleDebugIgnore;
import com.teamytz.teamytzrpg.region.RegionFunctions;
import com.teamytz.teamytzrpg.region.RegionLog;
import com.teamytz.teamytzrpg.region.SearchItem;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 基准方块编辑 UI（客户端）。
 * 收集目标行（功能=搜索区域时）：[物品ID][数量][选择物品]。
 * "选择物品"打开 FTB 风格选择器（仿 FTBQuests/FTBLib 的物品选取），
 * 点选即整栈(含NBT)回填该行 —— 匹配语义 = 玩家点啥就要求拾取物包含该栈的全部信息。
 * "+ 增加一栏" 可无限添加（上限 config: search_max_items），滚轮滚动浏览。
 */
@SideOnly(Side.CLIENT)
public class GuiAnchorEdit extends GuiScreen {

    private static final int BOX_W = 180;
    private static final int BOX_H = 18;
    private static final int ROW_H = 13;
    private static final int MAX_VISIBLE = 6;

    private static final int ID_W = 150;
    private static final int QTY_W = 44;
    private static final int ROW_STEP = 18;
    private static final int ITEM_TOP = 152;
    /** 行内"选择物品"按钮 id 基准（5000+行号）。 */
    private static final int PICK_BTN_BASE = 5000;

    private final BlockPos pos;
    private final String initName;

    private GuiTextField nameField;
    private String selectedCode = "";
    private boolean dropdownOpen = false;
    private int funcScrollTop = 0;
    private int itemScroll = 0;

    private final List<String[]> items = new ArrayList<>();

    /** 一行目标的可编辑数据（含 NBT，来自选择器整栈复制）。 */
    private static final class ItemRowData {
        String id = "";
        String qty = "1";
        NBTTagCompound nbt;
    }

    private final List<ItemRowData> rowData = new ArrayList<>();

    /** 可见（已物化）行输入框。 */
    private static final class Row {
        final int index;
        final GuiTextField idField;
        final GuiTextField qtyField;

        Row(int index, GuiTextField id, GuiTextField qty) {
            this.index = index;
            this.idField = id;
            this.qtyField = qty;
        }
    }

    private final List<Row> searchRows = new ArrayList<>();

    public GuiAnchorEdit(BlockPos pos, String anchorName, String function, List<SearchItem> prefilled) {
        this.pos = pos;
        this.initName = anchorName == null ? "" : anchorName;
        RegionFunctions.Descriptor cur = RegionFunctions.find(function);
        this.selectedCode = cur != null ? cur.code : RegionFunctions.FUNCTION_KEEP;
        items.add(new String[]{"\u00a77继承(不修改，同名自动沿用模板功能)", RegionFunctions.FUNCTION_KEEP});
        items.add(new String[]{"\u00a7c无功能(清除)", RegionFunctions.FUNCTION_CLEAR});
        for (RegionFunctions.Descriptor d : RegionFunctions.all()) {
            items.add(new String[]{d.display, d.code});
        }
        if (prefilled != null) {
            for (SearchItem s : prefilled) {
                if (s != null && s.id != null && !s.id.isEmpty()) {
                    ItemRowData r = new ItemRowData();
                    r.id = s.id;
                    r.qty = String.valueOf(s.qty);
                    r.nbt = s.nbt;
                    rowData.add(r);
                }
            }
        }
    }

    // ================= 几何 =================

    private int idX() {
        return this.width / 2 - 150;
    }

    private int qtyX() {
        return this.width / 2 + 6;
    }

    private int pickBtnX() {
        return this.width / 2 + 56;
    }

    private int boxX() {
        return this.width / 2 - 90;
    }

    private int boxY() {
        return 96;
    }

    private int listY() {
        return boxY() + BOX_H + 2;
    }

    private int funcVisibleRows() {
        return Math.min(MAX_VISIBLE, items.size());
    }

    private int plusY() {
        return this.height - 90;
    }

    private int rowsBottom() {
        return this.height - 98;
    }

    private int itemVisibleRows() {
        return Math.max(0, (rowsBottom() - ITEM_TOP) / ROW_STEP);
    }

    private boolean showItems() {
        return !this.dropdownOpen && RegionFunctions.SEARCH.equals(this.selectedCode);
    }

    private boolean canAddRow() {
        return rowData.size() < RegionConfig.maxSearchItems;
    }

    private String currentLabel() {
        for (String[] it : items) {
            if (it[1].equals(selectedCode)) {
                return it[0];
            }
        }
        return items.get(0)[0];
    }

    // ================= 行数据管理 =================

    private void flushRows() {
        for (Row r : searchRows) {
            if (r.index >= 0 && r.index < rowData.size()) {
                ItemRowData d = rowData.get(r.index);
                d.id = r.idField.getText() == null ? "" : r.idField.getText();
                d.qty = r.qtyField.getText() == null ? "" : r.qtyField.getText();
            }
        }
    }

    /** 清掉行内"选择物品"按钮（5000+），保留固定按钮。 */
    private void clearPickButtons() {
        List<GuiButton> remove = new ArrayList<>();
        for (GuiButton b : this.buttonList) {
            if (b.id >= PICK_BTN_BASE) {
                remove.add(b);
            }
        }
        this.buttonList.removeAll(remove);
    }

    private void rebuildItemRows() {
        flushRows();
        searchRows.clear();
        clearPickButtons();
        int visible = itemVisibleRows();
        if (visible <= 0) {
            return;
        }
        int maxScroll = Math.max(0, rowData.size() - visible);
        if (itemScroll > maxScroll) {
            itemScroll = maxScroll;
        }
        int start = itemScroll;
        int end = Math.min(rowData.size(), start + visible);
        for (int idx = start; idx < end; idx++) {
            ItemRowData d = rowData.get(idx);
            int y = ITEM_TOP + (idx - start) * ROW_STEP;
            GuiTextField id = new GuiTextField(600 + idx * 2, this.mc.fontRenderer, idX(), y, ID_W, 14);
            id.setMaxStringLength(128);
            id.setText(d.id == null ? "" : d.id);
            GuiTextField qty = new GuiTextField(601 + idx * 2, this.mc.fontRenderer, qtyX(), y, QTY_W, 14);
            qty.setMaxStringLength(8);
            qty.setText(d.qty == null ? "" : d.qty);
            searchRows.add(new Row(idx, id, qty));
            // MC 风格按钮：选择物品（vanilla GuiButton 纹理）
            this.buttonList.add(new GuiButton(PICK_BTN_BASE + idx, pickBtnX(), y - 1, 90, 16, "选择物品"));
        }
    }

    private void addItemRow() {
        if (!canAddRow()) {
            return;
        }
        flushRows();
        rowData.add(new ItemRowData());
        itemScroll = Math.max(0, rowData.size() - itemVisibleRows());
        rebuildItemRows();
    }

    private boolean plusRect(int mouseX, int mouseY) {
        if (!showItems() || !canAddRow()) {
            return false;
        }
        int x = idX();
        int y = plusY();
        return mouseX >= x && mouseX < x + 130 && mouseY >= y && mouseY < y + 14;
    }

    private List<SearchItem> collectItems() {
        flushRows();
        List<SearchItem> out = new ArrayList<>();
        for (ItemRowData d : rowData) {
            String id = d.id == null ? "" : d.id.trim();
            if (id.isEmpty()) {
                continue;
            }
            int qty = 1;
            String qt = d.qty == null ? "" : d.qty.trim();
            if (!qt.isEmpty()) {
                try {
                    qty = Integer.parseInt(qt);
                } catch (NumberFormatException ignored) {
                }
            }
            out.add(new SearchItem(id, qty < 1 ? 1 : qty, SearchItem.MODE_MATCH, d.nbt));
        }
        return out;
    }

    /**
     * 选择器回填：把点选的整栈（含 NBT）写入该行。
     * 4) 修复回填：直接改 rowData 并重建行输入框/按钮显示，再回到本界面。
     */
    public void pickRowStack(int rowIndex, ItemStack stack) {
        try {
            RegionLog.info("[GUI] 收到回填调用 row=" + rowIndex + " rows=" + rowData.size()
                    + " stack=" + (stack == null ? "null"
                    : (stack.getItem().getRegistryName() == null ? "?" : stack.getItem().getRegistryName().toString())
                    + " nbt=" + stack.hasTagCompound()));
            if (stack == null || stack.isEmpty()) {
                this.mc.displayGuiScreen(this);
                return;
            }
            if (rowIndex >= 0 && rowIndex < rowData.size()) {
                ItemRowData d = rowData.get(rowIndex);
                if (stack.getItem().getRegistryName() != null) {
                    d.id = stack.getItem().getRegistryName().toString();
                }
                d.nbt = stack.hasTagCompound() ? stack.getTagCompound().copy() : null;
                if (d.qty.isEmpty()) {
                    d.qty = "1";
                }
                // 关键：同步改掉现有可见输入框文本，否则 rebuild 开头 flushRows()
                // 会用旧(空)文本把刚写入的 rowData 覆盖回去。
                for (Row r : searchRows) {
                    if (r.index == rowIndex) {
                        r.idField.setText(d.id);
                        r.qtyField.setText(d.qty);
                    }
                }
                RegionLog.info("[GUI] 回填数据已写入 row=" + rowIndex + " id=" + d.id
                        + " nbt=" + (d.nbt != null) + " qty=" + d.qty);
            } else {
                RegionLog.warn("[GUI] 回填 row 越界: row=" + rowIndex + " rows=" + rowData.size());
            }
            rebuildItemRows();
            RegionLog.info("[GUI] 回填重建完成，准备返回编辑界面 rows=" + searchRows.size());
            this.mc.displayGuiScreen(this);
        } catch (Exception e) {
            RegionLog.get().error("[RPG-Region][GUI] 回填异常", e);
        }
    }

    // ================= 初始化 =================

    @Override
    public void initGui() {
        this.buttonList.clear();
        int cx = this.width / 2;
        this.buttonList.add(new GuiButton(0, cx - 90, this.height - 32, 86, 20, "\u00a7a保存"));
        this.buttonList.add(new GuiButton(1, cx + 4, this.height - 32, 86, 20, "取消"));
        this.buttonList.add(new GuiButton(2, cx - 45, this.height - 58, 90, 20, "\u00a7c重置进度"));
        this.buttonList.add(new GuiButton(3, cx - 145, this.height - 58, 90, 20, "\u00a7e调试(忽略来源)"));

        this.nameField = new GuiTextField(0, this.mc.fontRenderer, cx - 90, 56, BOX_W, 16);
        this.nameField.setMaxStringLength(64);
        this.nameField.setText(this.initName);

        rebuildItemRows();
    }

    // ================= 输入 =================

    @Override
    public void updateScreen() {
        if (this.nameField != null) {
            this.nameField.updateCursorCounter();
        }
        for (Row r : searchRows) {
            r.idField.updateCursorCounter();
            r.qtyField.updateCursorCounter();
        }
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (this.dropdownOpen) {
            int maxScroll = Math.max(0, items.size() - funcVisibleRows());
            this.funcScrollTop = wheel > 0
                    ? Math.max(0, this.funcScrollTop - 1)
                    : Math.min(maxScroll, this.funcScrollTop + 1);
        } else if (showItems() && rowData.size() > itemVisibleRows()) {
            int maxScroll = Math.max(0, rowData.size() - itemVisibleRows());
            this.itemScroll = wheel > 0
                    ? Math.max(0, this.itemScroll - 1)
                    : Math.min(maxScroll, this.itemScroll + 1);
            rebuildItemRows();
        }
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (mouseButton == 0) {
            if (this.dropdownOpen && mouseX >= boxX() && mouseX < boxX() + BOX_W
                    && mouseY >= listY() && mouseY < listY() + funcVisibleRows() * ROW_H) {
                int row = this.funcScrollTop + (mouseY - listY()) / ROW_H;
                if (row >= 0 && row < items.size()) {
                    this.selectedCode = items.get(row)[1];
                    this.dropdownOpen = false;
                    this.funcScrollTop = 0;
                }
                return;
            }
            if (mouseX >= boxX() && mouseX < boxX() + BOX_W
                    && mouseY >= boxY() && mouseY < boxY() + BOX_H) {
                this.dropdownOpen = !this.dropdownOpen;
                this.funcScrollTop = 0;
                return;
            }
            if (this.plusRect(mouseX, mouseY)) {
                addItemRow();
                return;
            }
            this.dropdownOpen = false;
        }
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (this.nameField != null) {
            this.nameField.mouseClicked(mouseX, mouseY, mouseButton);
        }
        for (Row r : searchRows) {
            r.idField.mouseClicked(mouseX, mouseY, mouseButton);
            r.qtyField.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // ESC
            this.dropdownOpen = false;
            this.mc.displayGuiScreen(null);
            return;
        }
        if (this.dropdownOpen) {
            return;
        }
        if (this.nameField != null) {
            this.nameField.textboxKeyTyped(typedChar, keyCode);
        }
        for (Row r : searchRows) {
            r.idField.textboxKeyTyped(typedChar, keyCode);
            r.qtyField.textboxKeyTyped(typedChar, keyCode);
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String name = this.nameField.getText() == null ? "" : this.nameField.getText().trim();
            NetworkHandler.NET.sendToServer(new PacketSaveAnchor(this.pos, name, this.selectedCode,
                    collectItems()));
            this.mc.displayGuiScreen(null);
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(null);
        } else if (button.id == 2) {
            NetworkHandler.NET.sendToServer(new PacketResetSearchProgress(this.pos));
        } else if (button.id == 3) {
            // 调试模式：忽略"丢出再捡"来源判定（默认关；服务端回执当前开关状态）
            NetworkHandler.NET.sendToServer(new PacketToggleDebugIgnore());
        } else if (button.id >= PICK_BTN_BASE) {
            int rowIndex = button.id - PICK_BTN_BASE;
            flushRows();
            RegionLog.info("[GUI] 点击行内选择物品按钮 → 打开选择器 row=" + rowIndex
                    + " rows=" + rowData.size());
            this.mc.displayGuiScreen(new GuiStackPicker(this, rowIndex));
        }
    }

    // ================= 绘制 =================

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        FontRenderer fr = this.mc.fontRenderer;
        int cx = this.width / 2;

        this.drawCenteredString(fr, "\u00a7e基准方块 编辑 \u00a77(pos: " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")", cx, 18, 0xFFFFFF);
        this.drawCenteredString(fr, "名称(模板标识，同名基准方块共享区域设计):", cx, 42, 0xA0A0A0);
        if (this.nameField != null) {
            this.nameField.drawTextBox();
        }

        this.drawCenteredString(fr, "功能(点击下拉选择):", cx, boxY() - 12, 0xA0A0A0);
        drawBox(boxX(), boxY(), BOX_W, BOX_H, 0xFF222222, 0xFFAAAAAA);
        fr.drawString(currentLabel(), boxX() + 6, boxY() + (BOX_H - 8) / 2, 0xFFFFFF);
        fr.drawString("\u00a77▼", boxX() + BOX_W - 14, boxY() + (BOX_H - 8) / 2, 0xFFFFFF);

        if (this.dropdownOpen) {
            int visible = funcVisibleRows();
            Gui.drawRect(boxX() - 1, listY() - 1, boxX() + BOX_W + 1, listY() + visible * ROW_H + 1, 0xFF000000);
            for (int i = 0; i < visible; i++) {
                int idx = this.funcScrollTop + i;
                int y0 = listY() + i * ROW_H;
                if (idx >= items.size()) {
                    break;
                }
                boolean sel = items.get(idx)[1].equals(this.selectedCode);
                Gui.drawRect(boxX(), y0, boxX() + BOX_W, y0 + ROW_H, sel ? 0xFF2A6E2F : 0xFF303030);
                fr.drawString(items.get(idx)[0], boxX() + 6, y0 + 2, sel ? 0xFFFFFF : 0xC8C8C8);
            }
        }

        if (showItems()) {
            this.drawCenteredString(fr, "\u00a7a搜索目标: 玩家在该区域内拾取以下物品"
                    + "\u00a77(上限 " + RegionConfig.maxSearchItems + " 条，config/teamytzrpg.cfg 可调)", cx, 116, 0xC0FFC0);
            fr.drawString("\u00a77物品ID", idX(), 130, 0x909090);
            fr.drawString("\u00a77数量", qtyX(), 130, 0x909090);
            fr.drawString("\u00a78选择物品=整栈(含NBT)回填，匹配该物品的全部信息", cx - 150, 140, 0x808080);

            for (Row r : searchRows) {
                int y = ITEM_TOP + (r.index - itemScroll) * ROW_STEP;
                r.idField.drawTextBox();
                r.qtyField.drawTextBox();
                ItemRowData d = rowData.get(r.index);
                if (d.nbt != null) {
                    fr.drawString("\u00a76*", qtyX() + QTY_W + 2, y + 3, 0xFFB000);
                }
            }
            String info = rowData.size() + " / " + RegionConfig.maxSearchItems
                    + (rowData.size() > itemVisibleRows() ? "  \u00a77滚轮滚动" : "");
            fr.drawString(info, idX(), ITEM_TOP + itemVisibleRows() * ROW_STEP + 2, 0x808080);
            if (canAddRow()) {
                int y = plusY();
                drawBox(idX(), y, 130, 14, 0xFF173A1C, 0xFF2A6E2F);
                fr.drawString("\u00a7a+ 增加一栏", idX() + 8, y + 3, 0x90EE90);
            } else {
                fr.drawString("\u00a7c已达上限(" + RegionConfig.maxSearchItems + " 条)，改 config 可调整",
                        idX(), plusY(), 0xFF9090);
            }
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private static void drawBox(int x, int y, int w, int h, int fill, int border) {
        Gui.drawRect(x, y, x + w, y + h, fill);
        Gui.drawRect(x, y, x + w, y + 1, border);
        Gui.drawRect(x, y + h - 1, x + w, y + h, border);
        Gui.drawRect(x, y, x + 1, y + h, border);
        Gui.drawRect(x + w - 1, y, x + w, y + h, border);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
