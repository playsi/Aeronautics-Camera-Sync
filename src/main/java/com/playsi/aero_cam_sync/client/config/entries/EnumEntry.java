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
 * A row with a cycling button for enum values.
 * <p>
 * Clicking the button cycles through the enum constants. Its size and position match
 * {@link ToggleButtonEntry}: 68x18 px, flush right with a 4 px margin.
 *
 * @param <E> the enum type
 */
public class EnumEntry<E extends Enum<E>> extends ConfigOptionList.Entry {

    private static final int BTN_W = 68;
    private static final int BTN_H = 18;

    private interface CfgAdapter<E> {
        E    get();
        void set(E value);
        E    getDefault();
    }

    private final E[]              values;
    private final CfgAdapter<E>    adapter;
    private final Function<E, Component> labelOf;

    private E currentValue;
    private E snapshot;

    private final Button button;

    /**
     * @param labelKey   i18n key for the label on the left
     * @param tooltipKey i18n key for the tooltip (empty string means no tooltip)
     * @param config     the {@link ModConfigSpec.EnumValue} from the config
     * @param values     every enum constant, e.g. {@code MyEnum.values()}
     * @param labelOf    a function mapping an enum constant to its displayed Component
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

    private E next(E current) {
        int idx = (current.ordinal() + 1) % values.length;
        return values[idx];
    }

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

    @Override
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof EnumEntry<?> d) this.snapshot = (E) d.snapshot;
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}