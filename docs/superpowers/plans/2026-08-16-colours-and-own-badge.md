# Per-Site Colours and Hiding Your Own Badge — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two appearance settings — `hideOwnBadge`, and a per-site colour scheme
chosen from three presets or set by hand.

**Architecture:** `JustTiersConfig` owns resolving a site's colour, because the colours
are configuration and the config class is already Minecraft-free and unit-tested. Screens
read it through a one-line `SiteColors` facade; `NametagModel`, which must stay
Minecraft-free, receives the colours in the `NametagStyle` it is already handed.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API
`0.157.0+26.2`, YetAnotherConfigLib 3.9.4, JUnit 5. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-16-colours-and-own-badge-design.md`

## Global Constraints

- A site's colour changes everywhere at once — nametag, lookup screen, scan screen,
  gamemode grid, config previews. No per-screen override.
- `config`, `tier`, `render.model`, `preview`, `gui.state` must not import
  `net.minecraft.*`. `Palette` and `HexColor` join them.
- Old config files load unchanged: both keys optional, absent means today's behaviour.
  Bad values are corrected on load with a logged warning, never rejected.
- Colours are parsed once, on load and on save, into an `EnumMap<Source, Integer>`. The
  render path reads that map and never parses a string.
- Palette values, verbatim:
  - `default` — MCTiers `0xFFFF55`, SubTiers `0x55FFFF`, NovaTiers `0xAA55FF`
  - `colorblind` — MCTiers `0xE69F00`, SubTiers `0x56B4E9`, NovaTiers `0xFFFFFF`
  - `high_contrast` — MCTiers `0xFFFFFF`, SubTiers `0xFFAA00`, NovaTiers `0x00FFFF`
  - `custom` — from `customColors`, per-site fallback to the site default
- Every user-facing string is a translation key. Nothing hardcoded.
- Run `./gradlew test` before every commit.

## File Structure

| File | Responsibility |
|---|---|
| `config/HexColor.java` | Parse and format `#RRGGBB` |
| `config/Palette.java` | The four palettes and their colours |
| `config/JustTiersConfig.java` | *(modify)* the two keys, `colorOf`, `colors` |
| `tier/Source.java` | *(modify)* `color()` renamed `defaultColor()` |
| `render/model/NametagStyle.java` | *(modify)* carries the colours |
| `render/model/NametagModel.java` | *(modify)* colours segments from the style |
| `render/SiteColors.java` | One-line facade for screens |
| `mixin/PlayerMixin.java` | *(modify)* skip your own nametag |
| `command/JustTiersCommands.java` | *(modify)* `ownbadge`, `palette`, status line |
| `gui/state/ControlAvailability.java` | *(modify)* when the pickers are live |
| `gui/JustTiersScreens.java` | *(modify)* the new Appearance controls |

---

### Task 1: Hex parsing

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/config/HexColor.java`
- Test: `src/test/java/com/w0x7y/justtiers/config/HexColorTest.java`

**Interfaces:**
- Produces: `HexColor.parse(String) -> OptionalInt`, `HexColor.format(int) -> String`.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.config;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HexColorTest {

    @Test
    void parsesBothSpellings() {
        assertEquals(OptionalInt.of(0xFFFF55), HexColor.parse("#FFFF55"));
        assertEquals(OptionalInt.of(0xFFFF55), HexColor.parse("FFFF55"));
    }

    @Test
    void parsesCaseInsensitivelyAndIgnoresSurroundingSpace() {
        assertEquals(OptionalInt.of(0xAA55FF), HexColor.parse("#aa55ff"));
        assertEquals(OptionalInt.of(0xAA55FF), HexColor.parse("  #Aa55Ff  "));
    }

    @Test
    void rejectsAnythingThatIsNotSixHexDigits() {
        assertFalse(HexColor.parse(null).isPresent());
        assertFalse(HexColor.parse("").isPresent());
        assertFalse(HexColor.parse("#FFF").isPresent());
        assertFalse(HexColor.parse("#FFFF5").isPresent());
        assertFalse(HexColor.parse("#FFFF555").isPresent());
        assertFalse(HexColor.parse("#GGGGGG").isPresent());
        assertFalse(HexColor.parse("#FFFF55FF").isPresent(), "alpha is not accepted");
    }

    @Test
    void formatsBackToTheCanonicalSpelling() {
        assertEquals("#FFFF55", HexColor.format(0xFFFF55));
        assertEquals("#000000", HexColor.format(0x000000));
        assertEquals("#00FF00", HexColor.format(0x00FF00));
    }

    @Test
    void formatIgnoresAnythingAboveTheRgbTriple() {
        assertEquals("#FFFF55", HexColor.format(0xFF_FFFF55));
    }

    @Test
    void everyFormattedColourParsesBack() {
        for (int rgb : new int[] {0x000000, 0xFFFFFF, 0xE69F00, 0x56B4E9, 0xAA55FF}) {
            assertEquals(OptionalInt.of(rgb), HexColor.parse(HexColor.format(rgb)));
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*HexColorTest'`
Expected: FAIL — `HexColor` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.w0x7y.justtiers.config;

import java.util.Locale;
import java.util.OptionalInt;

/**
 * The on-disk spelling of a colour: {@code #RRGGBB}, with or without the hash, in either
 * case. Alpha is deliberately not accepted — every consumer supplies its own, and a
 * four-byte value read as three would be wrong in a way nobody could see coming.
 *
 * <p>Empty rather than an exception for anything unparseable: a hand-edited config is
 * corrected on load, not rejected.
 */
public final class HexColor {

