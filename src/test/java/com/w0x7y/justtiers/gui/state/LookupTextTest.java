package com.w0x7y.justtiers.gui.state;

import com.google.gson.JsonParser;
import com.w0x7y.justtiers.gui.state.LookupText.Message;
import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupResult;
import com.w0x7y.justtiers.lookup.LookupResult.Freshness;
import com.w0x7y.justtiers.lookup.LookupResult.RefreshStatus;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** The lookup screen's words, punctuation included, come from translations. */
class LookupTextTest {

    private static final Map<String, String> ENGLISH = english();
    private static final Pattern PLACEHOLDER = Pattern.compile("%s");
    private static final Tier HT1 = new Tier(1, true, false);
    private static final Gamemode REGISTERED = Gamemodes.of(Source.PVPTIERS).getFirst();
    private static final LookupResult ANSWERED = result(Optional.empty());

    private static LookupResult result(Optional<Freshness> freshness) {
        return new LookupResult(Optional.of(LookupReport.section(Source.PVPTIERS,
                Optional.of(Map.of()))), true, freshness);
    }

    private static Map<String, String> english() {
        try (var reader = new InputStreamReader(LookupTextTest.class.getResourceAsStream(
                "/assets/justtiers/lang/en_us.json"), StandardCharsets.UTF_8)) {
            Map<String, String> translations = new HashMap<>();
            JsonParser.parseReader(reader).getAsJsonObject().entrySet()
                    .forEach(entry -> translations.put(entry.getKey(), entry.getValue().getAsString()));
            return translations;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Fills each {@code %s} in order, as Minecraft does for these translations. */
    private static String render(Object part) {
        if (!(part instanceof Message message)) return String.valueOf(part);
        String format = ENGLISH.get(message.key());
        assertNotNull(format, "missing English translation: " + message.key());
        Matcher placeholders = PLACEHOLDER.matcher(format);
        StringBuilder text = new StringBuilder();
        int used = 0;
        while (placeholders.find()) {
            placeholders.appendReplacement(text, Matcher.quoteReplacement(render(message.args().get(used++))));
        }
        placeholders.appendTail(text);
        assertEquals(message.args().size(), used, "unused arguments for " + message.key());
        return text.toString();
    }

    private static String render(List<Message> lines) {
        return lines.stream().map(LookupTextTest::render).collect(Collectors.joining("\n"));
    }

    @Test
    void aRankedCellJoinsItsGamemodeAndTierThroughATranslation() {
        List<Message> lines = LookupText.tooltip(new LookupCell(REGISTERED, Optional.of(HT1)), ANSWERED);
        assertEquals(List.of(new Message("justtiers.lookup.cellTier", REGISTERED.displayName(), "HT1")), lines);
        assertEquals(REGISTERED.displayName() + ": HT1", render(lines));
    }

    @Test
    void anUnrankedCellSaysItHasNoListedTier() {
        List<Message> lines = LookupText.tooltip(new LookupCell(REGISTERED, Optional.empty()), ANSWERED);
        assertEquals(REGISTERED.displayName() + ": no listed tier", render(lines));
    }

    @Test
    void aDiscoveredGamemodeSaysThisBuildDoesNotRecognizeIt() {
        Gamemode discovered = Gamemodes.discovered(Source.PVPTIERS, List.of("future_mode")).getFirst();
        List<Message> lines = LookupText.tooltip(new LookupCell(discovered, Optional.of(HT1)), ANSWERED);
        assertEquals(List.of(new Message("justtiers.lookup.cellTier", "Future Mode", "HT1"),
                new Message("justtiers.lookup.unrecognizedGamemode")), lines);
    }

    @Test
    void freshnessAddsTheAnswerAgeAndOnlyANonFreshRefreshState() {
        LookupCell cell = new LookupCell(REGISTERED, Optional.of(HT1));
        assertEquals(REGISTERED.displayName() + ": HT1\nAnswer received 3 minute(s) ago.",
                render(LookupText.tooltip(cell, result(Optional.of(
                        new Freshness(Duration.ofMinutes(3), RefreshStatus.FRESH))))));
        assertEquals(new Message("justtiers.lookup.cachedRefreshing"),
                LookupText.tooltip(cell, result(Optional.of(
                        new Freshness(Duration.ofMinutes(3), RefreshStatus.REFRESHING)))).getLast());
        assertEquals(new Message("justtiers.lookup.cachedUnavailable"),
                LookupText.tooltip(cell, result(Optional.of(
                        new Freshness(Duration.ZERO, RefreshStatus.REFRESH_FAILED)))).getLast());
        assertEquals(new Message("justtiers.lookup.cachedStale"),
                LookupText.tooltip(cell, result(Optional.of(
                        new Freshness(Duration.ZERO, RefreshStatus.STALE)))).getLast());
    }

    @Test
    void aFocusedCellIsIntroducedByItsSiteThroughATranslation() {
        LookupResult result = result(Optional.of(new Freshness(Duration.ofMinutes(3), RefreshStatus.FRESH)));
        List<Message> lines = LookupText.cellNarration(Source.PVPTIERS,
                new LookupCell(REGISTERED, Optional.of(HT1)), result);
        assertEquals("justtiers.lookup.siteMessage", lines.getFirst().key());
        assertEquals("PvPTiers: " + REGISTERED.displayName() + ": HT1\nAnswer received 3 minute(s) ago.",
                render(lines));
    }

    @Test
    void narrationNamesEachSiteStillWaitingOrUnavailable() {
        Map<Source, LookupResult> results = new HashMap<>();
        for (Source source : Source.ALL) results.put(source, ANSWERED);
        results.put(Source.ALL.getFirst(), new LookupResult(Optional.empty(), false, Optional.empty()));
        results.put(Source.ALL.getLast(), new LookupResult(Optional.of(
                LookupReport.section(Source.ALL.getLast(), Optional.empty())), true, Optional.empty()));

        Message narration = LookupText.narration("Notch", Optional.empty(), results::get);
        assertEquals("justtiers.lookup.sentences", narration.key());
        assertEquals("Tiers for Notch. " + Source.ALL.getFirst().displayName() + ": Looking up.... "
                        + Source.ALL.getLast().displayName() + ": Site unavailable. Try again.",
                render(narration));
    }

    @Test
    void narrationOfAnAnsweredLookupIsItsHeader() {
        assertEquals(new Message("justtiers.lookup.header", "Notch"),
                LookupText.narration("Notch", Optional.empty(), source -> ANSWERED));
    }

    @Test
    void narrationOfAFailedLookupIsItsHeaderAndError() {
        Message narration = LookupText.narration("Notch", Optional.of("No such account."),
                source -> fail("a failed lookup has no rows to narrate"));
        assertEquals(new Message("justtiers.lookup.sentences",
                new Message("justtiers.lookup.header", "Notch"), "No such account."), narration);
        assertEquals("Tiers for Notch. No such account.", render(narration));
    }
}
