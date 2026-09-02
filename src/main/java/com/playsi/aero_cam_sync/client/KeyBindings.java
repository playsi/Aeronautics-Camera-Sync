package com.playsi.aero_cam_sync.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The mod's keybinds.
 *
 * <p>They are stored in exactly one place, the vanilla {@code options.txt}. All three are
 * registered through {@code RegisterKeyMappingsEvent}, so Minecraft reads them at startup and shows
 * them on its own controls screen alongside the rest.
 *
 * <p>They have no copy in the mod config, and must not. NeoForge calls {@code options.load(true)}
 * as the last line of mod loading, and that runs EVERY KeyMapping through options.txt, overwriting
 * any value applied from a mod config earlier. While the copy existed this looked like "I cleared
 * the bind and it came back after a restart": the mod config was written to disk and options.txt
 * was not.
 */
public class KeyBindings {

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.aero_cam_sync.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.category.aero_cam_sync"
    );

    public static final KeyMapping OPEN_CONFIG = new KeyMapping(
            "key.aero_cam_sync.open_config",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.category.aero_cam_sync"
    );

    public static final KeyMapping ADD_MAINHAND_ITEM = new KeyMapping(
            "key.aero_cam_sync.add_mainhand_item_client",
            InputConstants.Type.KEYSYM,
            InputConstants.UNKNOWN.getValue(),
            "key.category.aero_cam_sync"
    );

    /**
     * Assign a key from the mod's settings screen and write options.txt immediately.
     *
     * <p>The write must happen here: quitting the game does not save options.txt by itself, only
     * the vanilla settings screens do, on close. Without an explicit save the change would last
     * only until the end of the session.
     */
    public static void applyKey(KeyMapping mapping, InputConstants.Key key) {
        mapping.setKey(key);
        KeyMapping.resetMapping();
        Minecraft.getInstance().options.save();
    }
}
