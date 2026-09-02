package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.FrameReport;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import javax.annotation.Nullable;
import java.util.List;

/**
 * The phase 2 report. Built once per frame, after the pose has been applied to the camera.
 *
 * <p>The quaternion is returned as a COPY on every call: almost everything in joml mutates, and a
 * subscriber calling {@code mul} on it would otherwise corrupt the value for its neighbours in the
 * list.
 */
record FrameReportImpl(Player player,
                       float partialTick,
                       boolean firstPerson,
                       @Nullable String tiltSource,
                       boolean baselineActive,
                       Quaternionf applied,
                       Vec3 eyeOffset,
                       float tiltScale,
                       List<String> skipped) implements FrameReport {

    @Override
    public Quaternionf tilt() {
        return new Quaternionf(applied);
    }
}
