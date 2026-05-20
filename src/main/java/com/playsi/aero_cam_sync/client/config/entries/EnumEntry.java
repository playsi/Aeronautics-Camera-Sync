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
import java.util.function.Function;

import static com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList.ENTRY_H;

/**
 * Строка со кнопкой-«крутилкой» для enum-значений.
 * <p>
 * Клик по кнопке циклически переключает варианты перечисления.
 * Размер и позиция кнопки совпадают с {@link ToggleButtonEntry}: 68×18 px,
 * прижата к правому краю с отступом 4 px.
 *
 * @param <E> тип перечисления
 */
public class EnumEntry<E extends Enum<E>> extends ConfigOptionList.Entry {

    private static final int BTN_W = 68;
    private static final int BTN_H = 18;

    // ── адаптер конфига ───────────────────────────────────────────────────────

    private interface CfgAdapter<E> {
        E    get();
        void set(E value);
        E    getDefault();
    }

    // ── поля ─────────────────────────────────────────────────────────────────

    private final E[]              values;
    private final CfgAdapter<E>    adapter;
    private final Function<E, Component> labelOf;

    private E currentValue;
    private E snapshot;

    private final Button button;

    // ── конструктор ───────────────────────────────────────────────────────────

    /**
     * @param labelKey   ключ i18n для подписи строки слева
     * @param tooltipKey ключ i18n тултипа (пустая строка — без тултипа)
     * @param config     {@link ModConfigSpec.EnumValue} из конфига
     * @param values     все варианты перечисления, например {@code MyEnum.values()}
     * @param labelOf    функция «enum → отображаемый Component»
     */
    public EnumEntry(String labelKey, String tooltipKey,
                     ModConfigSpec.EnumValue<E> config,
                     E[] values,
                     Function<E, Component> labelOf) {
        super(labelKey, tooltipKey);

        this.values  = values;
        this.labelOf = labelOf;
        this.adapter = new CfgAdapter<>() {
            public E    get()          { return config.get(); }
            public void set(E value)   { config.set(value); }
            public E getDefault()      { return config.getDefault(); }
        };

        this.currentValue = config.get();
        this.snapshot     = currentValue;

        this.button = Button.builder(labelOf.apply(currentValue), btn -> {
                    currentValue = next(currentValue);
                    adapter.set(currentValue);
                    btn.setMessage(labelOf.apply(currentValue));
                })
                .bounds(0, 0, BTN_W, BTN_H)
                .build();

        if (!tooltipKey.isEmpty())
            button.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    // ── вспомогательное ───────────────────────────────────────────────────────

    /** Следующий вариант по кругу. */
    private E next(E current) {
        int idx = (current.ordinal() + 1) % values.length;
        return values[idx];
    }

    // ── render ────────────────────────────────────────────────────────────────

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
        adapter.set(snapshot);
        button.setMessage(labelOf.apply(snapshot));
    }

    @Override public void reset() {
        E def = adapter.getDefault();
        currentValue = def;
        adapter.set(def);
        button.setMessage(labelOf.apply(def));
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}