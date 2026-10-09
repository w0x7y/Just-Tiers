# Just-Tiers

A client-side Fabric mod that puts PvP tiers from PvPTiers, PvPHQ, SubTiers and NovaTiers in player
nametags. **Currently in beta.** Builds are available for Minecraft 1.21.10, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3.
Use the JAR for your exact game version, Fabric Loader 0.19 or newer, and Fabric API and
YetAnotherConfigLib built for that game version. Minecraft 1.21.10 and 1.21.11 require Java 21 and
YACL 3.8.2 or newer; 26.x requires Java 25 and YACL 3.9.4 or newer, or 3.9.7 for 26.3.
Minecraft 26.3 also requires Fabric Loader 0.19.5 or newer. ModMenu is optional.

![Nametag showcase](https://cdn.modrinth.com/data/8zkz6d1C/images/195de1f0351ecc64b1d7505f03aa71a3ef173b35.jpeg)

*Example nametags from an earlier release. Version 1.1.7 adds PvPHQ and individual site toggles.*

## New in 1.1.7

MCTiers is replaced by PvPTiers, using the same yellow color, and PvPHQ is added in
gray `#D2D2D2`. Each of the four sites now has its own toggle instead of the old
single-site/all-sites selector. PvPHQ middle tiers are supported, and existing
settings migrate automatically. Minecraft 1.21.10 joins the supported versions.
[Full release notes](https://github.com/w0x7y/Just-Tiers/blob/main/docs/releases/v1.1.7.md).

## What you can do

- Show one site's selected gamemode, falling back to the player's best placement on that site.
- Toggle each site independently, showing the best placement from every enabled site together.
- Use yellow for PvPTiers and gray `#D2D2D2` for PvPHQ in the default palette.
- Display PvPHQ middle tiers such as MT3 between high and low divisions.
- Move badges before or after names, hide icons or brackets, and hide your own badge.
- Choose default, Color vision alternative, high-contrast or custom leaderboard colors.
- Include retired placements with their R prefix, or show active placements only.
- Look up a player by name with `/justtiers lookup <player>` and inspect every known gamemode.

The lookup screen shows the skin and fills in each site as it answers. A dash means no placement
was reported, not that the player was never tested. Unavailable sites have a separate message. Cached placements remain visible while refreshing;
cell tooltips show their age and whether the latest refresh was unavailable.
You can change the name, retry, scroll results in smaller windows and navigate with the keyboard.
Tab list and chat names are unchanged.

## Install and configure

Put the matching Just-Tiers JAR, Fabric API and YACL in your instance's `mods` folder.
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
| `/justtiers site <site> [true\|false]` | Toggle or set `pvptiers`, `pvphq`, `subtiers` or `novatiers` |
| `/justtiers gamemode <slug>` | Set the only enabled site's gamemode; tab-completion lists choices |
| `/justtiers retired` | Toggle retired tiers in nametags |
| `/justtiers badge <before\|after>` | Move the badge |
| `/justtiers icons` or `/justtiers brackets` | Toggle those badge decorations |
| `/justtiers ownbadge` | Toggle hiding your own badge |
| `/justtiers palette <palette>` | `default`, `colorblind`, `high_contrast`, `custom` |
| `/justtiers refresh` | Recheck cached placements and refresh NovaTiers |
| `/justtiers debug` | Print and copy a diagnostic report |

The Data category sets cache and NovaTiers refresh intervals between 5 and 1440 minutes.
Defaults are 60 and 30 minutes respectively. Palette colors also apply to the download indicator;
icon artwork retains its original colors. Alternative palettes may help distinguish sites,
but visibility depends on your vision and the background.

## Network use

There is no mod analytics endpoint. PvPTiers, PvPHQ and SubTiers receive the account UUID being
looked up. NovaTiers supplies a bulk list, with no per-player identifier in that request.
Mojang receives a typed name if the tab list cannot resolve it to an account UUID. Displaying
the skin can also contact Mojang profile/session services and Minecraft's skin texture hosts
through Minecraft. These services receive connection metadata, including your IP address.

Automatic nametag lookups stop when nametag tiers are disabled. Each site toggle also
stops nametag lookups for that site. Explicit lookup screens and
NovaTiers' startup, scheduled and requested downloads remain available. Hiding the download
indicator does not stop downloads. Manual refresh and failure retries can make requests before
a cached answer's usual expiry; the cache interval is not a strict request-rate guarantee.

Failed or malformed responses are not shown as unranked players. Retries back off, and repeated
site failures temporarily pause new requests. Failed NovaTiers refreshes retain usable cached
data without resetting its original age. Old answers stay visible for up to six additional
hours beyond the configured cache interval, then become unavailable. Valid unranked replacements
remove old badges. NovaTiers records with an identifiable player but malformed or conflicting
placements are unavailable rather than unranked. Its first download shows bytes and a moving bar; later percentages are estimates based
on the last successful download's size, which can change.

Each site allows four active player requests and 128 queued requests, with explicit lookups
ahead of nametag work. Queue pressure defers background requests. HTTP Retry-After cooldowns
apply to rate-limited requests and bulk downloads, capped at 24 hours; missing or invalid hints
on HTTP 429 use 60 seconds. Refresh preserves those cooldowns and last successful placements.
Per-site caches and retry records are capped at 4096 players each, with periodic idle cleanup.

Settings are saved in `config/justtiers.json`. Existing MCTiers settings migrate to
PvPTiers and old display modes become individual site toggles. Minecraft can cache skins on disk, errors go to
the client log, and `/justtiers debug` writes its report to the clipboard. The mod does not send
chat, inventory or the server address to the leaderboard APIs.

## Reporting problems

Run `/justtiers debug` and include its copied report, reproduction steps and relevant logs or
screenshots in a [GitHub issue](https://github.com/w0x7y/Just-Tiers/issues). `PAUSED` means the
mod is waiting before probing a site that repeatedly failed. Diagnostics remain in English.

The [README](https://github.com/w0x7y/Just-Tiers#readme) lists all 43 supported gamemodes,
configuration fields and contributor instructions.

## Credits and licensing

Just-Tiers is maintained by Idan Gilboa under the [MIT license](https://github.com/w0x7y/Just-Tiers/blob/main/LICENSE).
PvPTiers, PvPHQ and SubTiers icon textures come from [TierTagger](https://github.com/mctiers-dev/TierTagger)
by uku and netiyiy and retain MPL-2.0 licensing. NovaTiers icons and the reused PvPHQ Spear icon are original project artwork
under MIT. See [NOTICE](https://github.com/w0x7y/Just-Tiers/blob/main/NOTICE).

Code and documentation include AI-assisted work reviewed by the maintainer. The project's
AI-content disclosure does not replace the third-party artwork attribution above.

Thanks to PvPTiers, PvPHQ, SubTiers and NovaTiers for their leaderboards and public APIs. Just-Tiers is
unofficial and is not affiliated with these services, Mojang or Microsoft.
