package com.playsi.aero_cam_sync.client.config.entries;

import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * Строка blacklist-списка высотой {@value #H} px.
 * <p>
 * Layout (слева направо, сверху вниз):
 * <pre>
 * ┌─────────────────────────────────────────────────────────────────┐
 * │ ┌──────┐  [  EditBox с автодополнением         ] [✕]           │
 * │ │ item │  ─────────────────────────────────────────────         │
 * │ │ icon │  Item Display Name                                     │
 * └─────────────────────────────────────────────────────────────────┘
 * </pre>
 * Содержит вложенный {@link ItemIDElement} — компонент с EditBox
 * и выпадающим списком автодополнения.
 * <p>
 * Три состояния иконки:
 * <ul>
 *   <li>{@link IconState#OK}        — предмет найден, рендерится нормально</li>
 *   <li>{@link IconState#NOT_FOUND} — ID не найден в реестре → серый «?»</li>
 *   <li>{@link IconState#ERROR}     — исключение при рендере → красный «!»,
 *                                     тултип с текстом исключения</li>
 * </ul>
 */
public class ListEntry extends ConfigOptionList.Entry {

    /** Высота одной строки blacklist-элемента. */
    public static final int H = 64;

    private static final int ICON_SIZE  = 48;
    private static final int PAD        =  4;
    private static final int BTN_DEL_W  = 18;
    private static final int BTN_DEL_H  = 18;
    private static final int SEP_H      =  1;

    // ── состояние иконки ──────────────────────────────────────────────────────

    private enum IconState { OK, NOT_FOUND, ERROR }

    private IconState iconState = IconState.NOT_FOUND;
    /** Текст последнего исключения — показывается в тултипе при IconState.ERROR */
    private String lastError = "";

    // ── данные строки ─────────────────────────────────────────────────────────

    private String value;
    private final java.util.function.Consumer<String> onChanged;
    private final Runnable onDelete;

    // ── виджеты ──────────────────────────────────────────────────────────────

    private final ItemIDElement itemIDElement;
    private final Button deleteBtn;

    // ── кеш иконки ───────────────────────────────────────────────────────────

    private ItemStack cachedStack = ItemStack.EMPTY;
    private String    cachedId    = "";

    // ── геометрия (заполняется в render) ─────────────────────────────────────

    private int iconX, iconY;

    // ── конструктор ───────────────────────────────────────────────────────────

    public ListEntry(String initialValue,
                     java.util.function.Consumer<String> onChanged,
                     Runnable onDelete) {
        super("", "");
        this.value     = initialValue;
        this.onChanged = onChanged;
        this.onDelete  = onDelete;

        this.itemIDElement = new ItemIDElement(initialValue, newVal -> {
            this.value = newVal;
            invalidateCache();
            onChanged.accept(newVal);
        });

        this.deleteBtn = Button.builder(Component.literal("✕"), btn -> onDelete.run())
                .bounds(0, 0, BTN_DEL_W, BTN_DEL_H)
                .build();

        updateCache(initialValue);
    }

    // ── кеш ──────────────────────────────────────────────────────────────────

    private void invalidateCache() { cachedId = null; }

    private void updateCache(String id) {
        if (id == null || id.equals(cachedId)) return;
        cachedId   = id;
        lastError  = "";
        iconState  = IconState.NOT_FOUND;
        cachedStack = ItemStack.EMPTY;

        if (id.isEmpty()) return;

        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                if (item != null && item != Items.AIR) {
                    cachedStack = new ItemStack(item);
                    iconState   = IconState.OK;
                }
                // rl найден, но это AIR → NOT_FOUND (иконка останется пустой)
            }
            // rl == null или не в реестре → NOT_FOUND
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            iconState = IconState.ERROR;
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public int getItemHeight() { return H; }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left,
                       int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {

        Minecraft mc = Minecraft.getInstance();

        if (cachedId == null) updateCache(value);

        // ── превью-рамка ─────────────────────────────────────────────────────
        iconX = left + PAD;
        iconY = top  + (H - ICON_SIZE) / 2;

        int borderColor = (iconState == IconState.ERROR) ? 0xFF8B1A1A : 0xFF555555;
        int bgColor     = (iconState == IconState.ERROR) ? 0xFF2A0A0A : 0xFF1A1A1A;
        gfx.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, borderColor);
        gfx.fill(iconX,     iconY,     iconX + ICON_SIZE,     iconY + ICON_SIZE,     bgColor);

        renderIcon(gfx, mc);

        // ── тултип при наведении на рамку иконки ─────────────────────────────
        if (mouseX >= iconX && mouseX <= iconX + ICON_SIZE
                && mouseY >= iconY && mouseY <= iconY + ICON_SIZE) {
            renderIconTooltip(gfx, mc, mouseX, mouseY);
        }

        // ── правая колонка ────────────────────────────────────────────────────
        int colX    = iconX + ICON_SIZE + PAD;
        int colW    = left + width - colX - PAD;

        int topRowH = (H - SEP_H) / 2;
        int editH   = 16;
        int editY   = top + (topRowH - editH) / 2;

        int delX = left + width - PAD - BTN_DEL_W;
        deleteBtn.setX(delX);
        deleteBtn.setY(editY + (editH - BTN_DEL_H) / 2);
        deleteBtn.render(gfx, mouseX, mouseY, delta);

        int editW = delX - colX - PAD;
        itemIDElement.layout(colX, editY, editW, editH);
        itemIDElement.render(gfx, mouseX, mouseY, delta);

        // ── разделитель ───────────────────────────────────────────────────────
        int sepY = top + topRowH;
        gfx.fill(colX, sepY, left + width - PAD, sepY + SEP_H, 0xFF444444);

        // ── нижняя половина: название предмета ───────────────────────────────
        int nameY = iconY + ICON_SIZE - (ICON_SIZE / 4) - 4;

        Component name;
        if (!cachedStack.isEmpty()) {
            name = cachedStack.getHoverName();
        } else if (value.isEmpty()) {
            name = Component.translatable("aero_cam_sync.configuration.list.empty");
        } else {
            name = Component.literal(value).withStyle(s -> s.withColor(0xFF5555));
        }

        gfx.drawString(mc.font, name, colX + 2, nameY, 0xAAAAAA, false);
    }

    // ── рендер иконки ─────────────────────────────────────────────────────────

    private void renderIcon(GuiGraphics gfx, Minecraft mc) {
        switch (iconState) {
            case OK -> renderItemIcon(gfx, mc);
            case NOT_FOUND -> drawPlaceholder(gfx, mc, "?", 0x888888);
            case ERROR     -> drawPlaceholder(gfx, mc, "!", 0xFF5555);
        }
    }

    private void renderItemIcon(GuiGraphics gfx, Minecraft mc) {
        float scale = ICON_SIZE / 16f;
        gfx.pose().pushPose();
        gfx.pose().translate(iconX, iconY, 0);
        gfx.pose().scale(scale, scale, 1f);
        boolean ok = false;
        try {
            gfx.renderItem(cachedStack, 0, 0);
            ok = true;
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
            iconState = IconState.ERROR;
        }
        gfx.pose().popPose();

        if (!ok) {
            gfx.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, 0xFF8B1A1A);
            gfx.fill(iconX,     iconY,     iconX + ICON_SIZE,     iconY + ICON_SIZE,     0xFF2A0A0A);
            drawPlaceholder(gfx, mc, "!", 0xFF5555);
        }
    }

    private void drawPlaceholder(GuiGraphics gfx, Minecraft mc, String symbol, int color) {
        gfx.drawCenteredString(mc.font, symbol,
                iconX + ICON_SIZE / 2,
                iconY + (ICON_SIZE - 8) / 2,
                color);
    }

    // ── тултип иконки ─────────────────────────────────────────────────────────

    private void renderIconTooltip(GuiGraphics gfx, Minecraft mc, int mouseX, int mouseY) {
        List<Component> lines = new ArrayList<>();

        switch (iconState) {
            case OK -> {
                lines.add(cachedStack.getHoverName());
                lines.add(Component.translatable("aero_cam_sync.configuration.list.tooltip.ok",
                        value));
            }
            case NOT_FOUND -> {
                if (value.isEmpty()) {
                    lines.add(Component.translatable("aero_cam_sync.configuration.list.tooltip.empty"));
                } else {
                    lines.add(Component.translatable("aero_cam_sync.configuration.list.tooltip.not_found",
                            value));
                }
            }
            case ERROR -> {
                lines.add(Component.translatable("aero_cam_sync.configuration.list.tooltip.error"));
                // Имя класса исключения — отдельной строкой, красным
                lines.add(Component.literal(lastError).withStyle(s -> s.withColor(0xFF5555)));
            }
        }

        gfx.renderTooltip(mc.font, lines, java.util.Optional.empty(), mouseX, mouseY);
    }

    // ── children / narratables ────────────────────────────────────────────────

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(itemIDElement, deleteBtn);
    }

    @Override
    public List<? extends NarratableEntry> narratables() {
        return List.of(itemIDElement, deleteBtn);
    }

    // ── Entry contract ────────────────────────────────────────────────────────

    @Override public void saveSnapshot()             { }
    @Override public void restoreSnapshot()          { }
    @Override public void reset()                    { }
    @Override public boolean hasHardLimitViolation() { return false; }

    // ── геттер ───────────────────────────────────────────────────────────────

    public String getValue() { return value; }
}