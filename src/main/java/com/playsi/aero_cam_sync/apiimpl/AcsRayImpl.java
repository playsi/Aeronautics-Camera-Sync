package com.playsi.aero_cam_sync.apiimpl;

import com.playsi.aero_cam_sync.api.AcsRay;
import net.minecraft.world.phys.Vec3;

/** A ray from a snapshot. The direction is stored ready, computed once by the caller. */
record AcsRayImpl(Vec3 from, Vec3 to, Vec3 direction) implements AcsRay {

    static AcsRay of(Vec3 from, Vec3 direction, double reach) {
        return new AcsRayImpl(from, from.add(direction.scale(reach)), direction);
    }
}
