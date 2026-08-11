package com.w0x7y.justtiers;

import net.fabricmc.api.ClientModInitializer;

public class JustTiersClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        JustTiers.LOGGER.info("Just-Tiers {} initialising", JustTiers.VERSION);
    }
}
