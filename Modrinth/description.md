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
- **Colour-coded by site** - you can always tell where a tier came from.
- **Retired tiers handled properly** - shown with an `R` prefix in their site's colour, still counted when finding a player's highest tier, and hideable entirely with one setting.
- **Non-blocking** - all lookups are asynchronous and cached; the mod never stalls your frame rate waiting on a web request.
- **Shows up as it arrives** - a nametag gains its badge the moment the first site answers, then fills in as the others land, rather than waiting on the slowest one.
- **Fails safe** - a site that is down, rate-limiting or unreachable is retried; it is never mistaken for "this player is unranked".
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

## Data and network use

Just-Tiers reads three public leaderboards, so it has to talk to them. Everything it
sends is listed below. Nothing is sent to the mod's author, there is no analytics and no
telemetry, and your account details, chat, inventory, server address and gameplay are
never transmitted anywhere.

| Contacted | What is sent | When |
|---|---|---|
| `mctiers.com` | The account UUID of a player whose nametag is being drawn | Once per player per session, while a mode including MCTiers is active |
| `subtiers.net` | The same, for SubTiers | Once per player per session, while a mode including SubTiers is active |
| `novatiers.com` | **Nothing about any player.** NovaTiers has no per-player endpoint, so the mod downloads that site's whole ranked-player list (~1.7 MB) and answers from it locally | At startup, then every 30 minutes by default (configurable, 5–1440) |
| `api.mojang.com` | A username you typed into `/justtiers lookup` | Only when that name belongs to nobody on the server — anyone in the tab list is resolved locally with no request |

Every request is a plain `GET`. The only identifying header is a User-Agent naming the
mod and its version, `Just-Tiers/<version> (+https://github.com/w0x7y/Just-Tiers)`.
Answers are cached for the session, so the same player is never asked about twice.

The UUIDs sent are the ones the server already gave your client for the players around
you. They are public identifiers, and the leaderboards are public pages keyed by them —
looking a player up here sends no more than opening their page on those sites by hand.

**Turning it off.** `/justtiers toggle` stops the per-player lookups entirely, so no
UUID leaves your machine. Be aware that the NovaTiers list download is on its own timer
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
| `/justtiers toggle` | Turn the nametag display on or off |
| `/justtiers mode <mode>` | Set display mode: `mctiers_only`, `subtiers_only`, `novatiers_only`, `all` |
| `/justtiers gamemode <gamemode>` | Set the selected gamemode for the current single-site mode |
| `/justtiers badge <position>` | Put the badge `before` or `after` the player's name |
| `/justtiers icons` | Show or hide the gamemode icons inside the badge |
| `/justtiers brackets` | Show or hide the `[ ]` around the badge |
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
