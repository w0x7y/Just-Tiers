# Just-Tiers Config GUI Implementation Plan

> **Status: historical.** This plan was written before implementation and is kept as a
> record of what was planned, not of what was built. It was implemented in full, but
> several details changed once the APIs were checked against the actual jars, so where
> this document and the code disagree, **the code is correct**. Known divergences:
>
> - **Test counts.** The plan's "89 existing tests" was already stale when written; the
>   real baseline was 103, so the totals here (112) should read **126 across 12 classes**.
> - **`ControlAvailability`.** Implemented as `Map<Source, Reason> reasons` with
>   `reasonFor(Source)`, not `Map<Source, Boolean> gamemodes`. The `gamemode(Source)`
>   accessor and the `Reason` values are as described.
> - **ModMenu entry point.** `ModMenuIntegration.getModConfigScreenFactory()` adds a
>   config button to the Just-Tiers entry in ModMenu's mod list. There is no pause-menu
>   route, contrary to the verification checklist below.
> - **Minecraft 26.2 API.** `setScreen` is `setScreenAndShow`; the Fabric keybind API is
>   `keymapping.v1/KeyMappingHelper`; `KeyMapping` takes a registered `Category`, not a
>   `String`.
> - **Dependencies.** YACL's `org.quiltmc.parsers` transitives are not on Maven Central,
>   so the Quilt maven is declared as well.
> - **Option descriptions.** YACL caches an `Option`'s description, so a greyed gamemode
>   row carries the explanations for all three inert states rather than only the one that
>   currently applies.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** An in-game configuration screen for Just-Tiers, built on YetAnotherConfigLib (YACL), with a live nametag preview, every option visible at all times (irrelevant ones greyed rather than hidden), and a full-screen icon grid for picking a gamemode that returns to the config screen the moment you click a tile.

**Architecture:** YACL owns the chrome — window, category tabs, option rows, search, Save/Cancel/Undo. Just-Tiers supplies three custom pieces on top of it: a preview widget (a `Controller<Component>` whose widget draws the real nametag, scaled, on a plate), a gamemode picker (a `Controller<String>` whose widget opens a child `Screen` holding a grid of icon tiles), and an availability rule that greys options out as `displayMode` / `enabled` change. All layout maths, availability rules and preview data live in Minecraft-free packages so they stay unit-testable, exactly as the rest of the mod is.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), YACL `3.9.4+26.2-fabric`, ModMenu `20.0.1` (compile-only), JUnit 5.

---

## Global Constraints

- **Reuse the render path, do not re-implement it.** The preview must produce its `Component` through `TierResolver.resolve(...)` → `NametagModel.build(...)`, the same two calls `NametagRenderer` makes. A preview that draws its own idea of a nametag is a bug waiting to happen.
- **Minecraft-free packages stay Minecraft-free.** `tier`, `api`, `cache`, `resolve`, `render.model` already are. This plan adds three more: `preview`, `gui.layout`, `gui.state`. They must not import `net.minecraft.*`. Everything under `gui` itself is Minecraft-facing.
- **Nothing is hidden, only greyed.** Every option is present in every state. Availability is computed by one pure function (`ControlAvailability`) so the rule lives in one place and is testable.
- **Color discipline.** Color carries exactly one meaning in this UI: *which leaderboard this is*. `Source.color()` — MCTiers `0xFFFF55`, SubTiers `0x55FFFF`, NovaTiers `0xAA55FF` — is used for the display-mode value text, the gamemode picker value text, the grid screen's header and its selected-tile border, and (via the existing model) the tier text inside the preview. Everything else is neutral: white labels, `0xA0A0A0` secondary text, `0x707070` when disabled. Booleans use plain tick boxes, **not** YACL's colored yes/no controller. No tier-rank colors, no red/green toggles.
- **Pending values, not saved values.** YACL edits a pending copy and applies on Save. The preview and the grid screen both read and write *pending* values (`Option#pendingValue`, `Option#requestSet`), never `JustTiersConfig` directly, or Cancel will lie.
- **The grid returns on click.** One click on a tile sets the pending gamemode and immediately restores the YACL screen. No confirm button.
- **No behaviour change to the nametag itself.** This plan touches rendering only by extracting a shared `Segment`→`Component` helper. `NametagModelTest` and `TierResolverTest` must stay green untouched.

---

## Verified API Reference

Confirmed on 2026-08-13 by inspecting `yet-another-config-lib-3.9.4+26.2-fabric.jar` from Maven Central. **Do not re-derive these.**

### Dependency coordinates

| Artifact | Coordinate | Repository |
|---|---|---|
| YACL | `dev.isxander:yet-another-config-lib:3.9.4+26.2-fabric` | Maven Central (already in `repositories`) |
| ModMenu | `com.terraformersmc:modmenu:20.0.1` | `https://maven.terraformersmc.com/releases` (must be added) |

YACL's own `fabric.mod.json`: id `yet_another_config_lib_v3`, version `3.9.4+26.2-fabric`, `depends` `minecraft ~26.2-beta.4`, `fabric-api >=0.150.3+26.2`. It loads on 26.2 release (a release outranks its own pre-releases in semver). Maven Central's newest 26.2 build is 3.9.4; Modrinth may carry a newer 3.9.x — check before release, but do not chase it if Central lags.

### YACL types used by this plan

```java
// dev.isxander.yacl3.api
YetAnotherConfigLib.createBuilder()
    .title(Component).category(ConfigCategory).save(Runnable)
    .screenInit(Consumer<YACLScreen>).build().generateScreen(Screen parent) -> Screen

ConfigCategory.createBuilder().name(Component).tooltip(Component...)
    .group(OptionGroup).option(Option<?>).build()

OptionGroup.createBuilder().name(Component).description(OptionDescription)
    .option(Option<?>).collapsed(boolean).build()

Option.<T>createBuilder()
    .name(Component)
    .description(OptionDescription)                     // or Function<T, OptionDescription>
    .binding(T defaultValue, Supplier<T> getter, Consumer<T> setter)
    .controller(Function<Option<T>, ControllerBuilder<T>>)
    .customController(Function<Option<T>, Controller<T>>)   // our custom widgets
    .available(boolean)
    .addListener(OptionEventListener<T>)                // fires on pending change
    .instant(boolean)
    .build() -> Option<T>

// Runtime, on a built Option:
option.setAvailable(boolean);      // THE greying mechanism
option.pendingValue();             // live edited value
option.requestSet(T);              // write a pending value

OptionDescription.createBuilder().text(Component...).build()
LabelOption.create(Component)
ButtonOption.createBuilder().name(..).text(..).action(BiConsumer<YACLScreen, ButtonOption>).build()

// Controller contract we implement:
interface Controller<T> {
    Option<T> option();
    Component formatValue();
    AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim);
}

Dimension.ofInt(x, y, width, height);   // dim.withHeight(int) for self-sizing widgets
```

`OptionEventListener.Event` values: `INITIAL`, `STATE_CHANGE`, `AVAILABILITY_CHANGE`, `OTHER`.

### YACL widget base classes

