package com.playsi.aero_cam_sync.client.config.entries;

import com.mojang.blaze3d.platform.InputConstants;
import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import com.playsi.aero_cam_sync.client.KeyBindings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;

import static com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList.ENTRY_H;

/**
 * A row for assigning a key.
 * <p>
 * Clicking the button enters listening mode. The next key or mouse button pressed becomes the new
 * bind, except Escape, which clears it.
 *
 * <p>The row edits the {@link KeyMapping} itself and stores nothing in the mod config: binds live
 * in the vanilla options.txt, see {@link KeyBindings}.
 */
public class KeyBindEntry extends ConfigOptionList.Entry {

    private final KeyMapping mapping;

    private static final int BTN_W = 68;
    private static final int BTN_H = 18;

    private InputConstants.Key currentKey;
    private InputConstants.Key snapshot;

    private boolean listening = false;

    private boolean hasConflict = false;
    private final Button bindButton;

    public KeyBindEntry(String labelKey, String tooltipKey,
                        KeyMapping mapping) {
        super(labelKey, tooltipKey);
        this.mapping    = mapping;
        this.currentKey = mapping.getKey();
        this.snapshot   = currentKey;

        Button btn = Button.builder(keyLabel(currentKey), b -> {
                    listening = true;
                    b.setMessage(Component.literal("> ... <"));
                })
                .bounds(0, 0, BTN_W, BTN_H)
                .build();

        this.bindButton = btn;

        if (!tooltipKey.isEmpty())
            bindButton.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
    }

    public boolean onKeyPressed(int keyCode, int scanCode) {
        if (!listening) return false;

        InputConstants.Key pressed = keyCode == GLFW.GLFW_KEY_ESCAPE
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(keyCode, scanCode);

        setKey(pressed);
        listening = false;
        return true;
    }

    public boolean onMouseClicked(int button) {
        if (!listening) return false;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT || button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            setKey(InputConstants.Type.MOUSE.getOrCreate(button));
            listening = false;
            return true;
        }
        return false;
    }

    public boolean isListening()  { return listening; }

    public void cancelListening() {
        listening = false;
        bindButton.setMessage(keyLabel(currentKey));
    }

    private void setKey(InputConstants.Key key) {
        currentKey = key;
        KeyBindings.applyKey(mapping, key);

        hasConflict = false;
        if (!key.equals(InputConstants.UNKNOWN)) {
            for (KeyMapping other : Minecraft.getInstance().options.keyMappings) {
                if (other != mapping && other.getKey().equals(key)) {
                    hasConflict = true;
                    break;
                }
            }
        }

        bindButton.setMessage(keyLabel(key));
    }

    private static Component keyLabel(InputConstants.Key key) {
        if (key.equals(InputConstants.UNKNOWN))
            return Component.translatable("key.aero_cam_sync.none");
        return key.getDisplayName();
    }

    @Override
    public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                       int mouseX, int mouseY, boolean hovered, float delta) {
        drawLabel(gfx, left, top);

        int btnX = left + width - BTN_W - 4;
        int btnY = top + (ENTRY_H - BTN_H) / 2;
        bindButton.setX(btnX);
        bindButton.setY(btnY);
        bindButton.setWidth(BTN_W);

        if (listening)
            gfx.fill(btnX - 1, btnY - 1, btnX + BTN_W + 1, btnY + BTN_H + 1, 0xFFFFAA00);

        if (hasConflict) {
            gfx.fill(btnX - 1, btnY - 1, btnX + BTN_W + 1, btnY + BTN_H + 1, 0xFFFF2222);

            Minecraft mc = Minecraft.getInstance();
            gfx.drawString(mc.font, "!", btnX - 10, btnY + 6, 0xFFFF2222, false);
            bindButton.setTooltip(Tooltip.create(
                    Component.translatable("aero_cam_sync.configuration.keybind.conflict")));
        } else {
            if (!tooltipKey.isEmpty())
                bindButton.setTooltip(Tooltip.create(Component.translatable(tooltipKey)));
            else
                bindButton.setTooltip(null);
        }

        bindButton.render(gfx, mouseX, mouseY, delta);
    }

    @Override public List<? extends GuiEventListener> children()    { return List.of(bindButton); }
    @Override public List<? extends NarratableEntry>  narratables() { return List.of(bindButton); }

    @Override public void saveSnapshot()    { snapshot = currentKey; }

    @Override public void restoreSnapshot() {
        setKey(snapshot);
        hasConflict = false;
    }

    @Override public void reset()           {
        setKey(mapping.getDefaultKey());
        hasConflict = false;
    }

    @Override
    public void inheritSnapshot(ConfigOptionList.Entry donor) {
        if (donor instanceof KeyBindEntry d) this.snapshot = d.snapshot;
    }

    @Override public boolean hasHardLimitViolation() { return false; }
}