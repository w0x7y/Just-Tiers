# Just-Tiers

See players' PvP tiers from PvPTiers, PvPHQ, SubTiers and NovaTiers right on their nametags.

![Live tiers on real players' nametags](https://raw.githubusercontent.com/w0x7y/Just-Tiers/main/Modrinth/gallery/nametags.jpg)

*Live tiers on ranked players' nametags in Just-Tiers 1.2.2.*

- **Four tier lists, one badge.** Each site's best tier sits side by side. Turn any site off.
- **Look anyone up.** `/justtiers lookup <player>` shows their skin and every gamemode on all four sites.
- **Make it yours.** Pick gamemodes, colors, badge position, icons and brackets.
- **Client-side.** Nothing to install on the server. Works on Minecraft 1.21.10 through 26.3.
- **Open source, no analytics.** MIT licensed. Release JARs are built from their tagged commit by the
  public [GitHub Actions release workflow](https://github.com/w0x7y/Just-Tiers/actions/workflows/release.yml).

## Install

Put the Just-Tiers JAR for your exact Minecraft version in your `mods` folder, along with Fabric API
and YetAnotherConfigLib (YACL) for that same version. ModMenu is optional and adds a settings button
to the mod list.

| Minecraft | Java | Fabric Loader | YACL |
|---|---|---|---|
| 1.21.10, 1.21.11 | 21 | 0.19+ | 3.8.2+ |
| 26.1, 26.1.1, 26.1.2, 26.2 | 25 | 0.19+ | 3.9.4+ |
| 26.3 | 25 | 0.19.5+ | 3.9.7+ |

## How badges work

All four sites are on by default, and each shows the player's best tier. With only one site on, the
badge shows your chosen gamemode for that site instead, or the player's best tier there if they
aren't ranked in it. Turn on **Prefer selected gamemodes** to use your chosen gamemode on every site.
The icon shows the gamemode and the color shows the site.

Retired tiers have an R in front, like RHT1, and you can hide them. PvPHQ's middle tiers, like MT3,
sit between HT3 and LT3. Badges fill in as each site answers, and the tab list and chat stay as they are.

## Player lookup

The lookup screen always shows all four sites and retired tiers. A dash means the site reported no
placement, not that the player was never tested, and a site that is loading or unavailable says so.
Tooltips show how old each answer is. You can change the name or retry without closing the screen,
and it works with the keyboard. Your last eight lookups appear as buttons and in tab completion, and
they're cleared when you restart the game.

## Settings

Open the settings with `/justtiers gui`, ModMenu or a keybind you can set under Controls. A live
preview shows your changes with example tiers and doesn't contact any site.

- Move the badge before or after the name, hide icons or brackets, or hide your own badge.
- Choose the default palette, a color vision alternative, high contrast or your own colors per site.
  The alternatives can help tell sites apart, but how visible they are depends on your eyes and the
  background.
- Set how long tiers are cached and how often the NovaTiers list refreshes, from 5 to 1440 minutes.
  The defaults are 60 and 30.

If saving fails, your old settings stay in place and you see an error. A command that can't save
warns that its change lasts only for this session.

## Commands

| Command | What it does |
|---|---|
| `/justtiers` | Show your settings and the NovaTiers index |
| `/justtiers gui` | Open the settings |
| `/justtiers lookup <player>` | Look up a player |
| `/justtiers toggle` | Turn nametag tiers on or off |
| `/justtiers site <site> [true\|false]` | Toggle `pvptiers`, `pvphq`, `subtiers` or `novatiers` |
| `/justtiers gamemode [site] <slug>` | Choose a site's gamemode; skip the site when only one is on |
| `/justtiers preferselected` | Toggle Prefer selected gamemodes |
| `/justtiers retired` | Show or hide retired tiers on nametags |
| `/justtiers badge <before\|after>` | Put the badge before or after the name |
| `/justtiers icons` or `brackets` | Show or hide icons or brackets |
| `/justtiers ownbadge` | Show or hide your own badge |
| `/justtiers palette <palette>` | `default`, `colorblind`, `high_contrast` or `custom` |
| `/justtiers refresh` | Recheck cached tiers and refresh NovaTiers |
| `/justtiers debug` | Print a diagnostic report and copy it |

The [README](https://github.com/w0x7y/Just-Tiers#readme) lists all 43 gamemodes and every setting in
the config file. If a site adds a new gamemode, its tiers still show up, named by the site and without
an icon. You can pick it for nametags once a mod update adds it.

## New in 1.2.2

Every gamemode icon is now original pixel art, replacing the icons earlier versions took from
TierTagger. Gamemodes that play the same way share an icon, and the badge color still shows the site.
Nothing else changed. See the
[release notes](https://github.com/w0x7y/Just-Tiers/blob/main/docs/releases/v1.2.2.md).

## Privacy and network use

Just-Tiers has no analytics. It only contacts:

- **PvPTiers, PvPHQ and SubTiers**, sending the account UUID of a player on screen or one you look up.
- **NovaTiers**, downloading its whole list in one request with no player ID in it.
- **Mojang**, to turn a typed name into a UUID when the tab list can't, and through Minecraft to load
  skins on the lookup screen.

These services see ordinary connection details such as your IP address. Chat, inventory and the server
address are never sent. Offline-mode players and server NPCs are never looked up automatically.

Turning off nametag tiers, or a single site, stops automatic lookups for it, including lookups from
other mods that use the [tier-query API](https://github.com/w0x7y/Just-Tiers/blob/main/docs/api.md).
The lookup screen still works, and the NovaTiers list still downloads at startup, on its schedule and
when you refresh. Manual refresh and retries can make requests before a cached answer expires, so the
cache interval isn't a strict limit.

Failed or broken responses never show as unranked. Retries back off, and a site that keeps failing is
paused for a while. Settings are saved in `config/justtiers.json`, and older MCTiers settings move to
PvPTiers automatically. Minecraft can cache skins on disk, errors go to the game log, and
`/justtiers debug` writes its report to your clipboard.
[How it works](https://github.com/w0x7y/Just-Tiers/blob/main/docs/how-it-works.md) has every rule,
timeout and limit.

## Reporting problems

Run `/justtiers debug` and paste the copied report into a
[GitHub issue](https://github.com/w0x7y/Just-Tiers/issues), with steps to reproduce and any logs or
screenshots. `PAUSED` in the report means a site failed repeatedly and the mod is waiting before
trying it again.

## Credits and licensing

Just-Tiers is made by Idan Gilboa under the [MIT license](https://github.com/w0x7y/Just-Tiers/blob/main/LICENSE).
Since 1.2.2, every gamemode icon is original project artwork under MIT. Versions up to 1.2.1 use
PvPTiers, PvPHQ and SubTiers icon textures from [TierTagger](https://github.com/mctiers-dev/TierTagger)
by uku and netiyiy. See [NOTICE](https://github.com/w0x7y/Just-Tiers/blob/main/NOTICE).

Code, documentation and the gamemode icons include AI-assisted work reviewed by the maintainer.

Thanks to PvPTiers, PvPHQ, SubTiers and NovaTiers for their leaderboards and public APIs. Just-Tiers is
unofficial and isn't affiliated with these services, Mojang or Microsoft.
