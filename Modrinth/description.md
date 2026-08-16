# Just-Tiers

A Minecraft **Fabric** client mod that shows a player's competitive PvP tier directly in their nametag. 3 leaderboards are available: **MCTiers**, **SubTiers** and **NovaTiers**.

![banner](https://cdn.modrinth.com/data/8zkz6d1C/images/dcdc175ec264cb63acc0019e57a5cb6c1b8fdc97.jpeg)

**! The mod is still in beta !**

---

## Why this exists

[TierTagger](https://github.com/mctiers-dev/TierTagger) is the established mod in this space, and it is good, but it has two limitations that Just-Tiers is built to fix:

1. **No NovaTiers support.** Only MCTiers and SubTiers are available, so newer gamemodes(Like elytramace/spearmace) are made unavailable.
2. **One leaderboard at a time.** You must pick MCTiers *or* SubTiers; you cannot see both at once.

Just-Tiers supports all three leaderboards, and adds an **All** mode that shows each site's best tier side by side in a single nametag.

---

## Features

- **All three leaderboards** - MCTiers, SubTiers and NovaTiers.
- **Four display modes** - MCTiers only, SubTiers only, NovaTiers only and All.
- **Per-site gamemode selection** - pick the gamemode you care about on each site.
- **Automatic fallback** - not ranked in your chosen gamemode? It shows that player's highest tier on that same site instead.
- **Gamemode icons** - a small icon shows *which* gamemode earned the tier.
- **Color-coded by site** - you can always tell where a tier came from.
- **Scan the whole lobby** - `/justtiers scan` scores everyone on the server out of every placement they hold on all three sites and sorts them best first, so you know who is dangerous before the fight starts.
- **Look anyone up** - `/justtiers lookup <player>` opens a screen with that player's skin and every gamemode all three sites run, tier by tier, without them being anywhere near you.
- **Color palettes** - swap the three site colors for a colorblind-safe or high-contrast preset, or pick three of your own.
- **Shape the badge** - before or after the name, brackets and icons on or off, and your own nametag left plain if you prefer. The config screen previews every combination live.
- **Retired tiers handled properly** - shown with an `R` prefix in their site's color, still counted when finding a player's highest tier, and hideable entirely with one setting.
- **In-game config screen** - every setting in one place, with a live nametag preview and an icon grid for picking gamemodes.
- **Non-blocking** - all lookups are asynchronous and cached; the mod never stalls your frame rate waiting on a web request.
- **Shows up as it arrives** - a nametag gains its badge the moment the first site answers, then fills in as the others land, rather than waiting on the slowest one.
- **Keeps up with the leaderboards** - a cached tier is re-checked every hour, so a player tested or re-ranked mid-session stops showing the wrong thing without a restart.
- **Fails safe** - a site that is down, rate-limiting or unreachable is retried with a growing delay, and one that keeps failing is left alone entirely rather than asked again every minute. A failure is never mistaken for "this player is unranked".
- **Client-side only** - works on any server, nothing to install server-side.

---

## Display modes

| Mode | What it shows |
|---|---|
| `mctiers_only` | Your selected MCTiers gamemode; falls back to their highest MCTiers tier; shows nothing if untested on MCTiers |
| `subtiers_only` | Same, for SubTiers |
| `novatiers_only` | Same, for NovaTiers |
| `all` *(default)* | The highest tier from **each** site, side by side. Sites where the player is untested are omitted |

Example nametag in `all` mode, where a player is LT3 in NetheriteOP on MCTiers, LT3 in Elytra on SubTiers and HT3 in Spearmace on NovaTiers:

![Nametag showcase](https://cdn.modrinth.com/data/8zkz6d1C/images/195de1f0351ecc64b1d7505f03aa71a3ef173b35.jpeg)

Each icon shows the gamemode that earned the tier, so you know a tier came from Axe rather than Vanilla.

### Colors

These are the default palette:

| Source | Color | Hex |
|---|---|---|
| MCTiers | Yellow | `#FFFF55` |
| SubTiers | Cyan | `#55FFFF` |
| NovaTiers | Purple | `#AA55FF` |

All three can be changed. The config screen offers a **Colorblind-safe** palette (orange,
sky blue, white — separated by brightness as well as hue, so it works for red-green
color blindness) and a **High contrast** one, or you can pick three colors of your own.
Whichever you choose applies everywhere at once: nametags, lookup, scan and the config
screen itself.

---

## Scanning a lobby

`/justtiers lookup` answers a question about one player. `/justtiers scan` answers the one
you actually have when you join a lobby: **who here is dangerous?**

It lists everyone on the server with their head, their name, a points total and the full
grid of every gamemode all three sites run — sorted by that total, best first.

Every placement is worth points, best to worst:

| Tier | HT1 | LT1 | HT2 | LT2 | HT3 | LT3 | HT4 | LT4 | HT5 | LT5 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 10 | 9 | 8 | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

A player's total is the **sum across every gamemode on every site**, not their best and
not their average — someone placed in eleven gamemodes really is more dangerous than
someone placed in one at the same tier. Retired placements are not counted here and not
shown: a scan asks who is a threat right now, and nobody is defending a retired tier.

NovaTiers is already in memory, so the screen opens sorted immediately; the other two
columns fill in as they answer, and the list re-sorts as they land. Clicking a row opens
that player's lookup screen.

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

## Data and network use

Just-Tiers reads three public leaderboards, so it has to talk to them. Everything it
sends is listed below. Nothing is sent to the mod's author, there is no analytics and no
telemetry, and your account details, chat, inventory, server address and gameplay are
never transmitted anywhere.

| Contacted | What is sent | When |
|---|---|---|
| `mctiers.com` | The account UUID of a player being looked up | Whenever their nametag is drawn (while a mode including MCTiers is active), when you run `/justtiers lookup` on them, and for **everyone on the server** when you run `/justtiers scan`. At most once per player per `tierCacheMinutes` (60 by default, configurable 5–1440), however many of those happen |
| `subtiers.net` | The same, for SubTiers | The same |
| `novatiers.com` | **Nothing about any player.** NovaTiers has no per-player endpoint, so the mod downloads that site's whole ranked-player list (~1.7 MB) and answers from it locally | At startup, then every 30 minutes by default (configurable, 5–1440) |
| `api.mojang.com` | A username you typed into `/justtiers lookup` | Only when that name belongs to nobody on the server — anyone in the tab list is resolved locally with no request |

Every request is a plain `GET`. The only identifying header is a User-Agent naming the
mod and its version, `Just-Tiers/<version> (+https://github.com/w0x7y/Just-Tiers)`.
Answers are cached for `tierCacheMinutes` — an hour by default — so the same player is
asked about at most once an hour, however long you play and however many times their
nametag is drawn. Set it higher for fewer requests, or lower to pick up a tier change
sooner.

When a site fails, the mod backs off rather than retrying on a fixed timer: each player's
retry delay doubles, and after eight failures in a row that site is not asked at all for
a while, then probed with a single request to see whether it has recovered.

The UUIDs sent are the ones the server already gave your client for the players around
you. They are public identifiers, and the leaderboards are public pages keyed by them —
looking a player up here sends no more than opening their page on those sites by hand.

**Turning it off.** `/justtiers toggle` stops the per-player lookups entirely, so no
UUID leaves your machine unless you ask for one by running `/justtiers lookup` or
`/justtiers scan` yourself. Be aware that the NovaTiers list download is on its own timer
and keeps running; it is an anonymous download of a public file and carries no
information about you or anyone else.

The mod writes exactly one file, `config/justtiers.json`, holding your own settings. It
reads and changes nothing else on your system.

---

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/).
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and put it in your `mods` folder.
3. Download [YetAnotherConfigLib](https://modrinth.com/mod/yacl) and put it in your `mods` folder.
4. Put the Just-Tiers jar in your `mods` folder.

### Optionally

* Install [ModMenu](https://modrinth.com/mod/modmenu) for a GUI access to the config.

### Finally

5. Launch the game.

---

## Commands

All commands are client-side and start with `/justtiers`. Everything they change is also available on the config screen — `/justtiers gui`, the Mod Menu entry, or a key of your choosing under **Controls → Just-Tiers** (unbound by default).

| Command | Description |
|---|---|
| `/justtiers` | Show current settings, the selected gamemode per site, and how many players are in the NovaTiers index |
| `/justtiers gui` | Open the config screen |
| `/justtiers lookup <player>` | Look a player up on all three sites and show the result on its own screen. Tab-completes anyone on the server; offline names are resolved through Mojang |
| `/justtiers scan` | Rank everyone on the server by their tiers across all three sites |
| `/justtiers toggle` | Turn the nametag display on or off |
| `/justtiers mode <mode>` | Set display mode: `mctiers_only`, `subtiers_only`, `novatiers_only`, `all` |
| `/justtiers gamemode <gamemode>` | Set the selected gamemode for the current single-site mode |
| `/justtiers badge <position>` | Put the badge `before` or `after` the player's name |
| `/justtiers icons` | Show or hide the gamemode icons inside the badge |
| `/justtiers brackets` | Show or hide the `[ ]` around the badge |
| `/justtiers ownbadge` | Show or hide the badge on your own nametag |
| `/justtiers palette <palette>` | Set the color palette: `default`, `colorblind`, `high_contrast`, `custom` |
| `/justtiers retired` | Show or hide retired tiers, across every display mode |
| `/justtiers refresh` | Clear the cache and re-download tier data |

---

## Licensing

### This project

Just-Tiers is released under the **MIT License**. See [`LICENSE`]([LICENSE](https://github.com/w0x7y/Just-Tiers/blob/main/LICENSE)).

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

### Trademarks and affiliation

Just-Tiers is an **unofficial**, community-made client mod.

It is not affiliated with, endorsed by, sponsored by, or approved by MCTiers, SubTiers, NovaTiers, Mojang Studios, or Microsoft. All product names, logos, trademarks and leaderboard data are the property of their respective owners and are used here only to identify those services.

If you represent one of these leaderboards and want a change to how your data, name or artwork is used, please open an issue.

---

## AI assistance

Parts of the source code and in-game text were written with the help of an AI coding
agent, working from my design decisions and under my review; the commits that used
one are marked as such in the Git history. The gamemode icons, the design of the mod and every choice about how it behaves are my own work, and no image on this page or inside this mod was generated by AI. The project carries Modrinth's *Contains AI-generated content*
disclosure accordingly.

---

## Credits

- **[TierTagger](https://github.com/mctiers-dev/TierTagger)** by uku and netiyiy — the mod that inspired this one, and the source of the MCTiers and SubTiers gamemode icons.
- **[MCTiers](https://mctiers.com)**, **[SubTiers](https://subtiers.net)** and **[NovaTiers](https://novatiers.com)** — for running the leaderboards and exposing public APIs.

---

## Contributing

If you want to contribute, you are more than welcome to do so on [GitHub](https://github.com/w0x7y/Just-Tiers).
