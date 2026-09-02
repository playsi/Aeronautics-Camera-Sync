package com.playsi.aero_cam_sync.mixins;

import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * A bisect switch for the MAIN mixin set: diagnostics, not a feature. It catches conflicts caused by
 * the mere fact that a mixin applied, which a gate in a method body cannot catch by construction -
 * the bytecode is already inserted and the handler order already changed.
 *
 * <pre>
 *   -Daero_cam_sync.mixins=off        no ACS mixin applies
 *   -Daero_cam_sync.mixins=client     client.* does not apply
 *   -Daero_cam_sync.mixins=compat     compat.* does not apply
 * </pre>
 *
 * <p>It ships in releases so a player with an irreproducible conflict can be asked for one launch
 * argument. With {@code off} the mod is not "cleanly disabled": it does nothing while config,
 * networking and the API stay alive; {@code Enabled} is the proper switch.
 *
 * <p>Second job: disabling {@code CutThroughDistanceMixin} when MixinSquared is absent, since an
 * unregistered selector is "invalid string", not "no targets found", and drops the whole mod at
 * startup.
 */
public final class BisectMixinPlugin implements IMixinConfigPlugin {

    private static final String PROPERTY = "aero_cam_sync.mixins";

    /** Strings, not classes: this plugin lives in the transformation layer. */
    private static final String MIXIN_SQUARED_DEPENDENT =
            "com.playsi.aero_cam_sync.mixins.compat.CutThroughDistanceMixin";
    private static final String MIXIN_SQUARED_SELECTOR =
            "com.bawnorton.mixinsquared.selector.DynamicSelectorHandler";

    /** The mod logger must not be touched: it would pull ACS classes into the transformer. */
    private static final Logger LOGGER = LoggerFactory.getLogger("AeroCamSync");

    /** {@code null} means no flag: apply everything. */
    private final String mode = resolve();

    private static String resolve() {
        String value = System.getProperty(PROPERTY);
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    @Override
    public void onLoad(String mixinPackage) {
        if (mode == null) return;
        // Loud on purpose: it is easy to forget a launch used this flag and then spend half a
        // day wondering why the mod "stopped working". This line must come first in the log.
        LOGGER.warn("[AeroCamSync] ⚠ DIAGNOSTIC: -D{}={}, part of the mixin set is NOT applied."
                + " This is a bisect switch, not a way to disable the mod.", PROPERTY, mode);
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.equals(MIXIN_SQUARED_DEPENDENT) && !mixinSquaredPresent()) {
            // Once per launch: there is one such mixin, and this runs once for it.
            LOGGER.warn("[AeroCamSync] MixinSquared is missing: the Cut Through occlusion fix"
                    + " (compat.CutThroughDistanceMixin) is NOT applied. Everything else works."
                    + " The released jar carries the library in META-INF/jarjar, so this means the"
                    + " jar was repacked or the bundled library was stripped: reinstall ACS from"
                    + " the original download.");
            return false;
        }

        if (mode == null) return true;
        if (mode.equals("off")) return false;

        // For com.playsi.aero_cam_sync.mixins.client.CameraMixin the group is what sits between
        // the mixin package and the class name.
        String tail = mixinClassName.startsWith("com.playsi.aero_cam_sync.mixins.")
                ? mixinClassName.substring("com.playsi.aero_cam_sync.mixins.".length())
                : mixinClassName;
        int dot = tail.indexOf('.');
        String group = dot < 0 ? "root" : tail.substring(0, dot);

        return !group.equals(mode);
    }

    /**
     * Whether the MixinSquared library made it into the build.
     *
     * <p>Checked by loading a class rather than parsing a selector: parsing is the thing that
     * fails, and Mixin's selector registry is not readable from outside. {@code initialize = false}
     * because presence is all that matters. {@link Throwable} rather than
     * {@link ClassNotFoundException}
     * because any load failure here means the same thing: no selector, do not apply the mixin.
     */
    private static boolean mixinSquaredPresent() {
        try {
            Class.forName(MIXIN_SQUARED_SELECTOR, false, BisectMixinPlugin.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override public String getRefMapperConfig() { return null; }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String t, ClassNode n, String m, IMixinInfo i) {}
    @Override public void postApply(String t, ClassNode n, String m, IMixinInfo i) {}
}
