package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * A visual separator in the settings list.
 *
 * <ul>
 *   <li><b>Without text</b> ({@code new SeparatorEntry()}): just a horizontal line, minimum row
 *       height (10 px).</li>
 *   <li><b>With text</b> ({@code new SeparatorEntry("my.translation.key")}): a section heading,
 *       centred text with a line beneath it, row height 18 px.</li>
 * </ul>
 *
 * It holds no value, so snapshot, restore and reset are empty and hasHardLimitViolation is always
 * false.
 */
public class SeparatorEntry extends ConfigOptionList.Entry {

    private static final int LINE_COLOR    = 0x88AAAAAA;
    private static final int TEXT_COLOR    = 0xFFDDDDDD;

    private static final int HEIGHT_LINE_ONLY = 10;
    private static final int HEIGHT_WITH_TEXT = 18;

    private final boolean hasText;
    private final int     rowHeight;

    public SeparatorEntry() {
        super("", "");
        this.hasText   = false;
        this.rowHeight = HEIGHT_LINE_ONLY;
    }

    /**
     * A section heading: text with a line beneath it.
     *
     * @param labelKey key for {@link Component#translatable}
     */
    public SeparatorEntry(String labelKey) {
        super(labelKey, "");
        this.hasText   = !labelKey.isEmpty();
        this.rowHeight = hasText ? HEIGHT_WITH_TEXT : HEIGHT_LINE_ONLY;
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {

        int lineY = top + rowHeight - 1;

        if (hasText) {
            Minecraft mc = Minecraft.getInstance();
            Component text = Component.translatable(labelKey);

            // Text centred on the row, above the line.
            int textX = left + width / 2;
            int textY = top + (rowHeight - mc.font.lineHeight) / 2 - 1;
            gfx.drawCenteredString(mc.font, text, textX, textY, TEXT_COLOR);
        }

        gfx.fill(left, lineY, left + width, lineY + 1, LINE_COLOR);
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(); }

    @Override public void saveSnapshot()              { /* no state */ }
    @Override public void restoreSnapshot()           { /* no state */ }
    @Override public void reset()                     { /* no state */ }
    @Override public boolean hasHardLimitViolation()  { return false; }
}