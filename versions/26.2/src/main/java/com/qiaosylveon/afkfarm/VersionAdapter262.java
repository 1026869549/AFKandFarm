package com.qiaosylveon.afkfarm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * 26.2 adapter: Minecraft.gui.screen(), ContainerInput/handleContainerInput, sendOverlayMessage.
 */
public final class VersionAdapter262 implements VersionAdapter {

    @Override
    public boolean isScreenOpen(Minecraft minecraft) {
        return minecraft.gui.screen() != null;
    }

    @Override
    public void swapSlot(Minecraft minecraft, LocalPlayer player, int menuSlot, int hotbarIndex) {
        if (minecraft.gameMode == null) {
            return;
        }
        minecraft.gameMode.handleContainerInput(
                player.inventoryMenu.containerId,
                menuSlot,
                hotbarIndex,
                net.minecraft.world.inventory.ContainerInput.SWAP,
                player
        );
    }

    @Override
    public void sendOverlay(LocalPlayer player, Component component) {
        player.sendOverlayMessage(component);
    }

    @Override
    public void sendChat(LocalPlayer player, Component component) {
        player.sendSystemMessage(component);
    }
}
