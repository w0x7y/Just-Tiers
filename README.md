# Just-Tiers

[![Build](https://github.com/w0x7y/Just-Tiers/actions/workflows/build.yml/badge.svg)](https://github.com/w0x7y/Just-Tiers/actions/workflows/build.yml)

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
- **Color-coded by site** — you can always tell where a tier came from.
- **Look anyone up** — `/justtiers lookup <player>` opens a screen with that player's skin and every gamemode all three sites run, tier by tier, without them being anywhere near you. See [Looking a player up](#looking-a-player-up).
- **Shape the badge** — put it before or after the name, and turn the icons or the brackets off to make it as short as you like. The config screen previews every combination live.
- **Recolor the sites** — the three leaderboard colors can be swapped for a colorblind-safe or high-contrast palette, or for three colors of your own. See [Color palettes](#color-palettes).
- **Hide your own badge** — leave your own nametag undecorated while everyone else's keeps its tiers.
- **Retired tiers handled properly** — shown with an `R` prefix in their site's color, still counted when finding a player's highest tier, and hideable entirely with one setting.
- **Non-blocking** — all lookups are asynchronous and cached; the mod never stalls your frame rate waiting on a web request.
- **Keeps up with the leaderboards** — a cached tier is re-checked every hour, so a player tested or re-ranked mid-session stops showing the wrong thing without a restart.
- **Shows up as it arrives** — a nametag gains its badge the moment the first site answers, then fills in as the others land, rather than waiting on the slowest one.
- **Fails safe** — a site that is down, rate-limiting or unreachable is retried; it is never mistaken for "this player is unranked". Repeated failures back off, and a site that keeps failing is left alone entirely rather than being asked once a minute per player.
- **Explains itself when something is wrong** — `/justtiers debug` prints what each site has actually answered, how fast, and what it last said when it failed, and copies it to your clipboard for a bug report. See [Reporting a bug](#reporting-a-bug).
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

A **retired** tier is one a player earned but is no longer actively defending. Just-Tiers displays these with an `R` prefix (for example `RHT1`), colored like any other tier from that site, and still counts them when working out a player's highest tier — otherwise many well-known players, whose placements are entirely retired, would show nothing at all.

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

### Colors

These are the **default** palette. All three can be changed — see [Color palettes](#color-palettes).

| Source | Color | Hex |
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
| `/justtiers toggle` | Turn the nametag display on or off |
| `/justtiers retired` | Show or hide retired tiers, across every display mode |
| `/justtiers mode <mode>` | Set display mode: `mctiers_only`, `subtiers_only`, `novatiers_only`, `all` |
| `/justtiers gamemode <gamemode>` | Set the selected gamemode for the current single-site mode |
| `/justtiers badge <before\|after>` | Put the badge in front of the player's name or behind it |
| `/justtiers icons` | Show or hide the gamemode icons |
| `/justtiers brackets` | Show or hide the `[ ]` around the badge |
| `/justtiers ownbadge` | Show or hide the badge on your own nametag |
| `/justtiers palette <palette>` | Set the color palette: `default`, `colorblind`, `high_contrast`, `custom` |
| `/justtiers refresh` | Re-download tier data and clear the cache |
| `/justtiers gui` | Open the configuration screen |
| `/justtiers debug` | Print what each site has been doing, and copy it for a bug report |

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
one lookup to the next. A cell is either a tier in its site's color or `---`, which means that
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
apart by tier color alone, which is the same legend the display-mode row is colored with.

**Nothing is hidden, only greyed.** Options that cannot do anything useful in the current state are
greyed out rather than removed, so the screen never changes shape under you. In `all` mode, for
instance, all three gamemode rows go inert — that mode always shows each site's highest tier, so
there is no gamemode to pick — but they stay visible, still showing the value they hold, and their
description says why they are inert.

**A gamemode grid.** Clicking a gamemode row opens a full-screen grid of that site's gamemodes with
their icons. The current selection is outlined in the site's color, hovering a tile previews the
nametag that choice would produce, and a single click selects it and returns — there is no confirm
button. Escape or **Back** leaves it unchanged. Arrow keys and Enter work too.

<!-- screenshot: the gamemode grid, with one tile outlined in the site color -->

**Nothing is written until you press Save.** Edits — including a gamemode picked in the grid — live
in a pending state that **Cancel** discards and **Undo** reverts. **Save** writes
`config/justtiers.json` once, the same file the commands write.

---

## Color palettes

Color is how Just-Tiers tells you which leaderboard a tier came from, which makes it the
one thing in the mod that has to be legible to everybody. The default yellow/cyan/purple
is not, for everybody, so it can be changed.

| Palette | `id` | MCTiers | SubTiers | NovaTiers |
|---|---|---|---|---|
| Default | `default` | `#FFFF55` yellow | `#55FFFF` cyan | `#AA55FF` purple |
| Colorblind-safe | `colorblind` | `#E69F00` orange | `#56B4E9` sky blue | `#FFFFFF` white |
| High contrast | `high_contrast` | `#FFFFFF` white | `#FFAA00` amber | `#00FFFF` cyan |
| Custom | `custom` | whatever you pick | | |

**One colorblind preset, not one per condition.** Its three colors are derived from the
Okabe-Ito palette and separate by *luminance* as well as by hue, so the same three work
for protanopia, deuteranopia and tritanopia alike. A second preset differing only slightly
would be a worse answer than one that works for everybody.

**A palette applies everywhere at once** — nametags, `/justtiers lookup`, the gamemode
grid and the config screen's own previews. Color carries
exactly one meaning in this mod, and a palette that only reached some screens would stop
that being true.

**Custom colors are set on the config screen**, under Appearance: pick Custom from the
palette row and the three color pickers below it come alive. They stay greyed under any
other palette rather than disappearing, so the screen never changes shape. There is no
command for them — a color is chosen by looking at it, which chat cannot do.

**Presets do not overwrite your custom colors.** Switching to Custom, then to Default,
then back to Custom returns the colors you picked.

---

## Hiding your own badge

`/justtiers ownbadge`, or **Hide my own badge** on the config screen, leaves your own
nametag undecorated while everyone else's keeps its badge. It is worth having in third
person, where your own tag is in shot constantly and tells you nothing you did not already
know.

It applies to the nametag only: `/justtiers lookup <yourself>` still lists you and your
tiers. The setting is about not cluttering your own tag, not about hiding yourself from
yourself.

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
  "tierCacheMinutes": 60,
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
| `tierCacheMinutes` | How long a fetched tier is trusted before it is looked up again (clamped to 5–1440) |
| `showDownloadProgress` | Whether a progress bar is shown in the bottom-right while the NovaTiers list downloads |
| `badgePosition` | `before` or `after` — which side of the player's name the badge sits on |
| `showIcons` | Whether each tier carries its gamemode glyph |
| `showBrackets` | Whether the badge is wrapped in `[ ]` |
| `hideOwnBadge` | Whether your own nametag is left undecorated |
| `palette` | `default`, `colorblind`, `high_contrast` or `custom` |
| `customColors` | Per-site `#RRGGBB`, used only while `palette` is `custom` |

Settings are also written by the [configuration screen](#configuration-screen), which produces the
same file. `novaRefreshMinutes` and `showDownloadProgress` have no commands, but both appear on the
screen's **Data** category — a slider and a tick box respectively. Changing the interval takes effect
as soon as you press Save: the refresh timer is rescheduled rather than waiting for the next launch.
Out-of-range or unrecognised values are corrected on load rather than rejected.

`palette` is written and read exactly as `displayMode` is: lower-case on disk,
case-insensitive on load, falling back to `default` with a logged warning when
unrecognised. A malformed entry in `customColors` falls back to **that site's** default
color and leaves the other two alone, so one typo costs one color rather than three.

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

NovaTiers offers no per-player route, so its full list (roughly 6,500 players, about 1.7 MB) is downloaded once, indexed by UUID in memory, and refreshed on a timer. MCTiers and SubTiers are queried per player, with results cached for `tierCacheMinutes` — an hour by default — and concurrent requests for the same player coalesced into one. See [How long an answer is kept](#how-long-an-answer-is-kept).

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

### How long an answer is kept

A tier fetched from MCTiers or SubTiers is trusted for `tierCacheMinutes` — an hour by
default — and then looked up again.

The expiry applies to **"unranked" as much as to a tier**, which is the point of it. A
player nobody had tested when you logged in would otherwise read as untested for your
whole session, however long that is, and a player who ranks up mid-session would keep
their old tier just as long. An hour is short enough that neither outlives your evening
and long enough that nothing is re-fetched often.

The slider is on the config screen's **Data** category and takes effect as soon as you
press Save, without discarding what is already cached — nudging a slider should not blank
every badge on screen while they are all fetched again.

Set it to its lowest for a testing session, or leave it alone; `/justtiers refresh` still
drops everything immediately whatever it is set to.

### Backing off

A failing lookup is held off twice over, because one of the two is not enough on its own.

**Per player**, the wait after a failure doubles each time — a minute, two, four, up to
sixteen — with ±25% of random jitter. The jitter matters more than it looks: without it, a
lobby whose lookups all failed in the same frame would retry in the same frame too, for as
long as the outage lasted, turning one bad moment into a repeating stampede. A successful
answer resets the run, so the next failure starts from a minute again.

**Per site**, eight consecutive failures across *any* players stop that leaderboard being
asked at all for thirty seconds. Then exactly one request is let through to test the
water: if it answers, the site is open again; if it fails, the pause doubles, up to four
minutes. This is the half that actually protects a full lobby — a per-player delay alone
still means one request per player per period, which for two hundred players is precisely
the flood a struggling site does not need.

While a site is closed, `/justtiers lookup` reports it as unavailable rather than
quietly queueing behind it, because that is the truth of the
situation. `/justtiers refresh` reopens it immediately — that command is you saying *try
again now*.

### When a site is down

An empty answer and a failed request are deliberately different things:

- **HTTP 404** on MCTiers or SubTiers means the site answered and the player is genuinely unranked. That is cached.
- **Any other status, or a transport failure**, means the lookup did not complete. It is not cached as "unranked"; it is retried, behind the two delays described in [Backing off](#backing-off), so a rate-limited or briefly unavailable site does not get hammered by a lookup that runs every frame.
- **A failed NovaTiers refresh** keeps the index already in memory rather than replacing it with nothing, so one bad refresh cannot blank every NovaTiers badge until the next successful one.
- **A refresh in progress** keeps serving the tiers already on screen. The cached entries are only dropped once the new list has finished downloading, so badges do not disappear for the length of a scheduled refresh.

### Reporting a bug

Everything above happens where you cannot see it, which makes "it doesn't show tiers for
X" almost impossible to answer from the outside. `/justtiers debug` prints the state
behind it and copies the same text to your clipboard, so a bug report can be one paste:

```
=== Just-Tiers debug ===
Just-Tiers *+mc26.2 | Minecraft 26.2 | Fabric Loader 0.19.3
nametags on | mode all | cache TTL 60m
NovaTiers index 12345 players | refresh every 30m
MCTiers: ok | 12 ok, 0 failed | last ok 4s ago | latency 180ms last, 210ms mean | 42 cached, 1 in flight, 0 retrying
SubTiers: PAUSED, retrying in 28s | 3 ok, 9 failed | last ok 6m ago, last fail 12s ago | latency 4.0s last, 1.2s mean | 8 cached, 0 in flight, 4 retrying
  last error: TierLookupException: HTTP 503 from subtiers.net
NovaTiers: ok | no lookups yet | 120 cached, 0 in flight, 0 retrying
```

Reading one site's line left to right:

- **`ok` / `PAUSED`** — whether the site gate is letting requests out at all, and when it
  will next try if not. A gate that is open but has failures behind it says so
  (`ok (7 failures in a row)`), because seven of the eight it takes to close reads as
  healthy right up until it is not.
- **`3 ok, 9 failed`** — lookups completed this session, and how each ended.
- **`last ok`, `last fail`** — how long ago each last happened. A site whose last success
  is hours old but which is not paused is failing slowly, not failing loudly.
- **`latency`** — the last round trip and the mean, failures included. A ten-second
  failure is a timeout; an instant one is a refused connection.
- **`cached / in flight / retrying`** — players the site holds an answer for, of those how
  many are still waiting on the network, and how many are sitting out a per-player retry
  delay.
- **`last error`** — the most recent failure, kept even after the site recovers, since
  what went wrong is usually the reason a report is being written at all. Long messages
  are cut to one line.

The report is deliberately in English rather than your game language: it is written to be
read by whoever fixes the bug.

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

### Continuous integration

`.github/workflows/build.yml` runs `./gradlew build` on every pull request and every push to
`main`. That is the unit tests *and* a full compile and packaging of the mod jar. The tests cover
the logic, but the mixin, the config screen and the renderers are Minecraft-facing and no unit test
reaches them — a change that breaks the mixin while compiling perfectly well would otherwise get as
far as a release. The jar is kept as a run artifact; the test report is kept only when something
failed.

### Releasing

Tagging is the whole process:

```bash
# gradle.properties must already say mod_version=<version>
git tag v<version>
git push origin v<version>
```

`.github/workflows/release.yml` then builds, publishes to Modrinth with
[Minotaur](https://github.com/modrinth/minotaur), and opens a GitHub release with the jar attached
and the commits since the previous tag as its notes.

Two things are worth knowing before the first tagged release:

- **`MODRINTH_TOKEN` must be set** as a repository secret, from
  [your Modrinth personal access tokens](https://modrinth.com/settings/pats), with the
  `CREATE_VERSION` scope (`PROJECT_WRITE` too, if you ever want to run `modrinthSyncBody`). Minotaur reads that environment variable itself, so no token appears
  anywhere in `build.gradle.kts`.
- **The tag must match `mod_version`.** The workflow checks and stops if it does not, because a jar
  whose own metadata contradicts the release it is attached to is much easier to prevent than to
  withdraw once Modrinth has it.

Published versions are labelled from `release_type` in `gradle.properties`, which stays `beta`
until the mod does.

#### Checking the publish without publishing

A release is the worst possible place to find out that a token is wrong. This runs the entire
Modrinth path — authenticating, looking the project up through the live API, assembling the
version payload — and then prints it and stops instead of creating anything:

```bash
MODRINTH_TOKEN=... ./gradlew modrinth -Pmodrinth_dry_run=true
```

The **Modrinth dry run** workflow does the same from the Actions tab, using the repository
secret, so it can be checked without a token on your machine.

What a dry run proves: the token is valid, the project id resolves, and the version number,
game versions, loaders and dependencies are what you meant. What it cannot prove: that Modrinth
will *accept* the upload — game versions are validated server-side, so a Minecraft version
Modrinth has not listed yet will only fail for real.

If a Modrinth upload does fail, the GitHub release has already been made — the release job does
that first, deliberately, since it depends on nothing outside this repository. Fix the cause and
re-run the job; it will not trip over the release it already created.

The Modrinth listing body is kept in `Modrinth/description.md` and is **not** part of the release
job — pushing it overwrites the project body irreversibly. Sync it deliberately, when you mean to:

```bash
./gradlew modrinthSyncBody
```

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

Run `./gradlew test` before opening a pull request.

As much of this mod as possible is deliberately free of Minecraft types, so it can be unit-tested
without launching the game. That now covers the parsers, the resolver, the cache, the config, the
whole badge pipeline, the decision about what a player wears, the palette rule and the lookup
screen's geometry. New logic belongs on that side of the line wherever it can:

| If you are adding | Put it behind |
|---|---|
| Anything that decides what a badge contains or looks like | `render/model/Badge` — the one way a badge is built |
| Anything the nametag needs to know about the running mod | `render/model/TierView` — `LiveTierView` reads the real config and cache, tests supply their own |
| A new site color rule | `config/Palette`, which works in ints and never learns the file format |
| Where something sits on a screen | `gui/layout/` — `GridLayout`, `SkinLayout`, `LookupLayout`, `CreditLine` |

The Minecraft-facing classes left over are then thin enough to read: `NametagRenderer` is twenty
lines, and `PlayerLookupScreen` keeps no coordinates of its own.

Decisions that were considered and deliberately not taken are recorded in `docs/adr/`. If a review
turns down a change for a reason that will come up again, write it down there rather than leaving
the next person to rediscover it.

When adding a gamemode, three things must stay in sync: the registry in `Gamemodes.java`, the icon codepoint list in `tools/gen_font_provider.py`, and the icon texture itself. Run `python3 tools/gen_font_provider.py` after changing either of the first two; it rewrites `assets/justtiers/font/icons.json` from the codepoint list. The plan document explains the layout in detail.

### The icon font

The gamemode glyphs live in Just-Tiers' own font, `justtiers:icons`, at private-use
codepoints `U+E101..`, `U+E201..` and `U+E301..`. They are deliberately **not** added to
`minecraft:default`: a mod that ships `assets/minecraft/font/default.json` is overriding a
vanilla file, and pack order alone decides whether it or the next mod's copy survives.

The trade is that a private font inherits none of the vanilla fallbacks, so anything drawn
in it that is not one of those glyphs comes out as a missing-glyph box. Everything that
draws an icon therefore goes through `render/Icons.java`, which is the only place that
names the font, and only ever wraps the single glyph character — never a label beside it.
Two consequences worth remembering when editing this code:

- A component appended to an icon component **inherits** the icon font. Build a glyph and
  a label as two children of `Component.empty()`, never the label appended to the glyph.
- `Segment` carries an `icon` flag. Recolor with `Segment.withColor`, which keeps it;
  rebuilding through the two-argument constructor silently turns an icon back into text.
  `Badge.recolor` does this for a whole badge and is what the dimmed config-screen preview
  uses, so reach for that before touching segments one at a time.
