package com.playsi.aero_cam_sync.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;
import java.util.function.DoubleConsumer;

public class ConfigOptionList extends ContainerObjectSelectionList<ConfigOptionList.Entry> {

    private static final int ENTRY_H = 26;

    public ConfigOptionList(Minecraft mc, int width, int height, int y, int itemHeight) {
        super(mc, width, height, y, itemHeight);
    }

    public void addPublicEntry(Entry entry) { super.addEntry(entry); }

    @Override
    public int getRowWidth() { return this.width - 20; }

    @Override
    protected int getScrollbarPosition() { return this.getRight() - 6; }

    // ═══════════════════════════════════════════════════════════════════════
    // Base Entry
    // ═══════════════════════════════════════════════════════════════════════

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        protected final String labelKey;
        protected final String tooltipKey;

        protected Entry(String labelKey, String tooltipKey) {
            this.labelKey   = labelKey;
            this.tooltipKey = tooltipKey;
        }

        /** Зафиксировать текущее значение конфига (вызвать при открытии экрана) */
        public abstract void saveSnapshot();
        /** Восстановить значение на момент открытия (Cancel) */
        public abstract void restoreSnapshot();
        /** Сбросить к default */
        public abstract void reset();
        /** true если значение вышло за hardMin/hardMax */
        public abstract boolean hasHardLimitViolation();

        protected void drawLabel(GuiGraphics gfx, int x, int y) {
            Minecraft mc = Minecraft.getInstance();
            gfx.drawString(mc.font, Component.translatable(labelKey),
                    x + 4, y + (ENTRY_H - 8) / 2, 0xFFFFFF, false);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // BooleanEntry
    // ═══════════════════════════════════════════════════════════════════════

    public static class BooleanEntry extends Entry {

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
                    .onValueChange((box, val) -> config.set(val)) // live apply
                    .build();

            if (!tooltipKey.isEmpty()) {
                checkbox.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }
        }

        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float delta) {
            drawLabel(gfx, left, top);
            checkbox.setX(left + width - 26);
            checkbox.setY(top + (ENTRY_H - checkbox.getHeight()) / 2);
            checkbox.render(gfx, mouseX, mouseY, delta);
        }

        @Override public List<? extends GuiEventListener> children()   { return List.of(checkbox); }
        @Override public List<? extends NarratableEntry>  narratables() { return List.of(checkbox); }

