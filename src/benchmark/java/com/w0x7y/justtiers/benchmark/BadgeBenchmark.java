package com.w0x7y.justtiers.benchmark;

import com.sun.management.ThreadMXBean;
import com.w0x7y.justtiers.api.TierSource;
import com.w0x7y.justtiers.cache.TierCache;
import com.w0x7y.justtiers.config.JustTiersConfig;
import com.w0x7y.justtiers.render.NametagRenderer;
import com.w0x7y.justtiers.render.Nametags;
import com.w0x7y.justtiers.render.model.BadgeMemo;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.render.model.TierView;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.network.chat.Component;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.IntStream;

/** Standalone diagnostic, deliberately without pass/fail timing thresholds. */
public final class BadgeBenchmark {
    private static final int ROUNDS = 5;
    private static final Component NAME = Component.literal("Player");
    private static volatile int consumed;

    /** What every warmed player holds on every site, and whether retired tiers are shown. */
    private record Answers(String name, Function<Source, Map<String, Tier>> tiers, boolean showRetired) { }

    /** One way of turning a player into what the nametag draws, each with its own loop so its calls stay monomorphic. */
    private enum Path {
        /** What a frame costs when the inputs changed: a fresh memo has to build and draw. */
        REBUILT {
            int run(TierView view, UUID[] players, int iterations) {
                int result = 0;
                for (int i = 0; i < iterations; i++) {
                    Nametags.Drawn badge = NametagRenderer.memo().get(view, players[i & 127]);
                    result += (badge == null ? NAME : badge.beside(NAME)).getSiblings().size();
                }
                return result;
            }
        },
        /** What an ordinary frame costs: NametagRenderer.decorate, with one memo per player entity. */
        MEMOIZED {
            private final List<BadgeMemo<Nametags.Drawn>> memos =
                    IntStream.range(0, 128).mapToObj(i -> NametagRenderer.memo()).toList();

            int run(TierView view, UUID[] players, int iterations) {
                int result = 0;
                for (int i = 0; i < iterations; i++) {
                    Nametags.Drawn badge = memos.get(i & 127).get(view, players[i & 127]);
                    result += (badge == null ? NAME : badge.beside(NAME)).getSiblings().size();
                }
                return result;
            }
        };

        abstract int run(TierView view, UUID[] players, int iterations);
    }

    private static final List<Answers> CASES = List.of(
            new Answers("registered", source -> Map.of(
                    JustTiersConfig.defaultGamemode(source), new Tier(2, true, false)), true),
            // Gamemodes this build does not register: discovered, sorted and named per build.
            new Answers("discovered", source -> Map.of(
                    "unlisted_one", new Tier(2, true, false), "unlisted_two", new Tier(3, false, false)), true),
            // Retired tiers hidden: every build copies the answers without them.
            new Answers("retired-hidden", source -> Map.of(
                    JustTiersConfig.defaultGamemode(source), new Tier(1, true, true),
                    otherGamemode(source), new Tier(3, false, false)), false));

    public static void main(String[] args) {
        int iterations = args.length == 0 ? 200_000 : Integer.parseInt(args[0]);
        if (iterations <= 0) throw new IllegalArgumentException("Iterations must be positive");
        ThreadMXBean allocation = ManagementFactory.getThreadMXBean() instanceof ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported() ? bean : null;
        if (allocation != null) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().threadId();
        UUID[] players = new UUID[128];
        for (int i = 0; i < players.length; i++) {
            players[i] = new UUID(0x123456789abc4000L + i, 0x8123456789abcdefL);
        }
        System.out.printf("Java %s; players=%d; iterations=%d; median of %d rounds%n",
                System.getProperty("java.version"), players.length, iterations, ROUNDS);
        for (Answers answers : CASES) {
            AtomicInteger requests = new AtomicInteger();
            List<TierSource> sources = Source.ALL.stream().<TierSource>map(source -> new TierSource() {
                public Source source() { return source; }
                public CompletableFuture<Answer> fetchAnswer(UUID uuid) {
                    requests.incrementAndGet();
                    return CompletableFuture.completedFuture(
                            new Answer(answers.tiers().apply(source), Duration.ZERO, false));
                }
            }).toList();
            TierCache cache = new TierCache(sources);
            for (UUID player : players) {
                for (Source source : Source.ALL) cache.loadAnswer(source, player).join();
            }
            int warmedRequests = requests.get();
            JustTiersConfig config = new JustTiersConfig();
            config.setShowRetired(answers.showRetired());
            TierView view = new TierView() {
                public NametagSettings settings() { return config.nametagSettings(); }
                public Optional<Map<String, Tier>> peek(Source source, UUID uuid) {
                    return cache.peek(source, uuid);
                }
            };
            for (Set<Source> sites : List.of(Set.of(Source.PVPTIERS), Set.of(Source.PVPHQ), Set.of(Source.SUBTIERS), Set.of(Source.NOVATIERS), Set.copyOf(Source.ALL))) {
                Source.ALL.forEach(source -> config.setSiteEnabled(source, sites.contains(source)));
                for (Path path : Path.values()) {
                    consumed = path.run(view, players, iterations);
                    double[] nanos = new double[ROUNDS];
                    double[] bytes = new double[ROUNDS];
                    for (int round = 0; round < ROUNDS; round++) {
                        long beforeBytes = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
                        long started = System.nanoTime();
                        consumed = path.run(view, players, iterations);
                        nanos[round] = (double) (System.nanoTime() - started) / iterations;
                        bytes[round] = allocation == null ? Double.NaN
                                : (double) (allocation.getThreadAllocatedBytes(thread) - beforeBytes) / iterations;
                    }
                    System.out.printf(Locale.ROOT, "%-14s %-42s %-9s ns/op=%7.1f bytes/op=%s%n", answers.name(),
                            sites, path.name().toLowerCase(Locale.ROOT), median(nanos),
                            allocation == null ? "unsupported" : String.format(Locale.ROOT, "%.1f", median(bytes)));
                }
            }
            if (requests.get() != warmedRequests) throw new AssertionError("Benchmark cache was not warm");
        }
        System.out.println("BADGE_BENCHMARK_COMPLETE consumed=" + consumed + " additionalRequests=0");
    }

    private static double median(double[] values) {
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    private static String otherGamemode(Source source) {
        return Gamemodes.of(source).stream().map(Gamemode::slug)
                .filter(slug -> !slug.equals(JustTiersConfig.defaultGamemode(source))).findFirst().orElseThrow();
    }
}
