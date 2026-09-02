package com.playsi.aero_cam_sync.api;

import net.minecraft.world.entity.player.Player;

/**
 * What is known about a frame when {@link AcsConditions#conditionsFor} is called. Valid for the
 * duration of that call only.
 *
 * <p>Much smaller than {@link TiltContext}, because it happens earlier. There is no surface normal
 * here, that ray has not been cast yet, and no {@code acsTilt}, since whether it gets computed at
 * all is partly what you are about to answer. If you need those values you are describing a
 * {@link TiltSource}, which is asked later with everything in hand and can still decline.
 *
 * <p>Do not call {@link AcsHandle#state} from in here. This frame is not resolved yet, so you would
 * be reading the previous one while believing you read this one.
 * {@link AcsConditions#frameResolved} is where a resolved frame is available.
 */
public interface ConditionContext {

    /** The local player the frame is being drawn for. Never null. */
    Player player();

    /** Render partial tick of the frame being drawn. */
    float partialTick();

    /**
     * Whether the camera is in first person right now: vanilla's own answer, unfiltered.
     *
     * <p>Here so that a condition and the frame it conditions cannot disagree about the camera
     * mode. Reading {@code options.getCameraType()} yourself works, right up until something
     * changes it between your read and the ACS one.
     */
    boolean firstPerson();
}
