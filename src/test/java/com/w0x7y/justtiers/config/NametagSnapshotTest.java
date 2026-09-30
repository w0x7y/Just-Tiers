package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.render.model.BadgePosition;
import com.w0x7y.justtiers.render.model.NametagSettings;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class NametagSnapshotTest {
    @Test
    void unchangedReadsReuseTheImmutableSnapshot() {
        JustTiersConfig config = new JustTiersConfig();
        assertSame(config.nametagSettings(), config.nametagSettings());
    }

    @Test
    void everyBadgeMutationPublishesFreshSettingsWithoutChangingPreviousSnapshots() {
        List<Consumer<JustTiersConfig>> changes = List.of(
                config -> config.setEnabled(false),
                config -> config.setShowRetired(false),
                config -> config.setDisplayMode(DisplayMode.SUBTIERS_ONLY),
                config -> config.setSelectedGamemode(Source.MCTIERS, "sword"),
                config -> config.setBadgePosition(BadgePosition.AFTER),
                config -> config.setShowIcons(false),
                config -> config.setShowBrackets(false),
                config -> config.setPalette(Palette.HIGH_CONTRAST),
                config -> config.setCustomColor(Source.MCTIERS, 0x123456));
        for (Consumer<JustTiersConfig> change : changes) {
            JustTiersConfig config = new JustTiersConfig();
            config.setPalette(Palette.CUSTOM);
            NametagSettings previous = config.nametagSettings();
            JustTiersConfig expected = config.copy();
            change.accept(expected);
            change.accept(config);
            assertEquals(expected.nametagSettings(), config.nametagSettings());
            assertNotEquals(previous, config.nametagSettings());
            assertEquals(new JustTiersConfig().nametagSettings(), previous);
        }
    }

    @Test
    void draftAndSavedSettingsNeverCarryAnotherConfigsSnapshot(@TempDir Path directory) throws Exception {
        JustTiersConfig config = new JustTiersConfig();
        NametagSettings original = config.nametagSettings();
        JustTiersConfig draft = config.copy();
        draft.setShowIcons(false);
        assertFalse(draft.nametagSettings().style().icons());
        assertSame(original, config.nametagSettings());
        Path file = directory.resolve("settings.json");
        draft.save(file);
        assertFalse(Files.readString(file).contains("resolvedNametagSettings"));
        assertEquals(draft.nametagSettings(), JustTiersConfig.load(file).nametagSettings());
    }
}
