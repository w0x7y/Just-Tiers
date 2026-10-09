package com.w0x7y.justtiers.benchmark;

import com.sun.management.ThreadMXBean;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.render.model.Badge;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.render.model.TierView;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/** Standalone diagnostic, deliberately without pass/fail timing thresholds. */
public final class BadgeBenchmark {
    private static volatile int consumed;

    public static void main(String[] args) {
        int iterations = args.length == 0 ? 200_000 : Integer.parseInt(args[0]);
        if (iterations <= 0) throw new IllegalArgumentException("Iterations must be positive");
        AtomicInteger requests = new AtomicInteger();
        List<TierSource> sources = Source.ALL.stream().<TierSource>map(source -> new TierSource() {
            public Source source() { return source; }
            public CompletableFuture<Map<String, Tier>> fetch(UUID uuid) {
                requests.incrementAndGet();
                return CompletableFuture.completedFuture(Map.of(
                        JustTiersConfig.defaultGamemode(source), new Tier(2, true, false)));
            }
        }).toList();
        TierCache cache = new TierCache(sources);
        UUID[] players = new UUID[128];
        for (int i = 0; i < players.length; i++) {
            players[i] = new UUID(0x123456789abc4000L + i, 0x8123456789abcdefL);
            for (Source source : Source.ALL) cache.load(source, players[i]).join();
        }
        int warmedRequests = requests.get();
        JustTiersConfig config = new JustTiersConfig();
        TierView view = new TierView() {
            public NametagSettings settings() { return config.nametagSettings(); }
            public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
                return cache.peek(source, uuid);
            }
        };
        ThreadMXBean allocation = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() ? bean : null;
        if (allocation != null) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        System.out.printf("Java %s; players=%d; iterations=%d; warmRequests=%d%n",
                System.getProperty("java.version"), players.length, iterations, warmedRequests);
        for (Set<Source> sites : java.util.List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
            Source.ALL.forEach(source -> config.setSiteEnabled(source, sites.contains(source)));
            run(view, players, iterations);
            for (int round = 1; round <= 5; round++) {
                long beforeBytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
                long started = System.nanoTime();
                run(view, players, iterations);
                long elapsed = System.nanoTime() - started;
                long bytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread) - beforeBytes;
                System.out.printf(Locale.ROOT, "%s round=%d ns/op=%.1f bytes/op=%s%n", sites.toString(),
                        round, (double) elapsed / iterations,
                        allocation == null ? "unsupported" : String.format(Locale.ROOT, "%.1f", (double) bytes / iterations));
            }
        }
        if (requests.get() != warmedRequests) throw new AssertionError("Benchmark cache was not warm");
        System.out.println("BADGE_BENCHMARK_COMPLETE consumed=" + consumed + " additionalRequests=0");
    }

    private static void run(TierView view, UUID[] players, int iterations) {
        int result = 0;
        for (int i = 0; i < iterations; i++) {
            Badge badge = Badge.forPlayer(view, players[i & 127]);
            result += badge.segments().size();
        }
        consumed = result;
    }
}
