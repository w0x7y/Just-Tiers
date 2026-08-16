# Per-Site Colors and Hiding Your Own Badge — Design

**Goal:** Two appearance settings the mod has no answer for today. **Hide my own badge**
stops Just-Tiers decorating your own nametag. **Per-site colors** lets the three
leaderboard colors be changed — from a short list of palettes, or by picking three
colors outright — so that a colorblind player, or one whose HUD already uses yellow and
cyan for something else, can still tell the sites apart.

**Architecture:** One facade, `SiteColors`, becomes the single answer to "what color is
this site", and every existing call site routes through it. The palettes themselves are a
Minecraft-free enum with the arithmetic and fallbacks unit-tested. `Source` keeps its
colors as the defaults and nothing else.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API
`0.157.0+26.2`, YetAnotherConfigLib 3.9.4, JUnit 5. No new dependencies.

---

## Global Constraints

- **Color still carries exactly one meaning.** A site's color changes everywhere at
  once — nametag, lookup screen, scan screen, gamemode grid, config previews — or the
  rule the whole UI is built on stops being true. There is no per-screen override.
- **`config`, `tier`, `render.model`, `gui.state` stay Minecraft-free** and unit-tested.
  `Palette` and `SiteColors` live in Minecraft-free packages.
- **Old config files load unchanged.** Both keys are optional; a file written before this
  change produces exactly today's behaviour. Bad values are corrected on load with a
  logged warning, never rejected — the rule `displayMode` and `badgePosition` follow.
- **The render path stays allocation-free per frame.** `SiteColors.of` is a map read, not
  a parse: hex strings are decoded once when the config loads or is saved.
- **Nothing is hidden on the config screen, only greyed** — the existing rule.
- **No change to which tiers are shown.** Both settings are cosmetic; neither touches the
  resolver, the cache or any request.

---

## Hiding Your Own Badge

`hideOwnBadge`, default `false`. When true, the client player's own nametag is left
undecorated.

The check lives in `PlayerMixin`, which already holds the entity and can compare it with
`Minecraft.getInstance().player` directly — cheaper and plainer than handing
`NametagRenderer` a UUID to compare against. `NametagRenderer.decorate` is unchanged.

**Nametag only.** `/justtiers lookup <yourself>` and `/justtiers scan` still show you:
this setting is about not cluttering your own tag in third person, not about hiding
yourself from yourself. A scan that silently omitted you would be a bug, not a preference.

Command: `/justtiers ownbadge`, toggling like the existing `icons` and `brackets`.

---

## Per-Site Colors

### The palettes

`config/Palette.java`, a Minecraft-free enum. Each palette answers all three sites.

| Palette | `id` | MCTiers | SubTiers | NovaTiers |
|---|---|---|---|---|
| Default | `default` | `#FFFF55` yellow | `#55FFFF` cyan | `#AA55FF` purple |
| Colorblind-safe | `colorblind` | `#E69F00` orange | `#56B4E9` sky blue | `#FFFFFF` white |
| High contrast | `high_contrast` | `#FFFFFF` white | `#FFAA00` amber | `#00FFFF` cyan |
| Custom | `custom` | from `customColors` | | |

**One colorblind preset, not several.** The trio above is derived from the Okabe-Ito
palette and separates by luminance as well as by hue, so it survives red-green blindness —
protanopia and deuteranopia, which together account for the overwhelming majority of
color vision deficiency — and tritanopia alike. Two near-identical presets, one labelled
for a condition affecting roughly one person in ten thousand, would be a worse answer than
one that works for everybody.

**Default is unchanged.** Every existing user sees exactly what they see today.

### Where the answer comes from

The colors are configuration, so the config object owns resolving them:

```java
// JustTiersConfig — Minecraft-free, and therefore unit-testable
public int colorOf(Source source)                 // RGB triple, as Source.color() returned
public Map<Source, Integer> colors()              // all three, for callers that want them at once
```

Screens read it through a one-line facade so call sites stay short:

```java
// com.w0x7y.justtiers.render.SiteColors
public static int of(Source source) { return JustTiersClient.config().colorOf(source); }
```

**`NametagModel` must not use either.** It is Minecraft-free *and* unit-tested, and
reaching `JustTiersClient` from it would make it neither. It already receives a
`NametagStyle`, so that record gains the colors:

```java
public record NametagStyle(BadgePosition position, boolean showIcons, boolean showBrackets,
                           Map<Source, Integer> colors)
```

