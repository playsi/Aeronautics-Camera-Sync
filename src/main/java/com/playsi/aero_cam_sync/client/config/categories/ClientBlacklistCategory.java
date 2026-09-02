package com.playsi.aero_cam_sync.client.config.categories;

import com.playsi.aero_cam_sync.client.KeyBindings;
import com.playsi.aero_cam_sync.client.config.Config;
import com.playsi.aero_cam_sync.client.config.entries.ListEntry;
import com.playsi.aero_cam_sync.client.config.entries.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;
import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ClientBlacklistCategory {

    public static ConfigCategory build(List<String> blacklist,
                                       Runnable onRebuild) {
        ConfigCategory cat = new ConfigCategory(
                "aero_cam_sync.configuration.clientBlacklist");

        cat.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.clientBlacklist.enabled",
                "aero_cam_sync.configuration.clientBlacklist.enabled.tooltip",
                Config.CLIENT_BLACKLIST_ENABLED));

        cat.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.considerOffhand",
                "aero_cam_sync.configuration.considerOffhand.tooltip",
                Config.CONSIDER_OFFHAND));

        cat.add(new ToggleButtonEntry(
                "aero_cam_sync.configuration.autoDisableForRaycastItems",
                "aero_cam_sync.configuration.autoDisableForRaycastItems.tooltip",
                Config.AUTO_DISABLE_FOR_RAYCAST_ITEMS));

        cat.add(new KeyBindEntry(
                "aero_cam_sync.configuration.addMainhandItemKey",
                "aero_cam_sync.configuration.addMainhandItemKey.tooltip",
                KeyBindings.ADD_MAINHAND_ITEM));

        cat.add(new SeparatorEntry("aero_cam_sync.configuration.clientBlacklist.separator"));

        for (int i = 0; i < blacklist.size(); i++) {
            final int idx = i;
            cat.add(new ListEntry(
                    blacklist.get(i),
                    newVal -> {
                        blacklist.set(idx, newVal);
                        Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(blacklist));
                    },
                    () -> {
                        blacklist.remove(idx);
                        Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(blacklist));
                        onRebuild.run();
                    }
            ));
        }

        cat.add(buildAddButton(blacklist, onRebuild));
        return cat;
    }

    private static ConfigOptionList.Entry buildAddButton(List<String> blacklist,
                                                         Runnable onRebuild) {
        return new ConfigOptionList.Entry("", "") {
            private final Button addBtn = Button.builder(
                    Component.translatable("aero_cam_sync.configuration.list.add"),
                    btn -> {

                        blacklist.add("");
                        Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(blacklist));
                        onRebuild.run();
                    }
            ).bounds(0, 0, 80, 20).build();

            @Override
            public void render(GuiGraphics gfx, int i, int top, int left,
                               int width, int height, int mx, int my,
                               boolean hovered, float delta) {
                addBtn.setX(left + (width - 80) / 2);
                addBtn.setY(top + 3);
                addBtn.render(gfx, mx, my, delta);
            }

            @Override public List<? extends GuiEventListener> children()   { return List.of(addBtn); }
            @Override public List<? extends NarratableEntry> narratables()  { return List.of(addBtn); }
            @Override public void saveSnapshot()  {}
            @Override public void restoreSnapshot() {}
            @Override public void reset() {}
            @Override public boolean hasHardLimitViolation() { return false; }
        };
    }
}