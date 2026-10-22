package com.qiaosylveon.afkfarm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 1.21.11 adapter: Minecraft.screen, ClickType/handleInventoryMouseClick, displayClientMessage.
 */
public final class VersionAdapter12111 implements VersionAdapter {

    @Override
    public boolean isScreenOpen(Minecraft minecraft) {
        return minecraft.screen != null;
    }

    @Override
    public void swapSlot(Minecraft minecraft, LocalPlayer player, int menuSlot, int hotbarIndex) {
        if (minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                menuSlot,
                hotbarIndex,
                net.minecraft.world.inventory.ClickType.SWAP,
                player
        );
    }

    @Override
    public void sendOverlay(LocalPlayer player, Component component) {
        player.displayClientMessage(component, true);
    }

    @Override
    public void sendChat(LocalPlayer player, Component component) {
        player.displayClientMessage(component, false);
    }
}
