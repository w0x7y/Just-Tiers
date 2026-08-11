package com.w0x7y.justtiers.config;

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
}
