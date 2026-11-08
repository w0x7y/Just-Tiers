# How Just-Tiers works

The [README](../README.md) covers everyday use. This page has the exact rules behind nametags,
the lookup screen, the config file and network requests.

## Nametag tiers

All four sites are enabled by default and appear in PvPTiers, PvPHQ, SubTiers, NovaTiers order.
With several sites enabled, each site shows its best tier. With one site enabled, the mod shows
your selected gamemode for that site and falls back to the player's best tier on it. Prefer
selected gamemodes applies that rule to every enabled site. Disabling every site shows no
nametag tiers; player lookups still include all four.

Tiers rank from LT5 through HT5, LT4, HT4, LT3, HT3, LT2, HT2 and LT1 up to HT1. PvPHQ also
reports middle divisions such as MT3, which rank between HT3 and LT3. Retired placements have an
R prefix, such as RHT1. They compete by rank like any other placement, unless you hide retired
tiers, in which case the mod uses active placements. Inactive PvPHQ placements count as current
tiers. Peak tiers are ignored.

A nametag fills in as sites answer. Requests run in the background, and a site without an answer
yet is left out of the badge. The tab list and chat are unchanged.

### Unrecognized gamemodes

When a site reports a gamemode the mod doesn't list, its placements still appear. The name comes
from the site's identifier, so `bed_wars` becomes Bed Wars, and there is no icon. These placements
compete for the player's best tier, but you can't select the gamemode for nametags or with
`/justtiers gamemode` until a mod update adds it. An identifier must be 1 to 32 lowercase letters,
digits, `_` or `-`; placements under other identifiers are ignored. One site's answer for a player
contributes at most 16 unrecognized gamemodes.

## Player lookup

The lookup includes retired tiers and all four sites, whatever your nametag settings. Every known
gamemode has a cell. A dash means the site reported no placement for that gamemode. It does not
tell you whether the player was ever tested. A placement in an unrecognized gamemode gets an extra
cell after the known ones, marked with the first letter of its name; its tooltip shows the derived
name and says the mod doesn't recognize it yet. A waiting or unavailable site has its own message
and is never shown as a row of unranked placements.

Rows update independently. Cached placements stay visible during a refresh, and cell tooltips show
the answer's age and whether it is checking for updates or the refresh is unavailable. You can edit
the name or retry on the screen. Smaller windows scroll the results while the search field and Done
stay available. Cells and leaderboard links take keyboard focus, and links use Minecraft's browser
confirmation.

Recent lookups appear as buttons under the search field, most recent first. Click one, or focus it
and press Enter or Space, to look that player up again. History keeps up to eight names that
resolved to a player; a name that couldn't be found is not added. Smaller windows show fewer names,
and very short windows hide the row so the results keep room to scroll. `/justtiers lookup` offers
the same names in tab completion, along with the server's tab list. History lives in memory only and
is empty after restarting Minecraft.

Online players with account UUIDs resolve locally. Other names, including offline-mode server
identities, are checked through Mojang. A successful name lookup is remembered for the session. If
the profile or skin service fails, the screen keeps a default skin and the tier lookup can still
complete.

## Settings screen

Display, Data and About categories group the settings. Unavailable controls stay visible and explain
why they can't be changed right now.

The live preview uses invented HT1 tiers and makes no leaderboard request. It alternates with RHT1
every five seconds while retired tiers are enabled. With several sites enabled, it uses fixed example
gamemodes, or your selections while Prefer selected gamemodes is on. Gamemode pickers are available
for every enabled site when that option is on, and for the only enabled site otherwise. The preview
shows pending changes, including badge position, icons, brackets, colors and hiding your own badge.

A gamemode picker opens with a click or keyboard activation. Arrow keys move between tiles, and Enter
or Space selects the focused tile. Hovering or focusing a tile previews it. The narrator announces
the current selection. Back or Escape returns without selecting. The selection stays pending until
you save the parent screen.

Save writes settings, Cancel discards pending edits and Undo restores saved values. A failed save
reports the problem and keeps the previously saved file and active settings. A failed settings command
warns that its change applies only to the current session. Refresh is an immediate action, separate
from saving settings. Changing an interval on Save updates the running schedule without a restart.

Custom color controls are available in the Custom palette. Palette colors reach nametags, lookup rows,
previews, pickers and the NovaTiers progress bar, and the palette controls stay available while
nametag display is off. Icon artwork keeps its own colors.

## Config file

Enum values are saved in lowercase and read case-insensitively. Missing settings get defaults. A
setting with an unknown or wrongly typed value, such as `"enabled": 1`, falls back to its default
and the rest of the file is kept. In `enabledSites`, `selectedGamemodes` and `customColors` this
happens per site. Intervals are clamped to 5 to 1440 minutes when loading. Custom colors use per-site
`#RRGGBB` strings. If the file is not valid JSON or doesn't hold a JSON object, Just-Tiers copies it
to `justtiers.json.bak` before loading defaults.

