package com.qiaosylveon.afkfarm;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * The only Minecraft-version-specific surface used by the shared harvest core.
 *
 * <p>Implemented once per target version group:</p>
 * <ul>
 *     <li>1.21.11: {@code Minecraft.screen}, {@code ClickType}, {@code displayClientMessage};</li>
 *     <li>26.1.2: {@code Minecraft.screen}, {@code ContainerInput}, {@code sendOverlayMessage};</li>
 *     <li>26.2: {@code Minecraft.gui.screen()}, {@code ContainerInput}, {@code sendOverlayMessage}.</li>
 * </ul>
 */
public interface VersionAdapter {

    boolean isScreenOpen(Minecraft minecraft);

    void swapSlot(Minecraft minecraft, LocalPlayer player, int menuSlot, int hotbarIndex);

    void sendOverlay(LocalPlayer player, Component component);

    void sendChat(LocalPlayer player, Component component);
}
