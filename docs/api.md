# Tier-query API for other mods

Other Fabric client mods can read the tiers Just-Tiers knows about a player through
`com.w0x7y.justtiers.query.JustTiersQuery`. This page describes API version 1, added after
Just-Tiers 1.1.7.

The `query` package is the only supported surface. Its types hold standard Java values
and UUIDs, with no Minecraft types. Every other Just-Tiers package, including
`com.w0x7y.justtiers.api` (the HTTP clients), is internal and can change in any release.

## Depending on Just-Tiers

There is no separate API artifact or Maven publication. Compile against the Just-Tiers
JAR for your Minecraft target. One option is Modrinth's Maven repository, using the
project id `8zkz6d1C` and a published version number of the form
`<mod version>+mc<Minecraft version>`, or that version's Modrinth id:

```kotlin
repositories {
    maven("https://api.modrinth.com/maven") { content { includeGroup("maven.modrinth") } }
}
dependencies {
    // 26.x targets are unobfuscated: use compileOnly.
    // 1.21.10 and 1.21.11 use Loom remapping: use modCompileOnly.
    compileOnly("maven.modrinth:8zkz6d1C:<version>")
}
```

A downloaded JAR in a local `libs` folder works the same way. Do not bundle Just-Tiers in
your mod. Players install it separately, together with its own dependencies.

Declare the relationship in your `fabric.mod.json`. Use `suggests` when your mod works
without Just-Tiers, or `depends` when it cannot:

```json
"suggests": { "justtiers": "*" }
```

With an optional dependency, check `FabricLoader.getInstance().isModLoaded("justtiers")`
before touching any Just-Tiers class. Keep those references in a separate class that is
loaded only after the check, so your mod still loads when Just-Tiers is absent. Check
`JustTiersQuery.API_VERSION >= 1` if you rely on a feature from a later version.

## Reading answers

```java
import com.w0x7y.justtiers.query.JustTiersQuery;
import com.w0x7y.justtiers.query.SiteAnswer;

String describe(UUID player) {
    return switch (JustTiersQuery.peek(player, "pvptiers")) {
        case SiteAnswer.Disabled disabled -> "";
        case SiteAnswer.Pending pending -> "checking";
        case SiteAnswer.Unavailable unavailable -> "PvPTiers unavailable";
        case SiteAnswer.Unranked unranked -> "unranked";
        case SiteAnswer.Ranked ranked -> ranked.best().label() + " " + ranked.best().gamemodeName();
    };
}
```

| Method | Result |
|---|---|
| `SiteAnswer peek(UUID player, String siteId)` | One site's current answer |
| `Map<String, SiteAnswer> peekAll(UUID player)` | Every site's answer, keyed and ordered by `SITE_IDS` |
| `Optional<Placement> best(UUID player, String siteId)` | The site's best placement, or empty unless the answer is `Ranked` |
| `boolean isReady()` | Whether Just-Tiers has initialized |
| `int API_VERSION` | The installed API version, currently 1 |
| `List<String> SITE_IDS` | `pvptiers`, `pvphq`, `subtiers`, `novatiers` |

Site ids are read without case sensitivity and match `/justtiers site`. An unknown id
throws `IllegalArgumentException`; a null argument throws `NullPointerException`.

`best` returning empty does not mean the player is unranked. Use `peek` when the difference
matters.

## Answer cases

`SiteAnswer` is a sealed interface with exactly five cases. Only `Unranked` and `Ranked`
are evidence about the player's standing on that site.

| Case | Meaning |
|---|---|
| `Disabled` | The player has turned off nametag tiers or this site's toggle. Just-Tiers makes no request and reports nothing it already knows. |
| `Pending` | No answer yet; a request is queued or in flight. Also every site before Just-Tiers initializes. |
| `Unavailable` | No answer, and none is being fetched. The site failed or is paused, a server cooldown applies, the player is backing off after a failure, or the request was deferred. A later peek can retry. A UUID that is not an online-mode account, such as an offline-mode player or an NPC, is always unavailable and is never sent to a site. |
| `Unranked` | The site answered successfully with no placement for the player. HTTP 404 from PvPTiers, PvPHQ and SubTiers counts as unranked. |
| `Ranked` | The site answered successfully with at least one placement. |

Do not present `Disabled`, `Pending` or `Unavailable` as unranked. One site's answer says
nothing about another site; they are independent competitions.

### Respecting the player's settings

Just-Tiers promises players that turning off nametag tiers, or one site's toggle, stops
automatic lookups. A query from your mod is automatic: the player did not ask for it. It
therefore follows the same switches. While nametag tiers are off, every site is
`Disabled`. While only a site's toggle is off, that site is `Disabled` and the others
answer normally. A disabled site starts no request, even when an answer is cached or has
expired. Answers return to normal on the next peek after the player turns the setting
back on.

