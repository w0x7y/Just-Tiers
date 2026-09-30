package com.w0x7y.justtiers.settings;

import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.CachePolicy;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SettingsApplicationTest {
    @TempDir Path directory;
    private final AtomicLong clock = new AtomicLong();
    private final UUID player = UUID.randomUUID();
    private final Map<Source, Integer> fetches = new EnumMap<>(Source.class);
    private final TierCache cache = new TierCache(List.of(source(Source.MCTIERS), source(Source.NOVATIERS)),
            CachePolicy.DEFAULT, clock::get, () -> 0.5);
    private final ManualTimer timer = new ManualTimer();
    private final List<CompletableFuture<Void>> downloads = new ArrayList<>();
    private final RefreshLifecycle refresh = new RefreshLifecycle(cache, () -> {
        var download = new CompletableFuture<Void>();
        downloads.add(download);
        return download;
    }, timer);

    private SettingsApplication application(Path path) {
        return new SettingsApplication(new JustTiersConfig(), path, cache, refresh);
    }

    @Test void failedDraftLeavesActiveSettingsDiskAndRuntimeUntouched() throws Exception {
        Path blocked = blockedDestination();
        String diskBefore = Files.readString(blocked.resolve("previous.json"));
        SettingsApplication settings = application(blocked);
        cache.peek(Source.MCTIERS, player);
        JustTiersConfig draft = settings.draft();
        draft.setEnabled(false);
        draft.setTierCacheMinutes(5);
        draft.setNovaRefreshMinutes(5);

        assertThrows(UncheckedIOException.class, () -> settings.commitDraft(draft));

        assertTrue(settings.active().isEnabled());
        assertEquals(60, settings.active().getTierCacheMinutes());
        assertEquals(30, settings.active().getNovaRefreshMinutes());
        assertEquals(diskBefore, Files.readString(blocked.resolve("previous.json")));
        clock.set(Duration.ofMinutes(6).toNanos());
        assertTrue(cache.peek(Source.MCTIERS, player).isPresent());
        assertEquals(1, fetches.get(Source.MCTIERS));
        timer.advance(6);
        assertEquals(0, downloads.size());
        assertFalse(draft.isEnabled(), "the failed draft remains available for retry");
    }

    @Test void failedDraftCanBeRetriedAfterTheFilesystemIsRepaired() throws Exception {
        Path blocked = blockedDestination();
        SettingsApplication settings = application(blocked);
        JustTiersConfig draft = settings.draft();
        draft.setEnabled(false);
        assertThrows(UncheckedIOException.class, () -> settings.commitDraft(draft));
        Files.delete(blocked.resolve("previous.json"));
        Files.delete(blocked);

        settings.commitDraft(draft);

        assertFalse(settings.active().isEnabled());
        assertFalse(JustTiersConfig.load(blocked).isEnabled());
    }

    @Test void failedCommandKeepsSessionValuesAndRuntimeEffectsButPreservesDisk() throws Exception {
        Path blocked = blockedDestination();
        String diskBefore = Files.readString(blocked.resolve("previous.json"));
        SettingsApplication settings = application(blocked);
        cache.peek(Source.MCTIERS, player);

        assertThrows(UncheckedIOException.class, () -> settings.editSession(config -> {
            config.setEnabled(false);
            config.setTierCacheMinutes(5);
            config.setNovaRefreshMinutes(5);
        }));

        assertFalse(settings.active().isEnabled());
        assertEquals(5, settings.active().getTierCacheMinutes());
        assertEquals(5, settings.active().getNovaRefreshMinutes());
        assertEquals(diskBefore, Files.readString(blocked.resolve("previous.json")));
        clock.set(Duration.ofMinutes(6).toNanos());
        cache.peek(Source.MCTIERS, player);
        assertEquals(2, fetches.get(Source.MCTIERS));
        timer.advance(5);
        assertEquals(1, downloads.size());
    }

    @Test void successfulDraftPublishesIndependentValuesAndUpdatesExistingCacheTtl() {
        Path file = directory.resolve("justtiers.json");
        SettingsApplication settings = application(file);
        cache.peek(Source.MCTIERS, player);
        JustTiersConfig draft = settings.draft();
        draft.setEnabled(false);
        draft.setTierCacheMinutes(5);
        draft.setNovaRefreshMinutes(5);
        settings.commitDraft(draft);
        draft.setEnabled(true);

        assertFalse(settings.active().isEnabled());
        assertFalse(JustTiersConfig.load(file).isEnabled());
        assertEquals(5, JustTiersConfig.load(file).getTierCacheMinutes());
        assertTrue(cache.peek(Source.MCTIERS, player).isPresent(), "saving retains fresh entries");
        clock.set(Duration.ofMinutes(6).toNanos());
        cache.peek(Source.MCTIERS, player);
        assertEquals(2, fetches.get(Source.MCTIERS), "the existing entry uses the new TTL");
        timer.advance(5);
        assertEquals(1, downloads.size());
    }

    @Test void successfulCommandPersistsAndUnchangedIntervalKeepsItsCountdown() {
        Path file = directory.resolve("justtiers.json");
        SettingsApplication settings = application(file);
        timer.advance(20);
        settings.editSession(config -> config.setEnabled(false));
        assertFalse(settings.active().isEnabled());
        assertFalse(JustTiersConfig.load(file).isEnabled());
        timer.advance(10);
        assertEquals(1, downloads.size(), "an unrelated edit must not postpone the refresh");
        timer.advance(30);
        assertEquals(2, downloads.size());
    }

    @Test void changedIntervalCancelsOldCountdown() {
        SettingsApplication settings = application(directory.resolve("justtiers.json"));
        timer.advance(20);
        settings.editSession(config -> config.setNovaRefreshMinutes(60));
        timer.advance(10);
        assertEquals(0, downloads.size());
        timer.advance(50);
        assertEquals(1, downloads.size());
    }

    @Test void scheduledRefreshRetainsEntriesUntilSuccessAndPreservesOtherSites() {
        application(directory.resolve("justtiers.json"));
        cache.peek(Source.NOVATIERS, player);
        cache.peek(Source.MCTIERS, player);
        timer.advance(30);
        assertEquals(1, cache.cachedPlayers(Source.NOVATIERS));
        downloads.getFirst().completeExceptionally(new IllegalStateException("offline"));
        assertEquals(1, cache.cachedPlayers(Source.NOVATIERS));
        timer.advance(30);
        downloads.getLast().complete(null);
        assertEquals(0, cache.cachedPlayers(Source.NOVATIERS));
        assertEquals(1, cache.cachedPlayers(Source.MCTIERS));
    }

    @Test void synchronousDownloadFailureDoesNotStopTheRepeatingTimer() {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        RefreshLifecycle throwing = new RefreshLifecycle(cache, () -> {
            calls.incrementAndGet();
            throw new IllegalStateException("source failed before returning a future");
        }, timer);
        throwing.setInterval(5);
        cache.peek(Source.NOVATIERS, player);

        timer.advance(10);

        assertEquals(2, calls.get());
        assertEquals(1, cache.cachedPlayers(Source.NOVATIERS));
    }

    @Test void manualRefreshRetainsAnswersAndOnlyInvalidatesNewNovaEntriesOnSuccess() {
        application(directory.resolve("justtiers.json"));
        cache.peek(Source.MCTIERS, player);
        cache.peek(Source.NOVATIERS, player);
        CompletableFuture<Void> failed = refresh.refreshNow();
        assertEquals(1, cache.cachedPlayers(Source.MCTIERS));
        assertEquals(1, cache.cachedPlayers(Source.NOVATIERS));
        cache.peek(Source.NOVATIERS, player);
        downloads.getLast().completeExceptionally(new IllegalStateException("offline"));
        assertTrue(failed.isCompletedExceptionally());
        assertEquals(1, cache.cachedPlayers(Source.NOVATIERS));
        CompletableFuture<Void> success = refresh.refreshNow();
        cache.peek(Source.NOVATIERS, player);
        downloads.getLast().complete(null);
        success.join();
        assertEquals(0, cache.cachedPlayers(Source.NOVATIERS));
    }

    private Path blockedDestination() throws Exception {
        Path blocked = Files.createDirectory(directory.resolve("justtiers.json"));
        new JustTiersConfig().save(blocked.resolve("previous.json"));
        return blocked;
    }

    private TierSource source(Source source) {
        return new TierSource() {
            public Source source() { return source; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID player) {
                fetches.merge(source, 1, Integer::sum);
                return CompletableFuture.completedFuture(Map.of());
            }
        };
    }

    private static final class ManualTimer implements RefreshLifecycle.Timer {
        private long minute;
        private final List<Task> tasks = new ArrayList<>();
        public Runnable repeat(int minutes, Runnable action) {
            Task task = new Task(minute + minutes, minutes, action);
            tasks.add(task);
            return () -> task.cancelled = true;
        }
        void advance(int minutes) {
            long end = minute + minutes;
            while (++minute <= end) {
                for (Task task : tasks) {
                    if (!task.cancelled && task.due == minute) {
                        task.action.run();
                        task.due += task.interval;
                    }
                }
            }
            minute = end;
        }
        private static final class Task {
            long due;
            final int interval;
            final Runnable action;
            boolean cancelled;
            Task(long due, int interval, Runnable action) {
                this.due = due;
                this.interval = interval;
                this.action = action;
            }
        }
    }
}