`config.nametagStyle()` fills them in, `NametagModel` reads `style.colors()` when building
a segment, and its tests pass whatever colors they want to assert on — which is a better
test than the one it has today, where the expected color is a constant on `Source`.

The remaining twelve call sites — in `Segments`, `JustTiersScreens`,
`GamemodePickerController`, `GamemodeGridScreen`, `NametagPreviewController`,
`PlayerLookupScreen` and `ScanScreen` — go through `SiteColors.of`.

`Source.color()` is **renamed `Source.defaultColor()`**. The rename is the point: it makes
every remaining call site read as "the fallback", so a future one cannot quietly bypass
the user's choice by reaching for the obvious-looking method. After this change its only
callers are `Palette.DEFAULT` and the per-site fallback for a malformed hex.

### Storage

```json
"hideOwnBadge": false,
"palette": "default",
"customColors": { "MCTIERS": "#FFFF55", "SUBTIERS": "#55FFFF", "NOVATIERS": "#AA55FF" }
```

- `palette` is persisted by the existing `IdEnumAdapter`, so it is written lower-case,
  read case-insensitively, and falls back to `default` with a warning when unrecognised —
  identical to `displayMode`.
- `customColors` is a `Map<String, String>` keyed by `Source.name()`, matching how
  `selectedGamemodes` is already stored. It is only consulted when `palette` is `custom`.
- A missing, malformed or out-of-range hex falls back to **that site's default color**,
  per site rather than for the whole map: two good colors and one typo should cost the
  typo, not the other two.
- Accepted spellings: `#RRGGBB` and `RRGGBB`, case-insensitive. Alpha is not accepted —
  every consumer supplies its own.
- Decoded once, on load and on save, into an `EnumMap<Source, Integer>` held transiently.
  The render path reads that map and never parses a string.

---

## Config Screen

The **Appearance** group in the Display category gains:

- **Hide my own badge** — a tick box, beside Show gamemode icons and Show brackets.
- **Color palette** — a cycling control over the four palettes.
- **MCTiers / SubTiers / NovaTiers color** — three YACL `ColorControllerBuilder` pickers,
  greyed unless the palette is Custom, each carrying a description saying so.

`ControlAvailability` grows one field, `customColors`, true only when the mod is enabled
*and* the pending palette is Custom. The three pickers read it. This keeps the greying
rule in the tested, Minecraft-free class where the rest of it already lives.

The live nametag preview picks up both settings immediately. Switching palettes recolors
the preview in place, which is the whole point of previewing: a palette is chosen by
looking at it.

Selecting a preset does **not** overwrite `customColors`. Switching to Custom and back
leaves the custom colors untouched, so the two are not a trap for each other.

---

## Commands

| Command | Effect |
|---|---|
| `/justtiers ownbadge` | Toggles hiding your own badge |
| `/justtiers palette <id>` | Sets a palette, tab-completing the four ids |

`/justtiers palette custom` is accepted and switches to whatever custom colors are
stored, but the colors themselves are set only on the config screen. Three hex codes
typed into chat is not an interface anybody wants, and a color is chosen by looking at
it, which chat cannot do.

`/justtiers` status output gains a line naming the current palette.

---

## Testing

Minecraft-free, in the existing layout:

- **`PaletteTest`** — every palette answers all three sites; every preset's three colors
  are distinct; ids round-trip; `custom` reads the supplied map.
- **`SiteColorsTest`** — a preset ignores `customColors`; `custom` uses them; a malformed
  hex falls back for that site alone and leaves its neighbours intact; both `#RRGGBB` and
  `RRGGBB` parse; an out-of-range or empty value falls back.
- **`JustTiersConfigTest`** — a file with neither new key loads as today; an unrecognised
  palette falls back to `default`; a malformed hex is corrected on load; both keys
  round-trip through save and load.
- **`ControlAvailabilityTest`** — the pickers are live only when enabled and Custom.
- **`NametagModelTest`** — segments carry the colors their style was given, not
  `Source`'s constants.
- **Existing tests** asserting on `Source.color()` are updated to `defaultColor()`.

Manual verification: switch palettes on the config screen and watch the preview, the
lookup screen and the scan screen all follow; toggle Hide my own badge in third person.

---

## Out of Scope

- Per-screen or per-mode color overrides.
- Coloring anything other than the three sites — tier text, icons, brackets and the
  progress bar are unchanged.
- Alpha or gradient support.
- Setting custom colors from chat.
- The badge-scale idea, which needs the nametag render path replaced rather than the
  `getDisplayName` hook and is not attempted here.
