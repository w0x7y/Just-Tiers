package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagStyle;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class JustTiersConfigTest {

    @Test
    void defaultsAreSensible() {
        JustTiersConfig config = new JustTiersConfig();
        assertTrue(config.isEnabled());
        assertEquals(DisplayMode.ALL, config.getDisplayMode());
        assertEquals("vanilla", config.selectedGamemode(Source.MCTIERS));
        assertEquals("elytra", config.selectedGamemode(Source.SUBTIERS));
        assertEquals("vanilla", config.selectedGamemode(Source.NOVATIERS));
        assertEquals(30, config.getNovaRefreshMinutes());
    }

    @Test
    void selectedGamemodesAreExposedBySource() {
        JustTiersConfig config = new JustTiersConfig();
        config.setSelectedGamemode(Source.MCTIERS, "axe");
        assertEquals("axe", config.selectedGamemodesBySource().get(Source.MCTIERS));
    }

    @Test
    void aLaterSelectionReplacesAnAlreadyReadOne() {
        JustTiersConfig config = new JustTiersConfig();
        config.setSelectedGamemode(Source.MCTIERS, "axe");
        assertEquals("axe", config.selectedGamemodesBySource().get(Source.MCTIERS));

        config.setSelectedGamemode(Source.MCTIERS, "sword");
        assertEquals("sword", config.selectedGamemodesBySource().get(Source.MCTIERS));
    }

    @Test
    void roundTripsThroughDisk(@TempDir Path dir) {
        Path file = dir.resolve("justtiers.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setDisplayMode(DisplayMode.SUBTIERS_ONLY);
        config.setSelectedGamemode(Source.SUBTIERS, "trident");
        config.setEnabled(false);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertEquals(DisplayMode.SUBTIERS_ONLY, loaded.getDisplayMode());
        assertEquals("trident", loaded.selectedGamemode(Source.SUBTIERS));
        assertFalse(loaded.isEnabled());
    }

    @Test
    void loadingAMissingFileYieldsDefaults(@TempDir Path dir) {
        JustTiersConfig loaded = JustTiersConfig.load(dir.resolve("absent.json"));
        assertEquals(DisplayMode.ALL, loaded.getDisplayMode());
        assertTrue(loaded.isEnabled());
    }

    @Test
    void loadingCorruptJsonYieldsDefaults(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, "{ this is not json");
        assertEquals(DisplayMode.ALL, JustTiersConfig.load(file).getDisplayMode());
    }

    @Test
    void unknownGamemodeSlugsFallBackToTheSiteDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("stale.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"MCTIERS_ONLY",
                 "selectedGamemodes":{"MCTIERS":"mode_that_no_longer_exists"},
                 "novaRefreshMinutes":30}
                """);
        assertEquals("vanilla", JustTiersConfig.load(file).selectedGamemode(Source.MCTIERS));
    }

    @Test
    void refreshIntervalIsClampedToASaneRange() {
        JustTiersConfig config = new JustTiersConfig();
        config.setNovaRefreshMinutes(0);
        assertEquals(5, config.getNovaRefreshMinutes());
        config.setNovaRefreshMinutes(100_000);
        assertEquals(1440, config.getNovaRefreshMinutes());
    }

    @Test
    void loadClampsAnOutOfRangeNovaRefreshMinutes(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("unclamped.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"ALL",
                 "selectedGamemodes":{},
                 "novaRefreshMinutes":999999}
                """);
        assertEquals(1440, JustTiersConfig.load(file).getNovaRefreshMinutes());
    }

    @Test
    void saveWritesDisplayModeAsLowerCaseId(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("lowercase.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setDisplayMode(DisplayMode.MCTIERS_ONLY);
        config.save(file);
        String written = Files.readString(file);
        assertTrue(written.contains("\"mctiers_only\""));
        assertFalse(written.contains("\"MCTIERS_ONLY\""));
    }

    @Test
    void loadingLegacyUppercaseDisplayModeStillResolves(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"MCTIERS_ONLY",
                 "selectedGamemodes":{},
                 "novaRefreshMinutes":30}
                """);
        assertEquals(DisplayMode.MCTIERS_ONLY, JustTiersConfig.load(file).getDisplayMode());
    }

    @Test
    void loadingAnUnrecognisedDisplayModeFallsBackToAll(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("unrecognised.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"not_a_real_mode",
                 "selectedGamemodes":{},
                 "novaRefreshMinutes":30}
                """);
        assertEquals(DisplayMode.ALL, JustTiersConfig.load(file).getDisplayMode());
    }

    @Test
    void showRetiredDefaultsToTrueAndRoundTrips(@TempDir Path dir) throws Exception {
        assertTrue(new JustTiersConfig().isShowRetired());

        Path file = dir.resolve("retired.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setShowRetired(false);
        config.save(file);
        assertTrue(Files.readString(file).contains("\"showRetired\": false"));
        assertFalse(JustTiersConfig.load(file).isShowRetired());
    }

    @Test
    void configsWrittenBeforeShowRetiredExistedStillShowRetiredTiers(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("older.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"all",
                 "selectedGamemodes":{},
                 "novaRefreshMinutes":30}
                """);
        assertTrue(JustTiersConfig.load(file).isShowRetired());
    }

    @Test
    void downloadProgressIsShownByDefault() {
        assertTrue(new JustTiersConfig().isShowDownloadProgress());
    }

    @Test
    void downloadProgressRoundTripsThroughDisk(@TempDir Path dir) {
        Path file = dir.resolve("justtiers.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setShowDownloadProgress(false);
        config.save(file);

        assertFalse(JustTiersConfig.load(file).isShowDownloadProgress());
    }

    @Test
    void aConfigWrittenBeforeTheSettingExistedStillShowsProgress(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("justtiers.json");
        Files.writeString(file, "{\"enabled\":true,\"displayMode\":\"all\"}");

        assertTrue(JustTiersConfig.load(file).isShowDownloadProgress());
    }

    // --- appearance ---

    @Test
    void theBadgeDefaultsToTheShapeJustTiersHasAlwaysDrawn() {
        JustTiersConfig config = new JustTiersConfig();
        assertEquals(BadgePosition.BEFORE, config.getBadgePosition());
        assertTrue(config.isShowIcons());
        assertTrue(config.isShowBrackets());
        assertEquals(NametagStyle.DEFAULT, config.nametagStyle());
    }

    @Test
    void appearanceRoundTripsThroughDisk(@TempDir Path dir) {
        Path file = dir.resolve("appearance.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setBadgePosition(BadgePosition.AFTER);
        config.setShowIcons(false);
        config.setShowBrackets(false);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertEquals(BadgePosition.AFTER, loaded.getBadgePosition());
        assertFalse(loaded.isShowIcons());
        assertFalse(loaded.isShowBrackets());
        assertEquals(new NametagStyle(BadgePosition.AFTER, false, false),
                loaded.nametagStyle());
    }

    @Test
    void saveWritesBadgePositionAsLowerCaseId(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("position.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setBadgePosition(BadgePosition.AFTER);
        config.save(file);

        String written = Files.readString(file);
        assertTrue(written.contains("\"after\""));
        assertFalse(written.contains("\"AFTER\""));
    }

    @Test
    void anUnrecognisedBadgePositionFallsBackToBefore(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("nonsense.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"all","badgePosition":"sideways"}
                """);
        assertEquals(BadgePosition.BEFORE, JustTiersConfig.load(file).getBadgePosition());
    }

    @Test
    void aConfigWrittenBeforeTheAppearanceSettingsExistedKeepsTheOldLook(@TempDir Path dir)
            throws Exception {
        Path file = dir.resolve("older.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"all",
                 "selectedGamemodes":{},
                 "novaRefreshMinutes":30}
                """);
        assertEquals(NametagStyle.DEFAULT, JustTiersConfig.load(file).nametagStyle());
    }

    @Test
    void aFileWithoutTheColorKeysBehavesAsBefore(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("old.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"all","selectedGamemodes":{}}
                """);
        JustTiersConfig config = JustTiersConfig.load(file);

        assertFalse(config.isHideOwnBadge());
        assertEquals(Palette.DEFAULT, config.getPalette());
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), config.colorOf(source));
        }
    }

    @Test
    void aPresetPaletteColorsEverySite(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("preset.json");
        Files.writeString(file, """
                {"palette":"colorblind","selectedGamemodes":{}}
                """);
        JustTiersConfig config = JustTiersConfig.load(file);

        assertEquals(0xE69F00, config.colorOf(Source.MCTIERS));
        assertEquals(0x56B4E9, config.colorOf(Source.SUBTIERS));
        assertEquals(0xFFFFFF, config.colorOf(Source.NOVATIERS));
    }

    @Test
    void anUnrecognisedPaletteFallsBackToDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("rainbow.json");
        Files.writeString(file, """
                {"palette":"rainbow","selectedGamemodes":{}}
                """);
        JustTiersConfig config = JustTiersConfig.load(file);

        assertEquals(Palette.DEFAULT, config.getPalette());
        assertEquals(Source.MCTIERS.defaultColor(), config.colorOf(Source.MCTIERS));
    }

    @Test
    void aPaletteIsReadCaseInsensitively(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy-palette.json");
        Files.writeString(file, """
                {"palette":"HIGH_CONTRAST","selectedGamemodes":{}}
                """);
        assertEquals(Palette.HIGH_CONTRAST, JustTiersConfig.load(file).getPalette());
    }

    @Test
    void customColorsAreUsedOnlyByTheCustomPalette(@TempDir Path dir) throws Exception {
        Path custom = dir.resolve("custom.json");
        Files.writeString(custom, """
                {"palette":"custom","customColors":{"MCTIERS":"#123456"},
                 "selectedGamemodes":{}}
                """);
        assertEquals(0x123456, JustTiersConfig.load(custom).colorOf(Source.MCTIERS));

        Path preset = dir.resolve("preset-with-custom.json");
        Files.writeString(preset, """
                {"palette":"default","customColors":{"MCTIERS":"#123456"},
                 "selectedGamemodes":{}}
                """);
        assertEquals(Source.MCTIERS.defaultColor(),
                JustTiersConfig.load(preset).colorOf(Source.MCTIERS));
    }

    @Test
    void aMalformedCustomColorFallsBackForThatSiteAlone(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("typo.json");
        Files.writeString(file, """
                {"palette":"custom",
                 "customColors":{"MCTIERS":"#123456","SUBTIERS":"nonsense"},
                 "selectedGamemodes":{}}
                """);
        JustTiersConfig config = JustTiersConfig.load(file);

        assertEquals(0x123456, config.colorOf(Source.MCTIERS));
        assertEquals(Source.SUBTIERS.defaultColor(), config.colorOf(Source.SUBTIERS));
        assertEquals(Source.NOVATIERS.defaultColor(), config.colorOf(Source.NOVATIERS));
    }

    @Test
    void bothNewKeysSurviveSaveAndLoad(@TempDir Path dir) {
        Path file = dir.resolve("roundtrip.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setHideOwnBadge(true);
        config.setPalette(Palette.CUSTOM);
        config.setCustomColor(Source.NOVATIERS, 0xABCDEF);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertTrue(loaded.isHideOwnBadge());
        assertEquals(Palette.CUSTOM, loaded.getPalette());
        assertEquals(0xABCDEF, loaded.colorOf(Source.NOVATIERS));
    }

    @Test
    void switchingToAPresetKeepsTheCustomColors(@TempDir Path dir) {
        Path file = dir.resolve("kept.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setPalette(Palette.CUSTOM);
        config.setCustomColor(Source.MCTIERS, 0x123456);
        config.setPalette(Palette.DEFAULT);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertEquals(Source.MCTIERS.defaultColor(), loaded.colorOf(Source.MCTIERS));
        assertEquals(0x123456, loaded.getCustomColor(Source.MCTIERS));
    }

    @Test
    void colorsAnswersEverySiteAtOnce() {
        JustTiersConfig config = new JustTiersConfig();
        config.setPalette(Palette.COLORBLIND);

        assertEquals(Source.ALL.size(), config.colors().size());
        for (Source source : Source.ALL) {
            assertEquals(config.colorOf(source), config.colors().get(source));
        }
    }

    @Test
    void savingWritesThePaletteAsItsLowerCaseId(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("palette-case.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setPalette(Palette.HIGH_CONTRAST);
        config.save(file);

        String written = Files.readString(file);
        assertTrue(written.contains("\"high_contrast\""));
        assertFalse(written.contains("\"HIGH_CONTRAST\""));
    }

    @Test
    void tierCacheMinutesDefaultsToAnHourAndRoundTrips(@TempDir Path dir) {
        assertEquals(60, new JustTiersConfig().getTierCacheMinutes());

        Path file = dir.resolve("ttl.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setTierCacheMinutes(120);
        config.save(file);

        assertEquals(120, JustTiersConfig.load(file).getTierCacheMinutes());
    }

    @Test
    void loadClampsAnOutOfRangeTierCacheMinutes(@TempDir Path dir) throws Exception {
        Path low = dir.resolve("low.json");
        Files.writeString(low, """
                {"tierCacheMinutes":0,"selectedGamemodes":{}}
                """);
        assertEquals(5, JustTiersConfig.load(low).getTierCacheMinutes());

        Path high = dir.resolve("high.json");
        Files.writeString(high, """
                {"tierCacheMinutes":999999,"selectedGamemodes":{}}
                """);
        assertEquals(1440, JustTiersConfig.load(high).getTierCacheMinutes());
    }

    @Test
    void aFileWithoutTierCacheMinutesGetsTheDefault(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("older.json");
        Files.writeString(file, """
                {"enabled":true,"displayMode":"all","selectedGamemodes":{}}
                """);
        assertEquals(60, JustTiersConfig.load(file).getTierCacheMinutes());
    }
}