```java
// dev.isxander.yacl3.gui.AbstractWidget
//   implements GuiEventListener, Renderable, NarratableEntry
protected final Minecraft client;
protected final Font textRenderer;
protected final int inactiveColor;
public AbstractWidget(Dimension<Integer> dim);
public void setDimension(Dimension<Integer>);      // widgets self-size with this
public Dimension<Integer> getDimension();
protected void drawButtonRect(GuiGraphicsExtractor, int x1, int y1, int x2, int y2,
                              boolean hovered, boolean enabled);
public void playDownSound();

// dev.isxander.yacl3.gui.controllers.ControllerWidget<T extends Controller<?>>
//   extends AbstractWidget — gives hover/disabled/label/value-text rendering for free
public ControllerWidget(T control, YACLScreen screen, Dimension<Integer> dim);
public void extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float delta);
protected void extractValueText(GuiGraphicsExtractor, int, int, float);
protected abstract int getHoveredControlWidth();
protected Component getValueText();
protected int getValueColor();
protected boolean isAvailable();
protected boolean hovered;   // field
```

A row's height is whatever `getDimension().height()` says: `LabelControllerElement` self-sizes by calling `setDimension(getDimension().withHeight(h))`, and `OptionListWidget.Entry.getHeight()` reads it back. The preview widget uses the same trick.

### Minecraft 26.2 GUI API

26.2 renders by **extracting render state**, not by an immediate-mode `render(GuiGraphics, …)`. These signatures were read straight out of YACL's 26.2 bytecode:

```java
// net.minecraft.client.gui.GuiGraphicsExtractor  (replaces GuiGraphics in draw calls)
void fill(int x1, int y1, int x2, int y2, int argb);
void fillGradient(int x1, int y1, int x2, int y2, int argbTop, int argbBottom);
void outline(int x, int y, int width, int height, int argb);
void text(Font font, Component text, int x, int y, int color, boolean shadow);
void text(Font font, String text, int x, int y, int color, boolean shadow);
void centeredText(Font font, Component text, int centerX, int y, int color);
void enableScissor(int x1, int y1, int x2, int y2);
void disableScissor();
Matrix3x2fStack pose();                 // JOML — pushMatrix/popMatrix/translate/scale
void blitSprite(RenderPipeline, Identifier, int x, int y, int w, int h);
void blit(RenderPipeline, Identifier, int x, int y, float u, float v, int w, int h, int tw, int th);

// net.minecraft.client.gui.screens.Screen
protected void init();
protected void repositionElements();
public void extractRenderState(GuiGraphicsExtractor, int mouseX, int mouseY, float delta);
public void extractBackground(GuiGraphicsExtractor, int mouseX, int mouseY, float delta);
public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick);
public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical);
public boolean keyPressed(KeyEvent event);
public boolean charTyped(CharacterEvent event);

// net.minecraft.client.input.MouseButtonEvent — button(), x(), y(), hasShiftDown(), hasControlDown()
// net.minecraft.client.input.KeyEvent          — key(), hasShiftDown(), hasControlDown()
// net.minecraft.client.gui.components.Button.builder(Component, Button.OnPress)
//     .pos(int,int).size(int,int).tooltip(Tooltip).build()
```

**Naming gotchas in 26.2:** `net.minecraft.resources.Identifier` (not `ResourceLocation`); `RenderPipelines.GUI_TEXTURED` for textured blits; mouse and key handlers take event objects, not `(double, double, int)` / `(int, int, int)`.

### Not verified — confirm at implementation time

- **Fabric keybinding API** (`KeyBindingHelper.registerKeyBinding`, `KeyMapping`, `InputConstants.Type.KEYSYM`, `ClientTickEvents.END_CLIENT_TICK`). The Fabric maven was unreachable from the planning environment. The shapes in Task 9 are the long-stable ones; if 26.2 moved them, adjust there and nowhere else.
- **ModMenu 20.0.1's `ModMenuApi` / `ConfigScreenFactory` package path.** Assumed `com.terraformersmc.modmenu.api.*`, unchanged for many releases.
- `Matrix3x2fStack.pushMatrix()/popMatrix()` naming on the JOML version 26.2 ships.

---

## Screen Design

### Layout

```
┌───────────────────────────────────────────────────────────────────────┐
│  Just-Tiers                                          [search]         │
│  ┌─────────────┬──────────────────────────────────────────────────┐   │
│  │ Display     │  ╭──────────────────────────────────────────╮    │   │
│  │ Data        │  │   [⛏HT2 🏹LT3 ⚔RHT1] Steve               │    │   │  preview row
│  │ About       │  │   Sample data · MCTiers Vanilla          │    │   │  (self-sized, 2x text)
│  │             │  ╰──────────────────────────────────────────╯    │   │
│  │             │                                                  │   │
│  │             │  Show tiers in nametags               [x]        │   │
│  │             │  Display mode                     < MCTiers >    │   │  ← site-colored
│  │             │  Show retired tiers                   [x]        │   │
│  │             │                                                  │   │
│  │             │  ── Gamemodes ───────────────────────────────    │   │
│  │             │  MCTiers gamemode                 ⛏ Vanilla >    │   │  ← site-colored
│  │             │  SubTiers gamemode                🏹 Elytra >    │   │  ← greyed
│  │             │  NovaTiers gamemode              ⚔ Vanilla >     │   │  ← greyed
│  └─────────────┴──────────────────────────────────────────────────┘   │
│                    [ Undo ]  [ Cancel ]  [ Save ]                     │
└───────────────────────────────────────────────────────────────────────┘
```

Clicking any gamemode row opens the grid screen:

```
┌───────────────────────────────────────────────────────────────────────┐
│                        MCTiers gamemode                               │  ← site-colored title
│               [⛏HT2 🏹LT3 ⚔RHT1] Steve                                │  ← live preview, follows hover
│                                                                       │
│      ╭────────╮  ╭────────╮  ╭────────╮  ╭────────╮                   │
│      │   ⛏    │  │   🔨   │  │   🌶   │  │   🧪   │                   │
│      │  Axe   │  │  Mace  │  │Neth. OP│  │  Pot   │                   │
│      ╰────────╯  ╰────────╯  ╰────────╯  ╰────────╯                   │
│      ╭────────╮  ╭────────╮  ╭────────╮  ╭━━━━━━━━╮                   │
│      │   🏠   │  │   ⚔    │  │  ❤    │  │   🟩   │                   │  ← selected tile:
│      │  SMP   │  │ Sword  │  │  UHC   │  │Vanilla │                   │    site-colored border
│      ╰────────╯  ╰────────╯  ╰────────╯  ╰━━━━━━━━╯                   │
│                                                                       │
│                          [ Back ]                                     │
└───────────────────────────────────────────────────────────────────────┘
```

### Categories

| Category | Contents |
|---|---|
| **Display** | Preview row; `enabled`; `displayMode`; `showRetired`; group **Gamemodes** with the three pickers |
| **Data** | `novaRefreshMinutes` slider; "Refresh tier data now" button; a read-only label with the indexed NovaTiers player count |
| **About** | Static labels: version, the three leaderboard names in their own colors, a line pointing at `/justtiers`, licence note |

### Availability rules (the greying)

| Control | Available when |
|---|---|
| `enabled` | always |
| `displayMode` | `enabled` |
| `showRetired` | `enabled` |
| MCTiers gamemode | `enabled` && mode == `MCTIERS_ONLY` |
| SubTiers gamemode | `enabled` && mode == `SUBTIERS_ONLY` |
| NovaTiers gamemode | `enabled` && mode == `NOVATIERS_ONLY` |
| `novaRefreshMinutes` | always (the download happens regardless of display mode) |
| Refresh button | always |

A greyed gamemode row still shows its stored value, and its description explains *why* it is inert: "`all` mode always shows each site's highest tier, so there is no gamemode to pick." That is the same sentence `/justtiers gamemode` already prints — keep them identical.

