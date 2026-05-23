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
 */
public class ListEntry extends ConfigOptionList.Entry {

    /** Высота одной строки blacklist-элемента. */
    public static final int H = 64;

    private static final int ICON_SIZE  = 48; // px, квадрат превью
    private static final int PAD        =  4;
    private static final int BTN_DEL_W  = 18;
    private static final int BTN_DEL_H  = 18;
    private static final int SEP_H      =  1;

    // ── состояние ────────────────────────────────────────────────────────────

    private String value;
    private final java.util.function.Consumer<String> onChanged;
    private final Runnable onDelete;

    // ── виджеты ──────────────────────────────────────────────────────────────

    private final ItemIDElement itemIDElement;
    private final Button deleteBtn;

    // ── кеш ──────────────────────────────────────────────────────────────────

    private ItemStack cachedStack = ItemStack.EMPTY;
    private String    cachedId    = "";

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

    // ── превью-стек ───────────────────────────────────────────────────────────

    private void invalidateCache() { cachedId = null; } // null = грязный

    private void updateCache(String id) {
        if (id.equals(cachedId)) return;
        cachedId = id;
        try {
            ResourceLocation rl = ResourceLocation.tryParse(id);
            if (rl != null && BuiltInRegistries.ITEM.containsKey(rl)) {
                Item item = BuiltInRegistries.ITEM.get(rl);
                cachedStack = item != Items.AIR ? new ItemStack(item) : ItemStack.EMPTY;
            } else {
                cachedStack = ItemStack.EMPTY;
            }
        } catch (Exception e) {
            cachedStack = ItemStack.EMPTY;
        }
    }

    // ── render ────────────────────────────────────────────────────────────────

    @Override
    public int getItemHeight() { return H; } // 64

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left,
                       int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {

        Minecraft mc = Minecraft.getInstance();

        // Грязный кеш — обновить
        if (cachedId == null) updateCache(value);

        // ── превью-рамка ─────────────────────────────────────────────────────
        int iconX = left + PAD;
        int iconY = top  + (H - ICON_SIZE) / 2;

        // фон рамки
        gfx.fill(iconX - 1, iconY - 1, iconX + ICON_SIZE + 1, iconY + ICON_SIZE + 1, 0xFF555555);
        gfx.fill(iconX,     iconY,     iconX + ICON_SIZE,     iconY + ICON_SIZE,     0xFF1A1A1A);

        if (!cachedStack.isEmpty()) {
            // Рендер иконки предмета масштабированно (16→ICON_SIZE)
            float scale = ICON_SIZE / 16f;
            gfx.pose().pushPose();
            gfx.pose().translate(iconX, iconY, 0);
            gfx.pose().scale(scale, scale, 1f);
            gfx.renderItem(cachedStack, 0, 0);
            gfx.pose().popPose();
        } else {
            // Плейсхолдер: «?» по центру рамки
            gfx.drawCenteredString(mc.font, "?",
                    iconX + ICON_SIZE / 2,
                    iconY + (ICON_SIZE - 8) / 2,
                    0x888888);
        }

        // ── правая колонка ────────────────────────────────────────────────────
        int colX    = iconX + ICON_SIZE + PAD;
        int colW    = left + width - colX - PAD;

        // верхняя половина: EditBox + кнопка удаления
        int topRowH = (H - SEP_H) / 2;
        int editH   = 16;
        int editY   = top + (topRowH - editH) / 2;

        // кнопка «✕»
        int delX = left + width - PAD - BTN_DEL_W;
        deleteBtn.setX(delX);
        deleteBtn.setY(editY + (editH - BTN_DEL_H) / 2);
        deleteBtn.render(gfx, mouseX, mouseY, delta);

        // ItemIDElement (EditBox + dropdown)
        int editW = delX - colX - PAD;
        itemIDElement.layout(colX, editY, editW, editH);
        itemIDElement.render(gfx, mouseX, mouseY, delta);

        // ── разделитель ───────────────────────────────────────────────────────
        int sepY = top + topRowH;
        gfx.fill(colX, sepY, left + width - PAD, sepY + SEP_H, 0xFF444444);

        // ── нижняя половина: translatable название предмета ──────────────────
        int nameY = iconY + ICON_SIZE - (ICON_SIZE / 4) - 4;

        Component name;
        if (!cachedStack.isEmpty()) {
            name = cachedStack.getHoverName();
        } else if (value.isEmpty()) {
            name = Component.translatable("aero_cam_sync.configuration.list.empty");
        } else {
            name = Component.literal(value).withStyle(s -> s.withColor(0xFF5555));
        }

        gfx.drawString(mc.font, name,
                colX + 2, nameY,  // +2 чуть правее
                0xAAAAAA, false);
    }


    // ── children / narratables ────────────────────────────────────────────────

    @Override
    public List<? extends GuiEventListener> children() {
        // itemIDElement сам является GuiEventListener
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

    // ── геттер для BlacklistListManager ──────────────────────────────────────

    public String getValue() { return value; }
}