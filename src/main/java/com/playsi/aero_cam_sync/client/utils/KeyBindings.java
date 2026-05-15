package com.playsi.aero_cam_sync.client.utils;

import com.mojang.blaze3d.platform.InputConstants;
import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

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

    /** Вызвать после загрузки конфига — применить сохранённые значения */
    public static void loadFromConfig() {
        applyKey(TOGGLE,      Config.TOGGLE_KEY.get());
        applyKey(OPEN_CONFIG, Config.OPEN_CONFIG_KEY.get());
    }

    /** Сохранить текущие значения KeyMapping в конфиг */
    public static void saveToConfig() {
        Config.TOGGLE_KEY.set(TOGGLE.getKey().getName());
        Config.OPEN_CONFIG_KEY.set(OPEN_CONFIG.getKey().getName());
    }

    public static void applyKey(KeyMapping mapping, String keyName) {
        InputConstants.Key key = keyName.equals("key.unknown") || keyName.isEmpty()
                ? InputConstants.UNKNOWN
                : InputConstants.getKey(keyName);
        mapping.setKey(key);
        KeyMapping.resetMapping();
    }

    public static void syncFromMappings() {
        Config.TOGGLE_KEY.set(TOGGLE.getKey().getName());
        Config.OPEN_CONFIG_KEY.set(OPEN_CONFIG.getKey().getName());
    }

    private static InputConstants.Key parseKey(String keyName) {
        if (keyName == null || keyName.isEmpty() || keyName.equals("key.unknown"))
            return InputConstants.UNKNOWN;
        try {
            return InputConstants.getKey(keyName);
        } catch (Exception e) {
            return InputConstants.UNKNOWN;
        }
    }
}