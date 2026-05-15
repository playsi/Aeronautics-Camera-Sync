package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

import static com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList.ENTRY_H;

/**
 * Строка с чекбоксом. Если хочешь кнопку вкл/выкл — используй {@link ToggleButtonEntry}.
 */
public class BooleanEntry extends ConfigOptionList.Entry {

    private final ModConfigSpec.BooleanValue config;
    private final Checkbox checkbox;
    private boolean snapshot;

    public BooleanEntry(String labelKey, String tooltipKey,
                        ModConfigSpec.BooleanValue config) {
        super(labelKey, tooltipKey);
        this.config   = config;
        this.snapshot = config.get();

        this.checkbox = Checkbox.builder(Component.empty(), Minecraft.getInstance().font)
                .selected(config.get())
                .onValueChange((box, val) -> config.set(val))
                .build();

        if (!tooltipKey.isEmpty())
            checkbox.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top);
        checkbox.setX(left + width - 26);
        checkbox.setY(top + (ENTRY_H - checkbox.getHeight()) / 2);
        checkbox.render(gfx, mouseX, mouseY, delta);
    }

    @Override public List<? extends net.minecraft.client.gui.components.events.GuiEventListener> children()   { return List.of(checkbox); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(checkbox); }

    @Override public void saveSnapshot()    { snapshot = config.get(); }
    @Override public void restoreSnapshot() {
        config.set(snapshot);
        if (checkbox.selected() != snapshot) checkbox.onPress();
    }
    @Override public void reset() {
        boolean def = (boolean) config.getDefault();
        config.set(def);
        if (checkbox.selected() != def) checkbox.onPress();
    }
    @Override public boolean hasHardLimitViolation() { return false; }
}