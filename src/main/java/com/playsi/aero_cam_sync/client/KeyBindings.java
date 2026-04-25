package com.playsi.aero_cam_sync.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.aero_cam_sync.toggle",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_I,
            "key.category.aero_cam_sync"
    );
}