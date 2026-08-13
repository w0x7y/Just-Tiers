package com.w0x7y.justtiers.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

/**
 * Adds the config button to Just-Tiers' entry in ModMenu's mod list. Entrypoints for
 * absent mods are never constructed, so this class is only ever loaded when ModMenu is.
 */
public final class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return JustTiersScreens::create;
    }
}