### Preview behaviour

Fixed sample data, chosen so every toggle visibly does something:

| Site | Sample placements |
|---|---|
| MCTiers | `vanilla` HT2, `axe` LT3, `sword` HT4 |
| SubTiers | `elytra` LT3, `bow` HT5, `minecart` RHT2 *(retired)* |
| NovaTiers | `vanilla` HT4, `uhc` LT4, `spearmace` RHT1 *(retired)* |

Consequences, all of which the user sees by flipping one switch:

- `all` + retired shown → `[⛏HT2 🚂RHT2 🔱RHT1] Steve` (best per site, two of them retired).
- Turning **Show retired** off → `[⛏HT2 🏹LT3 ⚔HT4] Steve`. The retired entries fall back to the best active tier rather than vanishing.
- `mctiers_only` with gamemode **Mace** → `[⛏HT2] Steve` plus the caption "Not ranked in Mace — showing best on MCTiers", because the sample has no Mace placement. This is the fallback rule made visible.
- `enabled` off → the plate dims and the caption reads "Nametag tiers are turned off".

The caption is one line of `0xA0A0A0` text under the tag. The tag itself is drawn at 2× via `pose().scale(2f, 2f)` so the 8×8 icon glyphs are legible.

---

## File Structure

```
src/main/java/com/w0x7y/justtiers/
  preview/PreviewSample.java            fixed sample tiers + caption logic     (Minecraft-free)
  gui/state/ControlAvailability.java    which controls are live                (Minecraft-free)
  gui/layout/GridLayout.java            tile grid maths + hit testing          (Minecraft-free)
  gui/JustTiersScreens.java             assembles the YACL screen
  gui/NametagPreviewController.java     Controller<Component> + preview widget
  gui/GamemodePickerController.java     Controller<String> + row widget
  gui/GamemodeGridScreen.java           the child grid Screen
  gui/JustTiersKeybinds.java            KeyMapping registration + tick hook
  gui/ModMenuIntegration.java           ModMenuApi entrypoint
  render/Segments.java                  List<Segment> -> Component (extracted)
src/main/resources/assets/justtiers/lang/en_us.json
src/test/java/com/w0x7y/justtiers/
  preview/PreviewSampleTest.java
  gui/state/ControlAvailabilityTest.java
  gui/layout/GridLayoutTest.java
```

Modified: `build.gradle.kts`, `gradle.properties`, `fabric.mod.json`, `JustTiersClient.java`, `JustTiersCommands.java`, `NametagRenderer.java`, `README.md`.

---

### Task 1: Dependencies and mod metadata

**Files:**
- Modify: `gradle.properties`, `build.gradle.kts`, `src/main/resources/fabric.mod.json`
- Create: `src/main/resources/assets/justtiers/lang/en_us.json`

**Interfaces:**
- Consumes: nothing.
- Produces: a build that compiles against YACL and ModMenu, and a `runClient` whose mod list shows Just-Tiers with a config button.

- [ ] **Step 1: Add versions to `gradle.properties`**

```properties
yacl_version=3.9.4+26.2-fabric
modmenu_version=20.0.1
```

- [ ] **Step 2: Add the repository and dependencies in `build.gradle.kts`**

YACL is on Maven Central, which is already declared. ModMenu is not, so add Terraformers' maven. Keep using plain `implementation` — 26.2 is unobfuscated and this project performs no remapping, so `modImplementation` is wrong here.

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://maven.terraformersmc.com/releases/")
}

dependencies {
    // …existing…
    implementation("dev.isxander:yet-another-config-lib:${property("yacl_version")}")
    compileOnly("com.terraformersmc:modmenu:${property("modmenu_version")}")
}
```

ModMenu is `compileOnly` on purpose: the `modmenu` entrypoint is only loaded when ModMenu is installed, so the integration class is never touched otherwise and no runtime dependency is created.

- [ ] **Step 3: Declare YACL in `fabric.mod.json`**

YACL becomes a **required** runtime dependency — the config screen is its whole implementation, so a missing YACL is a hard error, not a degraded mode. Say so on the download page.

```json
  "entrypoints": {
    "client": ["com.w0x7y.justtiers.JustTiersClient"],
    "modmenu": ["com.w0x7y.justtiers.gui.ModMenuIntegration"]
  },
  "depends": {
    "minecraft": "~26.2",
    "fabricloader": ">=0.19",
    "fabric-api": "*",
    "java": ">=25",
    "yet_another_config_lib_v3": ">=3.9.4"
  },
  "suggests": {
    "modmenu": "*"
  }
```

- [ ] **Step 4: Create `assets/justtiers/lang/en_us.json`**

Every user-visible string in this plan goes here; no literal English in Java. Keys use the `justtiers.` prefix.

```json
{
  "justtiers.config.title": "Just-Tiers",
  "justtiers.config.category.display": "Display",
  "justtiers.config.category.data": "Data",
  "justtiers.config.category.about": "About",

  "justtiers.option.enabled": "Show tiers in nametags",
  "justtiers.option.enabled.desc": "Master switch. When off, nametags render untouched and no lookups are made.",
  "justtiers.option.displayMode": "Display mode",
  "justtiers.option.displayMode.desc": "Which leaderboards to show. 'All' shows each site's highest tier side by side.",
  "justtiers.option.showRetired": "Show retired tiers",
  "justtiers.option.showRetired.desc": "Retired tiers are shown with an R prefix. When off, a player falls back to their best active tier.",
  "justtiers.option.gamemode": "%s gamemode",
  "justtiers.option.gamemode.desc": "The gamemode to show for %s. If the player is not ranked in it, their highest tier on that site is shown instead.",
  "justtiers.option.gamemode.inactive": "'all' mode always shows each site's highest tier, so there is no gamemode to pick. Switch mode first.",
  "justtiers.option.gamemode.disabled": "Turn Just-Tiers on to change this.",
  "justtiers.option.novaRefresh": "NovaTiers refresh interval",
  "justtiers.option.novaRefresh.desc": "NovaTiers has no per-player endpoint, so the full ranked list (~1.9 MB) is downloaded on this interval.",
  "justtiers.option.refresh": "Refresh tier data now",
  "justtiers.option.refresh.text": "Refresh",
  "justtiers.option.refresh.desc": "Clears the cache and re-downloads. Use if a site was down.",

  "justtiers.mode.mctiers_only": "MCTiers only",
  "justtiers.mode.subtiers_only": "SubTiers only",
  "justtiers.mode.novatiers_only": "NovaTiers only",
  "justtiers.mode.all": "All three",

  "justtiers.preview.player": "Steve",
  "justtiers.preview.sample": "Sample data · %s",
  "justtiers.preview.fallback": "Not ranked in %s — showing best on %s",
  "justtiers.preview.off": "Nametag tiers are turned off",
  "justtiers.preview.empty": "Nothing would be shown",

  "justtiers.grid.title": "%s gamemode",
  "justtiers.grid.back": "Back",
  "justtiers.grid.hint": "Click a gamemode to select it",

  "justtiers.data.indexed": "NovaTiers players indexed: %s",
  "justtiers.about.version": "Just-Tiers %s",
  "justtiers.about.commands": "All settings are also available via /justtiers",

  "key.justtiers.open_config": "Open config screen",
  "key.categories.justtiers": "Just-Tiers"
}
```

- [ ] **Step 5: Verify**

Run: `./gradlew build`
Expected: PASS, all 89 existing tests still green, YACL on the compile classpath.

---

### Task 2: `PreviewSample` — fixed sample data (Minecraft-free)

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/preview/PreviewSample.java`
- Test: `src/test/java/com/w0x7y/justtiers/preview/PreviewSampleTest.java`