    private static final int DIGITS = 6;
    private static final int RGB_MASK = 0xFFFFFF;

    public static OptionalInt parse(String raw) {
        if (raw == null) {
            return OptionalInt.empty();
        }
        String text = raw.trim();
        if (text.startsWith("#")) {
            text = text.substring(1);
        }
        if (text.length() != DIGITS) {
            return OptionalInt.empty();
        }
        for (int i = 0; i < DIGITS; i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return OptionalInt.empty();
            }
        }
        return OptionalInt.of(Integer.parseInt(text, 16));
    }

    /** The canonical spelling: hashed, upper case, six digits, no alpha. */
    public static String format(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb & RGB_MASK);
    }

    private HexColor() {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*HexColorTest'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/config/HexColor.java \
        src/test/java/com/w0x7y/justtiers/config/HexColorTest.java
git commit -m "Parse and format colours as #RRGGBB"
```

---

### Task 2: Rename `Source.color()` to `defaultColor()`

Mechanical, and done before anything reads a configured colour so that no call site is
written against the name that is about to change.

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/tier/Source.java`
- Modify: every caller — `render/Segments.java`, `render/model/NametagModel.java`,
  `gui/JustTiersScreens.java`, `gui/GamemodePickerController.java`,
  `gui/GamemodeGridScreen.java`, `gui/NametagPreviewController.java`,
  `gui/PlayerLookupScreen.java`, `gui/ScanScreen.java`
- Modify: `src/test/java/com/w0x7y/justtiers/tier/SourceTest.java` and any other test
  naming `color()`

**Interfaces:**
- Produces: `Source.defaultColor() -> int`. `Source.color()` no longer exists.

- [ ] **Step 1: Rename the accessor**

In `Source.java`, rename the method (leave the private field named `color`):

```java
    /**
     * The colour this site is drawn in when nothing else is configured. Read this only
     * as a fallback — {@link com.w0x7y.justtiers.config.JustTiersConfig#colorOf} is what
     * the user actually chose, and reaching past it is how a screen ends up ignoring
     * their palette.
     */
    public int defaultColor() {
        return color;
    }
```

- [ ] **Step 2: Update every caller**

```bash
grep -rln 'color()' src/main src/test | xargs sed -i 's/\.color()/.defaultColor()/g'
```

Then check the result: `Colors.opaque(...)`'s javadoc mentions `Source.color()` and should
be reworded to `Source.defaultColor()`; nothing else should have changed meaning. Verify
no unrelated `.color()` was caught:

```bash
grep -rn 'defaultColor()' src/main src/test
```

- [ ] **Step 3: Build and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass. This is a rename with no behaviour change, so
a green suite is the whole verification.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Rename Source.color to defaultColor"
```

---

### Task 3: The palettes

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/config/Palette.java`
- Test: `src/test/java/com/w0x7y/justtiers/config/PaletteTest.java`

**Interfaces:**
- Consumes: `Source.defaultColor()`, `HexColor.parse`.
- Produces: `Palette.DEFAULT / COLORBLIND / HIGH_CONTRAST / CUSTOM`,
  `Palette.id() -> String`, `Palette.displayKey() -> String`,
  `Palette.colorOf(Source, Map<String, String> customColors) -> int`,
  `Palette.isCustom() -> boolean`.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaletteTest {

    private static Map<String, String> custom(String mctiers, String subtiers, String nova) {
        Map<String, String> colors = new HashMap<>();
        colors.put(Source.MCTIERS.name(), mctiers);
        colors.put(Source.SUBTIERS.name(), subtiers);
        colors.put(Source.NOVATIERS.name(), nova);
        return colors;
    }

    @Test
    void theDefaultPaletteIsWhatTheSitesAlreadyUse() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.DEFAULT.colorOf(source, Map.of()));
        }
    }

    @Test
    void presetsCarryTheirDocumentedColours() {
        assertEquals(0xE69F00, Palette.COLORBLIND.colorOf(Source.MCTIERS, Map.of()));
        assertEquals(0x56B4E9, Palette.COLORBLIND.colorOf(Source.SUBTIERS, Map.of()));
        assertEquals(0xFFFFFF, Palette.COLORBLIND.colorOf(Source.NOVATIERS, Map.of()));

        assertEquals(0xFFFFFF, Palette.HIGH_CONTRAST.colorOf(Source.MCTIERS, Map.of()));
        assertEquals(0xFFAA00, Palette.HIGH_CONTRAST.colorOf(Source.SUBTIERS, Map.of()));
        assertEquals(0x00FFFF, Palette.HIGH_CONTRAST.colorOf(Source.NOVATIERS, Map.of()));
    }

    @Test
    void everyPresetTellsTheThreeSitesApart() {
        for (Palette palette : Palette.values()) {
            if (palette.isCustom()) {
                continue;
            }
            Set<Integer> colors = new HashSet<>();
            for (Source source : Source.ALL) {
                colors.add(palette.colorOf(source, Map.of()));
            }
            assertEquals(Source.ALL.size(), colors.size(), palette.id());
        }
    }

    @Test
    void aPresetIgnoresTheCustomColours() {
        Map<String, String> colors = custom("#111111", "#222222", "#333333");
        assertEquals(0xFFFF55, Palette.DEFAULT.colorOf(Source.MCTIERS, colors));
        assertEquals(0xE69F00, Palette.COLORBLIND.colorOf(Source.MCTIERS, colors));
    }

    @Test
    void customUsesTheSuppliedColours() {
        Map<String, String> colors = custom("#111111", "#222222", "#333333");
        assertEquals(0x111111, Palette.CUSTOM.colorOf(Source.MCTIERS, colors));
        assertEquals(0x222222, Palette.CUSTOM.colorOf(Source.SUBTIERS, colors));
        assertEquals(0x333333, Palette.CUSTOM.colorOf(Source.NOVATIERS, colors));
    }

    @Test
    void aBadCustomColourCostsOnlyItsOwnSite() {
        Map<String, String> colors = custom("#111111", "not a colour", null);
        assertEquals(0x111111, Palette.CUSTOM.colorOf(Source.MCTIERS, colors));
        assertEquals(Source.SUBTIERS.defaultColor(),
                Palette.CUSTOM.colorOf(Source.SUBTIERS, colors));
        assertEquals(Source.NOVATIERS.defaultColor(),
                Palette.CUSTOM.colorOf(Source.NOVATIERS, colors));
    }

    @Test
    void customWithNothingStoredIsTheDefaultPalette() {
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), Palette.CUSTOM.colorOf(source, Map.of()));
            assertEquals(source.defaultColor(), Palette.CUSTOM.colorOf(source, null));
        }
    }

    @Test
    void idsAreStableAndUnique() {
        assertEquals("default", Palette.DEFAULT.id());
        assertEquals("colorblind", Palette.COLORBLIND.id());
        assertEquals("high_contrast", Palette.HIGH_CONTRAST.id());
        assertEquals("custom", Palette.CUSTOM.id());

        Set<String> ids = new HashSet<>();
        for (Palette palette : Palette.values()) {
            assertTrue(ids.add(palette.id()), palette.name());
        }
    }

    @Test
    void onlyCustomIsCustom() {
        assertTrue(Palette.CUSTOM.isCustom());
        assertFalse(Palette.DEFAULT.isCustom());
        assertFalse(Palette.COLORBLIND.isCustom());
        assertFalse(Palette.HIGH_CONTRAST.isCustom());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*PaletteTest'`
Expected: FAIL — `Palette` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.w0x7y.justtiers.config;

import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The colour scheme telling the three leaderboards apart. Colour carries exactly one
 * meaning in this UI — which site something came from — so a palette answers for all
 * three sites or it is not a palette.
 *
 * <p>There is one colourblind preset rather than one per condition. Its colours separate
 * by luminance as well as by hue, so the same three work for protanopia, deuteranopia and
 * tritanopia; a second preset differing only slightly would be a worse answer than one
 * that works for everybody.
 */
public enum Palette {

    DEFAULT("default", 0xFFFF55, 0x55FFFF, 0xAA55FF),
    COLORBLIND("colorblind", 0xE69F00, 0x56B4E9, 0xFFFFFF),
    HIGH_CONTRAST("high_contrast", 0xFFFFFF, 0xFFAA00, 0x00FFFF),
    /** Whatever the user picked; colours come from the config rather than from here. */
    CUSTOM("custom");

    private final String id;
    private final Map<Source, Integer> colors;

    Palette(String id) {
        this.id = id;
        this.colors = Map.of();
    }

    Palette(String id, int mctiers, int subtiers, int novatiers) {
        this.id = id;
        Map<Source, Integer> bySource = new EnumMap<>(Source.class);
        bySource.put(Source.MCTIERS, mctiers);
        bySource.put(Source.SUBTIERS, subtiers);
        bySource.put(Source.NOVATIERS, novatiers);
        this.colors = Map.copyOf(bySource);
    }

    /** The on-disk and command-argument spelling. */
    public String id() {
        return id;
    }

    /** The translation key for this palette's name on the config screen. */
    public String displayKey() {
        return "justtiers.palette." + id;
    }

    public boolean isCustom() {
        return this == CUSTOM;
    }

    /**
     * This palette's colour for a site. {@code customColors} is consulted only by
     * {@link #CUSTOM}, and a missing or unparseable entry falls back to that site's own
     * default — per site, so one typo costs one colour rather than three.
     */
    public int colorOf(Source source, Map<String, String> customColors) {
        if (!isCustom()) {
            return colors.getOrDefault(source, source.defaultColor());
        }
        if (customColors == null) {
            return source.defaultColor();
        }
        OptionalInt parsed = HexColor.parse(customColors.get(source.name()));
        return parsed.orElseGet(source::defaultColor);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*PaletteTest'`
Expected: PASS, 9 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/config/Palette.java \
        src/test/java/com/w0x7y/justtiers/config/PaletteTest.java
git commit -m "Add the four site colour palettes"
```

---

### Task 4: The config keys

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java`
- Test: `src/test/java/com/w0x7y/justtiers/config/JustTiersConfigTest.java`

**Interfaces:**
- Produces: `isHideOwnBadge() / setHideOwnBadge(boolean)`,
  `getPalette() / setPalette(Palette)`,
  `getCustomColor(Source) -> int`, `setCustomColor(Source, int)`,
  `colorOf(Source) -> int`, `colors() -> Map<Source, Integer>`.

- [ ] **Step 1: Write the failing test**

Append to `JustTiersConfigTest`. It already has helpers for writing a config file and
loading it; follow whatever those are named in the file — the tests below assume a
`write(String json)` returning a `Path` and `JustTiersConfig.load(Path)`, and must be
adapted to the existing helpers rather than duplicating them.

```java
    @Test
    void aFileWithoutTheColourKeysBehavesAsBefore() throws Exception {
        JustTiersConfig config = JustTiersConfig.load(write("""
                { "enabled": true, "displayMode": "all" }
                """));

        assertFalse(config.isHideOwnBadge());
        assertEquals(Palette.DEFAULT, config.getPalette());
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), config.colorOf(source));
        }
    }

    @Test
    void aPresetPaletteColoursEverySite() throws Exception {
        JustTiersConfig config = JustTiersConfig.load(write("""
                { "palette": "colorblind" }
                """));

        assertEquals(0xE69F00, config.colorOf(Source.MCTIERS));
        assertEquals(0x56B4E9, config.colorOf(Source.SUBTIERS));
        assertEquals(0xFFFFFF, config.colorOf(Source.NOVATIERS));
    }

    @Test
    void anUnrecognisedPaletteFallsBackToDefault() throws Exception {
        JustTiersConfig config = JustTiersConfig.load(write("""
                { "palette": "rainbow" }
                """));

        assertEquals(Palette.DEFAULT, config.getPalette());
        assertEquals(Source.MCTIERS.defaultColor(), config.colorOf(Source.MCTIERS));
    }

    @Test
    void aPaletteIsReadCaseInsensitively() throws Exception {
        assertEquals(Palette.HIGH_CONTRAST,
                JustTiersConfig.load(write("""
                        { "palette": "HIGH_CONTRAST" }
                        """)).getPalette());
    }

    @Test
    void customColoursAreUsedOnlyByTheCustomPalette() throws Exception {
        String json = """
                { "palette": "%s", "customColors": { "MCTIERS": "#123456" } }
                """;

        assertEquals(0x123456,
                JustTiersConfig.load(write(json.formatted("custom"))).colorOf(Source.MCTIERS));
        assertEquals(Source.MCTIERS.defaultColor(),
                JustTiersConfig.load(write(json.formatted("default"))).colorOf(Source.MCTIERS));
    }

    @Test
    void aMalformedCustomColourFallsBackForThatSiteAlone() throws Exception {
        JustTiersConfig config = JustTiersConfig.load(write("""
                { "palette": "custom",
                  "customColors": { "MCTIERS": "#123456", "SUBTIERS": "nonsense" } }
                """));

        assertEquals(0x123456, config.colorOf(Source.MCTIERS));
        assertEquals(Source.SUBTIERS.defaultColor(), config.colorOf(Source.SUBTIERS));
        assertEquals(Source.NOVATIERS.defaultColor(), config.colorOf(Source.NOVATIERS));
    }

    @Test
    void bothNewKeysSurviveSaveAndLoad() throws Exception {
        Path file = write("{}");
        JustTiersConfig config = JustTiersConfig.load(file);
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
    void switchingToAPresetKeepsTheCustomColours() throws Exception {
        Path file = write("{}");
        JustTiersConfig config = JustTiersConfig.load(file);
        config.setPalette(Palette.CUSTOM);
        config.setCustomColor(Source.MCTIERS, 0x123456);
        config.setPalette(Palette.DEFAULT);
        config.save(file);

        JustTiersConfig loaded = JustTiersConfig.load(file);
        assertEquals(Source.MCTIERS.defaultColor(), loaded.colorOf(Source.MCTIERS));
        assertEquals(0x123456, loaded.getCustomColor(Source.MCTIERS));
    }

    @Test
    void colorsAnswersEverySiteAtOnce() throws Exception {
        JustTiersConfig config = JustTiersConfig.load(write("""
                { "palette": "colorblind" }
                """));

        assertEquals(Source.ALL.size(), config.colors().size());
        for (Source source : Source.ALL) {
            assertEquals(config.colorOf(source), config.colors().get(source));
        }
    }
```

`config.save(file)` must match whatever the class actually exposes — check the existing
save tests in this file and use the same call.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*JustTiersConfigTest'`
Expected: FAIL — the new accessors do not exist.

- [ ] **Step 3: Write minimal implementation**

Register the adapter alongside the existing two, in the `GSON` builder:

```java
            .registerTypeAdapter(Palette.class, new IdEnumAdapter<>(
                    "palette", Palette.class, Palette.DEFAULT, Palette::id))
```

Add the fields beside `showBrackets`:

```java
    private boolean hideOwnBadge = false;
    private Palette palette = Palette.DEFAULT;
    private Map<String, String> customColors = new HashMap<>();
```

Add a transient cache beside `resolvedSelection`, following exactly the pattern already
there:

```java
    /**
     * Cache for {@link #colors()}, dropped whenever the palette or a custom colour
     * changes. Transient so it never reaches the config file, and volatile because the
     * render thread reads it.
     */
    private transient volatile Map<Source, Integer> resolvedColors;
```

Add the accessors:

```java
    public boolean isHideOwnBadge() {
        return hideOwnBadge;
    }

    public void setHideOwnBadge(boolean hideOwnBadge) {
        this.hideOwnBadge = hideOwnBadge;
    }

    public Palette getPalette() {
        return palette == null ? Palette.DEFAULT : palette;
    }

    public void setPalette(Palette palette) {
        this.palette = palette == null ? Palette.DEFAULT : palette;
        this.resolvedColors = null;
    }

    /**
     * The stored custom colour for a site, whether or not the custom palette is in use.
     * Selecting a preset does not discard these, so switching to Custom and back is not
     * a way to lose them.
     */
    public int getCustomColor(Source source) {
        return Palette.CUSTOM.colorOf(source, customColors);
    }

    public void setCustomColor(Source source, int rgb) {
        if (customColors == null) {
            customColors = new HashMap<>();
        }
        customColors.put(source.name(), HexColor.format(rgb));
        this.resolvedColors = null;
    }

    /** What colour this site is drawn in, under the palette in force. */
    public int colorOf(Source source) {
        return colors().getOrDefault(source, source.defaultColor());
    }

    /**
     * Every site's colour at once. Resolved on first use and cached: this is read per
     * player per frame, and parsing three hex strings there would be three allocations a
     * frame for an answer that only changes when the config does.
     */
    public Map<Source, Integer> colors() {
        Map<Source, Integer> cached = resolvedColors;
        if (cached != null) {
            return cached;
        }
        Map<Source, Integer> resolved = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            resolved.put(source, getPalette().colorOf(source, customColors));
        }
        Map<Source, Integer> copy = Map.copyOf(resolved);
        resolvedColors = copy;
        return copy;
    }
```

Find where the class already normalises values after loading — the same place
`novaRefreshMinutes` is clamped and `selectedGamemodes` is validated — and drop the colour
cache there too, so a freshly loaded config does not serve a cache built before the file
was read:

```java
        this.resolvedColors = null;
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*JustTiersConfigTest'`
Expected: PASS, including the pre-existing tests unchanged.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java \
        src/test/java/com/w0x7y/justtiers/config/JustTiersConfigTest.java
git commit -m "Store the palette, the custom colours and hideOwnBadge"
```

---

### Task 5: Colours through the nametag style

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/render/model/NametagStyle.java`
- Modify: `src/main/java/com/w0x7y/justtiers/render/model/NametagModel.java`
- Modify: `src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java` (`nametagStyle`)
- Test: `src/test/java/com/w0x7y/justtiers/render/model/NametagModelTest.java`

**Interfaces:**
- Produces:
  - `NametagStyle(BadgePosition, boolean icons, boolean brackets, Map<Source,Integer> colors)`
  - `NametagStyle(BadgePosition, boolean, boolean)` — unchanged three-argument form,
    defaulting to the sites' own colours
  - `NametagStyle.colors() -> Map<Source, Integer>`
  - `NametagModel.entries(List<ResolvedTier>, boolean icons, Map<Source,Integer> colors)`

The three-argument constructor is kept deliberately: `NametagStyle` is built in around
fifteen places across the tests, and none of them are about colour.

- [ ] **Step 1: Write the failing test**

Append to `NametagModelTest`:

```java
    @Test
    void segmentsTakeTheirColourFromTheStyle() {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            colors.put(source, 0x123456);
        }
        NametagStyle style = new NametagStyle(BadgePosition.BEFORE, false, false, colors);

        List<Segment> segments = NametagModel.build(PAIR, style);

        assertTrue(segments.stream().allMatch(segment -> segment.color() == 0x123456),
                segments.toString());
    }

    @Test
    void theThreeArgumentStyleStillUsesTheSitesOwnColours() {
        NametagStyle style = new NametagStyle(BadgePosition.BEFORE, false, false);
        for (Source source : Source.ALL) {
            assertEquals(source.defaultColor(), style.colors().get(source));
        }
    }

    @Test
    void entriesColourEachTierByItsOwnSite() {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        colors.put(Source.MCTIERS, 0xAA0000);
        colors.put(Source.SUBTIERS, 0x00AA00);
        colors.put(Source.NOVATIERS, 0x0000AA);

        List<Segment> segments = NametagModel.entries(PAIR, false, colors);

        assertTrue(segments.stream().anyMatch(segment -> segment.color() == 0xAA0000));
    }
```

`PAIR` is the existing fixture in this file. Add the imports it needs
(`com.w0x7y.justtiers.tier.Source`, `java.util.EnumMap`, `java.util.Map`) and check what
sources `PAIR` actually holds — if it is not MCTiers-based, adjust the last assertion to
whichever site it does use.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*NametagModelTest'`
Expected: FAIL — the four-argument constructor and `colors()` do not exist.

- [ ] **Step 3: Write minimal implementation**

`NametagStyle`:

```java
package com.w0x7y.justtiers.render.model;

import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;

/**
 * The purely cosmetic half of the nametag: where the badge sits, how much chrome it
 * carries, and what colour each site is drawn in. None of it changes <em>which</em> tiers
 * are shown — that is {@link com.w0x7y.justtiers.resolve.DisplayMode}'s job — so the same
 * resolved tiers can be drawn in any of these shapes.
 *
 * <p>The colours travel in the style rather than being looked up where they are drawn,
 * which is what keeps {@link NametagModel} free of both Minecraft and the config.
 *
 * <p>With icons off, the sites are told apart by tier colour alone, which is the legend
 * the config screen already teaches on its display-mode row.
 */
public record NametagStyle(BadgePosition position, boolean icons, boolean brackets,
                           Map<Source, Integer> colors) {

    /** What Just-Tiers has always drawn: {@code [<icon>HT2] } in front of the name. */
    public static final NametagStyle DEFAULT = new NametagStyle(BadgePosition.BEFORE, true, true);

    public NametagStyle {
        // A null position can only come from a hand-edited config; before is the default
        // everywhere else, and a preview that refuses to draw would be worse.
        position = position == null ? BadgePosition.BEFORE : position;
        colors = colors == null || colors.isEmpty() ? defaultColors() : Map.copyOf(colors);
    }

    /** The shape alone, drawn in the sites' own colours. */
    public NametagStyle(BadgePosition position, boolean icons, boolean brackets) {
        this(position, icons, brackets, defaultColors());
    }

    public int colorOf(Source source) {
        return colors.getOrDefault(source, source.defaultColor());
    }

    private static Map<Source, Integer> defaultColors() {
        Map<Source, Integer> colors = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            colors.put(source, source.defaultColor());
        }
        return Map.copyOf(colors);
    }
}
```

`NametagModel` — `build` passes the style's colours down, and `entries` gains the
three-argument form while the two-argument one keeps working for callers with no opinion:

```java
    public static List<Segment> build(List<ResolvedTier> tiers, NametagStyle style) {
        List<Segment> entries = entries(tiers, style.icons(), style.colors());
        // ... rest unchanged
```

```java
    /** As {@link #entries(List, boolean, Map)}, in the sites' own colours. */
    public static List<Segment> entries(List<ResolvedTier> tiers, boolean icons) {
        return entries(tiers, icons, NametagStyle.DEFAULT.colors());
    }

    public static List<Segment> entries(List<ResolvedTier> tiers, boolean icons,
                                        Map<Source, Integer> colors) {
        if (tiers == null || tiers.isEmpty()) {
            return List.of();
        }

        List<Segment> segments = new ArrayList<>(tiers.size() * 3);
        for (int i = 0; i < tiers.size(); i++) {
            if (i > 0) {
                segments.add(new Segment(" ", BRACKET_COLOR));
            }
            ResolvedTier resolved = tiers.get(i);
            if (icons) {
                segments.add(new Segment(String.valueOf(resolved.gamemode().icon()), ICON_COLOR));
            }
            Source source = resolved.gamemode().source();
            segments.add(new Segment(resolved.tier().label(),
                    colors == null ? source.defaultColor()
                            : colors.getOrDefault(source, source.defaultColor())));
        }
        return List.copyOf(segments);
    }
```

Add `import com.w0x7y.justtiers.tier.Source;` and `import java.util.Map;` to
`NametagModel`.

`JustTiersConfig.nametagStyle()`:

```java
    public NametagStyle nametagStyle() {
        return new NametagStyle(getBadgePosition(), showIcons, showBrackets, colors());
    }
```

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test`
Expected: PASS. Every existing three-argument `NametagStyle` in the tests still compiles
and still asserts the same colours, which is the check that this was additive.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/render/model/NametagStyle.java \
        src/main/java/com/w0x7y/justtiers/render/model/NametagModel.java \
        src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java \
        src/test/java/com/w0x7y/justtiers/render/model/NametagModelTest.java
git commit -m "Carry the site colours in the nametag style"
```

---

### Task 6: The screens follow the palette

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/render/SiteColors.java`
- Modify: `gui/JustTiersScreens.java`, `gui/GamemodePickerController.java`,
  `gui/GamemodeGridScreen.java`, `gui/NametagPreviewController.java`,
  `gui/PlayerLookupScreen.java`, `gui/ScanScreen.java`, `gui/Colors.java` (javadoc only)

**Interfaces:**
- Produces: `SiteColors.of(Source) -> int`.

- [ ] **Step 1: Write the facade**

```java
package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.tier.Source;

/**
 * What colour a site is drawn in, right now, under whatever palette is configured.
 *
 * <p>Every screen goes through here rather than reading {@link Source#defaultColor()},
 * because colour carries exactly one meaning in this UI — which leaderboard this is — and
 * a screen that read the constant would keep saying it in a colour the user has changed.
 */
public final class SiteColors {

    public static int of(Source source) {
        return JustTiersClient.config().colorOf(source);
    }

    private SiteColors() {
    }
}
```

- [ ] **Step 2: Route every screen through it**

Replace `source.defaultColor()` with `SiteColors.of(source)` at each of these, adding the
import to each file. `NametagModel` and `NametagStyle` are **not** in this list — they get
their colours from the style, and reaching `JustTiersClient` from them would make them
untestable.

| File | What to change |
|---|---|
| `render/Segments.java` | the segment already carries its colour — **no change**; confirm it reads `segment.color()` and leave it |
| `gui/JustTiersScreens.java` | both `withColor(...)` calls |
| `gui/GamemodePickerController.java` | the `Colors.opaque(...)` call |
| `gui/GamemodeGridScreen.java` | both `Colors.opaque(source.defaultColor())` calls |
| `gui/NametagPreviewController.java` | `widget.dim(segment.color())` already reads the segment — **no change** |
| `gui/PlayerLookupScreen.java` | all four `Colors.opaque(...)` calls |
| `gui/ScanScreen.java` | both `Colors.opaque(...)` calls |

In `gui/Colors.java`, update the javadoc on `opaque` to say `SiteColors.of` rather than
`Source.color()`.

Afterwards, confirm nothing outside the fallback paths still reads the constant:

```bash
grep -rn 'defaultColor()' src/main
```

The only hits should be in `Palette`, `NametagStyle`, `NametagModel`, `JustTiersConfig`
and `SiteColors`'s javadoc.

- [ ] **Step 3: Build and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "Draw every screen in the configured site colours"
```

---

### Task 7: Hiding your own badge

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/mixin/PlayerMixin.java`

**Interfaces:**
- Consumes: `JustTiersClient.config().isHideOwnBadge()`.

- [ ] **Step 1: Write the mixin change**

```java
package com.w0x7y.justtiers.mixin;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.render.NametagRenderer;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Player.class)
public class PlayerMixin {

    @ModifyReturnValue(method = "getDisplayName", at = @At("RETURN"))
    private Component justtiers$prependTier(Component original) {
        Player self = (Player) (Object) this;
        if (!(self instanceof AbstractClientPlayer)) {
            return original;
        }
        // Your own tag, when you have asked not to see a badge on it. Checked here rather
        // than in the renderer because this is where the entity is in hand: the renderer
        // only ever sees a UUID.
        if (JustTiersClient.config().isHideOwnBadge() && self == Minecraft.getInstance().player) {
            return original;
        }
        return NametagRenderer.decorate(self.getUUID(), original);
    }
}
```

- [ ] **Step 2: Build and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass. There is no unit test here — the mixin needs a
running client, and everything it decides is a single boolean read. It is verified by hand
in Task 10.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/mixin/PlayerMixin.java
git commit -m "Let the badge be hidden on your own nametag"
```

---

### Task 8: Commands

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/command/JustTiersCommands.java`
- Modify: `src/main/resources/assets/justtiers/lang/en_us.json`

**Interfaces:**
- Consumes: `JustTiersConfig` accessors from Task 4, `Palette`.

- [ ] **Step 1: Add the strings**

```json
  "justtiers.command.ownbadge.on": "Your own badge is hidden",
  "justtiers.command.ownbadge.off": "Your own badge is shown",
  "justtiers.command.paletteSet": "Colour palette set to %s",
  "justtiers.command.status.palette": "Palette: %s",
  "justtiers.palette.default": "Default",
  "justtiers.palette.colorblind": "Colourblind-safe",
  "justtiers.palette.high_contrast": "High contrast",
  "justtiers.palette.custom": "Custom",
  "justtiers.config.hideOwnBadge": "Hide my own badge",
  "justtiers.config.hideOwnBadge.desc": "Leaves your own nametag undecorated. /justtiers lookup and /justtiers scan still show you.",
  "justtiers.config.palette": "Colour palette",
  "justtiers.config.palette.desc": "Which colours tell the three leaderboards apart.",
  "justtiers.config.customColor": "%s colour",
  "justtiers.config.customColor.desc": "Only used while the palette is Custom.",
  "justtiers.config.customColor.inactive": "Set the palette to Custom to change this."
```

- [ ] **Step 2: Register the toggle and the setter**

Alongside the existing `icons` and `brackets` toggles, which this copies exactly:

```java
                        .then(literal("ownbadge").executes(context -> toggle(context,
                                JustTiersConfig::isHideOwnBadge, JustTiersConfig::setHideOwnBadge,
                                "justtiers.command.ownbadge.on", "justtiers.command.ownbadge.off",
                                ChatFormatting.YELLOW)))
```

And alongside `mode` and `badge`, which this copies exactly:

```java
                        .then(literal("palette")
                                .then(argument("palette", StringArgumentType.word())
                                        .suggests(suggestIds(Palette.values(), Palette::id))
                                        .executes(context -> setEnum(context, "palette",
                                                Palette.values(), Palette::id,
                                                JustTiersConfig::setPalette,
                                                palette -> Component.translatable(
                                                        "justtiers.command.paletteSet",
                                                        Component.translatable(
                                                                palette.displayKey()))))))
```

Add `import com.w0x7y.justtiers.config.Palette;`.

Check the `toggle` helper's argument order against its existing callers before wiring
`ownbadge` — the on/off keys read the opposite way round here, since the flag being true
is the badge being *hidden*.

- [ ] **Step 3: Add the palette to the status output**

In the `status` method, beside the lines already printed for the mode and the gamemodes:

```java
        reply(context, ChatFormatting.GRAY, "justtiers.command.status.palette",
                Component.translatable(config.getPalette().displayKey()));
```

Match the actual signature of `reply` in this file — if it does not take arguments, use
whatever the neighbouring lines use to interpolate a value.

- [ ] **Step 4: Build and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/command/JustTiersCommands.java \
        src/main/resources/assets/justtiers/lang/en_us.json
git commit -m "Add /justtiers ownbadge and /justtiers palette"
```

---

### Task 9: The config screen

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/gui/state/ControlAvailability.java`
- Modify: `src/main/java/com/w0x7y/justtiers/gui/JustTiersScreens.java`
- Test: `src/test/java/com/w0x7y/justtiers/gui/state/ControlAvailabilityTest.java`

**Interfaces:**
- Produces: `ControlAvailability.customColors() -> boolean`, and
  `ControlAvailability.of(boolean enabled, DisplayMode mode, Palette palette)`.

- [ ] **Step 1: Write the failing test**

Append to `ControlAvailabilityTest`:

```java
    @Test
    void theColourPickersAreLiveOnlyForTheCustomPalette() {
        assertTrue(ControlAvailability.of(true, DisplayMode.ALL, Palette.CUSTOM).customColors());
        assertFalse(ControlAvailability.of(true, DisplayMode.ALL, Palette.DEFAULT).customColors());
        assertFalse(ControlAvailability.of(true, DisplayMode.ALL, Palette.COLORBLIND).customColors());
    }

    @Test
    void theColourPickersAreDeadWhileTheModIsOff() {
        assertFalse(ControlAvailability.of(false, DisplayMode.ALL, Palette.CUSTOM).customColors());
    }

    @Test
    void thePaletteDoesNotDisturbTheOtherControls() {
        ControlAvailability withCustom = ControlAvailability.of(true, DisplayMode.ALL, Palette.CUSTOM);
        ControlAvailability withDefault = ControlAvailability.of(true, DisplayMode.ALL, Palette.DEFAULT);

        assertEquals(withDefault.displayMode(), withCustom.displayMode());
        assertEquals(withDefault.showRetired(), withCustom.showRetired());
        assertEquals(withDefault.appearance(), withCustom.appearance());
        assertEquals(withDefault.reasons(), withCustom.reasons());
    }
```

Add `import com.w0x7y.justtiers.config.Palette;`.

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ControlAvailabilityTest'`
Expected: FAIL — the three-argument `of` and `customColors` do not exist.

- [ ] **Step 3: Write minimal implementation**

Add the component and the overload, keeping the two-argument `of` so existing callers and
tests compile:

```java
public record ControlAvailability(boolean displayMode,
                                  boolean showRetired,
                                  boolean appearance,
                                  boolean customColors,
                                  Map<Source, Reason> reasons) {

    public ControlAvailability {
        reasons = Map.copyOf(reasons);
    }

    /** As {@link #of(boolean, DisplayMode, Palette)}, with the default palette. */
    public static ControlAvailability of(boolean enabled, DisplayMode mode) {
        return of(enabled, mode, Palette.DEFAULT);
    }

    public static ControlAvailability of(boolean enabled, DisplayMode mode, Palette palette) {
        Map<Source, Reason> reasons = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            reasons.put(source, reasonFor(enabled, mode, source));
        }
        // The badge's shape - its side, its icons, its brackets - means the same thing in
        // every display mode, so the master switch is the only thing that can grey it.
        // The colour pickers additionally need the palette to be the one they feed.
        return new ControlAvailability(enabled, enabled, enabled,
                enabled && palette != null && palette.isCustom(), reasons);
    }
```

Add `import com.w0x7y.justtiers.config.Palette;`.

- [ ] **Step 4: Add the controls to the screen**

In `JustTiersScreens`, in the **Appearance** group that already holds Badge position, Show
gamemode icons and Show brackets, following the shape of those three exactly:

1. A **Hide my own badge** boolean option bound to `isHideOwnBadge`/`setHideOwnBadge`,
   labelled `justtiers.config.hideOwnBadge` with description
   `justtiers.config.hideOwnBadge.desc`.
2. A **Colour palette** option over `Palette.values()`, labelled
   `justtiers.config.palette`, its formatter `palette -> Component.translatable(palette.displayKey())`.
   Use whatever cycling-enum controller the display-mode option in this file already uses.
3. Three colour options, one per `Source.ALL`, labelled with
   `Component.translatable("justtiers.config.customColor", source.displayName())`, built
   with `ColorControllerBuilder.create(option)` over a `java.awt.Color` binding that reads
   `config.getCustomColor(source)` and writes `config.setCustomColor(source, color.getRGB())`.

The three colour options and the palette option must be wired to the same pending-state
mechanism the rest of the screen uses — nothing is written until Save. When the pending
palette changes, refresh availability so the pickers grey or ungrey immediately, using the
same listener the display-mode option already uses to grey the gamemode rows.

The preview built at `JustTiersScreens.java:87` constructs a `NametagStyle` from pending
values; give it a fourth argument built from the pending palette and pending custom
colours, so the preview recolours as the palette changes:

```java
                new NametagStyle(badgePosition.pendingValue(),
                        showIcons.pendingValue(), showBrackets.pendingValue(),
                        pendingColors())
```

where `pendingColors()` builds an `EnumMap<Source, Integer>` by calling
`palette.pendingValue().colorOf(source, pendingCustomColors)`.

- [ ] **Step 5: Build and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add -A
git commit -m "Add the palette and own-badge controls to the config screen"
```

---

### Task 10: Documentation and seeing it work

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Verify it in game**

Run: `./gradlew runClient`.

Confirm each of these:
- The config screen's Appearance group shows Hide my own badge, Colour palette and three
  colour pickers; the pickers are greyed until the palette is Custom.
- Changing the palette recolours the live preview immediately.
- Save, then check the nametag, `/justtiers lookup`, `/justtiers scan` and the gamemode
  grid are all in the new colours.
- Cancel discards a palette change; Undo reverts it.
- Switching to Custom, setting a colour, switching to Default and back to Custom keeps the
  colour.
- `/justtiers palette colorblind` and `/justtiers ownbadge` work and tab-complete.
- `/justtiers` prints the current palette.
- With Hide my own badge on, your own tag has no badge in third person while other
  players' tags still do; `/justtiers scan` still lists you with your tiers.
- Delete `config/justtiers.json`, relaunch: defaults are Default palette and badge shown.
- Hand-edit the file to `"palette": "rainbow"` and to a malformed custom colour; both are
  corrected on load with a warning in the log, and the game does not crash.

- [ ] **Step 2: Document it**

In `README.md`:
- Features list: one line for the colour palettes, one for hiding your own badge.
- Commands table: `/justtiers ownbadge` and `/justtiers palette <palette>`.
- The **Colours** table under Display modes: note that it lists the *default* palette and
  point at the new section.
- A new section after **Configuration screen**, covering the four palettes with their
  colours, why there is one colourblind preset rather than several, that custom colours
  are set on the config screen only, that presets do not overwrite custom colours, and
  that the palette applies to every screen at once.
- The **Configuration** key table: `hideOwnBadge`, `palette`, `customColors`, including
  that an unrecognised palette falls back to `default` and a malformed colour falls back
  per site.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "Document the colour palettes and hiding your own badge"
```

---

## Self-Review Notes

Spec coverage: hex parsing (Task 1), the rename (Task 2), palettes (Task 3), config keys
and resolution (Task 4), the `NametagStyle` route (Task 5), the twelve screen call sites
(Task 6), hiding your own badge (Task 7), commands (Task 8), the config screen and greying
(Task 9), docs and manual verification (Task 10).

Deviation from the spec, deliberate: the spec described `SiteColors` as the facade for all
thirteen call sites. Two of those — `Segments` and `NametagPreviewController` — turn out to
read `segment.color()` rather than the source, so they already carry whatever colour the
model gave them and need no change. Twelve routes through `SiteColors`; the nametag path
routes through `NametagStyle`.
