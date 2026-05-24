package com.playsi.aero_cam_sync.client.utils;

import net.minecraft.world.level.ClipContext;

public class LevelClipMixinState {
    public static boolean inTiltedClip = false;
    public static ClipContext tiltedContext = null;
}