**Interfaces:**
- Consumes: `Tier`, `Source`, `Gamemodes`, `DisplayMode`, `TierResolver`, `NametagModel`, `Segment`.
- Produces:
  - `PreviewSample.TIERS` → `Map<Source, Map<String, Tier>>`, immutable.
  - `PreviewSample.segments(DisplayMode, Map<Source,String>, boolean showRetired)` → `List<Segment>`.
  - `PreviewSample.Caption` → `record Caption(Kind kind, String gamemodeName, String sourceName)` with `Kind { SAMPLE, FALLBACK, EMPTY }`.
  - `PreviewSample.caption(DisplayMode, Map<Source,String>, boolean showRetired)` → `Caption`.

- [ ] **Step 1: Write the failing test**

```java
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*PreviewSampleTest*'`
Expected: FAIL — `PreviewSample` does not exist.

- [ ] **Step 3: Write `PreviewSample.java`**

```java
package com.w0x7y.justtiers.preview;

import com.w0x7y.justtiers.render.model.NametagModel;
import com.w0x7y.justtiers.render.model.Segment;
import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.resolve.ResolvedTier;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.tier.Gamemode;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;

import java.util.List;
import java.util.Map;

/**
 * The invented player behind the config screen's nametag preview. The placements are
 * chosen so that every switch in the UI visibly changes the result: two sites' best
 * tiers are retired (so {@code showRetired} does something), each site has three
 * placements (so changing gamemode does something), and no site is ranked in every
 * gamemode (so the fallback rule is reachable).
 *
 * <p>Minecraft-free on purpose — the same resolve-then-model path the real nametag
 * uses, so a preview can never disagree with what is drawn in the world.
 */
public final class PreviewSample {

    public static final Map<Source, Map<String, Tier>> TIERS = Map.of(
            Source.MCTIERS, Map.of(
                    "vanilla", new Tier(2, true, false),
                    "axe", new Tier(3, false, false),
                    "sword", new Tier(4, true, false)),
            Source.SUBTIERS, Map.of(
                    "elytra", new Tier(3, false, false),
                    "bow", new Tier(5, true, false),
                    "minecart", new Tier(2, true, true)),
            Source.NOVATIERS, Map.of(
                    "vanilla", new Tier(4, true, false),
                    "uhc", new Tier(4, false, false),
                    "spearmace", new Tier(1, true, true)));

    public record Caption(Kind kind, String gamemodeName, String sourceName) {
        public enum Kind { SAMPLE, FALLBACK, EMPTY }
    }

    public static List<ResolvedTier> resolve(DisplayMode mode,
                                             Map<Source, String> selectedGamemodes,
                                             boolean showRetired) {
        return TierResolver.resolve(mode, TIERS, selectedGamemodes, showRetired);
    }

    public static List<Segment> segments(DisplayMode mode,
                                         Map<Source, String> selectedGamemodes,
                                         boolean showRetired) {
        return NametagModel.build(resolve(mode, selectedGamemodes, showRetired));
    }

    /**
     * Explains what the preview is showing: the selected gamemode, or — when the sample
     * player has no placement there — which gamemode it fell back from.
     */
    public static Caption caption(DisplayMode mode,
                                  Map<Source, String> selectedGamemodes,
                                  boolean showRetired) {
        List<ResolvedTier> resolved = resolve(mode, selectedGamemodes, showRetired);
        if (resolved.isEmpty()) {
            return new Caption(Caption.Kind.EMPTY, "", "");
        }

        var single = mode.singleSource();
        if (single.isEmpty()) {
            return new Caption(Caption.Kind.SAMPLE, "", "");
        }

        Source source = single.get();
        String slug = selectedGamemodes.get(source);
        String requested = Gamemodes.find(source, slug).map(Gamemode::displayName).orElse(slug);
        String shown = resolved.getFirst().gamemode().displayName();
        Caption.Kind kind = shown.equals(requested)
                ? Caption.Kind.SAMPLE
                : Caption.Kind.FALLBACK;
        return new Caption(kind, requested, source.displayName());
    }

    private PreviewSample() {
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*PreviewSampleTest*'`
Expected: PASS (8 tests).

---

### Task 3: `ControlAvailability` — the greying rule (Minecraft-free)

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/state/ControlAvailability.java`
- Test: `src/test/java/com/w0x7y/justtiers/gui/state/ControlAvailabilityTest.java`

**Interfaces:**
- Consumes: `DisplayMode`, `Source`.
- Produces: `ControlAvailability.of(boolean enabled, DisplayMode mode)` → `record ControlAvailability(boolean displayMode, boolean showRetired, Map<Source, Boolean> gamemodes)`, plus `boolean gamemode(Source)` and `Reason reasonFor(Source)` where `Reason { AVAILABLE, MOD_DISABLED, MODE_IS_ALL, OTHER_SITE }`.

The `Reason` is what lets the UI put the *right* explanation in a greyed row's tooltip instead of a generic "unavailable".

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ControlAvailabilityTest {

    @Test
    void everythingIsLiveInASingleSiteModeForThatSite() {
        var state = ControlAvailability.of(true, DisplayMode.MCTIERS_ONLY);
        assertTrue(state.displayMode());
        assertTrue(state.showRetired());
        assertTrue(state.gamemode(Source.MCTIERS));
        assertFalse(state.gamemode(Source.SUBTIERS));
        assertFalse(state.gamemode(Source.NOVATIERS));
    }

    @Test
    void noGamemodeIsSelectableInAllMode() {
        var state = ControlAvailability.of(true, DisplayMode.ALL);
        assertTrue(state.displayMode());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MODE_IS_ALL, state.reasonFor(source));
        }
    }

    @Test
    void disablingTheModGreysEverythingButTheMasterSwitch() {
        var state = ControlAvailability.of(false, DisplayMode.MCTIERS_ONLY);
        assertFalse(state.displayMode());
        assertFalse(state.showRetired());
        for (Source source : Source.values()) {
            assertFalse(state.gamemode(source));
            assertEquals(ControlAvailability.Reason.MOD_DISABLED, state.reasonFor(source));
        }
    }

    @Test
    void reasonDistinguishesTheOtherSitesFromAllMode() {
        var state = ControlAvailability.of(true, DisplayMode.SUBTIERS_ONLY);
        assertEquals(ControlAvailability.Reason.AVAILABLE, state.reasonFor(Source.SUBTIERS));
        assertEquals(ControlAvailability.Reason.OTHER_SITE, state.reasonFor(Source.MCTIERS));
        assertEquals(ControlAvailability.Reason.OTHER_SITE, state.reasonFor(Source.NOVATIERS));
    }

    @Test
    void everyModeAndToggleCombinationIsCovered() {
        for (DisplayMode mode : DisplayMode.values()) {
            for (boolean enabled : new boolean[]{true, false}) {
                var state = ControlAvailability.of(enabled, mode);
                for (Source source : Source.values()) {
                    assertEquals(state.gamemode(source),
                            state.reasonFor(source) == ControlAvailability.Reason.AVAILABLE);
                }
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*ControlAvailabilityTest*'`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write `ControlAvailability.java`**

