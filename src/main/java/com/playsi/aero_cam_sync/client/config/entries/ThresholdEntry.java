package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import com.playsi.aero_cam_sync.client.config.ui.SnapSlider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
 * Одна компактная строка порога: «метка — [Вкл/Выкл] — ползунок — поле ≥значение».
 * Когда критерий выключен, ползунок и поле гаснут (становятся неактивны).
 *
 * <p>Объединяет тумблер и значение в одну строку — для габаритов (длина/высота/ширина)
 * это вдвое компактнее и аккуратнее, чем отдельные строки.</p>
 */
public class ThresholdEntry extends ConfigOptionList.Entry {

    private interface CfgAdapter {
        double get();
        void   set(double v);
        double getDefault();
    }

    private static final int TOGGLE_W = 40;
    private static final int SLIDER_W = 84;
    private static final int BOX_W     = 50;
    private static final int WIDGET_H  = 18;
    private static final int GAP        = 4;

    private final ModConfigSpec.BooleanValue enableCfg;
    private final CfgAdapter adapter;
    private final boolean isInt;
    private final double sliderMin, sliderMax, hardMin, hardMax, step;

    private boolean enabled,  enabledSnapshot;
    private double  value,    valueSnapshot;

    private final Button   toggle;
    private SnapSlider     slider;
    private EditBox        box;

    private boolean syncingFromSlider = false;
    private boolean syncingFromBox    = false;

    public ThresholdEntry(String labelKey, String tooltipKey,
                          ModConfigSpec.BooleanValue enableCfg,
                          ModConfigSpec.DoubleValue valueCfg,
                          double sliderMin, double sliderMax, double step,
                          double hardMin, double hardMax) {
        super(labelKey, tooltipKey);
        this.enableCfg = enableCfg;
        this.adapter = new CfgAdapter() {
            public double get()         { return valueCfg.get(); }
            public void   set(double v) { valueCfg.set(v); }
            public double getDefault()  { return (double) valueCfg.getDefault(); }
        };
        this.isInt = false;
        this.sliderMin = sliderMin; this.sliderMax = sliderMax;
        this.hardMin = hardMin; this.hardMax = hardMax; this.step = step;
        this.enabled = enableCfg.get(); this.enabledSnapshot = enabled;
        this.value = valueCfg.get();    this.valueSnapshot = value;
        this.toggle = buildToggle();
        buildValueWidgets();
        applyEnabledState();
    }

    public ThresholdEntry(String labelKey, String tooltipKey,
                          ModConfigSpec.BooleanValue enableCfg,
                          ModConfigSpec.IntValue valueCfg,
                          int sliderMin, int sliderMax, int step,
                          int hardMin, int hardMax) {
        super(labelKey, tooltipKey);
        this.enableCfg = enableCfg;
        this.adapter = new CfgAdapter() {
            public double get()         { return valueCfg.get(); }
            public void   set(double v) { valueCfg.set((int) Math.round(v)); }
            public double getDefault()  { return (int) valueCfg.getDefault(); }
        };
        this.isInt = true;
        this.sliderMin = sliderMin; this.sliderMax = sliderMax;
        this.hardMin = hardMin; this.hardMax = hardMax; this.step = step;
        this.enabled = enableCfg.get(); this.enabledSnapshot = enabled;
        this.value = valueCfg.get();    this.valueSnapshot = value;
        this.toggle = buildToggle();
        buildValueWidgets();
        applyEnabledState();
    }

    // ── widgets ───────────────────────────────────────────────────────────────

    private Button buildToggle() {
        Button b = Button.builder(toggleLabel(enabled), btn -> {
            enabled = !enabled;
            enableCfg.set(enabled);
            btn.setMessage(toggleLabel(enabled));
            applyEnabledState();
        }).bounds(0, 0, TOGGLE_W, WIDGET_H).build();
        if (!tooltipKey.isEmpty())
            b.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
        return b;
    }

