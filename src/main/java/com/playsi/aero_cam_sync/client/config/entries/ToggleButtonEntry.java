package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

import static com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList.ENTRY_H;

/**
 * Строка с кнопкой ВКЛ / ВЫКЛ.
 * <p>
 * Размер и позиция кнопки намеренно совпадают с полем ввода числа в {@link SliderEntry}:
 * ширина 68 px, высота 18 px, прижата к правому краю с отступом 4 px.
 */
public class ToggleButtonEntry extends ConfigOptionList.Entry {

    private static final int BTN_W = 68;
    private static final int BTN_H = 18;

    private final ModConfigSpec.BooleanValue config;
    private boolean currentValue;
    private boolean snapshot;

    private final Button button;

    public ToggleButtonEntry(String labelKey, String tooltipKey,
                             ModConfigSpec.BooleanValue config) {
        super(labelKey, tooltipKey);
        this.config       = config;
        this.currentValue = config.get();
        this.snapshot     = currentValue;

        this.button = Button.builder(label(currentValue), btn -> {
                    currentValue = !currentValue;
                    config.set(currentValue);
                    btn.setMessage(label(currentValue));
                })
                .bounds(0, 0, BTN_W, BTN_H)
                .build();

        if (!tooltipKey.isEmpty())
            button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    // ── метки ────────────────────────────────────────────────────────────────

    private static Component label(boolean value) {
        return value
                ? Component.translatable("aero_cam_sync.configuration.toggle.on")
                : Component.translatable("aero_cam_sync.configuration.toggle.off");
    }

    // ── render ───────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top);

        int rightEdge = left + width - 4;
        int btnX = rightEdge - BTN_W;
        int btnY = top + (ENTRY_H - BTN_H) / 2;

        button.setX(btnX);
        button.setY(btnY);
        button.setWidth(BTN_W);

        button.render(gfx, mouseX, mouseY, delta);
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(button); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(button); }

    // ── snapshot / restore / reset ────────────────────────────────────────────

    @Override public void saveSnapshot() { snapshot = currentValue; }

    @Override public void restoreSnapshot() {
        currentValue = snapshot;
        config.set(snapshot);
        button.setMessage(label(snapshot));
    }

    @Override public void reset() {
        boolean def = (boolean) config.getDefault();
        currentValue = def;
        config.set(def);
        button.setMessage(label(def));
    }

    @Override
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof ToggleButtonEntry d) this.snapshot = d.snapshot;
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}