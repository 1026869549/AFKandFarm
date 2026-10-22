package com.qiaosylveon.afkfarm.fabric;

import com.qiaosylveon.afkfarm.AfkFarmCommon;
import com.qiaosylveon.afkfarm.ClientHarvest;
import com.qiaosylveon.afkfarm.VersionBootstrap;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/**
 * Fabric client entrypoint. The click path is handled by
 * {@code MinecraftUseMixin} (startUseItem HEAD); the hold loop by the shared tick.
 */
public final class FabricEntry implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientHarvest.init(VersionBootstrap.create());

        FabricKeyRegistration.register(ClientHarvest.AFK_TOGGLE);
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientHarvest.onClientTick());

        AfkFarmCommon.LOGGER.info("AFK Farm Fabric initialized (client-only; server does not need this mod)");
    }
}