    private void buildValueWidgets() {
        slider = new SnapSlider(0, 0, SLIDER_W, WIDGET_H,
                sliderMin, sliderMax, step,
                Mth.clamp(value, sliderMin, sliderMax),
                v -> {
                    if (syncingFromBox) return;
                    syncingFromSlider = true;
                    value = v;
                    adapter.set(v);
                    if (!box.isFocused()) box.setValue(shortStr(v));
                    syncingFromSlider = false;
                });

        box = new EditBox(Minecraft.getInstance().font, 0, 0, BOX_W, WIDGET_H, Component.empty());
        box.setValue(shortStr(value));
        box.setMaxLength(16);
        if (isInt) box.setFilter(s -> s.matches("-?\\d*"));
        box.setResponder(text -> {
            if (syncingFromSlider) return;
            try {
                double parsed = isInt
                        ? Integer.parseInt(text.isEmpty() ? "0" : text)
                        : Double.parseDouble(text.replace(",", "."));
                value = parsed;
                if (parsed >= hardMin && parsed <= hardMax) adapter.set(parsed);
                syncingFromBox = true;
                slider.setExternalValue(Mth.clamp(parsed, sliderMin, sliderMax));
                syncingFromBox = false;
            } catch (NumberFormatException ignored) {}
        });
    }

    private void applyEnabledState() {
        slider.active = enabled;
        box.active = enabled;
        box.setEditable(enabled);
    }

    private static Component toggleLabel(boolean v) {
        return v ? Component.translatable("aero_cam_sync.configuration.toggle.on")
                 : Component.translatable("aero_cam_sync.configuration.toggle.off");
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top);

        int rightEdge = left + width - 4;
        int wy = top + (ENTRY_H - WIDGET_H) / 2;

        int boxX    = rightEdge - BOX_W;
        int sliderX = boxX    - GAP - SLIDER_W;
        int toggleX = sliderX - GAP - TOGGLE_W;

        toggle.setX(toggleX); toggle.setY(wy);
        slider.setX(sliderX); slider.setY(wy); slider.setWidth(SLIDER_W);
        box.setX(boxX);       box.setY(wy);     box.setWidth(BOX_W);

        boolean overBox = enabled && mouseX >= boxX && mouseX < boxX + BOX_W
                && mouseY >= wy && mouseY < wy + WIDGET_H;
        gfx.fill(boxX - 1, wy - 1, boxX + BOX_W + 1, wy + WIDGET_H + 1,
                enabled ? 0xFF555555 : 0xFF333333);

        if (!box.isFocused())
            box.setValue(overBox ? fullStr(value) : shortStr(value));

        toggle.render(gfx, mouseX, mouseY, delta);
        slider.render(gfx, mouseX, mouseY, delta);
        box.render(gfx, mouseX, mouseY, delta);
    }

    // ── formatting ──────────────────────────────────────────────────────────────

    private String shortStr(double v) {
        if (isInt) return String.valueOf((int) Math.round(v));
        String s = String.format("%.2f", v).replaceAll("0+$", "");
        if (s.endsWith(".") || s.endsWith(",")) s += "0";
        return s;
    }

    private String fullStr(double v) {
        if (isInt) return String.valueOf((int) Math.round(v));
        String s = String.format("%.4f", v).replaceAll("0+$", "");
        if (s.endsWith(".") || s.endsWith(",")) s += "0";
        return s;
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(toggle, slider, box); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(toggle, slider, box); }

    // ── snapshot / restore / reset ────────────────────────────────────────────

    @Override public void saveSnapshot() {
        enabledSnapshot = enabled;
        valueSnapshot = value;
    }

    @Override public void restoreSnapshot() {
        enabled = enabledSnapshot;
        value = valueSnapshot;
        enableCfg.set(enabled);
        adapter.set(value);
        toggle.setMessage(toggleLabel(enabled));
        slider.setExternalValue(Mth.clamp(value, sliderMin, sliderMax));
        box.setValue(shortStr(value));
        applyEnabledState();
    }

    @Override public void reset() {
        enabled = (boolean) enableCfg.getDefault();
        value = adapter.getDefault();
        enableCfg.set(enabled);
        adapter.set(value);
        toggle.setMessage(toggleLabel(enabled));
        slider.setExternalValue(Mth.clamp(value, sliderMin, sliderMax));
        box.setValue(shortStr(value));
        applyEnabledState();
    }

    @Override
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof ThresholdEntry d) {
            this.enabledSnapshot = d.enabledSnapshot;
            this.valueSnapshot = d.valueSnapshot;
        }
    }

    @Override public boolean hasHardLimitViolation() {
        return value < hardMin || value > hardMax;
    }
}