```java
package com.w0x7y.justtiers.gui.state;

import com.w0x7y.justtiers.resolve.DisplayMode;
import com.w0x7y.justtiers.tier.Source;

import java.util.EnumMap;
import java.util.Map;

/**
 * Decides which controls the config screen leaves live. Nothing is ever hidden; a
 * control that cannot do anything useful is greyed and carries a {@link Reason} the
 * UI turns into an explanation, so the screen always shows the whole configuration
 * surface rather than a shape that changes under the user.
 */
public record ControlAvailability(boolean displayMode,
                                  boolean showRetired,
                                  Map<Source, Reason> reasons) {

    public enum Reason { AVAILABLE, MOD_DISABLED, MODE_IS_ALL, OTHER_SITE }

    public static ControlAvailability of(boolean enabled, DisplayMode mode) {
        Map<Source, Reason> reasons = new EnumMap<>(Source.class);
        for (Source source : Source.values()) {
            reasons.put(source, reasonFor(enabled, mode, source));
        }
        return new ControlAvailability(enabled, enabled, Map.copyOf(reasons));
    }

    private static Reason reasonFor(boolean enabled, DisplayMode mode, Source source) {
        if (!enabled) {
            return Reason.MOD_DISABLED;
        }
        var single = mode.singleSource();
        if (single.isEmpty()) {
            return Reason.MODE_IS_ALL;
        }
        return single.get() == source ? Reason.AVAILABLE : Reason.OTHER_SITE;
    }

    public boolean gamemode(Source source) {
        return reasonFor(source) == Reason.AVAILABLE;
    }

    public Reason reasonFor(Source source) {
        return reasons.getOrDefault(source, Reason.OTHER_SITE);
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*ControlAvailabilityTest*'`
Expected: PASS (5 tests).

---

### Task 4: `GridLayout` — tile maths and hit testing (Minecraft-free)

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/layout/GridLayout.java`
- Test: `src/test/java/com/w0x7y/justtiers/gui/layout/GridLayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `GridLayout.of(int itemCount, int availableWidth, int tileWidth, int tileHeight, int gap, int maxColumns)` → `GridLayout`.
  - `int columns()`, `int rows()`, `int contentWidth()`, `int contentHeight()`.
  - `int xOf(int index)`, `int yOf(int index)` — relative to the grid's top-left origin.
  - `OptionalInt indexAt(int localX, int localY)` — hit test, gaps excluded.
  - `int move(int index, Direction direction)` — keyboard navigation, clamped.

Pulling this out of the screen is what makes "does the grid wrap correctly at 4 columns, and does clicking the gap between two tiles select nothing" a unit test instead of a manual click-around.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

class GridLayoutTest {

    private static GridLayout layout(int count, int width) {
        return GridLayout.of(count, width, 72, 72, 8, 6);
    }

    @Test
    void columnsFillTheAvailableWidthUpToTheCap() {
        assertEquals(4, layout(12, 340).columns());   // 4*72 + 3*8 = 312 fits, 5 would not
        assertEquals(6, layout(12, 2000).columns());  // capped
        assertEquals(1, layout(12, 100).columns());   // never zero
    }

    @Test
    void rowsCoverEveryItem() {
        GridLayout grid = layout(12, 340);
        assertEquals(3, grid.rows());
        assertEquals(2, layout(8, 340).rows());
        assertEquals(3, layout(9, 340).rows());       // partial last row still counts
    }

    @Test
    void positionsAdvanceByTileAndGap() {
        GridLayout grid = layout(12, 340);
        assertEquals(0, grid.xOf(0));
        assertEquals(80, grid.xOf(1));                // 72 + 8
        assertEquals(0, grid.xOf(4));                 // wrapped
        assertEquals(0, grid.yOf(0));
        assertEquals(80, grid.yOf(4));
    }

    @Test
    void contentSizeMatchesTheOccupiedArea() {
        GridLayout grid = layout(12, 340);
        assertEquals(312, grid.contentWidth());       // 4*72 + 3*8
        assertEquals(232, grid.contentHeight());      // 3*72 + 2*8
    }

    @Test
    void hitTestingFindsTheTileUnderThePoint() {
        GridLayout grid = layout(12, 340);
        assertEquals(OptionalInt.of(0), grid.indexAt(0, 0));
        assertEquals(OptionalInt.of(0), grid.indexAt(71, 71));
        assertEquals(OptionalInt.of(1), grid.indexAt(80, 0));
        assertEquals(OptionalInt.of(4), grid.indexAt(0, 80));
    }

