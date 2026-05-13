package com.playsi.aero_cam_sync.client.config;

import com.playsi.aero_cam_sync.client.Config;
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
    private final List<ConfigCategory> categories = new ArrayList<>();
    private int activeCategory = 0;

    private ConfigOptionList optionList;
    private Button resetButton;
    private Button saveButton;

    public ModConfigScreen(Screen parent) {
        super(Component.translatable("aero_cam_sync.configuration.title"));
        this.parent = parent;
        buildCategories();
        snapshotAll(); // запомнить значения до того как пользователь что-то изменит
    }

    // ── Категории ──────────────────────────────────────────────────────────────

    private void buildCategories() {
        // General
        ConfigCategory general = new ConfigCategory("aero_cam_sync.configuration.general");
        general.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.enabled",
                "aero_cam_sync.configuration.enabled.tooltip",
                Config.MOD_ENABLED));
        general.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.allow3rdPerson",
                "aero_cam_sync.configuration.allow3rdPerson.tooltip",
                Config.ALLOW_3RD_PERSON));
        general.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.ignoreServer",
                "aero_cam_sync.configuration.ignoreServer.tooltip",
                Config.IGNORE_SERVER));
        categories.add(general);

        // Camera
        ConfigCategory camera = new ConfigCategory("aero_cam_sync.configuration.camera");
        camera.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.rotateCamera",
                "aero_cam_sync.configuration.rotateCamera.tooltip",
                Config.MODIFY_CAMERA_ROT));
        camera.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.moveCamera",
                "aero_cam_sync.configuration.moveCamera.tooltip",
                Config.MODIFY_CAMERA_POS));
        camera.add(new ConfigOptionList.SliderEntry(
                "aero_cam_sync.configuration.smoothSpeed",
                "aero_cam_sync.configuration.smoothSpeed.tooltip",
                Config.SMOOTH_SPEED,
                0.1,  10.0,  0.05,   // sliderMin, sliderMax, step
                0.0,  9999.0));       // hardMin, hardMax
        camera.add(new ConfigOptionList.SliderEntry(
                "aero_cam_sync.configuration.minNormalY",
                "aero_cam_sync.configuration.minNormalY.tooltip",
                Config.MIN_NORMAL_Y,
                0.0, 1.0, 0.01,
                0.0, 1.0));
        categories.add(camera);

        // Raycast
        ConfigCategory raycast = new ConfigCategory("aero_cam_sync.configuration.raycast");
        raycast.add(new ConfigOptionList.SliderEntry(
                "aero_cam_sync.configuration.count",
                "aero_cam_sync.configuration.count.tooltip",
                Config.RAYCAST_COUNT,
                1, 100, 1,       // sliderMin, sliderMax, step
                1, 10000));      // hardMin, hardMax
        raycast.add(new ConfigOptionList.SliderEntry(
                "aero_cam_sync.configuration.downLength",
                "aero_cam_sync.configuration.downLength.tooltip",
                Config.RAYCAST_DOWN_LENGTH,
                0.1, 12.0, 0.1,
                0.1, 12.0));
        raycast.add(new ConfigOptionList.SliderEntry(
                "aero_cam_sync.configuration.upLength",
                "aero_cam_sync.configuration.upLength.tooltip",
                Config.RAYCAST_UP_LENGTH,
                -1.0, 1.0, 0.05,
                -1.0, 1.0));
        categories.add(raycast);

        // Debug
        ConfigCategory debug = new ConfigCategory("aero_cam_sync.configuration.debug");
        debug.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.rays",
                "aero_cam_sync.configuration.rays.tooltip",
                Config.DEBUG_RAYS));
        debug.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.pickRays",
                "aero_cam_sync.configuration.pickRays.tooltip",
                Config.DEBUG_PICK_RAYS));
        debug.add(new ConfigOptionList.BooleanEntry(
                "aero_cam_sync.configuration.debugMessages",
                "",
                Config.DEBUG_MESSAGES));
        categories.add(debug);
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

        // Cancel — восстанавливает значения на момент открытия экрана
        addRenderableWidget(Button.builder(
                        Component.literal("Cancel"), btn -> {
                            restoreAll();
                            this.minecraft.setScreen(parent);
                        })
                .bounds(startX, btnY, btnW, btnH)
                .build());

        // Reset — только при Shift
        resetButton = Button.builder(
                        Component.literal("Reset"), btn -> {
                            resetCurrent();
                            clearWidgets();
                            init();
                        })
                .bounds(startX + btnW + gap, btnY, btnW, btnH)
                .tooltip(Tooltip.create(Component.literal("Hold Shift to reset current category")))
                .build();
        resetButton.active = false;
        addRenderableWidget(resetButton);

        // Save — недоступна при нарушении hard limit
        saveButton = Button.builder(
                        Component.literal("Save"), btn ->
                                this.minecraft.setScreen(parent)  // значения уже применены live
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
    }

    private void restoreAll() {
        for (ConfigCategory cat : categories)
            for (ConfigOptionList.Entry e : cat.entries())
                e.restoreSnapshot();
    }

    private void resetCurrent() {
        for (ConfigOptionList.Entry e : categories.get(activeCategory).entries())
            e.reset();
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

        // Тонкие разделители — без глухих плашек
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
                        Component.literal("§cValue out of hard limit — cannot save"),
                        this.width / 2, this.height - FOOTER_HEIGHT + 28, 0xFFFFFF);
            }
        }
    }

    @Override
    public void onClose() {
        // Закрытие через Esc = Cancel
        restoreAll();
        this.minecraft.setScreen(parent);
    }
}