package com.qiaosylveon.afkfarm.neoforge;

import com.qiaosylveon.afkfarm.AfkFarmCommon;
import com.qiaosylveon.afkfarm.ClientHarvest;
import com.qiaosylveon.afkfarm.VersionBootstrap;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Shared NeoForge entrypoint for 1.21.11, 26.1.2 and 26.2.
 */
@Mod(value = "afkfarm", dist = Dist.CLIENT)
public final class NeoForgeEntry {

    public NeoForgeEntry(ModContainer container) {
        ClientHarvest.init(VersionBootstrap.create());

        IEventBus modBus = container.getEventBus();
        modBus.addListener((RegisterKeyMappingsEvent event) -> {
            event.register(ClientHarvest.AFK_TOGGLE);
            AfkFarmCommon.LOGGER.info("Registered '{}' (default F7, category MISC)", ClientHarvest.AFK_TOGGLE.getName());
        });

        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> ClientHarvest.onClientTick());
        NeoForge.EVENT_BUS.addListener((InputEvent.InteractionKeyMappingTriggered event) -> {
            if (!event.isUseItem()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (event.getKeyMapping() != minecraft.options.keyUse) {
                return;
            }
            if (ClientHarvest.onUseKeyClicked(minecraft)) {
                event.setCanceled(true);
            }
        });

        AfkFarmCommon.LOGGER.info("AFK Farm NeoForge initialized (client-only; server does not need this mod)");
    }
}
