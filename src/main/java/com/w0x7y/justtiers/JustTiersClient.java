package com.w0x7y.justtiers;

import com.w0x7y.justtiers.api.MctiersLikeSource;
import com.w0x7y.justtiers.api.NovaTiersSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.command.JustTiersCommands;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.gui.JustTiersKeybinds;
import com.w0x7y.justtiers.tier.Source;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JustTiersClient implements ClientModInitializer {

    private static JustTiersConfig config;
    private static TierCache cache;
    private static NovaTiersSource novaSource;
    private static Path configPath;
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> refreshTask;

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
        scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "just-tiers-refresh");
            thread.setDaemon(true);
            return thread;
        });
        scheduleNovaRefresh(config.getNovaRefreshMinutes());

        JustTiersCommands.register();
        JustTiersKeybinds.register();

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
        // The interval is a live setting, so a changed slider takes effect now rather
        // than at next launch. Harmless when the interval did not actually change.
        scheduleNovaRefresh(config.getNovaRefreshMinutes());
    }

    /**
     * Replaces the standing refresh timer with one at the given interval. The
     * outstanding task is cancelled without interrupting it — a download in flight is
     * left to finish rather than being torn up mid-stream.
     */
    private static void scheduleNovaRefresh(int minutes) {
        if (scheduler == null) {
            return;
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        refreshTask = scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        novaSource.refresh();
                        cache.invalidate(Source.NOVATIERS);
                    } catch (Throwable t) {
                        JustTiers.LOGGER.warn("NovaTiers refresh task failed; keeping stale data", t);
                    }
                },
                minutes, minutes, TimeUnit.MINUTES);
        JustTiers.LOGGER.info("NovaTiers refresh scheduled every {} minutes", minutes);
    }
}
