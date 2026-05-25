package com.playsi.aero_cam_sync.client.config.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;

public class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Entry> {

    public static final int ENTRY_H = 26;

    public ConfigOptionList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void addPublicEntry(Entry entry) { super.addEntry(entry); }

    @Override
    protected int getRowTop(int index) {
        int top = this.getY() + 4 - (int) this.getScrollAmount();
        for (int i = 0; i < index; i++) {
            top += this.children().get(i).getItemHeight();
        }
        return top;
    }

    @Override
    public int getRowBottom(int index) {
        return getRowTop(index) + this.children().get(index).getItemHeight();
    }

   @Override
    public int getMaxScroll() {
        int contentHeight = children().stream()
                .mapToInt(Entry::getItemHeight)
                .sum() + 8;
        return Math.max(0, contentHeight - (this.height - 8));
    }

    @Override public int getRowWidth()             { return this.width - 20; }
    @Override protected int getScrollbarPosition() { return this.getRight() - 6; }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) return false;

        // Обрабатываем клик по скроллбару напрямую
        if (button == 0) {
            int scrollbarX = getScrollbarPosition();
            if (mouseX >= scrollbarX && mouseX < scrollbarX + 6) {
                return super.mouseClicked(mouseX, mouseY, button);
            }
        }

        // Ищем entry через наши getRowTop/getRowBottom
        for (int i = 0; i < children().size(); i++) {
            int top    = getRowTop(i);
            int bottom = getRowBottom(i);
            if (mouseY >= top && mouseY < bottom) {
                Entry entry = children().get(i);
                if (entry.mouseClicked(mouseX, mouseY, button)) {
                    setFocused(entry);
                    setDragging(true);
                    return true;
                }
                return false;
            }
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Base Entry — общий контракт для всех строк списка
    // ═══════════════════════════════════════════════════════════════════════

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {

        protected final String labelKey;
        protected final String tooltipKey;

        protected Entry(String labelKey, String tooltipKey) {
            this.labelKey   = labelKey;
            this.tooltipKey = tooltipKey;
        }

        public abstract void saveSnapshot();

        public abstract void restoreSnapshot();

        public abstract void reset();

        public abstract boolean hasHardLimitViolation();

        public void inheritSnapshot(Entry donor) {}

        protected void drawLabel(GuiGraphics gfx, int x, int y) {
            Minecraft mc = Minecraft.getInstance();
            gfx.drawString(mc.font, Component.translatable(labelKey),
                    x + 4, y + (ENTRY_H - 8) / 2, 0xFFFFFF, false);
        }

        public int getItemHeight() { return ENTRY_H; }
    }
}