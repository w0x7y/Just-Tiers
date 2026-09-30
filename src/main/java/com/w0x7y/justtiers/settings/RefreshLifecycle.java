package com.w0x7y.justtiers.settings;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.tier.Source;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/** Owns refresh timing and the different invalidation rules for manual and timed refreshes. */
public final class RefreshLifecycle {
    @FunctionalInterface
    interface Timer {
        /** Starts a repeating task; the returned action cancels it without interruption. */
        Runnable repeat(int minutes, Runnable action);
    }

    private final TierCache cache;
    private final Supplier<CompletableFuture<Void>> download;
    private final Timer timer;
    private Runnable cancel;
    private int interval;

    public RefreshLifecycle(TierCache cache, Supplier<CompletableFuture<Void>> download) {
        this(cache, download, daemonTimer());
    }

    RefreshLifecycle(TierCache cache, Supplier<CompletableFuture<Void>> download, Timer timer) {
        this.cache = cache;
        this.download = download;
        this.timer = timer;
    }

    void setInterval(int minutes) {
        if (cancel != null && interval == minutes) return;
        if (cancel != null) cancel.run();
        interval = minutes;
        cancel = timer.repeat(minutes, this::scheduledRefresh);
        JustTiers.LOGGER.info("NovaTiers refresh scheduled every {} minutes", minutes);
    }

    /** Manual refresh clears retry state while keeping last-known placements visible. */
    public CompletableFuture<Void> refreshNow() {
        cache.refreshAll();
        return refreshIndex();
    }

    private CompletableFuture<Void> refreshIndex() {
        return download.get().whenComplete((ignored, error) -> {
            if (error == null) cache.invalidate(Source.NOVATIERS);
        });
    }

    private void scheduledRefresh() {
        try {
            refreshIndex().whenComplete((ignored, error) -> {
                if (error != null) {
                    JustTiers.LOGGER.warn("NovaTiers refresh failed; keeping stale data", error);
                }
            });
        } catch (Throwable failure) {
            // A timer must survive a synchronous source failure as well as a failed future.
            JustTiers.LOGGER.warn("NovaTiers refresh task failed; keeping stale data", failure);
        }
    }

    private static Timer daemonTimer() {
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "just-tiers-refresh");
            thread.setDaemon(true);
            return thread;
        });
        return (minutes, action) -> {
            var task = executor.scheduleWithFixedDelay(action, minutes, minutes, TimeUnit.MINUTES);
            return () -> task.cancel(false);
        };
    }
}
