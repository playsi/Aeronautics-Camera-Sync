package com.playsi.aero_cam_sync.client.config.alert;

import com.playsi.aero_cam_sync.client.config.Config;

public class ConfigMigrationManager {

    private static boolean configExisted = true;
    private static boolean promptShownThisSession = false;


    public static boolean needsResetPrompt() {
        if (!configExisted) return false;
        return Config.CONFIG_SCHEMA_VERSION.get() < Config.CURRENT_CONFIG_SCHEMA;
    }

    public static void markAccepted() {
        Config.CONFIG_SCHEMA_VERSION.set(Config.CURRENT_CONFIG_SCHEMA);
        Config.SPEC.save();
        promptShownThisSession = true;
    }

    public static boolean wasPromptShown() {
        return promptShownThisSession;
    }

    public static void setConfigExisted(boolean value) {
        configExisted = value;
    }
}