        @Override public void saveSnapshot()           { snapshot = config.get(); }
        @Override public void restoreSnapshot()        {
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

    // ═══════════════════════════════════════════════════════════════════════
    // SliderEntry — единый для double и int
    //
    // sliderMin/sliderMax  — диапазон ползунка  (жёлтое поле если вышел)
    // hardMin/hardMax      — абсолютный предел  (красное поле, блокирует Save)
    // step                 — шаг ползунка
    // isInt                — округлять до целых
    // ═══════════════════════════════════════════════════════════════════════

    public static class SliderEntry extends Entry {

        // Адаптер чтобы не дублировать логику для IntValue / DoubleValue
        private interface CfgAdapter {
            double get();
            void   set(double v);
            double getDefault();
        }

        private final CfgAdapter adapter;
        private final boolean    isInt;
        private final double     sliderMin, sliderMax;
        private final double     hardMin,   hardMax;
        private final double     step;

        private double  currentValue;
        private double  snapshot;

        private SnapSlider slider;
        private EditBox    editBox;

        private boolean syncingFromSlider = false;
        private boolean syncingFromBox    = false;

        // ── конструктор для DoubleValue ──────────────────────────────────

        public SliderEntry(String labelKey, String tooltipKey,
                           ModConfigSpec.DoubleValue config,
                           double sliderMin, double sliderMax, double step,
                           double hardMin,   double hardMax) {
            super(labelKey, tooltipKey);
            this.adapter   = new CfgAdapter() {
                public double get()          { return config.get(); }
                public void   set(double v)  { config.set(v); }
                public double getDefault()   { return (double) config.getDefault(); }
            };
            this.isInt     = false;
            this.sliderMin = sliderMin; this.sliderMax = sliderMax;
            this.hardMin   = hardMin;   this.hardMax   = hardMax;
            this.step      = step;
            this.currentValue = config.get();
            this.snapshot     = currentValue;
            buildWidgets();
        }

        // ── конструктор для IntValue ─────────────────────────────────────

        public SliderEntry(String labelKey, String tooltipKey,
                           ModConfigSpec.IntValue config,
                           int sliderMin, int sliderMax, int step,
                           int hardMin,   int hardMax) {
            super(labelKey, tooltipKey);
            this.adapter   = new CfgAdapter() {
                public double get()          { return config.get(); }
                public void   set(double v)  { config.set((int) Math.round(v)); }
                public double getDefault()   { return (int) config.getDefault(); }
            };
            this.isInt     = true;
            this.sliderMin = sliderMin; this.sliderMax = sliderMax;
            this.hardMin   = hardMin;   this.hardMax   = hardMax;
            this.step      = step;
            this.currentValue = config.get();
            this.snapshot     = currentValue;
            buildWidgets();
        }

        // ── виджеты ─────────────────────────────────────────────────────

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

            if (!tooltipKey.isEmpty()) {
                slider.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            }

            editBox = new EditBox(Minecraft.getInstance().font, 0, 0, 68, 18, Component.empty());
            editBox.setValue(shortStr(currentValue));
            editBox.setMaxLength(16);
            if (isInt) editBox.setFilter(s -> s.matches("-?\\d*"));
            editBox.setResponder(text -> {
                if (syncingFromSlider) return;
                try {
                    double parsed = isInt
                            ? Integer.parseInt(text.isEmpty() ? "0" : text)
                            : Double.parseDouble(text);
                    currentValue = parsed;
                    adapter.set(parsed);
                    syncingFromBox = true;
                    slider.setExternalValue(Mth.clamp(parsed, sliderMin, sliderMax));
                    syncingFromBox = false;
                } catch (NumberFormatException ignored) {}
            });
        }

        // ── рендер ──────────────────────────────────────────────────────

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

            // Цвет рамки EditBox
            boolean mouseOverBox = mouseX >= boxX && mouseX < boxX + boxW
                    && mouseY >= boxY && mouseY < boxY + 18;
            int borderColor = borderColor();
            gfx.fill(boxX - 1, boxY - 1, boxX + boxW + 1, boxY + 19, borderColor);

            // Обновить формат отображения
            if (!editBox.isFocused()) {
                editBox.setValue(mouseOverBox ? fullStr(currentValue) : shortStr(currentValue));
            }

            slider.render(gfx, mouseX, mouseY, delta);
            editBox.render(gfx, mouseX, mouseY, delta);
        }

        private int borderColor() {
            if (currentValue < hardMin  || currentValue > hardMax)    return 0xFFFF4444; // красный
            if (currentValue < sliderMin || currentValue > sliderMax) return 0xFFFFAA00; // жёлтый
            return 0xFF555555; // обычный
        }

        // ── форматирование ───────────────────────────────────────────────

        private String shortStr(double v) {
            if (isInt) return String.valueOf((int) Math.round(v));
            return String.format("%.3f", v);
        }

        private String fullStr(double v) {
            if (isInt) return String.valueOf((int) Math.round(v));
            // убираем лишние нули
            String s = String.format("%.6f", v).replaceAll("0+$", "");
            if (s.endsWith(".")) s += "0";
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

    // ═══════════════════════════════════════════════════════════════════════
    // SnapSlider — AbstractSliderButton с шагом и внешним set
    // ═══════════════════════════════════════════════════════════════════════

    static class SnapSlider extends AbstractSliderButton {

        private final double min, max, step;
        private final DoubleConsumer onChange;

        SnapSlider(int x, int y, int w, int h,
                   double min, double max, double step,
                   double initial, DoubleConsumer onChange) {
            super(x, y, w, h, Component.empty(), normalise(initial, min, max));
            this.min      = min;
            this.max      = max;
            this.step     = step;
            this.onChange = onChange;
            updateMessage();
        }

        private static double normalise(double v, double min, double max) {
            return (max - min) == 0 ? 0 : Mth.clamp((v - min) / (max - min), 0, 1);
        }

        private double real() {
            double raw     = min + this.value * (max - min);
            double snapped = Math.round(raw / step) * step;
            return Mth.clamp(snapped, min, max);
        }

        public void setExternalValue(double realVal) {
            this.value = normalise(realVal, min, max);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            double v = real();
            if (v == Math.floor(v) && !Double.isInfinite(v))
                setMessage(Component.literal(String.valueOf((int) v)));
            else
                setMessage(Component.literal(String.format("%.3f", v)));
        }

        @Override
        protected void applyValue() {
            onChange.accept(real());
        }
    }
}