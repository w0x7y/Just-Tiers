package com.w0x7y.justtiers;

import com.w0x7y.justtiers.api.MctiersLikeSource;
import com.w0x7y.justtiers.api.MojangNameSource;
import com.w0x7y.justtiers.api.NovaTiersSource;
import com.w0x7y.justtiers.cache.CachePolicy;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.command.JustTiersCommands;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.gui.DownloadHud;
import com.w0x7y.justtiers.gui.JustTiersKeybinds;
import com.w0x7y.justtiers.tier.Source;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class JustTiersClient implements ClientModInitializer {

    private static JustTiersConfig config;
    private static TierCache cache;
    private static NovaTiersSource novaSource;
    private static MojangNameSource nameSource;
    private static DownloadProgress downloadProgress;
    private static Path configPath;
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> refreshTask;
    private static int scheduledRefreshMinutes;

    @Override
    public void onInitializeClient() {
        configPath = FabricLoader.getInstance().getConfigDir().resolve("justtiers.json");
        config = JustTiersConfig.load(configPath);

        downloadProgress = new DownloadProgress();
        novaSource = new NovaTiersSource(
                JustTiers.httpClient(), Source.NOVATIERS.baseUrl(), downloadProgress);
        cache = new TierCache(List.of(
                new MctiersLikeSource(Source.MCTIERS, JustTiers.httpClient(), Source.MCTIERS.baseUrl()),
                new MctiersLikeSource(Source.SUBTIERS, JustTiers.httpClient(), Source.SUBTIERS.baseUrl()),
                novaSource),
                CachePolicy.DEFAULT.withTtl(Duration.ofMinutes(config.getTierCacheMinutes())));
        // Only ever asked about names /justtiers lookup could not find on the server.
        nameSource = new MojangNameSource(
                JustTiers.httpClient(), MojangNameSource.DEFAULT_BASE_URL);

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
        DownloadHud.register();

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

    public static MojangNameSource names() {
        return nameSource;
    }

    public static DownloadProgress downloadProgress() {
        return downloadProgress;
    }

    public static void saveConfig() {
        config.save(configPath);
        // The interval is a live setting, so a changed slider takes effect now rather
        // than at next launch.
        scheduleNovaRefresh(config.getNovaRefreshMinutes());
        // Live too, and applied without discarding what is already cached.
        cache.setTtl(Duration.ofMinutes(config.getTierCacheMinutes()));
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
        // Rescheduling restarts the countdown from zero. Every saved setting comes
        // through here, so rescheduling unconditionally meant a player who toggled
        // anything more often than the interval pushed the refresh back indefinitely
        // and never got one. Only an actual change to the interval is worth a restart.
        if (refreshTask != null && minutes == scheduledRefreshMinutes) {
            return;
        }
        if (refreshTask != null) {
            refreshTask.cancel(false);
        }
        scheduledRefreshMinutes = minutes;
        refreshTask = scheduler.scheduleWithFixedDelay(
                () -> {
                    try {
                        // refresh() returns as soon as the download starts, so the cache
                        // is cleared on completion rather than here: dropping the entries
                        // up front would blank every NovaTiers badge for the length of a
                        // ~1.7 MB download, and what is already cached stays correct until
                        // the new index actually replaces the old one.
                        novaSource.refresh().whenComplete((ignored, error) -> {
                            if (error != null) {
                                // NovaTiersSource already logs the cause and keeps the
                                // previous index; this only guards the cache.
                                JustTiers.LOGGER.warn("NovaTiers refresh failed; keeping stale data", error);
                                return;
                            }
                            cache.invalidate(Source.NOVATIERS);
                        });
                    } catch (Throwable t) {
                        JustTiers.LOGGER.warn("NovaTiers refresh task failed; keeping stale data", t);
                    }
                },
                minutes, minutes, TimeUnit.MINUTES);
        JustTiers.LOGGER.info("NovaTiers refresh scheduled every {} minutes", minutes);
    }
}
