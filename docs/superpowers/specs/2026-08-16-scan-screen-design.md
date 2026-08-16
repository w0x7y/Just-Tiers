# Lobby Scan Screen (`/justtiers scan`) — Design

**Goal:** Answer the question a PvP player actually has when they join a lobby — *who
here is dangerous?* — without walking up to each nametag or typing a name at a time.
`/justtiers scan` lists everyone on the server, scores each of them out of every
placement they hold on all three leaderboards, and sorts the list by that score.

**Architecture:** Scoring, row assembly and ordering are pure functions over the existing
`LookupSection` model, in a Minecraft-free `scan` package. A session object drives the
per-player requests through a bounded queue and publishes answers on the client thread.
The screen owns nothing but pixels, exactly as `PlayerLookupScreen` does today.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API
`0.157.0+26.2`, JUnit 5. No new dependencies.

---

## Global Constraints

- **Minecraft-free packages stay Minecraft-free.** `scan` joins `tier`, `api`, `cache`,
  `resolve`, `render.model`, `preview`, `lookup`, `download`, `gui.layout` and
  `gui.state`. It must not import `net.minecraft.*`.
- **No new model.** Rows reuse `LookupSection` and `LookupCell` unchanged. The fixed grid,
  the `---` cells and the three-way RANKED / UNRANKED / UNAVAILABLE distinction all come
  from `LookupReport` as they stand; this design adds no parallel representation of the
  same facts.
- **No new endpoints.** Scan calls `TierCache.load` per player per site and nothing else.
  It never touches Mojang: every player it scans came from the tab list and already has a
  UUID.
- **The scan is polite.** At most six MCTiers/SubTiers requests in flight at once, across
  the whole scan. NovaTiers is answered from the in-memory index and is not queued.
- **The render thread never blocks.** Sorting happens when an answer lands, not per frame.
  The screen draws a pre-sorted, immutable list.
- **Color discipline.** Site colors keep their single meaning: MCTiers `0xFFFF55`,
  SubTiers `0x55FFFF`, NovaTiers `0xAA55FF`. The points number is white; secondary text
  `0xA0A0A0`; an unavailable site `0xFF5555`.

---

## Scoring

Ten tiers, ten points, best first:

| Tier | HT1 | LT1 | HT2 | LT2 | HT3 | LT3 | HT4 | LT4 | HT5 | LT5 |
|---|---|---|---|---|---|---|---|---|---|---|
| Points | 10 | 9 | 8 | 7 | 6 | 5 | 4 | 3 | 2 | 1 |

`Tier.rank()` already runs HT1 = 0 through LT5 = 9, so this is `10 - rank()` and no table
of constants is needed. `TierPoints.points(Tier)` is that expression; the table above is
the test case, not the implementation.

**Retired placements score zero and are not drawn.** A scan strips them before anything
else runs, in both the total and the grid, and does so regardless of the `showRetired`
config key. This is a deliberate divergence from the nametag and the lookup screen, both
of which honour the setting: those answer "what has this player earned", while a scan
answers "who is a threat right now", and a tier nobody is defending is not a threat. The
setting is untouched and unread by this feature.

**A player's total is the sum of their points across every gamemode on every site.** Not
their best, and not an average: a player placed in eleven gamemodes *is* more dangerous
than one placed in a single gamemode at the same tier, and summing is the only rule that
says so. A player no site has placed scores 0.

A total is therefore only comparable between two players once both have been answered by
the same set of sites. During a scan they have not been, and the list is openly
provisional; see *Filling in*.

---

## Data Model — `com.w0x7y.justtiers.scan`

### `TierPoints`

```java
public static int points(Tier tier)                       // 0 if retired, else 10 - rank()
public static int total(Map<Source, Map<String, Tier>>)   // sum over all sites, all gamemodes
public static int total(Collection<LookupSection>)        // same, from assembled sections
```

Both `total` overloads ignore sites that did not answer — an unavailable site contributes
0, which is the same thing an unranked one contributes. The distinction matters to the
grid, not to the arithmetic.

### `ScanRow`

```java
public record ScanRow(PlayerRef player,
                      Map<Source, LookupSection> sections,   // absent site = still waiting
                      int points,
                      boolean complete)
```

`complete` is true once all three sites have answered or failed. It drives the per-row
"still filling" affordance and nothing else; an incomplete row still sorts on the points
it has.

### `ScanReport`

```java
public static List<ScanRow> sorted(Collection<ScanRow> rows)
```

Points descending, then name case-insensitively ascending, so ties are stable and a
re-sort after an answer lands never reshuffles equal rows. Returns an immutable list.

---

## Filling In

The screen opens populated and never blank:

1. **On open**, every online player is enumerated and a row is created for each. NovaTiers
   is resolved synchronously from the in-memory index, so the first paint already has
   NovaTiers points and NovaTiers grids for everyone, sorted.
2. **MCTiers and SubTiers** are queued. Each answer updates that player's row, recomputes
   their total, and re-sorts the whole list. Rows visibly move for the first seconds of a
   scan and then settle — this was chosen over holding the order, because a list that is
   correct for what is known beats one that is stable and wrong.
3. **A header readout** shows `answered / total` while the scan runs and disappears when
   it completes, so a moving list is explained rather than mysterious.

`TierCache` already coalesces concurrent requests for the same player and caches answers
for the session, so a second scan of the same lobby is instant and anyone already walked
past is free the first time.

