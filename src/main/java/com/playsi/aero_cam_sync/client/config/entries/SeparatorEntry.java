package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Визуальный разделитель в списке настроек.
 *
 * <ul>
 *   <li><b>Без текста</b> ({@code new SeparatorEntry()}) — просто горизонтальная линия,
 *       высота строки минимальная (10 px).</li>
 *   <li><b>С текстом</b> ({@code new SeparatorEntry("my.translation.key")}) — заголовок секции:
 *       текст по центру + линия под ним, высота строки 18 px.</li>
 * </ul>
 *
 * Разделитель не хранит никакого значения, поэтому snapshot/restore/reset — пустые,
 * а hasHardLimitViolation всегда false.
 */
public class SeparatorEntry extends ConfigOptionList.Entry {

    private static final int LINE_COLOR    = 0x88AAAAAA;
    private static final int TEXT_COLOR    = 0xFFDDDDDD;

    /** Высота строки когда нет текста */
    private static final int HEIGHT_LINE_ONLY = 10;
    /** Высота строки когда есть текст */
    private static final int HEIGHT_WITH_TEXT = 18;

    private final boolean hasText;
    private final int     rowHeight;

    // ── конструкторы ──────────────────────────────────────────────────────────

    /** Просто линия без текста */
    public SeparatorEntry() {
        super("", "");
        this.hasText   = false;
        this.rowHeight = HEIGHT_LINE_ONLY;
    }

    /**
     * Заголовок секции: текст + линия под ним.
     *
     * @param labelKey ключ для {@link Component#translatable}
     */
    public SeparatorEntry(String labelKey) {
        super(labelKey, "");
        this.hasText   = !labelKey.isEmpty();
        this.rowHeight = hasText ? HEIGHT_WITH_TEXT : HEIGHT_LINE_ONLY;
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {

        int lineY = top + rowHeight - 1;

        if (hasText) {
            Minecraft mc = Minecraft.getInstance();
            Component text = Component.translatable(labelKey);

            // Текст по центру строки (над линией)
            int textX = left + width / 2;
            int textY = top + (rowHeight - mc.font.lineHeight) / 2 - 1;
            gfx.drawCenteredString(mc.font, text, textX, textY, TEXT_COLOR);
        }

        gfx.fill(left, lineY, left + width, lineY + 1, LINE_COLOR);
    }

    // ── нет дочерних виджетов ─────────────────────────────────────────────────

    @Override public List<? extends GuiEventListener> children()    { return List.of(); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(); }

    // ── нет состояния ─────────────────────────────────────────────────────────

    @Override public void saveSnapshot()              { /* нет состояния */ }
    @Override public void restoreSnapshot()           { /* нет состояния */ }
    @Override public void reset()                     { /* нет состояния */ }
    @Override public boolean hasHardLimitViolation()  { return false; }
}