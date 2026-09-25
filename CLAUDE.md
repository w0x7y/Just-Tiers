# Working on Just-Tiers

Just-Tiers is a client-side Fabric mod with per-version builds. Read `gradle/targets/`
for supported Minecraft versions, compatibility strategies and dependency pins, and `gradle.properties` for the
default target and mod version. Fabric API and YACL are required; ModMenu is optional.
Read `README.md` for current commands and behavior.
Read `CONTEXT.md` for the domain terms used in source and tests.

## Build and verification

Install a JDK and expose `java` on PATH or set `JAVA_HOME` before invoking the wrapper.
JDK 25 is the supported development setup. The toolchain resolver cannot start Gradle
when no Java runtime is available.

```bash
./gradlew build
./gradlew test
./gradlew test --tests 'com.w0x7y.justtiers.resources.ResourceContractTest'
./gradlew test --tests 'com.w0x7y.justtiers.cache.TierCacheTest'
./gradlew runClient
python3 tools/gen_font_provider.py
python3 -m unittest discover -s tools/tests -v
python3 tools/minecraft_targets.py list
```

`build` runs tests, resource contracts and packaging. Select a version with
`-Pminecraft_version=<version>`. Installable JARs are in `build/<version>/libs/`;
1.21.11 uses `remapJar`, while 26.x uses `jar`. Edit authored sources in `src/main/java`.
Mechanical API renames live in `gradle/compatibility.gradle.kts`; generated sources are
build outputs. When changing integration APIs, build every target and run
`python3 tools/verify_artifact.py <version>`. See `docs/minecraft-compatibility.md`.
A successful build proves compilation and tested model behavior,
not runtime mixin application, rendering, keyboard navigation or narration. Follow
[docs/runtime-smoke-test.md](docs/runtime-smoke-test.md) when changing the client UI.

Workflows use Java 25, validate the wrapper and run Linux/Windows builds with retained test reports. A separate CI job runs
actionlint. Keep Loom pinned to a tested version so tagged builds resolve consistently.

## Minecraft-free logic and thin integration

Keep parsing, state transitions, tier selection, badge composition and layout arithmetic
free of Minecraft types. The game-facing classes adapt those decisions to widgets,
components, commands and events.

- `render/model/Badge` owns badge construction and position. Callers render the result.
- `render/model/TierView` is the nametag's access to settings and tier answers.
  `render/LiveTierView` reads the live config/cache; model tests provide their own view.
- `render/Nametags` converts segments to Minecraft components.
- `config/Palette` owns color rules in ints. Read active colors through
  `JustTiersConfig.colorOf`, not `Source.defaultColor`, when rendering user-facing UI.
- `gui/layout/` owns geometry. Screens own focus, widgets, scrolling and event routing.
  Test viewport bounds with actual registry counts and small GUI dimensions.
- `lookup/LookupReport.section` builds one site's lookup state. The lookup includes
  retired tiers independently of nametag settings. Pending and unavailable are distinct
  from a valid unranked answer.
- `lookup/LookupSession` owns online identity preference, remote resolution, concurrent
  site requests, partial answers and failed-entry eviction for explicit retry.
  `gui/MinecraftLookupSession` supplies client scheduling, translations and skins.
  Publish session state through its owning executor and read it on that same thread.

`PlayerMixin` decorates `Player.getDisplayName` for world nametags. Tab-list and chat
rendering use different paths and are outside the current feature.

## Network and asynchronous state

TierSource fetches one site's result; TierCache coordinates requests; TierResolver selects
placements; Badge builds display segments. `peek` never blocks. A miss can start an
asynchronous fetch and returns unknown until a completed result is available.

Preserve these distinctions when changing API or cache code:

- MCTiers/SubTiers HTTP 404 and valid empty payloads mean unranked and may be cached.
- Unexpected statuses, transport failures and malformed or wholly unrecognized payloads
  fail the lookup. A malformed HTTP 200 is not a successful empty answer.
