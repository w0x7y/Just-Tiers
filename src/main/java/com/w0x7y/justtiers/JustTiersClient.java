package com.w0x7y.justtiers;

import com.w0x7y.justtiers.api.MctiersLikeSource;
import com.w0x7y.justtiers.api.NovaTiersSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.tier.Source;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JustTiersClient implements ClientModInitializer {

    private static JustTiersConfig config;
    private static TierCache cache;
    private static NovaTiersSource novaSource;
    private static Path configPath;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("justtiers.json");
        config = JustTiersConfig.load(configPath);

        novaSource = new NovaTiersSource(JustTiers.httpClient(), Source.NOVATIERS.baseUrl());
        cache = new TierCache(List.of(
                new MctiersLikeSource(Source.MCTIERS, JustTiers.httpClient(), Source.MCTIERS.baseUrl()),
                new MctiersLikeSource(Source.SUBTIERS, JustTiers.httpClient(), Source.SUBTIERS.baseUrl()),
                novaSource));

        // NovaTiers only offers a bulk list, so warm it once up front and refresh on a timer.
        novaSource.refresh();
        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor(runnable -> {
                    Thread thread = new Thread(runnable, "just-tiers-refresh");
                    thread.setDaemon(true);
                    return thread;
                });
        scheduler.scheduleWithFixedDelay(
                () -> {
                    novaSource.refresh();
                    cache.invalidateAll();
                },
                config.getNovaRefreshMinutes(), config.getNovaRefreshMinutes(), TimeUnit.MINUTES);

        JustTiers.LOGGER.info("Just-Tiers {} ready (mode {})",
                JustTiers.VERSION, config.getDisplayMode());
    }

    public static JustTiersConfig config() {
        return config;
    }

    public static TierCache cache() {
        return cache;
    }

    public static NovaTiersSource novaSource() {
        return novaSource;
    }

    public static void saveConfig() {
        config.save(configPath);
    }
}
