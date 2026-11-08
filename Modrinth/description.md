# Just-Tiers

See players' PvP tiers from PvPTiers, PvPHQ, SubTiers and NovaTiers right on their nametags.

![Live tiers on real players' nametags](https://raw.githubusercontent.com/w0x7y/Just-Tiers/main/Modrinth/gallery/nametags.jpg)

- **Four sites, one badge.** Show all of them or just the ones you like.
- **Look anyone up.** See every gamemode a player is ranked in, on every site.
- **Make it yours.** Pick gamemodes, colors and where the badge goes.
- **Client-side.** Nothing to install on the server.
- **Open source, no analytics.** Releases are built by public
  [GitHub Actions](https://github.com/w0x7y/Just-Tiers/actions/workflows/release.yml).

## Install

Put the Just-Tiers JAR for your Minecraft version in your `mods` folder, with Fabric API and
YetAnotherConfigLib (YACL) for the same version. ModMenu is optional.

| Minecraft | Java | Fabric Loader | YACL |
|---|---|---|---|
| 1.21.10, 1.21.11 | 21 | 0.19+ | 3.8.2+ |
| 26.1 to 26.2 | 25 | 0.19+ | 3.9.4+ |
| 26.3 | 25 | 0.19.5+ | 3.9.7+ |

## Getting started

Open the settings with `/justtiers gui` or ModMenu. The commands you'll use most:

| Command | What it does |
|---|---|
| `/justtiers lookup <player>` | Look up a player |
| `/justtiers toggle` | Turn nametag tiers on or off |
| `/justtiers site <site>` | Turn a site on or off |
| `/justtiers gamemode [site] <gamemode>` | Choose which gamemode to show |
| `/justtiers refresh` | Recheck everyone's tiers |

The [README](https://github.com/w0x7y/Just-Tiers#readme) lists every command, gamemode and setting.

## Privacy

No analytics. The mod sends account UUIDs to PvPTiers, PvPHQ and SubTiers, downloads NovaTiers'
full list, and asks Mojang for names and skins. Like any website, they see your IP address.
Turning a site off stops its nametag lookups, but the NovaTiers list keeps updating, and refresh
or retries can make extra requests.
[Full details](https://github.com/w0x7y/Just-Tiers/blob/main/docs/how-it-works.md#requests-caching-and-privacy).

## Found a bug?

Run `/justtiers debug` and paste the copied report into a
[GitHub issue](https://github.com/w0x7y/Just-Tiers/issues).

## Credits

Made by Idan Gilboa under the [MIT license](https://github.com/w0x7y/Just-Tiers/blob/main/LICENSE).
Icons are original since 1.2.2. Versions up to 1.2.1 use icons from
[TierTagger](https://github.com/mctiers-dev/TierTagger) by uku and netiyiy
([NOTICE](https://github.com/w0x7y/Just-Tiers/blob/main/NOTICE)). Code, documentation and the
gamemode icons include AI-assisted work reviewed by the maintainer. Not affiliated with the tier
sites, Mojang or Microsoft.