**Failures.** A site that fails for a player is recorded UNAVAILABLE for that row and its
cache entry is dropped with `forgetFailed`, the same rule `LookupSession` follows, so
re-opening the screen retries rather than replaying a failure. A failed row is not
retried within a scan.

**The player list changes underneath.** Players joining or leaving mid-scan are not picked
up; the row set is fixed when the screen opens. Re-opening rescans. This keeps the queue
finite and the sort stable, and a lobby roster is stable enough over the ten seconds a
scan takes for the alternative to be worth its complexity.

---

## The Queue — `ScanQueue`

Pure, Minecraft-free and unit-tested: it owns no threads and performs no I/O.

```java
public ScanQueue(int maxInFlight)
public void submit(Runnable task)   // runs immediately if under the cap, else waits
public void completed()             // one task finished; pumps the next
public int inFlight()
public int remaining()
```

The session hands it work and calls `completed()` from the answer callback on the client
thread, so the queue is only ever touched from one thread and needs no locking. `6` is the
cap: enough that a 150-player lobby finishes in seconds, low enough that no leaderboard
sees a burst it could reasonably call abuse.

---

## Session — `gui.ScanSession`

Mirrors `LookupSession` in shape and threading: written and read on the client thread,
with answers handed over by `Minecraft.execute`.

```java
public static ScanSession start()
public List<ScanRow> rows()          // pre-sorted, immutable, safe to draw
public int answered()
public int total()
public boolean complete()
public Optional<Component> error()   // "nobody to scan" on the title screen / singleplayer
```

It needs one new method on an existing class:

```java
// OnlinePlayers
public static List<PlayerRef> all()
```

Everyone on the tab list holding a v4 UUID, in tab-list order. The v4 filter is the rule
`find` already applies: offline-mode and proxy servers mint v3 UUIDs, which the
leaderboards are not keyed by, so those players are excluded from the scan rather than
listed as permanently unranked.

---

## The Screen — `gui.ScanScreen`

```
┌─────────────────────────────────────────────────────────────┐
│  Lobby scan                    47 / 150            [Close]  │
│          │    MCTiers    │    SubTiers    │    NovaTiers    │
│──────────┼───────────────┼────────────────┼─────────────────│
│ ▣ Notch  │  ⛏HT1 ⚒---   │  🛏HT2 🏹LT3   │  ⛏HT1 🛒---    │
│      28  │  ⚔LT1 🏹HT3  │  💥--- 🧪---   │  💎--- 🪶HT3   │
│──────────┼───────────────┼────────────────┼─────────────────│
│ ▣ Steve  │  ⛏HT2 ⚒---   │  site unavail. │  ⛏--- 🛒LT2    │
│      14  │  ⚔--- 🏹---  │                │  💎--- 🪶---   │
└─────────────────────────────────────────────────────────────┘
```

- **The left column** is the player's head, their name, and their points total. The head
  is `SkinLayout.pieces(slim)` filtered to `Part.HEAD` — base face plus hat overlay,
  already the right two rectangles — drawn from the skin `PlayerSkins.resolve` returns.
  Every online player's skin is in the player list already, so no scan fetches one.
- **Each site column** is that site's `LookupSection` drawn as the fixed cell grid, in the
  site's declared gamemode order, wrapped to the column width. Every row is therefore the
  same height for a given site, and the columns line up down the screen. A site that has
  not answered yet says `Looking up...`; one that failed says `site unavailable` in red.
- **Scrolling** is by mouse wheel and by dragging a scrollbar on the right, clamped to the
  content height. Rows outside the viewport are not drawn.
- **Clicking a row** opens `PlayerLookupScreen` for that player; Escape from there returns
  to the scan with its rows and its progress intact.
- **Empty states.** No connection, or no scannable player, replaces the list with one
  centred line: nobody to scan.

All geometry lives in `gui.layout.ScanLayout` — row height, column x-positions, cells per
row per site, viewport clamping, and which row a click at *(x, y)* hits — so it is tested
without launching the game, the way `GridLayout` and `ProgressBarLayout` are.

---

## Command and Strings

`/justtiers scan` opens the screen. No arguments, no tab completion. It is listed in
`/justtiers` help output alongside `lookup`.

New `en_us.json` keys, all under `justtiers.scan.`: `title`, `progress` (`%s / %s`),
`column.points`, `empty.noServer`, `empty.noPlayers`, `row.waiting`, `site.unavailable`.
Nothing user-facing is hardcoded in the screen.

---

## Testing

Minecraft-free, in the existing test layout:

- **`TierPointsTest`** — the ten-value table above, exhaustively; retired scores 0 at every
  level; totals sum across sites and gamemodes; an empty answer scores 0.
- **`ScanReportTest`** — points descending; name-ascending tie-break; case-insensitive
  names; an incomplete row sorts on partial points; re-sorting after an answer lands
  produces the same order as sorting that state from scratch.
- **`ScanQueueTest`** — never exceeds the cap; drains fully; a completion pumps exactly one
  waiter; a failure still counts as a completion so the queue cannot stall.
- **`ScanLayoutTest`** — row heights, column positions, hit-testing a click to a row index,
  scroll clamping at both ends.

Manual verification, on a populated server: the screen opens already sorted on NovaTiers
data, the counter climbs, rows settle, a click reaches the lookup screen and Escape comes
back.

---

## Out of Scope

- Sorting by anything other than points; filtering; searching by name.
- Re-scanning on join/leave, or a refresh button.
- Persisting scan results between screen opens beyond what `TierCache` already holds.
- Any change to the nametag, the lookup screen, the config, or the `showRetired` semantics
  anywhere outside this screen.
