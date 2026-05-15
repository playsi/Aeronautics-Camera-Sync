package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import com.playsi.aero_cam_sync.client.config.ui.SnapSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

import static com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList.ENTRY_H;

/**
 * Строка с ползунком и полем ввода числа. Работает и для {@code DoubleValue}, и для {@code IntValue}.
 *
 * <ul>
 *   <li>sliderMin/sliderMax — диапазон ползунка (рамка желтеет, если вышли)</li>
 *   <li>hardMin/hardMax    — абсолютный предел (рамка краснеет, кнопка Save блокируется)</li>
 *   <li>step               — шаг ползунка</li>
 * </ul>
 */
public class SliderEntry extends ConfigOptionList.Entry {

    // ── адаптер ──────────────────────────────────────────────────────────────

    private interface CfgAdapter {
        double get();
        void   set(double v);
        double getDefault();
    }

    // ── поля ─────────────────────────────────────────────────────────────────

    private final CfgAdapter adapter;
    private final boolean    isInt;
    private final double     sliderMin, sliderMax;
    private final double     hardMin,   hardMax;
    private final double     step;

    private double currentValue;
    private double snapshot;

    private SnapSlider slider;
    private EditBox    editBox;

    private boolean syncingFromSlider = false;
    private boolean syncingFromBox    = false;

    // ── конструкторы ──────────────────────────────────────────────────────────

    /** Для {@link ModConfigSpec.DoubleValue} */
    public SliderEntry(String labelKey, String tooltipKey,
                       ModConfigSpec.DoubleValue config,
                       double sliderMin, double sliderMax, double step,
                       double hardMin,   double hardMax) {
        super(labelKey, tooltipKey);
        this.adapter   = new CfgAdapter() {
            public double get()         { return config.get(); }
            public void   set(double v) { config.set(v); }
            public double getDefault()  { return (double) config.getDefault(); }
        };
        this.isInt     = false;
        this.sliderMin = sliderMin; this.sliderMax = sliderMax;
        this.hardMin   = hardMin;   this.hardMax   = hardMax;
        this.step      = step;
        this.currentValue = config.get();
        this.snapshot     = currentValue;
        buildWidgets();
    }

    /** Для {@link ModConfigSpec.IntValue} */
    public SliderEntry(String labelKey, String tooltipKey,
                       ModConfigSpec.IntValue config,
                       int sliderMin, int sliderMax, int step,
                       int hardMin,   int hardMax) {
        super(labelKey, tooltipKey);
        this.adapter   = new CfgAdapter() {
            public double get()         { return config.get(); }
            public void   set(double v) { config.set((int) Math.round(v)); }
            public double getDefault()  { return (int) config.getDefault(); }
        };
        this.isInt     = true;
        this.sliderMin = sliderMin; this.sliderMax = sliderMax;
        this.hardMin   = hardMin;   this.hardMax   = hardMax;
        this.step      = step;
        this.currentValue = config.get();
        this.snapshot     = currentValue;
        buildWidgets();
    }

    // ── виджеты ──────────────────────────────────────────────────────────────

    private void buildWidgets() {
        slider = new SnapSlider(0, 0, 100, 20,
                sliderMin, sliderMax, step,
                Mth.clamp(currentValue, sliderMin, sliderMax),
                v -> {
                    if (syncingFromBox) return;
                    syncingFromSlider = true;
                    currentValue = v;
                    adapter.set(v);
                    if (!editBox.isFocused()) editBox.setValue(shortStr(v));
                    syncingFromSlider = false;
                });

        if (!tooltipKey.isEmpty())
            slider.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));

        editBox = new EditBox(Minecraft.getInstance().font, 0, 0, 68, 18, Component.empty());
        editBox.setValue(shortStr(currentValue));
        editBox.setMaxLength(16);
        if (isInt) editBox.setFilter(s -> s.matches("-?\\d*"));
        editBox.setResponder(text -> {
            if (syncingFromSlider) return;
            try {
                double parsed = isInt
                        ? Integer.parseInt(text.isEmpty() ? "0" : text)
                        : Double.parseDouble(text.replace(",", "."));
                currentValue = parsed;
                if (parsed >= hardMin && parsed <= hardMax) adapter.set(parsed);
                syncingFromBox = true;
                slider.setExternalValue(Mth.clamp(parsed, sliderMin, sliderMax));
                syncingFromBox = false;
            } catch (NumberFormatException ignored) {}
        });
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top);

        int sliderW   = 110;
        int boxW      = 68;
        int gap       = 4;
        int rightEdge = left + width - 4;

        int boxX = rightEdge - boxW;
        int boxY = top + (ENTRY_H - 18) / 2;
        int slX  = boxX - gap - sliderW;
        int slY  = top + (ENTRY_H - 20) / 2;

        slider.setX(slX); slider.setY(slY); slider.setWidth(sliderW);
        editBox.setX(boxX); editBox.setY(boxY); editBox.setWidth(boxW);

        boolean mouseOverBox = mouseX >= boxX && mouseX < boxX + boxW
                && mouseY >= boxY && mouseY < boxY + 18;
        gfx.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + 19, borderColor());

        if (!editBox.isFocused())
            editBox.setValue(mouseOverBox ? fullStr(currentValue) : shortStr(currentValue));

        slider.render(gfx, mouseX, mouseY, delta);
        editBox.render(gfx, mouseX, mouseY, delta);
    }

    private int borderColor() {
        if (currentValue < hardMin  || currentValue > hardMax)    return 0xFFFF4444;
        if (currentValue < sliderMin || currentValue > sliderMax) return 0xFFFFAA00;
        return 0xFF555555;
    }

    // ── форматирование ────────────────────────────────────────────────────────

    private String shortStr(double v) {
        if (isInt) return String.valueOf((int) Math.round(v));
        String s = String.format("%.3f", v).replaceAll("0+$", "");
        if (s.endsWith(".") || s.endsWith(",")) s += "0";
        return s;
    }

    private String fullStr(double v) {
        if (isInt) return String.valueOf((int) Math.round(v));
        String s = String.format("%.6f", v).replaceAll("0+$", "");
        if (s.endsWith(".") || s.endsWith(",")) s += "0";
        return s;
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(slider, editBox); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(slider, editBox); }

    @Override public void saveSnapshot()    { snapshot = currentValue; }
    @Override public void restoreSnapshot() {
        currentValue = snapshot;
        adapter.set(snapshot);
        slider.setExternalValue(Mth.clamp(snapshot, sliderMin, sliderMax));
        editBox.setValue(shortStr(snapshot));
    }
    @Override public void reset() {
        double def = adapter.getDefault();
        currentValue = def;
        adapter.set(def);
        slider.setExternalValue(Mth.clamp(def, sliderMin, sliderMax));
        editBox.setValue(shortStr(def));
    }
    @Override public boolean hasHardLimitViolation() {
        return currentValue < hardMin || currentValue > hardMax;
    }
}