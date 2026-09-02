package com.playsi.aero_cam_sync.client.config.ui;

import com.playsi.aero_cam_sync.SideManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * The current-mode label in the right of the settings screen header.
 *
 * <ul>
 *   <li>{@link SideManager.Side#CLIENT_SERVER}: "mode: server-client", tooltip "server connected";</li>
 *   <li>{@link SideManager.Side#CLIENT_ONLY}: "mode: client-only", tooltip "server not connected";</li>
 *   <li>{@link SideManager.Side#UNKNOWN} (main menu, or not yet determined): not rendered at all.</li>
 * </ul>
 *
 * All the mode logic lives here; {@link com.playsi.aero_cam_sync.client.config.ModConfigScreen}
 * only calls {@link #render}.
 */
public final class ModeIndicator {

    private static final int PADDING_RIGHT = 6;
    private static final int COLOR_CLIENT_ONLY   = 0xFFE0A030; // amber
    private static final int COLOR_CLIENT_SERVER = 0xFF57C15A; // green
    private static final int COLOR_PENDING       = 0xFF9A9A9A; // grey: mode awaits a rejoin

    private ModeIndicator() {}

    /**
     * Draws the mode badge on the right of row {@code topY}, plus a tooltip on hover. Does nothing
     * in {@link SideManager.Side#UNKNOWN}.
     *
     * @param topY the top of the text row (usually the same as the title's)
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

        // Client-only was toggled while already in a world: the mode changes on the next join
        // (SideManager latches the option per session), and the badge must show that, or it lies
        // about how the mod is working right now.
        boolean pending = SideManager.isIgnoreServerPending();

        Component shown = pending
                ? Component.empty().append(label)
                        .append(Component.translatable("aero_cam_sync.configuration.mode.pending.suffix"))
                : label;

        Font font = Minecraft.getInstance().font;
        int textW = font.width(shown);
        int x = screenWidth - PADDING_RIGHT - textW;

        gfx.drawString(font, shown, x, topY, pending ? COLOR_PENDING : color);

        boolean hovered = mouseX >= x && mouseX <= x + textW
                && mouseY >= topY && mouseY <= topY + font.lineHeight;
        if (hovered) {
            if (pending) {
                gfx.renderComponentTooltip(font, List.of(
                        tooltip,
                        Component.translatable("aero_cam_sync.configuration.mode.pending.tooltip")
                                .withStyle(ChatFormatting.GOLD)
                ), mouseX, mouseY);
            } else {
                gfx.renderTooltip(font, tooltip, mouseX, mouseY);
            }
        }
    }
}
