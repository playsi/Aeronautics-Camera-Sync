package com.playsi.aero_cam_sync.client.config;

import com.playsi.aero_cam_sync.client.config.categories.*;
import com.playsi.aero_cam_sync.client.config.ui.ConfigCategory;
import com.playsi.aero_cam_sync.client.config.ui.ConfigOptionList;
import com.playsi.aero_cam_sync.client.KeyBindings;
import com.playsi.aero_cam_sync.client.config.entries.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class ModConfigScreen extends Screen {

    private static final int HEADER_HEIGHT = 48;
    private static final int FOOTER_HEIGHT = 36;
    private static final int TAB_HEIGHT    = 24;
    private static final int TAB_Y         = 22;

    private final Screen parent;
    private ConfigScreenController controller;

    private ConfigOptionList optionList;
    private Button resetButton;
    private Button saveButton;


    private final List<String> clientBlacklist = new ArrayList<>();

    private final List<ConfigCategory> categories = new ArrayList<>();


    private int activeCategory = 0;


    private List<String> clientSnapshot = new ArrayList<>();

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("aero_cam_sync.configuration.title"));
        this.parent = parent;
        KeyBindings.syncFromMappings();

        clientBlacklist.addAll(Config.CLIENT_BLACKLIST_IDS.get());
        clientSnapshot = new ArrayList<>(clientBlacklist);

        rebuild();
        controller.snapshotAll();
    }

    private void rebuild() {
        categories.clear();
        categories.add(GeneralCategory.build());
        categories.add(CameraCategory.build());
        categories.add(ActivationCategory.build());
        categories.add(ClientBlacklistCategory.build(clientBlacklist, this::rebuildScreen));
        categories.add(RaycastCategory.build());
        categories.add(DebugCategory.build());

        controller = new ConfigScreenController(categories, clientBlacklist);
    }

    private void rebuildScreen() {
        double scroll = optionList != null ? optionList.getScrollAmount() : 0;
        List<String> savedClientSnapshot = new ArrayList<>(clientSnapshot);

        List<List<ConfigOptionList.Entry>> oldEntries = new ArrayList<>();
        for (ConfigCategory cat : categories)
            oldEntries.add(new ArrayList<>(cat.entries()));

        rebuild();
        clientSnapshot = savedClientSnapshot;

        for (int c = 0; c < Math.min(categories.size(), oldEntries.size()); c++) {
            List<ConfigOptionList.Entry> oldList = oldEntries.get(c);
            List<ConfigOptionList.Entry> newList = categories.get(c).entries();
            for (int i = 0; i < Math.min(oldList.size(), newList.size()); i++)
                newList.get(i).inheritSnapshot(oldList.get(i));
        }

        clearWidgets();
        init();
        optionList.setScrollAmount(scroll);
    }

    // ── Init ───────────────────────────────────────────────────────────────────

    @Override
    protected void init() {
        rebuildList();
        addTabButtons();
        addFooterButtons();
    }

    private void rebuildList() {
        if (optionList != null) removeWidget(optionList);

        int listTop    = HEADER_HEIGHT;
        int listBottom = this.height - FOOTER_HEIGHT;
        optionList = new ConfigOptionList(
                this.minecraft, this.width, listBottom - listTop, listTop, 26);

        for (ConfigOptionList.Entry e : categories.get(activeCategory).entries())
            optionList.addPublicEntry(e);

        addRenderableWidget(optionList);
    }

    private void addTabButtons() {
        int tabW   = 80;
        int totalW = tabW * categories.size();
        int startX = (this.width - totalW) / 2;

        for (int i = 0; i < categories.size(); i++) {
            final int idx = i;
            Component name = Component.translatable(categories.get(i).nameKey());
            int x = startX + i * tabW;

            Button tab = Button.builder(name, btn -> {
                        activeCategory = idx;
                        clearWidgets();
                        init();
                    })
                    .bounds(x, TAB_Y, tabW - 2, TAB_HEIGHT)
                    .build();

            if (i == activeCategory) tab.active = false;
            addRenderableWidget(tab);
        }
    }

    private void addFooterButtons() {
        int btnW   = 80;
        int btnH   = 20;
        int gap    = 4;
        int totalW = btnW * 3 + gap * 2;
        int startX = (this.width - totalW) / 2;
        int btnY   = this.height - FOOTER_HEIGHT + 8;

        addRenderableWidget(Button.builder(
                        Component.translatable("aero_cam_sync.configuration.btn.cancel"), btn -> {
                            restoreAll();
                            this.minecraft.setScreen(parent);
                        })
                .bounds(startX, btnY, btnW, btnH)
                .build());

        // Reset — только при Shift
        resetButton = Button.builder(
                        Component.translatable("aero_cam_sync.configuration.btn.reset"), btn -> {
                            resetCurrent();
                            rebuildScreen();
                        })
                .bounds(startX + btnW + gap, btnY, btnW, btnH)
                .tooltip(Tooltip.create(Component.translatable("aero_cam_sync.configuration.btn.reset.tooltip")))
                .build();
        resetButton.active = false;
        addRenderableWidget(resetButton);

        saveButton = Button.builder(
                        Component.translatable("aero_cam_sync.configuration.btn.save"), btn -> {
                            Config.SPEC.save();
                            this.minecraft.setScreen(parent);
                        }
                )
                .bounds(startX + (btnW + gap) * 2, btnY, btnW, btnH)
                .build();
        addRenderableWidget(saveButton);
    }

    // ── Snapshot / Restore / Reset ─────────────────────────────────────────────

    private void snapshotAll() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                e.saveSnapshot();

        clientSnapshot = new ArrayList<>(clientBlacklist);
    }

    private void restoreAll() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                e.restoreSnapshot();

        clientBlacklist.clear();
        clientBlacklist.addAll(clientSnapshot);
        Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(clientBlacklist));
    }

    private void resetCurrent() {
        for (ConfigOptionList.Entry e : categories.get(activeCategory).entries())
            e.reset();

        int clientIdx = getCategoryIndex("aero_cam_sync.configuration.clientBlacklist");

        if (activeCategory == clientIdx) {
            clientBlacklist.clear();
            clientBlacklist.addAll(Config.CLIENT_BLACKLIST_IDS.getDefault());
            Config.CLIENT_BLACKLIST_IDS.set(new ArrayList<>(clientBlacklist));
        }
    }

    private boolean hasAnyHardViolation() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                if (e.hasHardLimitViolation()) return true;
        return false;
    }

    // ── Render ─────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float delta) {
        renderBackground(gfx, mouseX, mouseY, delta);
        super.render(gfx, mouseX, mouseY, delta);

        gfx.fill(0, HEADER_HEIGHT - 1, this.width, HEADER_HEIGHT, 0x88AAAAAA);
        gfx.fill(0, this.height - FOOTER_HEIGHT, this.width, this.height - FOOTER_HEIGHT + 1, 0x88AAAAAA);

        // Заголовок
        gfx.drawCenteredString(this.font, this.title, this.width / 2, 7, 0xFFFFFF);

        // Обновить кнопки каждый фрейм
        if (resetButton != null) resetButton.active = hasShiftDown();
        if (saveButton  != null) {
            boolean violation = hasAnyHardViolation();
            saveButton.active = !violation;
            if (violation) {
                gfx.drawCenteredString(this.font,
                        Component.translatable("aero_cam_sync.configuration.hardLimitWarning"),
                        this.width / 2, this.height - FOOTER_HEIGHT - 10, 0xFFFFFF);
            }
        }
    }

    @Override
    public void onClose() {
        controller.restoreAll();
        KeyBindings.saveToConfig();
        KeyBindings.loadFromConfig();
        this.minecraft.setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Сначала пробуем отдать активному KeyBindEntry
        if (activeKeyBindEntry() != null) {
            boolean consumed = activeKeyBindEntry().onKeyPressed(keyCode, scanCode);
            if (consumed) return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (activeKeyBindEntry() != null) {
            boolean consumed = activeKeyBindEntry().onMouseClicked(button);
            if (consumed) return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private KeyBindEntry activeKeyBindEntry() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                if (e instanceof KeyBindEntry kb && kb.isListening())
                    return kb;
        return null;
    }

    private int getCategoryIndex(String nameKey) {
        for (int i = 0; i < categories.size(); i++)
            if (categories.get(i).nameKey().equals(nameKey)) return i;
        return -1;
    }
}