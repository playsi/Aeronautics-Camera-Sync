package com.playsi.aero_cam_sync.client.config;

import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;

import java.util.ArrayList;
import java.util.List;

public class ConfigScreenController {

    private final List<ConfigCategory> categories;
    private final List<String> clientBlacklist;

    private List<String> clientSnapshot;

    public ConfigScreenController(List<ConfigCategory> categories,
                                  List<String> clientBlacklist) {
        this.categories = categories;
        this.clientBlacklist = clientBlacklist;
    }

    public void snapshotAll() {
        for (ConfigCategory cat : categories)
            for (var e : cat.entries())
                e.saveSnapshot();

        clientSnapshot = new ArrayList<>(clientBlacklist);
    }

    public void restoreAll() {
        for (ConfigCategory cat : categories)
            for (var e : cat.entries())
                e.restoreSnapshot();

        clientBlacklist.clear();
        clientBlacklist.addAll(clientSnapshot);
        Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(clientBlacklist));
    }
}