Older `displayMode` settings migrate to site toggles. An old single-site selection keeps just that
site enabled, and `all` enables all four. Explicit `enabledSites` settings take precedence over the
old mode. MCTiers gamemodes and custom colors move to PvPTiers, with `vanilla` becoming `crystal` and
`nethop` becoming `neth_pot`.

## Requests, caching and privacy

The mod uses public leaderboard APIs and Minecraft's profile and skin loading. There is no analytics
endpoint. Remote services receive ordinary connection metadata, including your IP address, along
with the request data below.

| Service | Request data and purpose |
|---|---|
| `pvptiers.com/api/profile/<uuid>` | Compact account UUID for an enabled visible player's nametag or an explicit lookup |
| `pvphq.com/api/v1/players/<uuid>` | Dashed account UUID for the same purpose |
| `subtiers.net/api/v2/…` | The same for SubTiers |
| `novatiers.com/users` | Bulk leaderboard download, without a per-player identifier in the request |
| `api.mojang.com/users/profiles/minecraft/<name>` | Typed name when the tab list can't provide a usable account UUID |
| Mojang profile/session services and Minecraft skin texture hosts | Profile and skin requests Minecraft makes to show a looked-up player's skin |

The mod's own API requests use a versioned Just-Tiers User-Agent. Minecraft controls its profile and
skin requests and can cache skin data on disk. The mod writes its settings file and diagnostic logs,
and `/justtiers debug` replaces your clipboard with its report. Chat, inventory and the server address
are never sent to the leaderboard APIs.

Disabling nametag tiers stops automatic per-player lookups, and each site toggle stops them for that
site. Lookup screens still work, and NovaTiers' startup, scheduled and requested downloads continue
regardless of that switch. Hiding the progress bar changes only the indicator, not the downloads.
Another mod that uses the [tier-query API](api.md) makes automatic lookups too, so the same switches
apply: it gets no answer and causes no request for a site while that site, or nametag tiers, are off.
Like nametags, it never sends a UUID that is not an online-mode account, such as an offline-mode
player or a server NPC.

### Caching

PvPTiers, PvPHQ and SubTiers answers, including valid unranked answers, expire after an hour by
default. Repeated requests for one player share the pending result. Expired successful answers stay
visible while a replacement loads, for up to six hours beyond the cache interval. A valid replacement
with no placements removes the old badge. Manual refresh keeps previous answers while rechecking them.
Manual refresh, failed requests and explicit retries can make requests before the usual interval, so
the interval is not a rate-limit guarantee. NovaTiers is indexed in memory and refreshed every 30
minutes by default; its payload size varies.

Each site keeps at most 4096 player entries and 4096 retry records. New entries can evict the least
recently used completed entries. Every 1200 client ticks, maintenance removes idle expired answers and
old retry records, even for players who never appear again. In-flight work is kept until it completes,
and idle entries are dropped after 24 hours.

### Failures and limits

HTTP 404 from PvPTiers, PvPHQ or SubTiers is a valid unranked answer. Unexpected statuses, transport
errors and malformed successful responses are failures, not unranked results. So is a response whose
only placements use identifiers the mod refuses. A player or Mojang request that hasn't finished after
20 seconds fails like a transport error, and so does a NovaTiers download after two minutes.

Failed requests back off per player, with jitter. Eight consecutive failures on a site pause new
requests to it and then allow a recovery probe. Each site allows four active player requests and 128
waiting ones. Explicit lookups go ahead of queued nametag work and can displace waiting background
requests. Queue pressure defers work without counting as a site failure.

HTTP 429, and HTTP 503 with Retry-After, pause requests to that site for the requested delay, capped
at 24 hours; malformed hints use 60 seconds. NovaTiers downloads follow the same cooldown rules. Manual
refresh resets player retry and pause state but keeps server cooldowns and diagnostic history.

A failed NovaTiers refresh keeps the last usable index without resetting its age, and the player cache
refuses a bulk snapshot older than the cache interval plus six hours. A refresh that lists no ranked
players counts as failed once the index has players. Identifiable players with malformed or conflicting
bulk records are unavailable, not unranked; the parser logs how many it rejected and keeps the valid
players. Older requests can't replace a newer index or restore retry state that a refresh cleared.

### Download progress

The first NovaTiers download in a session shows a moving bar and the bytes received. Later downloads
estimate progress from the last completed payload's size. That size can change, so the percentage is
an estimate and stays below 100% until the download completes. A failure shows a brief unavailable
message.
