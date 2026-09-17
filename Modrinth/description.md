# Just-Tiers

A client-side Fabric mod that puts PvP tiers from MCTiers, SubTiers and NovaTiers in player
nametags. **Currently in beta.** Requires Minecraft 26.2, Java 25, Fabric Loader 0.19 or newer,
Fabric API for 26.2 and YetAnotherConfigLib 3.9.4 or newer for 26.2. ModMenu is optional.

![Nametag showcase](https://cdn.modrinth.com/data/8zkz6d1C/images/195de1f0351ecc64b1d7505f03aa71a3ef173b35.jpeg)

## New in 1.1.2

Safer config saves with retry and Undo, more reliable tier lookups, keyboard-accessible
pickers and links, and scrolling results in small windows. You can edit or retry a player
lookup without leaving the screen. [Full release notes](https://github.com/w0x7y/Just-Tiers/blob/main/docs/releases/v1.1.2.md).

## What you can do

- Show one site's selected gamemode, falling back to the player's best placement on that site.
- Show the best placement from each site together in All mode.
- Move badges before or after names, hide icons or brackets, and hide your own badge.
- Choose default, Color vision alternative, high-contrast or custom leaderboard colors.
- Include retired placements with their R prefix, or show active placements only.
- Look up a player by name with `/justtiers lookup <player>` and inspect every known gamemode.

The lookup screen shows the skin and fills in each site as it answers. A dash means no placement
was reported, not that the player was never tested. Unavailable sites have a separate message.
You can change the name, retry, scroll results in smaller windows and navigate with the keyboard.
Tab list and chat names are unchanged.

## Install and configure

Put the Just-Tiers JAR, Fabric API and YACL in your Minecraft 26.2 instance's `mods` folder.
Install ModMenu if you want a config button in the mod list. Nothing is required on the server.

Open configuration with `/justtiers gui`, ModMenu or the configurable Just-Tiers keybind,
which starts unbound. The live preview uses example tiers and makes no leaderboard request.
Gamemode pickers support clicks and keyboard activation. Save applies pending edits; Cancel
discards them. Screen save failures leave the previous settings intact and show an error.
A settings command that cannot save warns that its change applies only to the current session.

| Command | Action |
|---|---|
| `/justtiers` | Show settings and index status |
| `/justtiers gui` | Open configuration |
| `/justtiers lookup <player>` | Open a player lookup |
| `/justtiers toggle` | Toggle nametag tiers |
| `/justtiers mode <mode>` | `all`, `mctiers_only`, `subtiers_only`, `novatiers_only` |
| `/justtiers gamemode <slug>` | Set the current site's gamemode; tab-completion lists choices |
| `/justtiers retired` | Toggle retired tiers in nametags |
| `/justtiers badge <before\|after>` | Move the badge |
| `/justtiers icons` or `/justtiers brackets` | Toggle those badge decorations |
| `/justtiers ownbadge` | Toggle hiding your own badge |
| `/justtiers palette <palette>` | `default`, `colorblind`, `high_contrast`, `custom` |
| `/justtiers refresh` | Clear lookup caches and refresh NovaTiers |
| `/justtiers debug` | Print and copy a diagnostic report |

The Data category sets cache and NovaTiers refresh intervals between 5 and 1440 minutes.
Defaults are 60 and 30 minutes respectively. Palette colors also apply to the download indicator;
icon artwork retains its original colors. Alternative palettes may help distinguish sites,
but visibility depends on your vision and the background.

## Network use

There is no mod analytics endpoint. MCTiers and SubTiers receive the account UUID being
looked up. NovaTiers supplies a bulk list, with no per-player identifier in that request.
Mojang receives a typed name if the tab list cannot resolve it to an account UUID. Displaying
the skin can also contact Mojang profile/session services and Minecraft's skin texture hosts
through Minecraft. These services receive connection metadata, including your IP address.

Automatic nametag lookups stop when nametag tiers are disabled. Explicit lookup screens and
NovaTiers' startup, scheduled and requested downloads remain available. Hiding the download
indicator does not stop downloads. Manual refresh and failure retries can make requests before
a cached answer's usual expiry; the cache interval is not a strict request-rate guarantee.

Failed or malformed responses are not shown as unranked players. Retries back off, and repeated
site failures temporarily pause new requests. Failed NovaTiers refreshes retain usable cached
data. Its first download shows bytes and a moving bar; later percentages are estimates based
on the last successful download's size, which can change.

Settings are saved in `config/justtiers.json`. Minecraft can cache skins on disk, errors go to
the client log, and `/justtiers debug` writes its report to the clipboard. The mod does not send
chat, inventory or the server address to the leaderboard APIs.

## Reporting problems

Run `/justtiers debug` and include its copied report, reproduction steps and relevant logs or
screenshots in a [GitHub issue](https://github.com/w0x7y/Just-Tiers/issues). `PAUSED` means the
mod is waiting before probing a site that repeatedly failed. Diagnostics remain in English.

The [README](https://github.com/w0x7y/Just-Tiers#readme) lists all 32 supported gamemodes,
configuration fields and contributor instructions.

## Credits and licensing

Just-Tiers is maintained by Idan Gilboa under the [MIT license](https://github.com/w0x7y/Just-Tiers/blob/main/LICENSE).
MCTiers and SubTiers icon textures come from [TierTagger](https://github.com/mctiers-dev/TierTagger)
by uku and netiyiy and retain MPL-2.0 licensing. NovaTiers icons are original project artwork
under MIT. See [NOTICE](https://github.com/w0x7y/Just-Tiers/blob/main/NOTICE).

Code and documentation include AI-assisted work reviewed by the maintainer. The project's
AI-content disclosure does not replace the third-party artwork attribution above.

Thanks to MCTiers, SubTiers and NovaTiers for their leaderboards and public APIs. Just-Tiers is
unofficial and is not affiliated with these services, Mojang or Microsoft.