    @Test
    void gapsAndOutOfBoundsSelectNothing() {
        GridLayout grid = layout(12, 340);
        assertEquals(OptionalInt.empty(), grid.indexAt(75, 0));    // horizontal gap
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 75));    // vertical gap
        assertEquals(OptionalInt.empty(), grid.indexAt(-1, 0));
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 1000));
    }

    @Test
    void trailingEmptyCellsOfThePartialRowSelectNothing() {
        GridLayout grid = layout(9, 340);              // 4 columns, last row holds one tile
        assertEquals(OptionalInt.of(8), grid.indexAt(0, 160));
        assertEquals(OptionalInt.empty(), grid.indexAt(80, 160));
    }

    @Test
    void keyboardNavigationClampsAtTheEdges() {
        GridLayout grid = layout(12, 340);
        assertEquals(1, grid.move(0, GridLayout.Direction.RIGHT));
        assertEquals(0, grid.move(0, GridLayout.Direction.LEFT));
        assertEquals(4, grid.move(0, GridLayout.Direction.DOWN));
        assertEquals(0, grid.move(0, GridLayout.Direction.UP));
        assertEquals(11, grid.move(11, GridLayout.Direction.DOWN));
        assertEquals(11, grid.move(11, GridLayout.Direction.RIGHT));
    }

    @Test
    void navigationDoesNotLandOnEmptyTrailingCells() {
        GridLayout grid = layout(9, 340);
        assertEquals(8, grid.move(4, GridLayout.Direction.DOWN));
        assertEquals(8, grid.move(5, GridLayout.Direction.DOWN));  // clamped onto the last item
    }

    @Test
    void zeroItemsIsHarmless() {
        GridLayout grid = layout(0, 340);
        assertEquals(0, grid.rows());
        assertEquals(OptionalInt.empty(), grid.indexAt(0, 0));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew test --tests '*GridLayoutTest*'`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Write `GridLayout.java`**

Implementation notes rather than a full listing — the tests above pin the behaviour exactly:

- `columns = clamp((availableWidth + gap) / (tileWidth + gap), 1, maxColumns)`, then also capped at `itemCount` when `itemCount > 0`, so eight items never spread across twelve columns.
- `rows = ceilDiv(itemCount, columns)`.
- `xOf(i) = (i % columns) * (tileWidth + gap)`, `yOf(i) = (i / columns) * (tileHeight + gap)`.
- `indexAt` maps the point to a cell, rejects the point if it landed in a gap (`offset % (tile + gap) >= tile`), rejects negative coordinates and any index `>= itemCount`.
- `move` computes the target row/column, clamps both to range, then clamps the resulting index to `itemCount - 1`.
- Guard `itemCount == 0` everywhere: `rows()` is 0 and `indexAt` is always empty.

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew test --tests '*GridLayoutTest*'`
Expected: PASS (10 tests).

---

### Task 5: Extract `Segments.toComponent`

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/render/Segments.java`
- Modify: `src/main/java/com/w0x7y/justtiers/render/NametagRenderer.java`

**Interfaces:**
- Produces: `Segments.toComponent(List<Segment>)` → `MutableComponent`.

The renderer and the preview widget must build their `Component` the same way. Today that loop lives inline in `NametagRenderer.decorate`; move it out and call it from both.

- [ ] **Step 1: Write `Segments.java`**

```java
package com.w0x7y.justtiers.render;

import com.w0x7y.justtiers.render.model.Segment;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/** Turns the Minecraft-free {@link Segment} list into a colored {@link Component}. */
public final class Segments {

    public static MutableComponent toComponent(List<Segment> segments) {
        MutableComponent result = Component.empty();
        for (Segment segment : segments) {
            result.append(Component.literal(segment.text())
                    .withStyle(style -> style.withColor(segment.color())));
        }
        return result;
    }

    private Segments() {
    }
}
```

- [ ] **Step 2: Use it in `NametagRenderer.decorate`**

Replace the inline loop:

```java
        MutableComponent prefix = Segments.toComponent(segments);
        return prefix.append(original);
```

- [ ] **Step 3: Verify nothing moved**

Run: `./gradlew build`
Expected: PASS, all existing tests green. No behaviour change is intended or acceptable here.

---

### Task 6: The nametag preview widget

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/NametagPreviewController.java`

**Interfaces:**
- Consumes: `PreviewSample`, `Segments`, YACL `Controller`/`AbstractWidget`.
- Produces: `NametagPreviewController.option(Supplier<PreviewState> state)` → `Option<Component>`, where `PreviewState` is a small record carrying the *pending* `enabled`, `displayMode`, per-source gamemode slugs and `showRetired`.

**Design:**
- The option's value is a `Component` (like `LabelOption`), but the widget re-reads the supplier every frame, so it tracks pending edits with no listener wiring.
- The widget extends YACL's `AbstractWidget` directly (the same base `LabelControllerElement` uses) and self-sizes to 56 px via `setDimension(getDimension().withHeight(56))` in its constructor.
- Drawing, per frame:
  1. `fill` a plate inset 4 px from the row: `0x40000000` background, then `outline(...)` in `0x30FFFFFF`. Nothing site-colored here — the tag inside supplies all the color.
  2. `pose().pushMatrix(); pose().translate(x, y); pose().scale(2f, 2f);` then `text(font, tagComponent, 0, 0, 0xFFFFFFFF, true)` and `popMatrix()`. Drawing at 2× is what makes the 8×8 gamemode glyphs readable; the tag text keeps its per-segment colors because they live in the `Style`.
  3. The player name is appended to the tag as `Component.translatable("justtiers.preview.player")` so the preview reads `[⛏HT2 …] Steve`, matching what the mixin produces in world.
  4. The caption line beneath, in `0xA0A0A0`, from `PreviewSample.caption(...)`: `justtiers.preview.sample`, `justtiers.preview.fallback`, `justtiers.preview.off` or `justtiers.preview.empty`.
- When `enabled` is false, draw the tag at 40 % alpha (multiply the segment colors through `AbstractWidget#multiplyColor`) and swap in the "turned off" caption. The preview never goes blank — a blank preview looks like a bug.
- The widget is inert: `mouseClicked` returns false, `canReset()` returns false, narration reports the caption text.

- [ ] **Step 1: Define `PreviewState`**

```java
public record PreviewState(boolean enabled,
                           DisplayMode displayMode,
                           Map<Source, String> selectedGamemodes,
                           boolean showRetired) {
}
```

Put it in `gui/state/` next to `ControlAvailability` — it holds no Minecraft types.

- [ ] **Step 2: Implement `NametagPreviewController implements Controller<Component>`**

```java
public final class NametagPreviewController implements Controller<Component> {

    private final Option<Component> option;
    private final Supplier<PreviewState> state;

    @Override
    public Component formatValue() {
        PreviewState current = state.get();
        return Segments.toComponent(PreviewSample.segments(
                current.displayMode(), current.selectedGamemodes(), current.showRetired()))
                .append(Component.translatable("justtiers.preview.player"));
    }

    @Override
    public AbstractWidget provideWidget(YACLScreen screen, Dimension<Integer> dim) {
        return new PreviewWidget(screen, dim);
    }
    // …PreviewWidget as described above…
}
```

Build the option with `Option.<Component>createBuilder().name(Component.empty()).binding(Component.empty(), Component::empty, value -> {}).customController(opt -> new NametagPreviewController(opt, stateSupplier)).build()` — a no-op binding, since the preview stores nothing.

- [ ] **Step 3: Verify in game**

Run: `./gradlew runClient`, open the screen.
Expected: the plate shows `[⛏HT2 🚂RHT2 🔱RHT1] Steve` at 2× with a caption underneath; toggling **Show retired** changes it within the same frame; toggling **Show tiers in nametags** off dims it and swaps the caption. Nothing is saved by any of this until Save is pressed.

---

### Task 7: The gamemode grid screen

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/GamemodeGridScreen.java`

**Interfaces:**
- Consumes: `GridLayout`, `Gamemodes`, `PreviewSample`, `Segments`.
- Produces: `new GamemodeGridScreen(Screen parent, Source source, String selectedSlug, Consumer<String> onPick)`.

**Design:**
- `extends Screen`, title `justtiers.grid.title` formatted with the site name and drawn in `Source.color()`.
- Under the title, the same preview the config screen shows — but recomputed for whichever tile the mouse is over, so hovering **Sword** shows what a Sword selection would look like before committing. Falls back to the current selection when nothing is hovered. This is the reason the grid is a screen rather than a dropdown.
- Tiles come from `Gamemodes.of(source)` in registry order. Each tile: `fill` a `0x40000000` panel; on hover, `0x60FFFFFF`; the icon glyph drawn centred at 2× through `pose().scale(2f, 2f)`; the display name centred under it via `centeredText`, truncated to the tile width if the font reports it wider.
- The selected tile gets a 1 px `outline` in `Source.color()` — the only color on the screen besides the title.
- Layout via `GridLayout.of(gamemodes.size(), width - 2 * MARGIN, 72, 72, 8, 6)`, recomputed in `init()` and `repositionElements()`. Origin is `(width - grid.contentWidth()) / 2` horizontally, below the preview vertically.
- If `grid.contentHeight()` exceeds the space, wrap the grid in `enableScissor`/`disableScissor` and offset by a scroll amount driven by `mouseScrolled`, clamped to `[0, contentHeight - viewportHeight]`. Twelve tiles at four columns is three rows, which fits at every supported GUI scale, so scrolling is a safety net rather than the normal path — but implement it, because a 6-column cap on a narrow window can produce four rows.
- `mouseClicked(MouseButtonEvent event, boolean doubleClick)`: only `event.button() == 0`; convert `event.x()`/`event.y()` to grid-local coordinates, `grid.indexAt(...)`, and on a hit call `onPick.accept(slug)`, `playDownSound()`-equivalent click feedback, then `minecraft.setScreen(parent)`. One click, straight back.
- `keyPressed(KeyEvent event)`: arrow keys call `grid.move(...)` on a focused index and redraw; Enter/Space picks it; Escape falls through to `onClose()` which returns to the parent **without** picking.
- `onClose()` → `minecraft.setScreen(parent)`. A `Back` button at the bottom does the same thing, for mouse users who expect one.
- `extractBackground` dims the world behind exactly as the parent YACL screen does, so the transition reads as a layer opening rather than a context switch.

- [ ] **Step 1: Implement the screen**

Skeleton, with the 26.2 signatures already verified:

```java
public final class GamemodeGridScreen extends Screen {

    private static final int TILE = 72;
    private static final int GAP = 8;
    private static final int MARGIN = 24;
    private static final int MAX_COLUMNS = 6;

    private final Screen parent;
    private final Source source;
    private final List<Gamemode> gamemodes;
    private final Consumer<String> onPick;
    private String selectedSlug;
    private GridLayout grid;
    private int originX;
    private int originY;
    private int focusedIndex;
    private int hoveredIndex = -1;

    @Override
    protected void init() {
        this.grid = GridLayout.of(gamemodes.size(), width - 2 * MARGIN, TILE, TILE, GAP, MAX_COLUMNS);
        this.originX = (width - grid.contentWidth()) / 2;
        this.originY = /* below the preview block */;
        addRenderableWidget(Button.builder(
                        Component.translatable("justtiers.grid.back"), b -> onClose())
                .pos(width / 2 - 50, height - 30).size(100, 20).build());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            OptionalInt hit = grid.indexAt((int) event.x() - originX, (int) event.y() - originY);
            if (hit.isPresent()) {
                onPick.accept(gamemodes.get(hit.getAsInt()).slug());
                minecraft.setScreen(parent);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
```

- [ ] **Step 2: Verify in game**

Run: `./gradlew runClient`
Expected: clicking a gamemode row opens the grid; the current gamemode is outlined in the site color; hovering a tile updates the preview at the top; clicking returns immediately with the new value pending; Escape and **Back** return with it unchanged; arrow keys plus Enter do the same as a click.

---

### Task 8: Assemble the YACL screen

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/JustTiersScreens.java`
- Create: `src/main/java/com/w0x7y/justtiers/gui/GamemodePickerController.java`

**Interfaces:**
- Produces: `JustTiersScreens.create(Screen parent)` → `Screen`.

- [ ] **Step 1: `GamemodePickerController implements Controller<String>`**

The row that shows the current gamemode and opens the grid. Extend `ControllerWidget<GamemodePickerController>` so hover, disabled tint, name-column layout and value-column layout all come from YACL and the row is indistinguishable from a native one:

```java
final class PickerWidget extends ControllerWidget<GamemodePickerController> {

    @Override
    protected Component getValueText() {
        Gamemode mode = control.currentGamemode();
        return Component.literal(String.valueOf(mode.icon()) + ' ')
                .append(Component.literal(mode.displayName()));
    }

    @Override
    protected int getValueColor() {
        return isAvailable() ? control.source().color() : inactiveColor;
    }

    @Override
    protected int getHoveredControlWidth() {
        return textRenderer.width(getValueText()) + 8;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isAvailable() || event.button() != 0 || !isMouseOver(event.x(), event.y())) {
            return false;
        }
        playDownSound();
        Minecraft client = Minecraft.getInstance();
        client.setScreen(new GamemodeGridScreen(
                client.screen, control.source(), control.option().pendingValue(),
                slug -> control.option().requestSet(slug)));
        return true;
    }
}
```

`requestSet` is the whole reason this is an `Option<String>` and not a `ButtonOption`: the choice joins YACL's pending state, so Cancel discards it and the preview sees it instantly.

- [ ] **Step 2: `JustTiersScreens.create(Screen parent)`**

Order matters — build the options first, keep references, then wire availability and the preview supplier from those references:

```java
public static Screen create(Screen parent) {
    JustTiersConfig config = JustTiersClient.config();

    Option<Boolean> enabled = Option.<Boolean>createBuilder()
            .name(Component.translatable("justtiers.option.enabled"))
            .description(OptionDescription.createBuilder()
                    .text(Component.translatable("justtiers.option.enabled.desc")).build())
            .binding(true, config::isEnabled, config::setEnabled)
            .controller(TickBoxControllerBuilder::create)
            .build();

    Option<DisplayMode> displayMode = Option.<DisplayMode>createBuilder()
            .name(Component.translatable("justtiers.option.displayMode"))
            .description(/* … */)
            .binding(DisplayMode.ALL, config::getDisplayMode, config::setDisplayMode)
            .controller(opt -> EnumControllerBuilder.create(opt)
                    .enumClass(DisplayMode.class)
                    .formatValue(JustTiersScreens::formatMode))
            .build();

    Option<Boolean> showRetired = /* TickBox, bound to config::isShowRetired */;

    Map<Source, Option<String>> pickers = new EnumMap<>(Source.class);
    for (Source source : Source.values()) {
        pickers.put(source, Option.<String>createBuilder()
                .name(Component.translatable("justtiers.option.gamemode", source.displayName()))
                .binding(config.selectedGamemode(source),
                         () -> config.selectedGamemode(source),
                         slug -> config.setSelectedGamemode(source, slug))
                .customController(opt -> new GamemodePickerController(opt, source))
                .build());
    }

    Runnable syncAvailability = () -> {
        ControlAvailability state = ControlAvailability.of(
                enabled.pendingValue(), displayMode.pendingValue());
        displayMode.setAvailable(state.displayMode());
        showRetired.setAvailable(state.showRetired());
        pickers.forEach((source, option) -> option.setAvailable(state.gamemode(source)));
    };
    enabled.addListener((opt, event) -> syncAvailability.run());
    displayMode.addListener((opt, event) -> syncAvailability.run());
    syncAvailability.run();   // set the initial state before the screen is built

    Option<Component> preview = NametagPreviewController.option(() -> new PreviewState(
            enabled.pendingValue(),
            displayMode.pendingValue(),
            pickers.entrySet().stream().collect(toMap(Map.Entry::getKey,
                    e -> e.getValue().pendingValue())),
            showRetired.pendingValue()));

    return YetAnotherConfigLib.createBuilder()
            .title(Component.translatable("justtiers.config.title"))
            .category(displayCategory(preview, enabled, displayMode, showRetired, pickers))
            .category(dataCategory(config))
            .category(aboutCategory())
            .save(JustTiersClient::saveConfig)
            .build()
            .generateScreen(parent);
}
```

`formatMode` maps each `DisplayMode` to `Component.translatable("justtiers.mode." + mode.id())` and colors the three single-site entries with their `Source.color()`, leaving `all` white — the display-mode row is where the color legend is taught.

The greyed pickers' descriptions come from `ControlAvailability.Reason`: `MODE_IS_ALL` → `justtiers.option.gamemode.inactive`, `MOD_DISABLED` → `justtiers.option.gamemode.disabled`, `OTHER_SITE` → the inactive text as well (switching mode is the fix in both cases).

- [ ] **Step 3: The Data and About categories**

`dataCategory`: the `novaRefreshMinutes` slider (`IntegerSliderControllerBuilder`, range 5–1440, step 5, formatted as `"%d min"`), the refresh `ButtonOption` (`JustTiersClient.cache().invalidateAll()` + `novaSource().refresh()`), and a `LabelOption` reporting `justtiers.data.indexed` with `JustTiersClient.novaSource().indexedPlayerCount()`.

`aboutCategory`: `LabelOption`s for the version, the three site names each in their own color, and the `/justtiers` pointer.

- [ ] **Step 4: Verify in game**

Run: `./gradlew runClient`
Expected: switching **Display mode** to `all` greys all three gamemode rows in the same frame, with the explanatory description; switching to `subtiers_only` re-enables exactly one; turning the master switch off greys everything below it; **Cancel** discards every pending change including gamemodes picked in the grid; **Save** writes `config/justtiers.json` once.

---

### Task 9: Entry points — keybind, command, ModMenu

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/JustTiersKeybinds.java`
- Create: `src/main/java/com/w0x7y/justtiers/gui/ModMenuIntegration.java`
- Modify: `JustTiersClient.java`, `JustTiersCommands.java`

- [ ] **Step 1: Keybind**

```java
public final class JustTiersKeybinds {

    private static KeyMapping openConfig;

    public static void register() {
        openConfig = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.justtiers.open_config",
                InputConstants.Type.KEYSYM,
                InputConstants.UNKNOWN.getValue(),   // unbound by default — no conflicts
                "key.categories.justtiers"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfig.consumeClick()) {
                client.setScreen(JustTiersScreens.create(client.screen));
            }
        });
    }
}
```

Unbound by default is deliberate: a client mod that steals a key on first launch is a nuisance, and the command and ModMenu both reach the same screen.

- [ ] **Step 2: `/justtiers gui`**

A command executes while the chat screen is still closing, so setting a screen inside the command body is immediately overwritten by `setScreen(null)`. Queue it for the next tick instead:

```java
.then(literal("gui").executes(context -> {
    Minecraft client = Minecraft.getInstance();
    ClientTickEvents.END_CLIENT_TICK.register(new ClientTickEvents.EndTick() {
        @Override public void onEndTick(Minecraft c) { /* one-shot: open then unregister */ }
    });
    return 1;
}))
```

Fabric event listeners cannot unregister, so use a one-shot flag instead: a `static volatile boolean openRequested` in `JustTiersKeybinds`, set by the command and consumed by the existing `END_CLIENT_TICK` handler registered in Step 1. One handler, no leak.

- [ ] **Step 3: ModMenu**

```java
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return JustTiersScreens::create;
    }
}
```

Only loaded when ModMenu is present, because entrypoints for absent mods are never constructed.

- [ ] **Step 4: Call `JustTiersKeybinds.register()` from `JustTiersClient.onInitializeClient`**

Place it beside `JustTiersCommands.register()`.

- [ ] **Step 5: Verify**

Expected: `/justtiers gui` opens the screen from in-game and the chat screen does not fight it; binding a key in Options → Controls under **Just-Tiers** opens it too; with ModMenu installed the mod list shows a config button; with ModMenu absent the game still starts cleanly.

---

### Task 10: Make `novaRefreshMinutes` take effect without a restart

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/JustTiersClient.java`

