package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.scan.ScanQueue;
import com.w0x7y.justtiers.scan.ScanReport;
import com.w0x7y.justtiers.scan.ScanRow;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One run of {@code /justtiers scan}: every player on the server, what each site has said
 * about them so far, and the order that puts the dangerous ones on top.
 *
 * <p>Threading follows {@link LookupSession} exactly — written and read on the client
 * thread, with answers handed over by {@link Minecraft#execute} — so nothing here needs
 * synchronising and the screen can draw {@link #rows()} without copying it.
 *
 * <p>NovaTiers is answered from the index already in memory, so the screen opens
 * populated and sorted. MCTiers and SubTiers are per-player HTTP calls and go through a
 * {@link ScanQueue}: a full lobby is several hundred requests, and arriving all at once
 * is how a client mod gets a leaderboard's rate limiter pointed at it.
 *
 * <p>The row set is fixed when the scan starts. Players joining or leaving are not picked
 * up, which keeps the queue finite and the order stable; re-opening the screen rescans.
 */
public final class ScanSession {

    /** Enough that a full lobby lands in seconds; few enough to stay a polite guest. */
    private static final int MAX_IN_FLIGHT = 6;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final ScanQueue queue = new ScanQueue(MAX_IN_FLIGHT);

    private List<ScanRow> rows = List.of();
    private Component error;
    private int answered;

    /** One player's answers as they arrive, before they are frozen into a {@link ScanRow}. */
    private record Entry(PlayerRef player, Map<Source, LookupSection> sections) {
    }

    private ScanSession() {
    }

    public static ScanSession start() {
        ScanSession session = new ScanSession();
        session.begin();
        return session;
    }

    private void begin() {
        if (Minecraft.getInstance().getConnection() == null) {
            error = Component.translatable("justtiers.scan.empty.noServer");
            return;
        }

        List<PlayerRef> players = OnlinePlayers.all();
        if (players.isEmpty()) {
            error = Component.translatable("justtiers.scan.empty.noPlayers");
            return;
        }
        for (PlayerRef player : players) {
            entries.put(player.uuid(), new Entry(player, new EnumMap<>(Source.class)));
        }
        rebuild();

        // NovaTiers first and unqueued: it is a map lookup, and putting it behind the
        // in-flight cap would make the screen open blank for no reason at all.
        for (PlayerRef player : players) {
            request(Source.NOVATIERS, player, false);
        }
        for (PlayerRef player : players) {
            queue.submit(() -> request(Source.MCTIERS, player, true));
            queue.submit(() -> request(Source.SUBTIERS, player, true));
        }
    }

    private void request(Source site, PlayerRef player, boolean queued) {
        JustTiersClient.cache().load(site, player.uuid()).handle((tiers, failure) -> {
            Optional<Map<String, Tier>> answer;
            if (failure != null) {
                // Same rule as a lookup: nothing will peek at these players to clear a
                // cached failure, so drop it and let re-opening the screen retry.
                JustTiersClient.cache().forgetFailed(site, player.uuid());
                answer = Optional.empty();
            } else {
                answer = Optional.of(TierResolver.activeOnly(tiers));
            }
            onClient(() -> {
                accept(site, player, answer);
                if (queued) {
                    queue.completed();
                }
            });
            return null;
        });
    }

    /**
     * Retired placements were stripped before this ran, so they can neither score nor
     * reach the grid. A scan asks who is a threat now; nobody is defending a retired tier.
     */
    private void accept(Source site, PlayerRef player, Optional<Map<String, Tier>> answer) {
        Entry entry = entries.get(player.uuid());
        if (entry == null) {
            return;
        }
        boolean wasComplete = entry.sections().size() == Source.ALL.size();
        entry.sections().put(site, LookupReport.section(site, answer));
        if (!wasComplete && entry.sections().size() == Source.ALL.size()) {
            answered++;
        }
        rebuild();
    }

    /** Rebuilt and re-sorted on every answer, which is cheap next to one HTTP round trip. */
    private void rebuild() {
        List<ScanRow> built = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            built.add(ScanRow.of(entry.player(), entry.sections()));
        }
        rows = ScanReport.sorted(built);
    }

    /** Pre-sorted and immutable: the screen draws this straight, every frame. */
    public List<ScanRow> rows() {
        return rows;
    }

    /** Players every site has now answered for. */
    public int answered() {
        return answered;
    }

    public int total() {
        return entries.size();
    }

    public boolean complete() {
        return !entries.isEmpty() && answered == entries.size();
    }

    /** The message to show instead of any rows, when there is nobody to scan. */
    public Optional<Component> error() {
        return Optional.ofNullable(error);
    }

    private static void onClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }
}
