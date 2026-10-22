package com.qiaosylveon.afkfarm.fabric.mixin;

import com.qiaosylveon.afkfarm.ClientHarvest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric equivalent of the Forge/NeoForge InteractionKeyMappingTriggered event.
 * Cancels vanilla use only when the shared core claims the click.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {

    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void afkfarm$onStartUseItem(CallbackInfo ci) {
        if (ClientHarvest.onUseKeyClicked(Minecraft.getInstance())) {
            ci.cancel();
        }
    }
}