Today the refresh timer is scheduled once at startup with `scheduleWithFixedDelay`, which is why the README says this key needs a restart. A GUI slider that silently does nothing until next launch is worse than no slider, so reschedule on save.

- [ ] **Step 1: Hold the scheduler and the current task**

Keep the `ScheduledExecutorService` in a field alongside a `ScheduledFuture<?>`; extract the existing refresh body into `private static void scheduleNovaRefresh(int minutes)` which cancels the outstanding future (`cancel(false)` — never interrupt a download in flight) and schedules a new one at the new interval.

- [ ] **Step 2: Reschedule from `saveConfig`**

```java
public static void saveConfig() {
    config.save(configPath);
    scheduleNovaRefresh(config.getNovaRefreshMinutes());
}
```

`saveConfig` is already what every command calls, so commands get the same fix for free.

- [ ] **Step 3: Verify**

Expected: changing the slider and pressing Save logs the new interval and does not restart the mod; the clamp to 5–1440 still holds because `setNovaRefreshMinutes` performs it and the slider range matches.

- [ ] **Step 4: Update the README claim**

The Configuration section currently states `novaRefreshMinutes` is "the one setting with no command; edit the file and restart the game". After this task that is false twice over. Rewrite it in Task 11.

---

### Task 11: Documentation

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Add a Configuration screen section**

