package com.playsi.aero_cam_sync.devtest;

import com.playsi.aero_cam_sync.api.AcsHandle;
import com.playsi.aero_cam_sync.api.AcsRay;
import com.playsi.aero_cam_sync.api.AcsState;
import com.playsi.aero_cam_sync.api.AeroCamSyncApi;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * The server half of the API test consumer. Dev runs only.
 *
 * <p>The client-side {@code AcsApiTestMod} cannot reach the server side by construction, and the
 * server side cannot be left untested: that is where the boundary breaks (Issue #33) and where the
 * contract differs: {@code client()} must be {@code null} and {@code suppress()} a no-op with a
 * warning.
 *
 * <p>The {@code /acsapi} command (operator level). It is the test in itself: if the API dragged in
 * a client type, execution never reaches the output.
 *
 * <p>Singleplayer is not a dedicated server. In singleplayer the process side is the client,
 * so {@code suppress()} really works there. It must be a no-op only on a dedicated server, and that
 * is where this point has to be checked.
 *
 * <p>The {@code !FMLLoader.isProduction()} gate lives in {@code AeroCamSync} rather than here, so
 * the built mod never loads the class and the command does not appear.
 */
public final class AcsApiServerTest {

    private AcsApiServerTest() {}

    private static AcsHandle acs;

    public static void register() {
        acs = AeroCamSyncApi.forMod("acs_test");
        NeoForge.EVENT_BUS.register(AcsApiServerTest.class);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("acsapi")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            report(context.getSource().getPlayerOrException());
                            return 1;
                        }));
    }

    private static void report(ServerPlayer player) {
        AcsState state = acs.state(player, 1.0f);

        say(player, "enabled=" + state.modEnabled()
                + " tilt=" + state.tiltApplied()
                + " suppressed=" + state.suppressed() + state.suppressedBy());
        say(player, "eye  " + vec(state.vanillaEye()) + " -> " + vec(state.aimEye()));
        say(player, "look " + vec(state.vanillaLook(1.0f)) + " -> " + vec(state.aimLook(1.0f)));

        AcsRay aim = state.aimRay(player.blockInteractionRange());
        say(player, "aim ray end " + vec(aim.to()));

        // The main server-side contract point: there is no client half on this side.
        say(player, "client() = " + (state.client() == null ? "null (correct for a server)"
                : "NOT null - the boundary leaked!"));

        // The second: on a dedicated server no lease is taken, and the log gets one warning.
        boolean before = acs.isSuppressed();
        acs.suppress(1_000);
        boolean after = acs.isSuppressed();
        acs.release();
        say(player, "suppress(): before=" + before + " after=" + after
                + (after ? " (took effect - so this is singleplayer, not a dedicated server)"
                         : " (no-op, as a server should be)"));
    }

    private static void say(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal("[acs_test/server] " + message));
    }

    private static String vec(Vec3 v) {
        return String.format("(%.3f %.3f %.3f)", v.x, v.y, v.z);
    }
}
