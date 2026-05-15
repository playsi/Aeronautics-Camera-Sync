package com.playsi.aero_cam_sync.client.config.ui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.function.DoubleConsumer;

/**
 * Слайдер с шагом (step) и возможностью программно задать значение (setExternalValue).
 */
public class SnapSlider extends AbstractSliderButton {

    private final double min, max, step;
    private final DoubleConsumer onChange;

    public SnapSlider(int x, int y, int w, int h,
                      double min, double max, double step,
                      double initial, DoubleConsumer onChange) {
        super(x, y, w, h, Component.empty(), normalise(initial, min, max));
        this.min      = min;
        this.max      = max;
        this.step     = step;
        this.onChange = onChange;
        updateMessage();
    }

    // ── внутренние утилиты ────────────────────────────────────────────────────

    private static double normalise(double v, double min, double max) {
        return (max - min) == 0 ? 0 : Mth.clamp((v - min) / (max - min), 0, 1);
    }

    private double real() {
        double raw     = min + this.value * (max - min);
        double snapped = Math.round(raw / step) * step;
        return Mth.clamp(snapped, min, max);
    }

    // ── публичный API ─────────────────────────────────────────────────────────

    /** Установить значение извне (например, из EditBox) без зацикливания. */
    public void setExternalValue(double realVal) {
        this.value = normalise(realVal, min, max);
        updateMessage();
    }

    // ── AbstractSliderButton ──────────────────────────────────────────────────

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