Cover: how to open it (keybind, `/justtiers gui`, ModMenu), that YACL is now a required dependency with a link, the preview, the greying rule, and the gamemode grid. One screenshot placeholder per screen.

- [ ] **Step 2: Add YACL to Requirements and Installation**

A new row in the requirements table and a step 3 in the install list ("Download YetAnotherConfigLib for 26.2 and put it in your `mods` folder"). Add YACL and ModMenu to the dependency-licence table (YACL is LGPL-3.0 — verify against the jar's licence field before publishing; it is linked at runtime, not redistributed).

- [ ] **Step 3: Correct the `novaRefreshMinutes` paragraph**

It now has a GUI control and applies without a restart.

- [ ] **Step 4: Note the commands still work**

The command surface is unchanged, and the GUI writes the same `config/justtiers.json`.

---

## Verification Checklist

Run before calling the GUI done:

- [ ] `./gradlew build` succeeds; 12 test classes, 112 tests (the existing 89 plus PreviewSample 8, ControlAvailability 5, GridLayout 10).
- [ ] The screen opens from all three entry points, and from the pause menu route ModMenu provides.
- [ ] Every option is visible in every state; none is ever removed from the list. Switching between all four display modes only changes what is greyed.
- [ ] A greyed gamemode row still displays its stored gamemode, and its description explains why it is inert.
- [ ] The preview updates in the same frame as the control that changed it, for `enabled`, `displayMode`, `showRetired` and every gamemode pick.
- [ ] The preview's retired entries disappear when **Show retired** is turned off, and the remaining tiers are the best *active* ones — not blanks.
- [ ] Selecting a gamemode the sample player is unranked in shows the fallback caption naming both the gamemode and the site.
- [ ] The grid opens on click, shows all of that site's gamemodes with icons, outlines the current one in the site color, previews on hover, and returns on a single click.
- [ ] Escape and **Back** leave the grid without changing the selection.
- [ ] Arrow keys move the grid focus, Enter selects, and focus never lands on an empty trailing cell.
- [ ] **Cancel** discards every pending change, including gamemodes picked in the grid. **Save** writes the file exactly once.
- [ ] Color appears only where the rule allows: display-mode value, gamemode picker value, grid title, selected tile border, and tier text inside the preview. Everything else is neutral.
- [ ] At GUI scales 1–4 and window widths down to 854 px the grid reflows without clipping, and the config rows do not overlap.
- [ ] Removing YACL from the mods folder produces Fabric's normal missing-dependency screen, not a crash inside Just-Tiers.
- [ ] With ModMenu absent, the game starts and every other entry point still works.

---

## Known Constraints and Follow-ups

Deliberately out of scope, recorded so they are not mistaken for oversights:

- **YACL is a hard dependency.** The user chose a config library over hand-rolled widgets, so the config screen cannot exist without it. Users must install one extra jar, and a YACL breaking change is a Just-Tiers breaking change. Nothing else in the mod depends on it: nametags, commands and lookups all work identically if the screen is never opened.
- **YACL's chrome is YACL's.** Tab strip, search box, Save/Cancel/Undo bar, row metrics and the description panel look the way YACL draws them. The parts this plan controls are the preview, the picker rows and the grid screen. If the result is not "modern" enough, the remaining lever is a custom screen, not more YACL configuration.
- **The preview player is invented.** Fixed sample data, chosen to exercise every switch. It deliberately does not fetch the local player's real tiers, so the screen makes no network request and behaves identically offline, on any account, at any time.
- **Gamemode tiles use the existing 8×8 bitmap glyphs**, drawn at 2×. They will look like upscaled 8×8 art, because they are. Higher-resolution tile art would mean a second texture set and a second font provider, and is a separate piece of work.
- **The grid has no search or sort.** Twelve tiles is the largest any site has; a filter box would be furniture. Revisit if a site passes ~24 gamemodes.
- **Availability is computed from pending values only.** An external change to the config while the screen is open (a `/justtiers` command run from another source) is not reflected until the screen is reopened. Nothing in the mod does this today.
