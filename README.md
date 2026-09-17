# Just-Tiers

[![Build](https://github.com/w0x7y/Just-Tiers/actions/workflows/build.yml/badge.svg)](https://github.com/w0x7y/Just-Tiers/actions/workflows/build.yml)

Just-Tiers is a client-side Fabric mod that adds competitive PvP tiers to Minecraft
nametags using [MCTiers](https://mctiers.com), [SubTiers](https://subtiers.net) and
[NovaTiers](https://novatiers.com). The mod is in beta.

Version 1.1.2 adds safer saves, more reliable lookups, keyboard navigation and small-window
scrolling. See the [release notes](docs/releases/v1.1.2.md).

## What it shows

Choose one leaderboard or show each site's best tier side by side. In a single-site
mode, the mod prefers your selected gamemode and falls back to that player's best tier
on the same site. Icons identify the gamemode; colors identify the leaderboard.

Tiers run from LT5 through HT5, LT4, HT4, LT3, HT3, LT2, HT2 and LT1 to HT1. Retired
placements have an R prefix, such as RHT1. They compete by rank unless you hide retired
tiers, in which case the mod falls back to active placements. Peak tiers are ignored.

A nametag fills in as sites answer. Network requests run asynchronously; a missing answer
leaves that site's part of the badge absent. The tab list and chat are unchanged.

| Mode | Nametag behavior |
|---|---|
| `all`, the default | Best known tier from each site in MCTiers, SubTiers, NovaTiers order |
| `mctiers_only` | Selected MCTiers gamemode, falling back to the best on that site |
| `subtiers_only` | Same rule for SubTiers |
| `novatiers_only` | Same rule for NovaTiers |

## Install

Use Minecraft 26.2 with Java 25, Fabric Loader 0.19 or newer, and versions of
[Fabric API](https://modrinth.com/mod/fabric-api) and
[YetAnotherConfigLib](https://modrinth.com/mod/yacl) built for Minecraft 26.2.
YACL 3.9.4 or newer is required. [ModMenu](https://modrinth.com/mod/modmenu) is optional.
The exact dependency versions used to build this project are in [gradle.properties](gradle.properties).

Put the Just-Tiers mod JAR and its required dependencies in the instance's `mods` folder.
Use the JAR without `-sources` in its filename. Nothing needs installing on the server.
The metadata declares Minecraft `~26.2`; compatibility with other release lines has not
been established.

## Commands

All commands are client-side. `/justtiers lookup` completes names from the server's tab list.

| Command | Action |
|---|---|
| `/justtiers` | Show current settings, per-site gamemodes and NovaTiers index size |
| `/justtiers gui` | Open configuration |
| `/justtiers lookup <player>` | Open a screen with all three sites' placements |
| `/justtiers toggle` | Toggle nametag tiers |
| `/justtiers retired` | Toggle retired tiers in nametags |
| `/justtiers mode <mode>` | Choose a mode from the table above |
| `/justtiers gamemode <slug>` | Choose a gamemode for the current single-site mode |
| `/justtiers badge <before\|after>` | Move the badge before or after the name |
| `/justtiers icons` | Toggle gamemode icons in nametags |
| `/justtiers brackets` | Toggle badge brackets |
| `/justtiers ownbadge` | Toggle hiding your own badge |
| `/justtiers palette <palette>` | Choose `default`, `colorblind`, `high_contrast` or `custom` |
| `/justtiers refresh` | Clear lookup caches and request a fresh NovaTiers list |
| `/justtiers debug` | Print diagnostics and copy them to the clipboard |

## Looking a player up

`/justtiers lookup Notch` opens a screen with the player's skin and one row per leaderboard.
Every known gamemode has a cell. A dash means no placement was reported for that gamemode;
it does not establish whether the player was ever tested. A waiting or unavailable site
has its own message and is never presented as a row of unranked placements.

The lookup includes retired tiers and all sites regardless of your nametag settings.
Rows update independently. Edit the name or retry on the screen; you do not need to close
it and type the command again. Smaller windows scroll the results while the search and
Done controls remain available. Cells and leaderboard links can receive keyboard focus.
Hover or focus a cell for its gamemode name. Links use Minecraft's browser confirmation.

Online players with account UUIDs resolve locally. Other names, including offline-mode
server identities, are checked through Mojang. A successful name lookup is remembered
for the session. If the profile or skin service fails, the screen keeps a default skin
and the tier lookup can still complete.

## Configuration screen

Open it with `/justtiers gui`, ModMenu, or the unbound-by-default Just-Tiers keybind under
Options > Controls. Display, Data and About categories keep related settings together.
Unavailable controls stay visible and explain why they cannot currently be changed.

The live preview uses invented HT1 tiers and makes no leaderboard request. It alternates
with RHT1 every five seconds while retired tiers are enabled. In All mode it uses fixed
example gamemodes. It previews pending changes, including badge position, icons, brackets,
colors and hiding your own badge.

A gamemode picker opens with a click or keyboard activation. Arrow keys move between tiles;
Enter or Space selects the focused tile. Hovering or focusing a tile previews it. Back or
Escape returns without selecting. Selection stays pending until you save the parent screen.

Save writes settings; Cancel discards pending edits and Undo restores saved values. A failed save
reports the problem and preserves the previously saved file and active settings. A failed
settings command warns that its change applies only to the current session; it does not claim
to have saved. Refresh is an immediate data action, separate from saving settings.

| Palette | MCTiers | SubTiers | NovaTiers |
|---|---|---|---|
| Default | `#FFFF55` yellow | `#55FFFF` cyan | `#AA55FF` purple |
| Color vision alternative | `#E69F00` orange | `#56B4E9` sky blue | `#FFFFFF` white |
| High contrast | `#FFFFFF` white | `#FFAA00` amber | `#00FFFF` cyan |
| Custom | Your selected color | Your selected color | Your selected color |

Alternative palettes offer different hue and brightness contrasts; their suitability depends
on the player and background. Custom color controls are available in the Custom palette.
The chosen colors reach nametags, lookup rows, previews, pickers and the NovaTiers progress bar.
Icon artwork keeps its original colors.

## Config file

Settings are stored in `config/justtiers.json` inside the Minecraft instance. Defaults are:

```json
{
  "enabled": true,
  "showRetired": true,
  "displayMode": "all",
  "selectedGamemodes": {
    "MCTIERS": "vanilla",
    "SUBTIERS": "elytra",
    "NOVATIERS": "vanilla"
  },
  "novaRefreshMinutes": 30,
  "tierCacheMinutes": 60,
  "showDownloadProgress": true,
  "badgePosition": "before",
  "showIcons": true,
  "showBrackets": true,
  "hideOwnBadge": false,
  "palette": "default",
  "customColors": {}
}
```

Enum values are saved in lowercase and read case-insensitively. Missing settings receive
defaults. Unknown selections and out-of-range intervals are corrected when loading.
Both intervals accept 5 to 1440 minutes. Custom colors use per-site `#RRGGBB` strings;
an invalid color falls back for that site. Changing the interval on Save updates the
running scheduler or cache policy without restarting Minecraft.

## Supported gamemodes

Sites are independent competitions. The slugs below are accepted by `/justtiers gamemode`
and stored in the config file. Unknown API gamemodes need a mod update before they can appear.

| Leaderboard | Gamemodes |
|---|---|
| **MCTiers** (8) | Axe (`axe`), Mace (`mace`), Netherite OP (`nethop`), Pot (`pot`), SMP (`smp`), Sword (`sword`), UHC (`uhc`), Vanilla (`vanilla`) |
| **SubTiers** (12) | Bed (`bed`), Bow (`bow`), Creeper (`creeper`), DeBuff (`debuff`), Diamond SMP (`dia_smp`), Diamond Vanilla (`dia_crystal`), Elytra (`elytra`), Manhunt (`manhunt`), Minecart (`minecart`), OG Vanilla (`og_vanilla`), Speed (`speed`), Trident (`trident`) |
| **NovaTiers** (12) | Axe (`axe`), Diamond Cart (`diamondcart`), Diamond OP (`diamondop`), Elytra (`elytra`), Elytra Spear (`elytraspear`), Modern SMP (`modernsmp`), Pufferfish (`pufferfish`), SMP (`smp`), Spear Mace (`spearmace`), Spleef (`spleef`), UHC (`uhc`), Vanilla (`vanilla`) |

## Requests, caching and privacy

The mod uses public leaderboard APIs and Minecraft's profile/skin loading facilities.
There is no mod analytics endpoint. Remote services receive ordinary connection metadata,
including your IP address, in addition to the request data below.

| Service | Request data and purpose |
|---|---|
| `mctiers.com/api/v2/…` | Account UUID for a visible player's nametag or an explicit lookup |
| `subtiers.net/api/v2/…` | The same for SubTiers |
| `novatiers.com/users` | Bulk leaderboard download, without a per-player identifier in the request |
| `api.mojang.com/users/profiles/minecraft/<name>` | Typed name when the tab list cannot provide a usable account UUID |
| Mojang profile/session services and Minecraft skin texture hosts | Account profile and skin requests made through Minecraft while displaying a looked-up player's skin |

The mod's own API requests use a versioned Just-Tiers User-Agent. Minecraft controls its
profile and skin requests and can cache skin data on disk. The mod writes its settings
file and diagnostic logs; `/justtiers debug` also replaces your clipboard with its report.
It does not send chat, inventory or the server address to the leaderboard APIs.

Disabling nametag tiers stops automatic per-player nametag lookups. Explicit lookup screens
still work, and NovaTiers' startup, scheduled and requested downloads remain independent of
that switch. Hiding the progress bar changes only the indicator, not the downloads.

MCTiers and SubTiers answers, including valid unranked answers, expire after an hour by default.
Repeated requests for one player share the pending result. Manual refresh, failed requests
and explicit retries can cause requests before that interval, so it is not a rate-limit guarantee.
NovaTiers is indexed in memory and refreshed every 30 minutes by default; its payload size varies.

HTTP 404 from MCTiers or SubTiers is a valid unranked answer. Unexpected statuses, transport
errors and malformed successful responses are failures, not unranked results. Failed requests
back off per player, with jitter; eight consecutive site failures pause new requests and then
allow a recovery probe. Manual refresh resets current retry state but keeps diagnostic history.
A failed NovaTiers refresh preserves the last usable index. Older requests cannot replace a
newer index or reinstate retry state cleared by a refresh.

The first NovaTiers download in a session shows a moving bar and bytes received. Later downloads
estimate progress from the last completed payload size. That size can change, so the percentage
is an estimate and stops below 100% until completion. A failure shows a brief unavailable message.

## Reporting a bug

Run `/justtiers debug` and paste the copied report into a GitHub issue, together with reproduction
steps, game/mod versions and relevant logs or screenshots. It includes version numbers, cache
counts, retry state, latency and the last error for each site. `PAUSED` means a site's circuit
breaker is waiting before allowing another probe. The report stays in English for bug reports.
A green build cannot establish that a Minecraft screen or mixin works in a running game.

## Building and testing

Install JDK 25 and make `java` available on PATH or set `JAVA_HOME` before running the wrapper.
Gradle's toolchain resolver can provision a compiler after Gradle starts; it cannot start the
wrapper on a machine with no Java runtime.

```bash
git clone https://github.com/w0x7y/Just-Tiers.git
cd Just-Tiers
./gradlew build
./gradlew test
./gradlew runClient
```

On Windows use `gradlew.bat`. `build` runs checks and packages the mod in `build/libs/`.
Minecraft 26.2 is unobfuscated, so the normal JAR is the distributable artifact.
The sources JAR is for developers. The build pins Loom and declares its dependency repositories.

Tests cover parsing, caching, retries, persistence, tier selection, badge composition,
progress state, lookup reports and screen geometry. Resource contract tests also verify the
registry's glyph-to-texture mapping and referenced English translation keys. They run through
`test`, `check` and `build`. Workflow syntax is checked by actionlint in CI.

Run the [runtime smoke checklist](docs/runtime-smoke-test.md) for changes affecting the game
client. It covers keyboard navigation, small windows, Save/Cancel, fonts and failure recovery.

### CI and releases

The [Build workflow](.github/workflows/build.yml) validates the wrapper, lints workflows, runs
`build` on Linux and Windows for pull requests and main pushes, and uploads JARs and test
reports from each platform. Reports are retained for 14 days.
These checks do not launch Minecraft.

To release, update `mod_version` in `gradle.properties`, verify the build and runtime checklist,
then push a matching `v<version>` tag. The release workflow rejects a mismatched tag, builds,
requires workflow lint, creates the GitHub release first, and then uploads to Modrinth. GitHub receives the mod JAR
without the sources JAR. `release_type=alpha` or `beta` creates a GitHub prerelease and the
matching Modrinth version type; `release` creates a regular GitHub release.

Publishing needs a repository `MODRINTH_TOKEN` secret with permission to create versions.
The manual Modrinth dry-run workflow checks payload construction without uploading. Locally:

```bash
MODRINTH_TOKEN=... ./gradlew modrinth -Pmodrinth_dry_run=true
```

The dry run contacts Modrinth and cannot guarantee that a later upload will be accepted.
If Modrinth rejects an upload, the GitHub release remains available. Correct the cause before
rerunning; the GitHub step updates an existing release. The listing body in
[Modrinth/description.md](Modrinth/description.md) is separate. `modrinthSyncBody` overwrites the
live listing and is deliberately absent from release automation. Run the manual
[Sync Modrinth description workflow](.github/workflows/modrinth-description.yml) when a
listing update is intended. Tagged release notes come from `docs/releases/v<version>.md`
when present, otherwise from commits since the previous tag.

## Contributing

Run `./gradlew build` before opening a pull request. Keep decisions and state transitions free
of Minecraft types where practical; keep the game integration thin. See [CLAUDE.md](CLAUDE.md)
for the module boundaries and [docs/adr](docs/adr) for retained architectural decisions.
Older plans in `docs/superpowers` are archived design history, not implementation instructions.

To add a gamemode, update `tier/Gamemodes.java`, `tools/gen_font_provider.py` and the matching
texture, then run `python3 tools/gen_font_provider.py` followed by `./gradlew check`.
The generator binds existing artwork to glyphs; it does not generate artwork.

Icons use the private `justtiers:icons` font. Apply it only to the glyph component, with labels
as separate siblings, so text does not inherit an icon-only font. Preserve `Segment.icon`
when recoloring, using `Segment.withColor` or `Badge.recolor`.

## Credits and licensing

Just-Tiers is maintained by Idan Gilboa and released under [MIT](LICENSE). MCTiers and SubTiers
icon textures come from [TierTagger](https://github.com/mctiers-dev/TierTagger) by uku and netiyiy
and retain their MPL-2.0 license. NovaTiers icons are original project artwork under MIT.
See [NOTICE](NOTICE) for asset attribution. Code and documentation include AI-assisted work
reviewed by the maintainer; that does not change the third-party artwork credits.

Thanks to MCTiers, SubTiers and NovaTiers for their leaderboards and public APIs.
This project is unofficial and is not affiliated with those sites, Mojang or Microsoft.
