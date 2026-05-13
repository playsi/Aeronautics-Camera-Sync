package com.playsi.aero_cam_sync.client.config;

import java.util.ArrayList;
import java.util.List;

public class ConfigCategory {

    private final String nameKey; // translation key
    private final List<ConfigOptionList.Entry> entries = new ArrayList<>();

    public ConfigCategory(String nameKey) {
        this.nameKey = nameKey;
    }

    public void add(ConfigOptionList.Entry entry) { entries.add(entry); }

    public String nameKey()                          { return nameKey; }
    public List<ConfigOptionList.Entry> entries()    { return entries; }
}