package com.playsi.aero_cam_sync.client.config.entries;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * EditBox с выпадающим списком автодополнения ID предметов.
 * <p>
 * Работает как самостоятельный {@link GuiEventListener} — его можно
 * добавить в {@code children()} родительского Entry.
 * <p>
 * Автодополнение фильтрует {@link BuiltInRegistries#ITEM} по введённому тексту
 * (поиск substring, не только prefix). Показывает максимум {@value #MAX_SUGGESTIONS}
 * подсказок; Tab/Click — применяют выбранную.
 */
public class ItemIDElement implements GuiEventListener, NarratableEntry {

    private static final int MAX_SUGGESTIONS = 7;
    private static final int ROW_H           = 12;
    private static final int DROPDOWN_PAD    =  2;

    // ── виджеты ──────────────────────────────────────────────────────────────

    private final EditBox editBox;

    // ── позиция / размер (задаются через layout()) ────────────────────────────

    private int x, y, w, h;

    // ── автодополнение ────────────────────────────────────────────────────────

    private final List<String> suggestions = new ArrayList<>();
    private int selectedSuggestion = -1;
    private boolean dropdownVisible = false;

    // ── состояние ────────────────────────────────────────────────────────────

    private boolean focused = false;

    // ── конструктор ───────────────────────────────────────────────────────────

    public ItemIDElement(String initialValue, Consumer<String> onChange) {
        Minecraft mc = Minecraft.getInstance();

        editBox = new EditBox(mc.font, 0, 0, 100, 16,
                Component.translatable("aero_cam_sync.configuration.list.placeholder"));
        editBox.setMaxLength(256);
        editBox.setValue(initialValue);
        editBox.moveCursorTo(0, false);
        editBox.setResponder(text -> {
            onChange.accept(text);
            rebuildSuggestions(text);
        });
    }

    // ── layout (вызывать каждый фрейм из render родителя) ────────────────────

    public void layout(int x, int y, int w, int h) {
        this.x = x; this.y = y; this.w = w; this.h = h;
        editBox.setX(x);
        editBox.setY(y);
        editBox.setWidth(w);
    }

    // ── автодополнение ────────────────────────────────────────────────────────

    private void rebuildSuggestions(String input) {
        suggestions.clear();
        selectedSuggestion = -1;

        if (input.isEmpty()) {
            dropdownVisible = false;
            return;
        }

        String lower = input.toLowerCase();
        for (ResourceLocation rl : BuiltInRegistries.ITEM.keySet()) {
            String id = rl.toString();
            if (id.contains(lower)) {
                suggestions.add(id);
                if (suggestions.size() >= MAX_SUGGESTIONS) break;
            }
        }

        dropdownVisible = !suggestions.isEmpty() && focused;
    }

    private void applySuggestion(int idx) {
        if (idx < 0 || idx >= suggestions.size()) return;
        String chosen = suggestions.get(idx);
        editBox.setValue(chosen);
        // setValue не вызывает responder автоматически во всех версиях MC,
        // поэтому дёргаем вручную через симуляцию — используем внутренний responder
        // через setValue который уже зарегистрирован в setResponder выше.
        dropdownVisible = false;
        suggestions.clear();
    }

    // ── render ────────────────────────────────────────────────────────────────

    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        editBox.render(gfx, mouseX, mouseY, delta);
        renderDropdown(gfx,mouseX, mouseY, delta);
    }

    public void renderDropdown(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        if (!dropdownVisible || suggestions.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();

        int dropX = x;
        int dropY = y + h + 1;
        int dropW = w;
        int dropH = suggestions.size() * ROW_H + DROPDOWN_PAD * 2;

        // фон выпадашки
        gfx.fill(dropX,     dropY,     dropX + dropW,     dropY + dropH,     0xFF111111);
        gfx.fill(dropX - 1, dropY - 1, dropX + dropW + 1, dropY + dropH + 1, 0xFF555555);
        gfx.fill(dropX,     dropY,     dropX + dropW,     dropY + dropH,     0xE8111111);

        for (int i = 0; i < suggestions.size(); i++) {
            int rowY  = dropY + DROPDOWN_PAD + i * ROW_H;
            boolean hovered   = mouseX >= dropX && mouseX <= dropX + dropW
                    && mouseY >= rowY  && mouseY <= rowY + ROW_H;
            boolean selected  = i == selectedSuggestion;

            if (selected || hovered) {
                gfx.fill(dropX, rowY, dropX + dropW, rowY + ROW_H, 0x80FFFFFF);
            }

            int color = selected ? 0xFFFFFF : (hovered ? 0xDDDDDD : 0xAAAAAA);
            gfx.drawString(mc.font, suggestions.get(i),
                    dropX + DROPDOWN_PAD, rowY + (ROW_H - 8) / 2, color, false);
        }
    }

    // ── GuiEventListener ──────────────────────────────────────────────────────

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (dropdownVisible && !suggestions.isEmpty()) {
            int dropX = x, dropY = y + h + 1, dropW = w;

            for (int i = 0; i < suggestions.size(); i++) {
                int rowY = dropY + DROPDOWN_PAD + i * ROW_H;
                if (mouseX >= dropX && mouseX <= dropX + dropW
                        && mouseY >= rowY && mouseY <= rowY + ROW_H) {
                    applySuggestion(i);
                    return true;
                }
            }

            dropdownVisible = false;
            return false;
        }

        return editBox.mouseClicked(mouseX, mouseY, button);
    }
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Tab — применить первую / выбранную подсказку
        if (keyCode == 258 /* Tab */ && dropdownVisible && !suggestions.isEmpty()) {
            applySuggestion(selectedSuggestion >= 0 ? selectedSuggestion : 0);
            return true;
        }
        // Стрелки вверх/вниз — навигация по выпадашке
        if (dropdownVisible) {
            if (keyCode == 264 /* Down */ ) {
                selectedSuggestion = Math.min(selectedSuggestion + 1, suggestions.size() - 1);
                return true;
            }
            if (keyCode == 265 /* Up */ ) {
                selectedSuggestion = Math.max(selectedSuggestion - 1, 0);
                return true;
            }
            if (keyCode == 257 /* Enter */ || keyCode == 335 /* numpad Enter */ ) {
                if (selectedSuggestion >= 0) { applySuggestion(selectedSuggestion); return true; }
            }
            if (keyCode == 256 /* Escape */ ) {
                dropdownVisible = false;
                return true;
            }
        }
        return editBox.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        return editBox.charTyped(c, modifiers);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        boolean insideMain =
                mouseX >= x && mouseX <= x + w &&
                        mouseY >= y && mouseY <= y + h;

        if (insideMain) return true;

        if (dropdownVisible) {
            int dropY = y + h + 1;
            int dropH = suggestions.size() * ROW_H + DROPDOWN_PAD * 2;

            boolean insideDrop =
                    mouseX >= x && mouseX <= x + w &&
                            mouseY >= dropY && mouseY <= dropY + dropH;

            return insideDrop;
        }

        return false;
    }

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
        editBox.setFocused(focused);
        if (!focused) {
            dropdownVisible = false;
        } else {
            rebuildSuggestions(editBox.getValue());
        }
    }

    @Override
    public boolean isFocused() { return focused; }

    // ── NarratableEntry ───────────────────────────────────────────────────────

    @Override
    public NarrationPriority narrationPriority() {
        return focused ? NarrationPriority.FOCUSED : NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, editBox.getValue());
    }

    // ── публичный геттер ──────────────────────────────────────────────────────

    public String getValue() { return editBox.getValue(); }
}