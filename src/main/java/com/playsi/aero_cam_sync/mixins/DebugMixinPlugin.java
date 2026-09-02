package com.playsi.aero_cam_sync.mixins;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Keeps diagnostic mixins out of the built mod.
 *
 * Why a plugin rather than a check inside the method
 *
 * <p>A check in the handler body is not enough: the diagnostic mixins interfere with each other by
 * the mere fact of being applied. The live example is a method overwrite conflict, where
 * identically named private methods of {@code PickScopeMixin} and {@code DebugPickTailMixin} merge
 * into one {@code GameRenderer}. A check in the body does not remove that conflict, the bytecode is
 * already inserted. A plugin does: in production those mixins simply are not there.
 *
 * How the environment is determined
 *
 * <p>{@link FMLLoader#isProduction()}. The distinction is not a heuristic but the ModLauncher
 * launch target: a dev run arrives as {@code forgeclientdev} and a launcher start as
 * {@code forgeclient}, and the dev handlers mark the environment as non-production.
 *
 * Manual override
 *
 * <p>{@code -Daero_cam_sync.debug=true} enables diagnostics in a RELEASE build, {@code =false}
 * disables them in a dev run. That is the main point: a player with a puzzling bug report can be
 * asked to launch with one JVM argument and send the log, without a special build.
 */
public final class DebugMixinPlugin implements IMixinConfigPlugin {

    private static final String PROPERTY = "aero_cam_sync.debug";

    /** The mod logger must not be touched: it would pull ACS classes into the transformer. */
    private static final Logger LOGGER = LoggerFactory.getLogger("AeroCamSync");

    private final boolean enabled = resolve();

    private static boolean resolve() {
        String override = System.getProperty(PROPERTY);
        if (override != null) return !"false".equalsIgnoreCase(override);
        return !FMLLoader.isProduction();
    }

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[AeroCamSync] diagnostic mixins: {} (-D{}=true|false to override)",
                enabled ? "ON" : "OFF", PROPERTY);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return enabled;
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode n, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode n, String m, IMixinInfo i) {}
}
