package com.playsi.aero_cam_sync.client.compat;

import com.playsi.aero_cam_sync.AeroCamSync;
import net.neoforged.fml.ModList;

/**
 * Watchdog for the link to Cut Through. {@code CutThroughDistanceMixin} targets foreign handlers by
 * name under {@code require = 0}, so a rename breaks all four redirects with NO line in the log.
 * This asks the same question at runtime.
 *
 * <p>It proves only that the ACS code runs, not that it computes the right thing.
 */
public final class CutThroughCompat {

    private CutThroughCompat() {}

    private static final boolean PRESENT =
            ModList.get() != null && ModList.get().isLoaded("cutthrough");

    /** Cumulative per launch, NOT reset on world change: resetting would only delay detection. */
    private static final int GRACE_TICKS = 200;

    private static boolean wired = false;

    private static int ticksInWorld = 0;
    private static boolean reported = false;

    /** Called on every distance comparison: nothing here beyond a check and an assignment. */
    public static void markWired() {
        if (!wired) wired = true;
    }

    /**
     * Keep the section sign out of the message: Minecraft reads it as a formatting-code prefix and
     * log4j eats it together with the following character.
     */
    public static void observeTick(boolean inWorld) {
        if (!PRESENT || reported || wired || !inWorld) return;

        if (++ticksInWorld < GRACE_TICKS) return;

        reported = true;
        AeroCamSync.LOGGER.warn(
                "[AeroCamSync] Cut Through is installed, but our distance fix inside its pick"
                        + " handlers never ran. Either Cut Through's own feature is turned off in"
                        + " cutthrough-client.toml (harmless), or it renamed pick$0/pick$1 and our"
                        + " @TargetHandler no longer matches. In the latter case Cut Through picks"
                        + " the wrong candidate while you stand on a contraption, nothing else in"
                        + " ACS is affected. Developers: see RELEASE-CHECKLIST.md, section 8.");
    }
}