- The per-player Backoff and per-site SiteGate limit retries independently.
- Publish a completed result after its bookkeeping is settled. Never infer that joining
  a future is safe merely because an earlier exceptional-state check returned false.
- Invalidation changes the site's generation. Old requests may update diagnostic history,
  but cannot restore retry delays or gate state cleared by that invalidation.
- NovaTiers uses a bulk index. Initial loads and refreshes share an in-flight download.
  `refresh()` reports failure while retaining a previously usable index. Callers must
  invalidate dependent entries only after a successful refresh.
- DownloadProgress tokens prevent callbacks from an older download changing a newer
  indicator. Its percentage estimates from the last completed payload size.

Read [ADR 0001](docs/adr/0001-tiercache-keeps-its-own-observability.md) before replacing
TierCache's observability accessors. They support tests and keep cache independent of debug.
Use controllable futures and clocks to test races and expiration without sleeping.

## Config and UI

Settings live in `config/justtiers.json`. Enum IDs save in lowercase and read without case
sensitivity. Missing values use defaults; invalid values are corrected on load.

Configuration screens edit a draft. Persist successfully before swapping it into active
settings. Write through a temporary sibling and replacement so a failed write preserves
the old file. Report failures to the player and retain a retry path. Settings commands
retain their changes in the current session when a save fails and explicitly warn that
the change was not saved.
Refresh is an immediate action, not a pending setting.

`settings/SettingsApplication` owns active settings and both publication policies.
Use `draft`/`commitDraft` for screens and `editSession` for commands; do not mutate
`active()` directly. `settings/RefreshLifecycle` owns refresh scheduling and cache
invalidation. Saving an unchanged interval must preserve the existing countdown.
Test these policies with real temporary config files and TierCache, using a controlled
timer rather than sleeping.

Keep unavailable controls visible with an accurate explanation. Use focusable controls
for selections and links, and let Enter/Space activate the focused widget. A grid's
keyboard selection must not intercept Back. Search and exit controls must remain reachable
at 320 by 240 GUI pixels; scroll the result content when it does not fit.

Player-facing strings belong in `assets/justtiers/lang/en_us.json`. DebugReport is the
exception: it deliberately uses English and Locale.ROOT for bug reports. ResourceContractTest
checks literal keys, checkbox descriptions and the mode/badge/palette enum families. Extend
its explicit expansion when adding another dynamically assembled translation family.

## Icon assets

Gamemode glyphs live in `justtiers:icons`, not `minecraft:default`. `render/Icons` is the
only place that names this font, and applies it only to the single glyph. Put the glyph
and its text label under separate children of Component.empty so labels retain a text font.
Preserve Segment.icon when recoloring with Segment.withColor or Badge.recolor.

Adding a gamemode updates the Java registry, tools/gen_font_provider.py and its texture.
Run the generator, then `./gradlew check`; the resource contract checks glyph identities,
provider counts and readable packaged textures. The generator binds existing artwork.
The old NovaTiers artwork generator was removed because it overwrote the current artwork.

## Documentation and releases

Keep README, the Modrinth description and player-facing copy aligned with actual behavior.
Network documentation must include Mojang profile/skin traffic, manual retry exceptions and
NovaTiers downloads continuing while nametag display is disabled. Avoid claims that a cache
interval is an absolute request cap or that a palette works for every visual condition.
README and Modrinth description use no em dashes.

August documents in docs/superpowers are archived decisions or deferred proposals. They
are not runnable implementation instructions. The lobby scan proposal is not implemented.
Read the current source and tests before reviving an archived design.

A release tag v<version> must match mod_version. The release job builds and tests, publishes
the GitHub JAR first, then uploads to Modrinth. Alpha/beta release_type values mark GitHub
prereleases; release marks a regular release. Dry-run publishing is available through the
manual workflow or `MODRINTH_TOKEN=... ./gradlew modrinth -Pmodrinth_dry_run=true`.
modrinthSyncBody overwrites the live listing and is deliberately outside the release job.
Use the manual Sync Modrinth description workflow when a listing update is requested.
Release notes use docs/releases/v<version>.md when available, otherwise commit summaries.
