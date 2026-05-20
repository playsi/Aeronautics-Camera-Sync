package com.playsi.aero_cam_sync.client.config.alert;

import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.NotNull;

import static com.playsi.aero_cam_sync.client.config.Config.*;

public class ConfigResetScreen extends Screen {

    private final Screen parent;

    public ConfigResetScreen(Screen parent) {
        super(Component.translatable("screen.aero_cam_sync.config_reset.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // Кнопка "Да — сбросить"
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.aero_cam_sync.config_reset.yes"),
                btn -> {
                    resetConfig();
                    ConfigMigrationManager.markAccepted();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(this.width / 2 - 155, this.height / 2 + 10, 150, 20).build());

        // Кнопка "Нет — оставить как есть"
        this.addRenderableWidget(Button.builder(
                Component.translatable("screen.aero_cam_sync.config_reset.no"),
                btn -> {
                    ConfigMigrationManager.markAccepted();
                    this.minecraft.setScreen(parent);
                }
        ).bounds(this.width / 2 + 5, this.height / 2 + 10, 150, 20).build());
    }

    public static void resetConfig() {
        resetAllToDefaults(Config.SPEC);
        Config.CONFIG_SCHEMA_VERSION.set(Config.CURRENT_CONFIG_SCHEMA);
        Config.SPEC.save();
    }

    @SuppressWarnings("unchecked")
    private static void resetAllToDefaults(ModConfigSpec spec) {
        spec.getValues().valueMap().values().forEach(obj -> {
            if (obj instanceof ModConfigSpec.ConfigValue<?> cv) {
                resetOne((ModConfigSpec.ConfigValue<Object>) cv);
            }
        });
    }

    private static <T> void resetOne(ModConfigSpec.ConfigValue<T> cv) {
        cv.set(cv.getDefault());
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(graphics, mouseX, mouseY, delta);
        
        super.render(graphics, mouseX, mouseY, delta);

        graphics.drawCenteredString(this.font, this.title,
                this.width / 2, this.height / 2 - 30, 0xFFFFFF);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.aero_cam_sync.config_reset.body"),
                this.width / 2, this.height / 2 - 10, 0xAAAAAA);
    }
}