# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A client-side Minecraft Fabric mod that puts a player's competitive PvP tier in their nametag,
reading three leaderboards at once (MCTiers, SubTiers, NovaTiers). Minecraft 26.2, Fabric Loader
0.19+, Java 25. Requires YetAnotherConfigLib at runtime; ModMenu is compile-only and optional.

## Commands

```bash
./gradlew build          # tests plus the mod jar, the same thing CI runs
./gradlew test           # unit tests only
./gradlew runClient      # dev client
./gradlew test --tests 'com.w0x7y.justtiers.render.model.BadgeTest'
./gradlew test --tests 'com.w0x7y.justtiers.cache.TierCacheTest.theGateIsVisibleOnceItHasGivenUpOnASite'
python3 tools/gen_font_provider.py    # rewrites assets/justtiers/font/icons.json
MODRINTH_TOKEN=... ./gradlew modrinth -Pmodrinth_dry_run=true   # publish path, no upload
```

Minecraft 26.2 ships unobfuscated, so Loom registers no `remapJar`. `build/libs/*.jar` (minus the
sources jar) is the shippable mod jar, not a dev-only artifact.

Releasing is a tag: `git tag v<version> && git push origin v<version>`. The workflow fails if the
tag disagrees with `mod_version` in `gradle.properties`. `modrinthSyncBody` overwrites the Modrinth
listing irreversibly and is deliberately kept out of the release job.

## The core rule: Minecraft-free logic, thin Minecraft shell

As much as possible is written without Minecraft types so it can be unit-tested without launching
the game. That covers the parsers, the cache, the resolver, the whole badge pipeline, the config,
the palette rule and the screens' geometry. New logic belongs on that side of the line.

The seams that keep it there:

- `render/model/Badge` is the only way a badge gets built. It takes tiers plus a style and returns
  finished segments with the side of the name they belong on. Do not reassemble that sequence in a
  caller.
- `render/model/TierView` is everything the nametag path may know about the running mod.
  `render/LiveTierView` is the single implementation that reads the real config and cache; tests
  hand in their own (`FakeTierView`).
- `render/Nametags` is the only place Minecraft-free `Segment`s become a `Component`.
- `config/Palette` holds site color rules in ints and never learns the file format. Read a site's
  color through `JustTiersConfig.colorOf`, never `Source.defaultColor()`, or the screen silently
  ignores the user's palette.
- `gui/layout/` (`GridLayout`, `SkinLayout`, `LookupLayout`, `CreditLine`, `ProgressBarLayout`) owns
  where things sit. `PlayerLookupScreen` keeps no coordinates of its own.

## Data flow

`api/TierSource` implementations fetch one player from one site, `cache/TierCache` holds the answers,
`resolve/TierResolver` picks which tiers to show, `render/model/Badge` lays them out, and
`render/Nametags` turns them into a `Component`.

`TierCache.peek` never blocks and never starts a lookup, because it runs per player per frame. A
miss schedules a background fetch and reports "not yet known" until it lands, so a nametag gains its
badge site by site as answers arrive.

Failure semantics are load-bearing and easy to break:

- HTTP 404 from MCTiers or SubTiers means "answered, genuinely unranked", and is cached.
- Any other status or a transport failure means the lookup did not complete, and is never cached as
  unranked. It retries behind two independent delays: `cache/Backoff` per player (doubling, with
  jitter) and `cache/SiteGate` per site (a circuit breaker after eight consecutive failures).
- NovaTiers has no per-player route, so `NovaTiersSource` downloads the whole list (~1.7 MB) and
  indexes it by UUID. A failed refresh keeps the index already in memory rather than blanking it.

The only mixin is `PlayerMixin`, a MixinExtras `@ModifyReturnValue` on `Player.getDisplayName`. That
reaches the in-world nametag only. The tab list and chat go through other paths and are untouched.

## The icon font

Gamemode glyphs live in Just-Tiers' own font `justtiers:icons` at `U+E101..`, `U+E201..`, `U+E301..`,
deliberately not in `minecraft:default` (overriding a vanilla file loses to pack order). A private
font inherits no vanilla fallbacks, so anything drawn in it that is not one of the 32 glyphs renders
as a missing-glyph box. Two rules follow:

- `render/Icons` is the only class that names the font, and only ever wraps the single glyph
  character. A component appended to an icon component inherits the icon font, so build a glyph and
  a label as two children of `Component.empty()`.
- `Segment` carries an `icon` flag. Recolor with `Segment.withColor` or `Badge.recolor`, which keep
  it. The two-argument `Segment` constructor silently turns an icon back into text.

Adding a gamemode means three things stay in sync: the registry in `tier/Gamemodes.java`, the
codepoint list in `tools/gen_font_provider.py`, and the texture under
`assets/justtiers/textures/<site>/`. Run the script after touching either of the first two.

## Config

`config/justtiers.json`, written by both the commands and the config screen. `IdEnumAdapter` writes
enum ids in lower case and reads them case-insensitively, falling back with a logged warning.
Out-of-range or unrecognised values are corrected on load, never rejected, so an old or hand-edited
file still loads.

The config screen is built on YACL in `gui/JustTiersScreens`. Two rules it enforces: options that
cannot do anything useful are greyed rather than removed, so the screen never changes shape, and
nothing is written until Save. `gui/state/ControlAvailability` holds the greying rule and is tested.

User-facing strings go through `assets/justtiers/lang/en_us.json`. The one exception is
`debug/DebugReport`, which is untranslated and formats in `Locale.ROOT` on purpose: its reader is
whoever fixes the bug, not the player who ran the command.

## Conventions

- Test methods are sentence-shaped (`aRankedPlayerGetsTheirSelectedGamemode`). Test classes carry a
  Javadoc saying what behaviour they pin down, not what class they cover.
- Comments explain why a thing is the way it is, at some length, including alternatives that were
  rejected. Match that when editing.
- README.md and Modrinth/description.md have had every em dash removed. Keep them out.
- `docs/adr/` records decisions deliberately not taken. Before "simplifying" `TierCache`'s five
  observability accessors, read ADR 0001: they are the test surface for its asynchronous behaviour,
  and folding them into one `diagnostics()` call would make `cache/` depend on `debug/`.
- `docs/superpowers/plans/` and `specs/` hold the design documents features were built from.
  `docs/list_of_ideas.txt` is the backlog.
