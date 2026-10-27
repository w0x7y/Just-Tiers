package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupResult;
import com.w0x7y.justtiers.lookup.LookupRow;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the lookup screen says: a cell's tooltip, a focused cell's narration and the
 * screen's own narration. The punctuation joining their parts is translated too, so a
 * translation can reorder them.
 *
 * <p>Minecraft-free: the screen turns each {@link Message} into a component.
 */
public final class LookupText {

    /** "Site: what the site says", for a focused cell and for a row without cells. */
    private static final String SITE_MESSAGE = "justtiers.lookup.siteMessage";
    /** One narrated sentence followed by the next. */
    private static final String SENTENCES = "justtiers.lookup.sentences";

    /** A translation key and its arguments: plain values, or messages to translate first. */
    public record Message(String key, List<?> args) {
        public Message {
            args = List.copyOf(args);
        }

        public Message(String key, Object... args) {
            this(key, List.of(args));
        }
    }

    /** One cell's tooltip, a message per line. */
    public static List<Message> tooltip(LookupCell cell, LookupResult result) {
        String gamemode = cell.gamemode().displayName();
        List<Message> lines = new ArrayList<>();
        lines.add(cell.tier()
                .map(tier -> new Message("justtiers.lookup.cellTier", gamemode, tier.label()))
                .orElseGet(() -> new Message("justtiers.lookup.cellUntested", gamemode)));
        if (!Gamemodes.isRegistered(cell.gamemode())) {
            lines.add(new Message("justtiers.lookup.unrecognizedGamemode"));
        }
        result.freshness().ifPresent(freshness -> {
            lines.add(new Message("justtiers.lookup.age", freshness.age().toMinutes()));
            String key = switch (freshness.status()) {
                case REFRESH_FAILED -> "justtiers.lookup.cachedUnavailable";
                case REFRESHING -> "justtiers.lookup.cachedRefreshing";
                case STALE -> "justtiers.lookup.cachedStale";
                case FRESH -> null;
            };
            if (key != null) lines.add(new Message(key));
        });
        return lines;
    }

    /** What a focused cell narrates: its tooltip, introduced by the cell's site. */
    public static List<Message> cellNarration(Source source, LookupCell cell, LookupResult result) {
        List<Message> lines = tooltip(cell, result);
        lines.set(0, new Message(SITE_MESSAGE, source.displayName(), lines.getFirst()));
        return lines;
    }

    /**
     * Whose tiers these are, then why the lookup failed or which sites are still pending
     * or unavailable. Answered sites are narrated by their focusable cells instead.
     *
     * @param error the failure the screen shows, already translated
     */
    public static Message narration(String name, Optional<?> error,
                                    Function<Source, LookupResult> results) {
        Message message = new Message("justtiers.lookup.header", name);
        if (error.isPresent()) {
            return new Message(SENTENCES, message, error.get());
        }
        for (Source source : Source.ALL) {
            String key = switch (LookupRow.state(results.apply(source).section())) {
                case PENDING -> "justtiers.lookup.pending";
                case UNAVAILABLE -> "justtiers.lookup.unavailable";
                case ANSWERED -> null;
            };
            if (key != null) {
                message = new Message(SENTENCES, message,
                        new Message(SITE_MESSAGE, source.displayName(), new Message(key)));
            }
        }
        return message;
    }

    private LookupText() {
    }
}