Show nothing for a `Disabled` site, as a nametag does. It is the player's choice, not an
error, so do not show a failure, retry or present it as unranked. If your mod has a
settings or status screen, you may explain that Just-Tiers' site is turned off there.
Do not change Just-Tiers' settings on the player's behalf.

A previous answer stays `Ranked` or `Unranked` while it is being refreshed, and after a
refresh fails, for up to six hours beyond the user's cache interval. Its `Freshness`
reports this:

| Field | Meaning |
|---|---|
| `Duration age` | Time since the site produced the answer. A NovaTiers answer is as old as its bulk index. |
| `boolean stale` | Past the cache interval, a refresh was requested, or the last refresh failed |
| `boolean refreshing` | A replacement request is in flight |
| `boolean refreshFailed` | The last attempt to replace this answer failed |

## Placements

`Ranked.placements()` is sorted best first: by rank, then active before retired, then
recognized gamemodes in the site's order, then discovered gamemodes by slug.
`Ranked.best()` is its first element. It matches the tier a nametag shows for that site
when retired tiers are shown and no selected gamemode applies.

| Field | Example | Meaning |
|---|---|---|
| `siteId` | `pvphq` | The site's id |
| `gamemode` | `neth_pot` | The site's gamemode slug, as listed in the README for recognized gamemodes |
| `gamemodeName` | `Netherite Pot` | English display name, derived from the slug for a discovered gamemode |
| `label` | `HT1`, `MT3`, `RLT3` | The tier as displayed; `R` marks a retired placement |
| `rank` | `0` | Lower is better, from 0 to 14 |
| `retired` | `true` | Whether the site marks the placement as retired |
| `recognized` | `true` | Whether this Just-Tiers build registers the gamemode |

### Discovered gamemodes

A site can add a gamemode before Just-Tiers is updated for it. Its placements still count:
they appear with `recognized` false and a name derived from the slug, so `quake_pro`
becomes `Quake Pro`. Just-Tiers has no icon for such a gamemode, and the player cannot
select it in settings. If you draw icons, use your own fallback for these placements; the
lookup screen shows the name's first letter. Accepted slugs are lowercase letters, digits,
`_` and `-`, at most 32 characters, and one site's answer for one player holds at most 16
discovered gamemodes. A later Just-Tiers version may recognize the same slug, with a
different display name.

`rank = (level - 1) * 3 + division`, where the division is 0 for high, 1 for middle and 2
for low. HT1 is 0, MT1 is 1, LT1 is 2, HT2 is 3, and LT5 is 14. Only PvPHQ reports middle
divisions, so MT3 ranks between HT3 and LT3. Retirement does not change the rank.

An enabled site's answer includes every placement, recognized or discovered, regardless of
the player's selected gamemodes, retired-tier setting or badge appearance.

## Threads and timing

Every method is thread-safe, may be called from any thread and never blocks. The cache and
the active settings are read through volatile references, and published settings are not
modified afterwards. Just-Tiers itself reads tiers on the client thread, which is the
expected caller. A settings change applies from the next peek. Each answer is a
snapshot that can become outdated as soon as it is returned. There is no listener or
future: peek again on a later frame or tick.

Before Just-Tiers' client initializer runs, `isReady()` is false, every site reads as
`Pending` and no request starts. Client initializers of different mods run in an
unspecified order, so do not treat a pending answer during startup as final.

## Network consequences

A peek behaves like a nametag reading the cache. For an enabled site, on a missing or
expired answer, it can start an asynchronous request:

- PvPTiers, PvPHQ and SubTiers receive the player's account UUID.
- NovaTiers is read from its bulk index. A peek can start that download if no index is
  loaded; the request has no per-player identifier.
- No request is made for a `Disabled` site. NovaTiers' startup, scheduled and requested
  bulk downloads continue independently of these switches, as they do without the API.

Repeated peeks for one player share a pending request, and fresh answers are not requested
again. API requests share each site's limits, retry delays, pauses and Retry-After
cooldowns with nametags. Each site allows four active and 128 waiting player requests,
and explicit `/justtiers lookup` requests take priority. Peek only for players your mod
presents to the user. Bulk scanning crowds out nametag lookups and sends more UUIDs to
the sites.

## Compatibility

Any addition to or incompatible change of these types, methods or site ids increases
`API_VERSION`. `API_VERSION` is initialized at run time, so your mod reads the installed
version rather than a value compiled into it. The record types are immutable; their
constructors validate values but exist for tests rather than for building answers.
