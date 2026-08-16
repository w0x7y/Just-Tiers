# Just-Tiers

A Minecraft **Fabric** client mod that shows a player's competitive PvP tier directly in their nametag, using all three major tier leaderboards at once: **MCTiers**, **SubTiers** and **NovaTiers**.

---

## Status

> ** ! THE MOD IS STILL IN BETA ! **

---

## Why this exists

[TierTagger](https://github.com/mctiers-dev/TierTagger) is the established mod in this space and it is good, but it has two limitations that Just-Tiers is built to fix:

1. **No NovaTiers support.** Only MCTiers and SubTiers are available.
2. **One leaderboard at a time.** You must pick MCTiers *or* SubTiers; you cannot see both at once.

Just-Tiers supports all three leaderboards, and adds an **All** mode that shows each site's best tier side by side in a single nametag.

---

## Features

- **All three leaderboards** — MCTiers, SubTiers and NovaTiers.
- **Four display modes** — focus on one site, or show all three at once.
- **Per-site gamemode selection** — pick the gamemode you care about on each site.
- **Automatic fallback** — not ranked in your chosen gamemode? It shows that player's highest tier on that same site instead.
- **Gamemode icons** — a small icon shows *which* gamemode earned the tier.
- **Colour-coded by site** — you can always tell where a tier came from.
- **Look anyone up** — `/justtiers lookup <player>` opens a screen with that player's skin and every gamemode all three sites run, tier by tier, without them being anywhere near you. See [Looking a player up](#looking-a-player-up).
- **Scan the whole lobby** — `/justtiers scan` scores everyone on the server out of every placement they hold on all three sites and sorts them best first, so you can see who is dangerous before the fight starts. See [Scanning a lobby](#scanning-a-lobby).
- **Shape the badge** — put it before or after the name, and turn the icons or the brackets off to make it as short as you like. The config screen previews every combination live.
- **Retired tiers handled properly** — shown with an `R` prefix in their site's colour, still counted when finding a player's highest tier, and hideable entirely with one setting.
- **Non-blocking** — all lookups are asynchronous and cached; the mod never stalls your frame rate waiting on a web request.
- **Shows up as it arrives** — a nametag gains its badge the moment the first site answers, then fills in as the others land, rather than waiting on the slowest one.
- **Fails safe** — a site that is down, rate-limiting or unreachable is retried; it is never mistaken for "this player is unranked".
- **In-game config screen** — every setting in one place, with a live nametag preview and an
  icon grid for picking gamemodes. See [Configuration screen](#configuration-screen).
- **Visible downloads** — the NovaTiers list has to be fetched in full, so a small progress bar
  appears in the bottom-right corner while it downloads, rather than leaving you wondering whether
  the mod is working.
- **Client-side only** — works on any server, nothing to install server-side.

---

## The tier system

Tiers run from **LT5** (lowest) to **HT1** (highest). `HT` means "high tier", `LT` means "low tier":

```
LT5 → HT5 → LT4 → HT4 → LT3 → HT3 → LT2 → HT2 → LT1 → HT1
lowest                                                highest
```

A **retired** tier is one a player earned but is no longer actively defending. Just-Tiers displays these with an `R` prefix (for example `RHT1`), coloured like any other tier from that site, and still counts them when working out a player's highest tier — otherwise many well-known players, whose placements are entirely retired, would show nothing at all.

If you would rather not see them, set `showRetired` to `false` (or run `/justtiers retired`). This applies across all four display modes: a player whose best tier is retired then falls back to their best active tier, and one whose placements are *all* retired shows nothing for that site.

**Peak tiers are ignored.** Only a player's current tier is ever displayed.

---

## Display modes

| Mode | What it shows |
|---|---|
| `mctiers_only` | Your selected MCTiers gamemode; falls back to their highest MCTiers tier; shows nothing if untested on MCTiers |
| `subtiers_only` | Same, for SubTiers |
| `novatiers_only` | Same, for NovaTiers |
| `all` *(default)* | The highest tier from **each** site, side by side. Sites where the player is untested are omitted |

Example nametag in `all` mode, where a player is HT2 on MCTiers, LT3 on SubTiers and HT4 on NovaTiers:

```
[⛏HT2 🏹LT3 ⚔HT4] PlayerName
  │      │      └── purple = NovaTiers
  │      └───────── cyan   = SubTiers
  └──────────────── yellow = MCTiers
```

Each icon shows the gamemode that earned the tier, so you know a tier came from Axe rather than Vanilla.

### Colours

| Source | Colour | Hex |
|---|---|---|
| MCTiers | Yellow | `#FFFF55` |
| SubTiers | Cyan | `#55FFFF` |
| NovaTiers | Purple | `#AA55FF` |

---

## Supported gamemodes

Each leaderboard tests its own gamemodes. They are kept separate and never merged — `Vanilla` on MCTiers and `Vanilla` on NovaTiers are different competitions with different testers.

The name in brackets is the slug you pass to `/justtiers gamemode` and the value stored in the
config file. Tab-completion offers exactly these.

| Leaderboard | Gamemodes |
|---|---|
| **MCTiers** (8) | Axe (`axe`), Mace (`mace`), Netherite OP (`nethop`), Pot (`pot`), SMP (`smp`), Sword (`sword`), UHC (`uhc`), Vanilla (`vanilla`) |
| **SubTiers** (12) | Bed (`bed`), Bow (`bow`), Creeper (`creeper`), DeBuff (`debuff`), Diamond SMP (`dia_smp`), Diamond Vanilla (`dia_crystal`), Elytra (`elytra`), Manhunt (`manhunt`), Minecart (`minecart`), OG Vanilla (`og_vanilla`), Speed (`speed`), Trident (`trident`) |
| **NovaTiers** (12) | Axe (`axe`), Diamond Cart (`diamondcart`), Diamond OP (`diamondop`), Elytra (`elytra`), Elytra Spear (`elytraspear`), Modern SMP (`modernsmp`), Pufferfish (`pufferfish`), SMP (`smp`), Spear Mace (`spearmace`), Spleef (`spleef`), UHC (`uhc`), Vanilla (`vanilla`) |

---

## Requirements

| | |
|---|---|
| Minecraft | 26.2 only — the mod declares `~26.2`, so it will not load on a later release until it has been verified against it |
| Mod loader | Fabric Loader 0.19 or newer (built against 0.19.3) |
| Dependency | Fabric API 0.157.0+26.2 or newer |
| Dependency | [YetAnotherConfigLib](https://modrinth.com/mod/yacl) 3.9.4 or newer — **required**, the config screen is built on it |
| Optional | [ModMenu](https://modrinth.com/mod/modmenu) — adds a config button to the mod list |
| Java | 25 or newer |

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) for Minecraft 26.2.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) for 26.2 and put it in your `mods` folder.
3. Download [YetAnotherConfigLib](https://modrinth.com/mod/yacl) for 26.2 and put it in your `mods` folder. This one is required — Just-Tiers will refuse to load without it.
4. Optionally add [ModMenu](https://modrinth.com/mod/modmenu) if you want a config button in the mod list.
5. Put the Just-Tiers jar in your `mods` folder.
6. Launch the game.

---

## Commands

All commands are client-side and start with `/justtiers`.

| Command | Description |
|---|---|
| `/justtiers` | Show current settings and cache status |
| `/justtiers lookup <player>` | Print every tier a player holds, on all three sites |
| `/justtiers scan` | Rank everyone on the server by their tiers across all three sites |
| `/justtiers toggle` | Turn the nametag display on or off |
| `/justtiers retired` | Show or hide retired tiers, across every display mode |
| `/justtiers mode <mode>` | Set display mode: `mctiers_only`, `subtiers_only`, `novatiers_only`, `all` |
| `/justtiers gamemode <gamemode>` | Set the selected gamemode for the current single-site mode |
| `/justtiers badge <before\|after>` | Put the badge in front of the player's name or behind it |
| `/justtiers icons` | Show or hide the gamemode icons |
| `/justtiers brackets` | Show or hide the `[ ]` around the badge |
| `/justtiers refresh` | Re-download tier data and clear the cache |
| `/justtiers gui` | Open the configuration screen |

`/justtiers gamemode` offers tab-completion limited to the gamemodes that actually exist on the site you are currently viewing. `/justtiers lookup` completes from the players currently on the server.

---

## Looking a player up

The nametag only tells you about people you can see. `/justtiers lookup <player>` tells you about
anyone — useful in the thirty seconds before a duel, when the person you are about to fight is on
the other side of the lobby.

```
/justtiers lookup Notch
```

It opens a screen rather than printing to chat: three sites' worth of placements is more than a
chat line can hold, and a duel is a bad time to scroll.

```
┌───────────────────────────────────────────────────────┐
│                        Notch                          │
├───────────────────────────────────────────────────────┤
│                     (their skin)                      │
├───────────────────────────────────────────────────────┤
│                         Tiers                         │
│   MCTiers  ┌───────────────────────────────────────┐  │
│            │ ⛏HT3 ⚒LT4 ⛏--- ⚔--- 🏹HT2 ⚔HT3 ⛏LT5 │  │  ← yellow
│            └───────────────────────────────────────┘  │
│  SubTiers  ┌───────────────────────────────────────┐  │
│            │ 🛏HT2 🏹HT3 💥--- 🧪LT5 💎HT2 ...      │  │  ← cyan
│            └───────────────────────────────────────┘  │
│ NovaTiers  ┌───────────────────────────────────────┐  │
│            │ ⛏HT1 🛒LT2 💎--- 🪶--- ...            │  │  ← purple
│            └───────────────────────────────────────┘  │
├───────────────────────────────────────────────────────┤
│      Tier data from MCTiers · SubTiers · NovaTiers    │
└───────────────────────────────────────────────────────┘
```

Every gamemode each site runs gets a cell, in that site's own order, so the columns stay put from
one lookup to the next. A cell is either a tier in its site's colour or `---`, which means that
site has never tested this player in that gamemode. Hover a cell to see which gamemode it is.

The rows fill in one at a time, the moment each site answers, rather than the screen waiting on the
slowest of the three — a cold NovaTiers list is a ~1.7 MB download, and a screen that showed
nothing until it landed would look broken. A row that is still waiting says `Looking up...`.

A lookup deliberately ignores your display mode and your gamemode selections: those settings exist
to keep a nametag short, and the reason to ask about a player by name is to see everything. Retired
placements are included, with their usual `R` prefix. Gamemode icons are always drawn here even if
you have turned them off for nametags — on a nametag an icon says which gamemode earned a tier, but
here it is the only thing naming the column.

The three answers a site can give are kept distinct, because they mean different things:

| Row | Meaning |
|---|---|
| cells, some with tiers | The site has placed this player |
| every cell `---` | The site answered, and has never placed them |
| `site unavailable` | The site could not be reached — it has said nothing either way |

**The skin.** Drawn flat and face on, straight out of the skin texture: no world, no entity, so the
screen works the same on a server, in singleplayer and from the title screen. A player on the
server already has their skin loaded; anyone else has their profile fetched from Mojang first. If
that fetch fails — Mojang rate-limits it like anything else — you get the default skin for that
account and the tiers are unaffected.

**The credit.** The three site names at the bottom are links to the leaderboards the data came
from, and open in your browser through Minecraft's usual confirmation prompt.

**Finding the player.** Names are resolved from the tab list first, which covers everyone on the
server whether or not they are in render distance, and costs nothing. A name that is not on the
server is resolved through Mojang's public profile API instead, so you can look up someone who is
not even online. Results are remembered for the session; a name nobody owns says so on the screen,
and a Mojang that does not answer is reported as a failure rather than as a player who does not
exist.

On offline-mode servers the tab list hands out UUIDs that are not real account UUIDs, so those
players go through Mojang as well rather than being looked up as someone the leaderboards will
never have heard of.

---

## Scanning a lobby

`/justtiers lookup` answers a question about one player. `/justtiers scan` answers the
question you actually have when you join: *who here is dangerous?*

It opens a scrollable screen listing everyone on the server, each with their head, their
name, a points total, and the full grid of every gamemode all three sites run.

### The points

Every placement a player holds is worth points, best to worst:

| Tier | HT1 | LT1 | HT2 | LT2 | HT3 | LT3 | HT4 | LT4 | HT5 | LT5 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 10 | 9 | 8 | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

A player's total is the **sum across every gamemode on every site** — not their best, and
not their average. Someone placed in eleven gamemodes really is more dangerous than
someone placed in one at the same tier, and summing is the only rule that says so. The
list is sorted by that total, highest first, with ties broken by name.

**Retired tiers do not count here, and are not shown.** This is the one place in
Just-Tiers where the `showRetired` setting is ignored on purpose: the nametag and the
lookup screen answer "what has this player earned", while a scan answers "who is a threat
right now", and nobody is defending a retired tier.

### While it fills

NovaTiers is already in memory, so the screen opens populated and sorted the moment you
run the command. MCTiers and SubTiers have to be asked per player, so those columns say
`Looking up...` until they answer, and the counter in the top right shows how many players
are fully answered. Rows re-sort every time an answer lands — the list visibly moves for
the first few seconds and then settles. A list that is correct for what is known beats one
that is stable and wrong.

At most six MCTiers/SubTiers requests are in flight at once, however large the lobby. A
site that cannot be reached shows `site unavailable` for that player rather than an empty
grid, because those mean different things, and re-opening the screen retries.

**The roster is fixed when the screen opens.** Players joining or leaving mid-scan are not
picked up; run the command again to rescan.

**Clicking a row** opens that player's [lookup screen](#looking-a-player-up); Escape
brings you back to the scan with its rows and its progress intact.

---

## Configuration screen

Everything the commands can do is also available on an in-game screen, built on
[YetAnotherConfigLib](https://modrinth.com/mod/yacl). Open it any of three ways:

- **A keybind** — unbound by default, so it never steals a key on first launch. Bind it under
  Options → Controls → **Just-Tiers**.
- **`/justtiers gui`** — from chat, in-game.
- **ModMenu** — the config button on the Just-Tiers entry in the mod list, if you have ModMenu installed.

<!-- screenshot: the Display category, showing the preview row above the options -->

The screen has three categories — **Display**, **Data** and **About** — and behaves as follows.

**A live preview.** The top of the Display category draws the nametag your current settings would
produce, under your own name. The tiers in it are invented — always `HT1` — so it is a picture of
your settings rather than a lookup: pick any gamemode and the preview shows *that* gamemode, whether
or not you have ever been tested in it. In `all` mode it shows one fixed gamemode per site — MCTiers
Vanilla, SubTiers Minecart, NovaTiers Spear Mace — since that mode has no gamemode to pick. While
**Show retired tiers** is on, the tag alternates every five seconds between `HT1` and `RHT1` so you
can see both spellings; turn it off and it stays active. A caption underneath says the same thing in
words, so the preview is never mistaken for your real placements.

Nothing about it is a lookup: the screen makes no network request and behaves identically offline,
on any account.

**Appearance.** Under the display mode sits an **Appearance** group with the three cosmetic
settings: **Badge position** (before or after the name), **Show gamemode icons** and **Show
brackets**. None of them change *which* tiers are shown — that is what the display mode and the
gamemode pickers are for — so they stay live in every mode, greying only when Just-Tiers itself is
switched off. Each one shows up in the preview immediately, including the space between the badge
and the name, which follows the badge to whichever side it is on. With icons off the sites are told
apart by tier colour alone, which is the same legend the display-mode row is coloured with.

**Nothing is hidden, only greyed.** Options that cannot do anything useful in the current state are
greyed out rather than removed, so the screen never changes shape under you. In `all` mode, for
instance, all three gamemode rows go inert — that mode always shows each site's highest tier, so
there is no gamemode to pick — but they stay visible, still showing the value they hold, and their
description says why they are inert.

**A gamemode grid.** Clicking a gamemode row opens a full-screen grid of that site's gamemodes with
their icons. The current selection is outlined in the site's colour, hovering a tile previews the
nametag that choice would produce, and a single click selects it and returns — there is no confirm
button. Escape or **Back** leaves it unchanged. Arrow keys and Enter work too.

<!-- screenshot: the gamemode grid, with one tile outlined in the site colour -->

**Nothing is written until you press Save.** Edits — including a gamemode picked in the grid — live
in a pending state that **Cancel** discards and **Undo** reverts. **Save** writes
`config/justtiers.json` once, the same file the commands write.

---

## Configuration

Settings are stored in `config/justtiers.json` and are written automatically whenever you change them with a command, or when you press Save on the configuration screen.

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
  "showDownloadProgress": true,
  "badgePosition": "before",
  "showIcons": true,
  "showBrackets": true
}
```

| Key | Meaning |
|---|---|
| `enabled` | Master on/off switch for the nametag display |
| `showRetired` | Whether retired (`R`-prefixed) tiers are displayed at all |
| `displayMode` | `mctiers_only`, `subtiers_only`, `novatiers_only` or `all` |
| `selectedGamemodes` | The chosen gamemode slug per site |
| `novaRefreshMinutes` | How often to re-download the NovaTiers list (clamped to 5–1440) |
| `showDownloadProgress` | Whether a progress bar is shown in the bottom-right while the NovaTiers list downloads |
| `badgePosition` | `before` or `after` — which side of the player's name the badge sits on |
| `showIcons` | Whether each tier carries its gamemode glyph |
| `showBrackets` | Whether the badge is wrapped in `[ ]` |

Settings are also written by the [configuration screen](#configuration-screen), which produces the
same file. `novaRefreshMinutes` and `showDownloadProgress` have no commands, but both appear on the
screen's **Data** category — a slider and a tick box respectively. Changing the interval takes effect
as soon as you press Save: the refresh timer is rescheduled rather than waiting for the next launch.
Out-of-range or unrecognised values are corrected on load rather than rejected.

`displayMode` and `badgePosition` are written in lower-case and read case-insensitively, so config
files written by older builds with upper-case values (e.g. `"ALL"`) still load correctly. An
unrecognised value falls back to `all` and `before` respectively, with a warning logged. A file
written before the appearance settings existed simply keeps the badge Just-Tiers has always drawn:
bracketed, with icons, in front of the name.

---

## How it works

Just-Tiers reads three public leaderboard APIs and normalises them into one internal model.

| Leaderboard | Endpoint | Lookup style |
|---|---|---|
| MCTiers | `mctiers.com/api/v2/…` | Per player, by UUID |
| SubTiers | `subtiers.net/api/v2/…` | Per player, by UUID (same schema as MCTiers) |
| NovaTiers | `novatiers.com/users` | Bulk only — the entire ranked player list in one request |

One more endpoint is contacted, and only by `/justtiers lookup`: Mojang's
`api.mojang.com/users/profiles/minecraft/<name>`, to turn a name that is not on the server into the
account UUID the leaderboards are keyed by. Nothing else in the mod ever calls it — every other
lookup already has a UUID in hand.

NovaTiers offers no per-player route, so its full list (roughly 6,500 players, about 1.7 MB) is downloaded once, indexed by UUID in memory, and refreshed on a timer. MCTiers and SubTiers are queried per player, with results cached for the session and concurrent requests for the same player coalesced into one.

Every lookup is asynchronous. A player whose data has not arrived yet simply renders with their
normal nametag until it does, and each site is drawn independently — the badge appears as soon as
the first site answers and gains the rest over the following frames. Entities without a v4 UUID
(offline-mode players, NPCs) are skipped outright, as they can never appear on these leaderboards.

**No data is redistributed.** Tier information is fetched from the public APIs at runtime, on your own machine, and is never bundled with the mod or forwarded anywhere.

### The download indicator

Because the NovaTiers list has to arrive in full before any NovaTiers badge can appear, a small
progress bar is drawn in the **bottom-right corner** while it downloads. It shows up for every
download — at launch, on the timed refresh, and when you run `/justtiers refresh` — and disappears
the moment the download finishes.

`novatiers.com` sends no `Content-Length`, so the size of the list is not knowable in advance. The
bar calibrates itself instead: the **first** download of a session shows a sliding bar and a live
byte count, and once one download has completed the mod knows how big the list actually is, so
**every later download in that session shows a true percentage**. Nothing is remembered between
sessions, so the first download after each launch is always the indeterminate one. A percentage is
never shown against a guess.

If a download fails, the bar is replaced for a few seconds by **NovaTiers unavailable** in red. This
is worth saying out loud, because a failed refresh is otherwise silent — the mod keeps serving the
tiers it already has, so without the message there is nothing to distinguish a failed refresh from a
successful one.

Set `showDownloadProgress` to `false`, or untick **Show download progress** on the config screen's
**Data** category, to turn it off entirely.

### When a site is down

An empty answer and a failed request are deliberately different things:

- **HTTP 404** on MCTiers or SubTiers means the site answered and the player is genuinely unranked. That is cached.
- **Any other status, or a transport failure**, means the lookup did not complete. It is not cached as "unranked"; it is retried, at most once a minute per player, so a rate-limited or briefly unavailable site does not get hammered by a lookup that runs every frame.
- **A failed NovaTiers refresh** keeps the index already in memory rather than replacing it with nothing, so one bad refresh cannot blank every NovaTiers badge until the next successful one.
- **A refresh in progress** keeps serving the tiers already on screen. The cached entries are only dropped once the new list has finished downloading, so badges do not disappear for the length of a scheduled refresh.

---

## Building from source

```bash
git clone https://github.com/w0x7y/Just-Tiers.git
cd Just-Tiers
./gradlew build
```

The jar is written to `build/libs/`.

To run a development client:

```bash
./gradlew runClient
```

Requires a JDK 25 toolchain. The Gradle build can provision one automatically.

The build resolves YetAnotherConfigLib from Maven Central, ModMenu from Terraformers' maven and
YACL's `org.quiltmc.parsers` transitives from Quilt's maven; all three repositories are declared
in `build.gradle.kts`, so no extra setup is needed.

---

## Licensing

### This project

Just-Tiers is released under the **MIT License**. See [`LICENSE`](LICENSE).

```
Copyright (c) 2026 Idan Gilboa
```

### Bundled third-party assets

Some gamemode icon textures are taken from [TierTagger](https://github.com/mctiers-dev/TierTagger), which is licensed under the **Mozilla Public License 2.0**, Copyright © 2025 MCTiers, mctiers.com.

MPL-2.0 is a file-level copyleft licence, so these files remain under MPL-2.0 even though the rest of the project is MIT. They are not relicensed, and they are attributed in the `NOTICE` file shipped with the source.

| Asset | Licence | Origin |
|---|---|---|
| MCTiers gamemode icons (8) | MPL-2.0 | TierTagger, © MCTiers |
| SubTiers gamemode icons (12) | MPL-2.0 | TierTagger, © MCTiers |
| NovaTiers gamemode icons (12) | MIT | Original work, part of this project |
| All source code | MIT | This project |

A full copy of the MPL 2.0 is available at <https://mozilla.org/MPL/2.0/>.

### Dependencies

Just-Tiers does not redistribute any of these; they are resolved at build time or provided at runtime by the mod loader.

| Dependency | Licence |
|---|---|
| Fabric Loader | Apache-2.0 |
| Fabric API | Apache-2.0 |
| YetAnotherConfigLib | LGPL-3.0-or-later — linked at runtime, not redistributed |
| ModMenu (compile-only, optional at runtime) | MIT |
| Fabric Loom (build only) | MIT |
| Mixin / MixinExtras | MIT |
| Gson | Apache-2.0 |
| JUnit 5 (tests only) | EPL-2.0 |

Minecraft itself is **not** redistributed. You must own a legitimate copy. Minecraft is © Mojang Studios / Microsoft and is governed by the [Minecraft End User Licence Agreement](https://www.minecraft.net/eula). Minecraft 26.2 ships unobfuscated with no Mojang mappings published for it, so the build performs no remapping step and does not use or redistribute any Mojang mappings.

### Trademarks and affiliation

Just-Tiers is an **unofficial**, community-made client mod.

It is not affiliated with, endorsed by, sponsored by, or approved by MCTiers, SubTiers, NovaTiers, Mojang Studios, or Microsoft. All product names, logos, trademarks and leaderboard data are the property of their respective owners and are used here only to identify those services.

If you represent one of these leaderboards and want a change to how your data, name or artwork is used, please open an issue.

---

## Credits

- **[TierTagger](https://github.com/mctiers-dev/TierTagger)** by uku and netiyiy — the mod that inspired this one, and the source of the MCTiers and SubTiers gamemode icons.
- **[MCTiers](https://mctiers.com)**, **[SubTiers](https://subtiers.net)** and **[NovaTiers](https://novatiers.com)** — for running the leaderboards and exposing public APIs.

---

## Contributing

Issues and pull requests are welcome.

Run `./gradlew test` before opening a pull request. The parsing, resolver, cache and config logic is
deliberately free of Minecraft types so it can all be unit-tested without launching the game.

When adding a gamemode, three things must stay in sync: the registry in `Gamemodes.java`, the icon codepoint list in `tools/gen_font_provider.py`, and the icon texture itself. The plan document explains the layout in detail.
