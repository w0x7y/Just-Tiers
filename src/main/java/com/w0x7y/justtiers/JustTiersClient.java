package com.w0x7y.justtiers;

import com.w0x7y.justtiers.api.MojangNameSource;
import com.w0x7y.justtiers.api.NovaTiersSource;
import com.w0x7y.justtiers.api.ProfileTierSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.command.JustTiersCommands;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.gui.DownloadHud;
import com.w0x7y.justtiers.gui.JustTiersKeybinds;
import com.w0x7y.justtiers.settings.RefreshLifecycle;
import com.w0x7y.justtiers.settings.SettingsApplication;
import com.w0x7y.justtiers.tier.Source;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;

public class JustTiersClient implements ClientModInitializer {

    private static SettingsApplication settings;
    private static RefreshLifecycle refresh;
    private static TierCache cache;
    private static NovaTiersSource novaSource;
    private static MojangNameSource nameSource;
    private static DownloadProgress downloadProgress;

    @Override
    public void onInitializeClient() {
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("justtiers.json");
        JustTiersConfig config = JustTiersConfig.load(configPath);

        downloadProgress = new DownloadProgress();
        novaSource = new NovaTiersSource(
                JustTiers.httpClient(), Source.NOVATIERS.baseUrl(), downloadProgress);
        cache = new TierCache(List.of(
                new ProfileTierSource(Source.PVPTIERS, JustTiers.httpClient(), Source.PVPTIERS.baseUrl()),
                new ProfileTierSource(Source.PVPHQ, JustTiers.httpClient(), Source.PVPHQ.baseUrl()),
                new ProfileTierSource(Source.SUBTIERS, JustTiers.httpClient(), Source.SUBTIERS.baseUrl()),
                novaSource));
        // Only ever asked about names /justtiers lookup could not find on the server.
        nameSource = new MojangNameSource(
                JustTiers.httpClient(), MojangNameSource.DEFAULT_BASE_URL);

        // NovaTiers only offers a bulk list, so warm it once up front and refresh on a timer.
        novaSource.refresh();
        refresh = new RefreshLifecycle(cache, novaSource::refresh);
        settings = new SettingsApplication(config, configPath, cache, refresh);

        JustTiersCommands.register();
        JustTiersKeybinds.register();
        DownloadHud.register();

        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(
                new net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.EndTick() {
                    private int ticks;
                    @Override public void onEndTick(net.minecraft.client.Minecraft client) {
                        if (++ticks >= 1200) {
                            ticks = 0;
                            cache.maintain();
                        }
                    }
                });

        JustTiers.LOGGER.info("Just-Tiers {} ready (sites {})",
                JustTiers.VERSION, config.enabledSources());
    }

    public static JustTiersConfig config() {
        return settings.active();
    }

    public static TierCache cache() {
        return cache;
    }

    public static NovaTiersSource novaSource() {
        return novaSource;
    }

    public static MojangNameSource names() {
        return nameSource;
    }

    public static DownloadProgress downloadProgress() {
        return downloadProgress;
    }

    public static SettingsApplication settings() {
        return settings;
    }

    public static java.util.concurrent.CompletableFuture<Void> refreshData() {
        return refresh.refreshNow();
    }
}
