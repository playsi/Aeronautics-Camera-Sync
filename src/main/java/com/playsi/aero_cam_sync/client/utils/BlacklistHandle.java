package com.playsi.aero_cam_sync.client.utils;

import com.playsi.aero_cam_sync.AeroCamSync;
import com.playsi.aero_cam_sync.client.config.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public class BlacklistHandle {
    public static void handleBlacklistToggle(Player player) {
        if (player == null) return;

        ItemStack mainHandItem = player.getMainHandItem();

        if (mainHandItem.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("msg.aero_cam_sync.no_item_in_hand"),
                    true
            );
            return;
        }

        Item item = mainHandItem.getItem();
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString();

        List<? extends String> currentBlacklist = Config.CLIENT_BLACKLIST_IDS.get();

        if (currentBlacklist.contains(itemId)) {
            // Удаляем из черного списка
            List<String> newBlacklist = new java.util.ArrayList<>(currentBlacklist);
            newBlacklist.remove(itemId);
            Config.CLIENT_BLACKLIST_IDS.set(newBlacklist);

            player.displayClientMessage(
                    Component.translatable("msg.aero_cam_sync.removed_from_blacklist",
                            Component.literal(itemId)),
                    true
            );

            if (Config.DEBUG_MESSAGES.get()) {
                AeroCamSync.LOGGER.info("[AeroCamSync] Removed {} from blacklist", itemId);
            }
        } else {
            // Добавляем в черный список
            List<String> newBlacklist = new java.util.ArrayList<>(currentBlacklist);
            newBlacklist.add(itemId);
            Config.CLIENT_BLACKLIST_IDS.set(newBlacklist);

            player.displayClientMessage(
                    Component.translatable("msg.aero_cam_sync.added_to_blacklist",
                            Component.literal(itemId)),
                    true
            );

            if (Config.DEBUG_MESSAGES.get()) {
                AeroCamSync.LOGGER.info("[AeroCamSync] Added {} to blacklist", itemId);
            }
        }

        Config.SPEC.save();
    }

    public static boolean holdBannedItem(List<? extends String> itemIDs) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return false;

        return isItemBanned(player.getMainHandItem(), itemIDs) ||
                (Config.CONSIDER_OFFHAND.get() && isItemBanned(player.getOffhandItem(), itemIDs));
    }

    private static boolean isItemBanned(ItemStack stack, List<? extends String> bannedIds) {
        return !stack.isEmpty() && bannedIds.contains(getItemId(stack));
    }

    private static String getItemId(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}