package com.playsi.aero_cam_sync.client.config.ui;

import com.playsi.aero_cam_sync.SideManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Лейбл текущего режима работы мода в правой части шапки экрана настроек.
 *
 * <ul>
 *   <li>{@link SideManager.Side#CLIENT_SERVER} — «mode: server-client», подсказка «сервер подключён»;</li>
 *   <li>{@link SideManager.Side#CLIENT_ONLY} — «mode: client-only», подсказка «сервер не подключён»;</li>
 *   <li>{@link SideManager.Side#UNKNOWN} (главное меню / ещё не определён) — не рендерится вовсе.</li>
 * </ul>
 *
 * Вся логика режима здесь; {@link com.playsi.aero_cam_sync.client.config.ModConfigScreen}
 * лишь вызывает {@link #render}.
 */
public final class ModeIndicator {

    private static final int PADDING_RIGHT = 6;
    private static final int COLOR_CLIENT_ONLY   = 0xFFE0A030; // янтарный
    private static final int COLOR_CLIENT_SERVER = 0xFF57C15A; // зелёный

    private ModeIndicator() {}

    /**
     * Рисует бейдж режима справа на строке {@code topY} и подсказку при наведении.
     * В режиме {@link SideManager.Side#UNKNOWN} не делает ничего.
     *
     * @param topY верх строки текста (обычно та же, что и у заголовка)
     */
    public static void render(GuiGraphics gfx, int screenWidth, int topY, int mouseX, int mouseY) {
        SideManager.Side side = SideManager.getSide();
        if (side == SideManager.Side.UNKNOWN) return;

        final Component label;
        final Component tooltip;
        final int color;

        if (side == SideManager.Side.CLIENT_SERVER) {
            label   = Component.translatable("aero_cam_sync.configuration.mode.clientServer");
            tooltip = Component.translatable("aero_cam_sync.configuration.mode.clientServer.tooltip");
            color   = COLOR_CLIENT_SERVER;
        } else { // CLIENT_ONLY
            label   = Component.translatable("aero_cam_sync.configuration.mode.clientOnly");
            tooltip = Component.translatable("aero_cam_sync.configuration.mode.clientOnly.tooltip");
            color   = COLOR_CLIENT_ONLY;
        }

        Font font = Minecraft.getInstance().font;
        int textW = font.width(label);
        int x = screenWidth - PADDING_RIGHT - textW;

        gfx.drawString(font, label, x, topY, color);

        boolean hovered = mouseX >= x && mouseX <= x + textW
                && mouseY >= topY && mouseY <= topY + font.lineHeight;
        if (hovered) {
            gfx.renderTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}
