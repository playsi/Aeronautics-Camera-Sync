package com.playsi.aero_cam_sync.client.config.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.network.chat.Component;

public class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Entry> {

    /** Высота строки по умолчанию — используется во всех Entry */
    public static final int ENTRY_H = 26;

    public ConfigOptionList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void addPublicEntry(Entry entry) { super.addEntry(entry); }

    @Override public int getRowWidth()             { return this.width - 20; }
    @Override protected int getScrollbarPosition() { return this.getRight() - 6; }

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

        /** Зафиксировать текущее значение конфига (вызывается при открытии экрана) */
        public abstract void saveSnapshot();

        /** Восстановить значение на момент открытия (Cancel) */
        public abstract void restoreSnapshot();

        /** Сбросить к default */
        public abstract void reset();

        /** true если значение вышло за hardMin/hardMax — блокирует кнопку Save */
        public abstract boolean hasHardLimitViolation();

        /** Утилита: нарисовать лейбл слева */
        protected void drawLabel(GuiGraphics gfx, int x, int y) {
            Minecraft mc = Minecraft.getInstance();
            gfx.drawString(mc.font, Component.translatable(labelKey),
                    x + 4, y + (ENTRY_H - 8) / 2, 0xFFFFFF, false);
        }
    }
}