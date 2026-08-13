package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreviewSampleTest {

    private static final Map<Source, String> DEFAULTS = Map.of(
            Source.MCTIERS, "vanilla",
            Source.SUBTIERS, "elytra",
            Source.NOVATIERS, "vanilla");

    private String text(DisplayMode mode, Map<Source, String> selected, boolean retired) {
        return NametagModel.plainText(PreviewSample.segments(mode, selected, retired));
    }

    @Test
    void everySampleSlugIsARealGamemode() {
        PreviewSample.TIERS.forEach((source, tiers) ->
                tiers.keySet().forEach(slug ->
                        assertTrue(Gamemodes.find(source, slug).isPresent(),
                                source + "/" + slug + " is not a real gamemode")));
    }

    @Test
    void everySiteHasSampleData() {
        for (Source source : Source.values()) {
            assertFalse(PreviewSample.TIERS.getOrDefault(source, Map.of()).isEmpty(),
                    "no sample data for " + source);
        }
    }

    @Test
    void allModeShowsOneEntryPerSite() {
        String text = text(DisplayMode.ALL, DEFAULTS, true);
        assertTrue(text.contains("HT2"), text);   // MCTiers vanilla
        assertTrue(text.contains("RHT2"), text);  // SubTiers minecart, retired
        assertTrue(text.contains("RHT1"), text);  // NovaTiers spearmace, retired
    }

    @Test
    void hidingRetiredChangesTheAllModePreview() {
        String shown = text(DisplayMode.ALL, DEFAULTS, true);
        String hidden = text(DisplayMode.ALL, DEFAULTS, false);
        assertNotEquals(shown, hidden);
        assertFalse(hidden.contains("R"), hidden);
        assertTrue(hidden.contains("LT3"), hidden);  // SubTiers falls back to elytra
        assertTrue(hidden.contains("HT4"), hidden);  // NovaTiers falls back to vanilla
    }

    @Test
    void changingTheSelectedGamemodeChangesTheSingleSitePreview() {
        String vanilla = text(DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, "vanilla"), true);
        String sword = text(DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, "sword"), true);
        assertTrue(vanilla.contains("HT2"), vanilla);
        assertTrue(sword.contains("HT4"), sword);
    }

    @Test
    void anUnrankedSelectionFallsBackAndSaysSo() {
        var caption = PreviewSample.caption(DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, "mace"), true);
        assertEquals(PreviewSample.Caption.Kind.FALLBACK, caption.kind());
        assertEquals("Mace", caption.gamemodeName());
        assertEquals("MCTiers", caption.sourceName());
        assertTrue(text(DisplayMode.MCTIERS_ONLY, Map.of(Source.MCTIERS, "mace"), true)
                .contains("HT2"));
    }

    @Test
    void aRankedSelectionReportsPlainSample() {
        var caption = PreviewSample.caption(DisplayMode.MCTIERS_ONLY,
                Map.of(Source.MCTIERS, "vanilla"), true);
        assertEquals(PreviewSample.Caption.Kind.SAMPLE, caption.kind());
        assertEquals("Vanilla", caption.gamemodeName());
    }

    @Test
    void sampleDataIsImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> PreviewSample.TIERS.get(Source.MCTIERS).clear());
    }
}
