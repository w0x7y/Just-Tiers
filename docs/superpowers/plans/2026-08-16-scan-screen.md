# Lobby Scan Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `/justtiers scan`, a scrollable screen listing everyone on the server scored
out of every placement they hold on all three leaderboards, sorted best first.

**Architecture:** Scoring, row assembly, ordering, request throttling and scroll geometry
are pure functions in Minecraft-free packages (`scan`, `gui.layout`), each unit-tested.
One session object drives requests and publishes answers on the client thread; the screen
only reads and draws. Rows reuse the existing `LookupSection`/`LookupCell` model, so the
fixed cell grid and the three-way answered/unranked/unavailable distinction come for free.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API
`0.157.0+26.2`, JUnit 5. No new dependencies.

**Spec:** `docs/superpowers/specs/2026-08-16-scan-screen-design.md`

## Global Constraints

- `com.w0x7y.justtiers.scan` and `com.w0x7y.justtiers.gui.layout` must not import
  `net.minecraft.*` or `com.mojang.*`. Everything in them is unit-tested directly.
- Points: `10 - Tier.rank()`, so HT1 = 10 and LT5 = 1. Retired placements score 0.
- Retired placements are stripped in a scan regardless of the `showRetired` config key —
  they are neither scored nor drawn. No other feature's retired behaviour changes.
- At most **6** MCTiers/SubTiers requests in flight at once across the whole scan.
  NovaTiers is answered from the in-memory index and is never queued.
- Site colours keep their one meaning: MCTiers `0xFFFF55`, SubTiers `0x55FFFF`,
  NovaTiers `0xAA55FF`. Points white, secondary text `0xA0A0A0`, unavailable `0xFF5555`.
- No new endpoints and no Mojang calls: every scanned player came from the tab list with a
  UUID already in hand.
- The row set is fixed when the screen opens; joins and leaves mid-scan are ignored.
- Every user-facing string is a translation key in `en_us.json`. Nothing hardcoded.
- Run `./gradlew test` before every commit.

## Verified API Reference

Confirmed 2026-08-16 against `~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`.
**Do not re-derive these.**

- `Screen.mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY)` —
  inherited from `ContainerEventHandler`; vertical wheel arrives as `scrollY`.
- `Screen.mouseClicked(MouseButtonEvent event, boolean doubleClick)` — as already
  overridden in `PlayerLookupScreen`; `event.button()`, `event.x()`, `event.y()`.
- `GuiGraphicsExtractor.enableScissor(int x1, int y1, int x2, int y2)` /
  `disableScissor()` — for clipping the scroll viewport.
- `GuiGraphicsExtractor.blit(RenderPipeline, Identifier, int x, int y, float u, float v,
  int w, int h, int texW, int texH, int sheetW, int sheetH)` — the overload
  `PlayerLookupScreen.drawSkin` already uses.

## File Structure

| File | Responsibility |
|---|---|
| `scan/TierPoints.java` | Tier → points, and a section collection → total |
| `scan/ScanRow.java` | One player's row: who, what each site said, points, completeness |
| `scan/ScanReport.java` | Ordering rows |
| `scan/ScanQueue.java` | Bounded in-flight dispatch, thread-free |
| `gui/layout/ScanLayout.java` | Row geometry, scroll clamping, hit-testing, columns |
| `gui/ScanSession.java` | Drives the scan, publishes on the client thread |
| `gui/ScanScreen.java` | Pixels only |
| `resolve/TierResolver.java` | *(modify)* expose `activeOnly` for one site's map |
| `api/OnlinePlayers.java` | *(modify)* add `all()` |
| `command/JustTiersCommands.java` | *(modify)* register `scan` |

---

### Task 1: Points

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/scan/TierPoints.java`
- Test: `src/test/java/com/w0x7y/justtiers/scan/TierPointsTest.java`

**Interfaces:**
- Consumes: `Tier.rank()`, `Tier.retired()`, `LookupSection.cells()`, `LookupCell.tier()`.
- Produces: `TierPoints.points(Tier) -> int`,
  `TierPoints.total(Collection<LookupSection>) -> int`.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TierPointsTest {

    private static Tier ht(int level) { return new Tier(level, true, false); }
    private static Tier lt(int level) { return new Tier(level, false, false); }

    @Test
    void everyTierScoresItsPlaceOnTheTenPointScale() {
        assertEquals(10, TierPoints.points(ht(1)));
        assertEquals(9, TierPoints.points(lt(1)));
        assertEquals(8, TierPoints.points(ht(2)));
        assertEquals(7, TierPoints.points(lt(2)));
        assertEquals(6, TierPoints.points(ht(3)));
        assertEquals(5, TierPoints.points(lt(3)));
        assertEquals(4, TierPoints.points(ht(4)));
        assertEquals(3, TierPoints.points(lt(4)));
        assertEquals(2, TierPoints.points(ht(5)));
        assertEquals(1, TierPoints.points(lt(5)));
    }

    @Test
    void retiredPlacementsScoreNothingAtEveryLevel() {
        for (int level = 1; level <= 5; level++) {
            assertEquals(0, TierPoints.points(new Tier(level, true, true)));
            assertEquals(0, TierPoints.points(new Tier(level, false, true)));
        }
    }

    @Test
    void aTotalSumsEveryGamemodeOnEverySite() {
        Map<String, Tier> mctiers = new LinkedHashMap<>();
        mctiers.put("vanilla", ht(1));   // 10
        mctiers.put("sword", lt(3));     // 5
        Map<String, Tier> nova = new LinkedHashMap<>();
        nova.put("vanilla", ht(2));      // 8

        List<LookupSection> sections = List.of(
                LookupReport.section(Source.MCTIERS, Optional.of(mctiers)),
                LookupReport.section(Source.NOVATIERS, Optional.of(nova)));

        assertEquals(23, TierPoints.total(sections));
    }

    @Test
    void unrankedAndUnavailableSitesBothContributeNothing() {
        List<LookupSection> sections = List.of(
                LookupReport.section(Source.MCTIERS, Optional.of(Map.of())),
                LookupReport.section(Source.SUBTIERS, Optional.empty()));

        assertEquals(0, TierPoints.total(sections));
        assertEquals(0, TierPoints.total(List.of()));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*TierPointsTest'`
Expected: FAIL — compilation error, `TierPoints` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Tier;

import java.util.Collection;

/**
 * What a placement is worth when ranking a lobby. Ten points for HT1 down to one for
 * LT5, which is exactly {@code 10 - Tier.rank()} — the tier model already orders itself
 * that way, so there is no table here to drift out of step with it.
 *
 * <p>A player's total is the sum over every gamemode on every site, not their best and
 * not their average: someone placed in eleven gamemodes is more dangerous than someone
 * placed in one at the same tier, and only summing says so.
 *
 * <p>Retired placements score nothing. A scan asks who is a threat right now, and a tier
 * nobody is defending is not one — which is why this ignores the {@code showRetired}
 * setting that the nametag and the lookup screen both honour.
 */
public final class TierPoints {

    /** The best possible placement, HT1, and therefore the size of the scale. */
    private static final int BEST = 10;

    public static int points(Tier tier) {
        if (tier == null || tier.retired()) {
            return 0;
        }
        return BEST - tier.rank();
    }

    /** Sums a player's placements. A site that never answered contributes nothing. */
    public static int total(Collection<LookupSection> sections) {
        int total = 0;
        for (LookupSection section : sections) {
            for (LookupCell cell : section.cells()) {
                total += cell.tier().map(TierPoints::points).orElse(0);
            }
        }
        return total;
    }

    private TierPoints() {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*TierPointsTest'`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/scan/TierPoints.java \
        src/test/java/com/w0x7y/justtiers/scan/TierPointsTest.java
git commit -m "Score a placement out of ten for lobby scanning"
```

---

### Task 2: Stripping retired placements from one site's map

`TierResolver` already knows how to drop retired tiers, but only across a whole
`Map<Source, Map<String, Tier>>` and only privately. A scan strips them one site at a
time, as each answer lands. Expose the single-map rule and have the existing code path
call it, so the rule lives in one place.

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/resolve/TierResolver.java`
- Test: `src/test/java/com/w0x7y/justtiers/resolve/TierResolverTest.java`

**Interfaces:**
- Produces: `TierResolver.activeOnly(Map<String, Tier>) -> Map<String, Tier>`.

- [ ] **Step 1: Write the failing test**

Append to `TierResolverTest`:

```java
    @Test
    void activeOnlyDropsRetiredPlacementsAndKeepsOrder() {
        Map<String, Tier> tiers = new LinkedHashMap<>();
        tiers.put("vanilla", new Tier(1, true, false));
        tiers.put("sword", new Tier(2, true, true));
        tiers.put("pot", new Tier(3, false, false));

        Map<String, Tier> active = TierResolver.activeOnly(tiers);

        assertEquals(List.of("vanilla", "pot"), List.copyOf(active.keySet()));
    }

    @Test
    void activeOnlyReturnsTheSameMapWhenNothingIsRetired() {
        Map<String, Tier> tiers = Map.of("vanilla", new Tier(1, true, false));
        assertSame(tiers, TierResolver.activeOnly(tiers));
    }

    @Test
    void activeOnlyHandlesNullAndEmpty() {
        assertTrue(TierResolver.activeOnly(null).isEmpty());
        assertTrue(TierResolver.activeOnly(Map.of()).isEmpty());
    }
```

Add whatever imports the file is missing (`java.util.LinkedHashMap`, `java.util.List`,
`assertSame`, `assertTrue`).

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*TierResolverTest'`
Expected: FAIL — `activeOnly` does not exist.

- [ ] **Step 3: Write minimal implementation**

Add to `TierResolver`, and rewrite the existing private `withoutRetired` to delegate so
there is only one copy of the rule:

```java
    /**
     * One site's placements with the retired ones dropped. Returns the argument
     * unchanged when it holds none, which is the common case and saves a copy on a path
     * that runs per player.
     */
    public static Map<String, Tier> activeOnly(Map<String, Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Map.of();
        }
        boolean anyRetired = false;
        for (Tier tier : tiers.values()) {
            if (tier.retired()) {
                anyRetired = true;
                break;
            }
        }
        if (!anyRetired) {
            return tiers;
        }
        Map<String, Tier> active = new LinkedHashMap<>();
        tiers.forEach((slug, tier) -> {
            if (!tier.retired()) {
                active.put(slug, tier);
            }
        });
        return active;
    }
```

Then replace the body of the existing private `withoutRetired` with:

```java
    private static Map<Source, Map<String, Tier>> withoutRetired(
            Map<Source, Map<String, Tier>> tiersBySource) {
        if (!anyRetired(tiersBySource)) {
            // Nothing to strip, so nothing to copy. This runs per player per frame for
            // anyone who has turned retired tiers off, and most players hold none.
            return tiersBySource;
        }
        Map<Source, Map<String, Tier>> filtered = new EnumMap<>(Source.class);
        tiersBySource.forEach((source, tiers) -> filtered.put(source, activeOnly(tiers)));
        return filtered;
    }
```

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test`
Expected: PASS. The pre-existing `TierResolver` retired tests must still pass unchanged —
they are the regression check on the refactor.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/resolve/TierResolver.java \
        src/test/java/com/w0x7y/justtiers/resolve/TierResolverTest.java
git commit -m "Expose the retired-stripping rule for a single site's placements"
```

---

### Task 3: Rows and their order

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/scan/ScanRow.java`
- Create: `src/main/java/com/w0x7y/justtiers/scan/ScanReport.java`
- Test: `src/test/java/com/w0x7y/justtiers/scan/ScanReportTest.java`

**Interfaces:**
- Consumes: `TierPoints.total`, `PlayerRef(name, uuid)`, `LookupSection`, `Source.ALL`.
- Produces:
  - `ScanRow.of(PlayerRef, Map<Source, LookupSection>) -> ScanRow`
  - `ScanRow.player() / sections() / points() / complete() / section(Source)`
  - `ScanReport.sorted(Collection<ScanRow>) -> List<ScanRow>`

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanReportTest {

    private static PlayerRef player(String name) {
        return new PlayerRef(name, UUID.nameUUIDFromBytes(name.getBytes()));
    }

    private static Map<Source, LookupSection> answered(Source source, Map<String, Tier> tiers) {
        Map<Source, LookupSection> sections = new EnumMap<>(Source.class);
        sections.put(source, LookupReport.section(source, Optional.of(tiers)));
        return sections;
    }

    private static ScanRow row(String name, Source source, Map<String, Tier> tiers) {
        return ScanRow.of(player(name), answered(source, tiers));
    }

    private static List<String> names(List<ScanRow> rows) {
        return rows.stream().map(r -> r.player().name()).toList();
    }

    @Test
    void aRowScoresTheSectionsItHas() {
        ScanRow row = row("Notch", Source.MCTIERS,
                Map.of("vanilla", new Tier(1, true, false)));
        assertEquals(10, row.points());
    }

    @Test
    void aRowIsIncompleteUntilEverySiteHasAnswered() {
        ScanRow partial = row("Notch", Source.MCTIERS, Map.of());
        assertFalse(partial.complete());

        Map<Source, LookupSection> all = new EnumMap<>(Source.class);
        for (Source source : Source.ALL) {
            all.put(source, LookupReport.section(source, Optional.of(Map.of())));
        }
        assertTrue(ScanRow.of(player("Notch"), all).complete());
    }

    @Test
    void rowsSortByPointsDescending() {
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("Low", Source.MCTIERS, Map.of("vanilla", new Tier(5, false, false))),
                row("High", Source.MCTIERS, Map.of("vanilla", new Tier(1, true, false))),
                row("Mid", Source.MCTIERS, Map.of("vanilla", new Tier(3, true, false)))));

        assertEquals(List.of("High", "Mid", "Low"), names(sorted));
    }

    @Test
    void equalPointsBreakByNameIgnoringCase() {
        Map<String, Tier> same = Map.of("vanilla", new Tier(1, true, false));
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("charlie", Source.MCTIERS, same),
                row("Alice", Source.MCTIERS, same),
                row("bob", Source.MCTIERS, same)));

        assertEquals(List.of("Alice", "bob", "charlie"), names(sorted));
    }

    @Test
    void anIncompleteRowSortsOnThePointsItHasSoFar() {
        // Nova has answered for both; MCTiers has answered for one of them. The list is
        // provisional, and must still be ordered by what is known.
        ScanRow waiting = row("Waiting", Source.NOVATIERS,
                Map.of("vanilla", new Tier(1, true, false)));
        ScanRow ahead = row("Ahead", Source.NOVATIERS,
                Map.of("vanilla", new Tier(1, true, false), "smp", new Tier(1, true, false)));

        assertEquals(List.of("Ahead", "Waiting"), names(ScanReport.sorted(List.of(waiting, ahead))));
    }

    @Test
    void resortingAfterAnAnswerMatchesSortingThatStateFromScratch() {
        ScanRow first = row("First", Source.NOVATIERS,
                Map.of("vanilla", new Tier(4, true, false)));
        ScanRow second = row("Second", Source.NOVATIERS,
                Map.of("vanilla", new Tier(2, true, false)));
        List<ScanRow> before = ScanReport.sorted(List.of(first, second));
        assertEquals(List.of("Second", "First"), names(before));

        // MCTiers now answers for First, taking it past Second.
        Map<Source, LookupSection> grown = new EnumMap<>(first.sections());
        grown.put(Source.MCTIERS, LookupReport.section(Source.MCTIERS,
                Optional.of(Map.of("vanilla", new Tier(1, true, false)))));
        ScanRow updated = ScanRow.of(first.player(), grown);

        assertEquals(List.of("First", "Second"), names(ScanReport.sorted(List.of(updated, second))));
    }

    @Test
    void sortedIsImmutable() {
        List<ScanRow> sorted = ScanReport.sorted(List.of(
                row("Notch", Source.MCTIERS, Map.of())));
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> sorted.add(null));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ScanReportTest'`
Expected: FAIL — `ScanRow` and `ScanReport` do not exist.

- [ ] **Step 3: Write minimal implementation**

`ScanRow.java`:

```java
package com.w0x7y.justtiers.scan;

import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.tier.Source;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * One player's line in a scan: who they are, what each site has said about them so far,
 * and what that is worth. A row is rebuilt rather than mutated every time an answer
 * lands, so the list the screen draws is always a consistent snapshot.
 *
 * <p>A site missing from {@code sections} has not answered yet, which is different from
 * a site that answered and had nothing: the first draws as still loading, the second as
 * a row of dashes.
 */
public record ScanRow(PlayerRef player,
                      Map<Source, LookupSection> sections,
                      int points,
                      boolean complete) {

    public static ScanRow of(PlayerRef player, Map<Source, LookupSection> sections) {
        Map<Source, LookupSection> copy = new EnumMap<>(Source.class);
        copy.putAll(sections);
        return new ScanRow(player, Collections.unmodifiableMap(copy),
                TierPoints.total(copy.values()), copy.size() == Source.ALL.size());
    }

    /** Empty while that site is still being waited on. */
    public Optional<LookupSection> section(Source source) {
        return Optional.ofNullable(sections.get(source));
    }
}
```

`ScanReport.java`:

```java
package com.w0x7y.justtiers.scan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The order a scan is read in: most dangerous first.
 *
 * <p>Rows are re-sorted every time an answer lands rather than once at the end, so the
 * list is always correct for what is known. It visibly moves for the first seconds of a
 * scan; a stable list would only be buying calm with a wrong order.
 */
public final class ScanReport {

    private static final Comparator<ScanRow> ORDER =
            Comparator.comparingInt(ScanRow::points).reversed()
                    .thenComparing(row -> row.player().name(),
                            String.CASE_INSENSITIVE_ORDER);

    public static List<ScanRow> sorted(Collection<ScanRow> rows) {
        List<ScanRow> ordered = new ArrayList<>(rows);
        ordered.sort(ORDER);
        return List.copyOf(ordered);
    }

    private ScanReport() {
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ScanReportTest'`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/scan/ScanRow.java \
        src/main/java/com/w0x7y/justtiers/scan/ScanReport.java \
        src/test/java/com/w0x7y/justtiers/scan/ScanReportTest.java
git commit -m "Assemble and order the rows of a lobby scan"
```

---

### Task 4: The bounded queue

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/scan/ScanQueue.java`
- Test: `src/test/java/com/w0x7y/justtiers/scan/ScanQueueTest.java`

**Interfaces:**
- Produces: `new ScanQueue(int maxInFlight)`, `submit(Runnable)`, `completed()`,
  `inFlight() -> int`, `remaining() -> int`.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.scan;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScanQueueTest {

    @Test
    void runsUpToTheCapImmediatelyAndHoldsTheRest() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(2);
        for (int i = 0; i < 5; i++) {
            String name = "task" + i;
            queue.submit(() -> started.add(name));
        }

        assertEquals(List.of("task0", "task1"), started);
        assertEquals(2, queue.inFlight());
        assertEquals(3, queue.remaining());
    }

    @Test
    void aCompletionStartsExactlyOneWaiter() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(1);
        queue.submit(() -> started.add("a"));
        queue.submit(() -> started.add("b"));
        queue.submit(() -> started.add("c"));

        queue.completed();
        assertEquals(List.of("a", "b"), started);
        assertEquals(1, queue.inFlight());
    }

    @Test
    void drainsFully() {
        List<String> started = new ArrayList<>();
        ScanQueue queue = new ScanQueue(3);
        for (int i = 0; i < 10; i++) {
            String name = "task" + i;
            queue.submit(() -> started.add(name));
        }
        for (int i = 0; i < 10; i++) {
            queue.completed();
        }

        assertEquals(10, started.size());
        assertEquals(0, queue.inFlight());
        assertEquals(0, queue.remaining());
    }

    @Test
    void aTaskThatThrowsStillCountsAsInFlightSoTheQueueCannotStall() {
        // The session calls completed() from the answer callback, so a task that blows up
        // on submission must not leave the slot occupied forever.
        ScanQueue queue = new ScanQueue(1);
        assertThrows(RuntimeException.class, () -> queue.submit(() -> {
            throw new RuntimeException("boom");
        }));
        assertEquals(0, queue.inFlight());

        List<String> started = new ArrayList<>();
        queue.submit(() -> started.add("next"));
        assertEquals(List.of("next"), started);
    }

    @Test
    void completingMoreThanWasStartedIsHarmless() {
        ScanQueue queue = new ScanQueue(2);
        queue.completed();
        queue.completed();
        assertEquals(0, queue.inFlight());
    }

    @Test
    void theCapMustBePositive() {
        assertThrows(IllegalArgumentException.class, () -> new ScanQueue(0));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ScanQueueTest'`
Expected: FAIL — `ScanQueue` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.w0x7y.justtiers.scan;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Keeps a scan from arriving at a leaderboard all at once. Work is handed in as fast as
 * the caller likes; at most {@code maxInFlight} of it is running, and each completion
 * releases exactly one waiter.
 *
 * <p>Owns no threads and performs no I/O: the caller runs the tasks and reports back.
 * Everything here is touched from the client thread only, so nothing is synchronised —
 * which is also what makes "never exceeds the cap" a unit test rather than a stress test.
 */
public final class ScanQueue {

    private final int maxInFlight;
    private final Deque<Runnable> waiting = new ArrayDeque<>();
    private int inFlight;

    public ScanQueue(int maxInFlight) {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("in-flight cap must be positive: " + maxInFlight);
        }
        this.maxInFlight = maxInFlight;
    }

    /** Runs the task now if there is room, and otherwise when room appears. */
    public void submit(Runnable task) {
        waiting.addLast(task);
        pump();
    }

    /** One task has finished — successfully or not — freeing its slot. */
    public void completed() {
        if (inFlight > 0) {
            inFlight--;
        }
        pump();
    }

    public int inFlight() {
        return inFlight;
    }

    public int remaining() {
        return waiting.size();
    }

    private void pump() {
        while (inFlight < maxInFlight && !waiting.isEmpty()) {
            Runnable task = waiting.pollFirst();
            inFlight++;
            try {
                task.run();
            } catch (RuntimeException error) {
                // The slot was never really occupied: nothing is in flight to report back.
                inFlight--;
                throw error;
            }
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ScanQueueTest'`
Expected: PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/scan/ScanQueue.java \
        src/test/java/com/w0x7y/justtiers/scan/ScanQueueTest.java
git commit -m "Cap how much of a scan is in flight at once"
```

---

### Task 5: Scroll and row geometry

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/layout/ScanLayout.java`
- Test: `src/test/java/com/w0x7y/justtiers/gui/layout/ScanLayoutTest.java`

**Interfaces:**
- Produces:
  - `ScanLayout.of(int rowCount, int rowHeight, int viewportHeight) -> ScanLayout`
  - `contentHeight() / maxScroll() / clampScroll(int) / firstVisible(int scroll)`
  - `lastVisible(int scroll) -> int` (exclusive)
  - `yOf(int index, int scroll) -> int` (relative to the viewport's top)
  - `indexAt(int y, int scroll) -> OptionalInt`
  - `columnWidth(int available, int columns, int gap) -> int`
  - `columnLeft(int left, int columnWidth, int gap, int index) -> int`

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScanLayoutTest {

    private static ScanLayout layout() {
        return ScanLayout.of(10, 20, 100);   // 200px of rows in a 100px viewport
    }

    @Test
    void contentIsAsTallAsItsRows() {
        assertEquals(200, layout().contentHeight());
    }

    @Test
    void scrollStopsAtTheEndOfTheContent() {
        assertEquals(100, layout().maxScroll());
        assertEquals(0, layout().clampScroll(-40));
        assertEquals(100, layout().clampScroll(500));
        assertEquals(60, layout().clampScroll(60));
    }

    @Test
    void contentShorterThanTheViewportDoesNotScroll() {
        ScanLayout small = ScanLayout.of(2, 20, 100);
        assertEquals(0, small.maxScroll());
        assertEquals(0, small.clampScroll(50));
    }

    @Test
    void onlyVisibleRowsAreDrawn() {
        ScanLayout layout = layout();
        assertEquals(0, layout.firstVisible(0));
        assertEquals(5, layout.lastVisible(0));

        // Scrolled by half a row: the first row is clipped but still on screen.
        assertEquals(0, layout.firstVisible(10));
        assertEquals(6, layout.lastVisible(10));

        assertEquals(3, layout.firstVisible(70));
        assertEquals(9, layout.lastVisible(70));
    }

    @Test
    void aRowSitsWhereTheScrollPutIt() {
        ScanLayout layout = layout();
        assertEquals(0, layout.yOf(0, 0));
        assertEquals(40, layout.yOf(2, 0));
        assertEquals(20, layout.yOf(2, 20));
        assertEquals(-10, layout.yOf(0, 10));
    }

    @Test
    void aClickFindsItsRow() {
        ScanLayout layout = layout();
        assertEquals(OptionalInt.of(0), layout.indexAt(5, 0));
        assertEquals(OptionalInt.of(1), layout.indexAt(25, 0));
        assertEquals(OptionalInt.of(2), layout.indexAt(5, 40));
    }

    @Test
    void aClickOutsideTheContentHitsNothing() {
        ScanLayout layout = layout();
        assertFalse(layout.indexAt(-1, 0).isPresent());
        assertFalse(layout.indexAt(120, 0).isPresent());
        // Scrolled to the bottom, the content fills the viewport exactly, so every
        // point in it is still on a row.
        assertTrue(layout.indexAt(95, 100).isPresent());

        // Two rows in a hundred pixels: the empty space below them hits nothing.
        ScanLayout short_ = ScanLayout.of(2, 20, 100);
        assertTrue(short_.indexAt(39, 0).isPresent());
        assertFalse(short_.indexAt(40, 0).isPresent());
        assertFalse(short_.indexAt(90, 0).isPresent());
    }

    @Test
    void columnsDivideTheSpaceEvenly() {
        assertEquals(100, ScanLayout.columnWidth(310, 3, 5));
        assertEquals(0, ScanLayout.columnLeft(0, 100, 5, 0));
        assertEquals(105, ScanLayout.columnLeft(0, 100, 5, 1));
        assertEquals(230, ScanLayout.columnLeft(20, 100, 5, 2));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests '*ScanLayoutTest'`
Expected: FAIL — `ScanLayout` does not exist.

- [ ] **Step 3: Write minimal implementation**

```java
package com.w0x7y.justtiers.gui.layout;

import java.util.OptionalInt;

/**
 * Where the rows of a scan sit, and which of them a scrolled viewport can see. Rows are
 * a fixed height, so all of this is arithmetic — and being arithmetic rather than
 * drawing, "a click in the gap below the last row selects nothing" is a unit test.
 *
 * <p>All y coordinates are relative to the viewport's own top-left, and a scroll offset
 * is how far the content has moved up past it.
 */
public final class ScanLayout {

    private final int rowCount;
    private final int rowHeight;
    private final int viewportHeight;

    private ScanLayout(int rowCount, int rowHeight, int viewportHeight) {
        this.rowCount = Math.max(0, rowCount);
        this.rowHeight = Math.max(1, rowHeight);
        this.viewportHeight = Math.max(0, viewportHeight);
    }

    public static ScanLayout of(int rowCount, int rowHeight, int viewportHeight) {
        return new ScanLayout(rowCount, rowHeight, viewportHeight);
    }

    public int rowCount() {
        return rowCount;
    }

    public int rowHeight() {
        return rowHeight;
    }

    public int contentHeight() {
        return rowCount * rowHeight;
    }

    public int maxScroll() {
        return Math.max(0, contentHeight() - viewportHeight);
    }

    public int clampScroll(int scroll) {
        return Math.clamp(scroll, 0, maxScroll());
    }

    /** The first row with any pixel on screen; it may be clipped at the top. */
    public int firstVisible(int scroll) {
        return Math.clamp(clampScroll(scroll) / rowHeight, 0, Math.max(0, rowCount));
    }

    /** One past the last row with any pixel on screen. */
    public int lastVisible(int scroll) {
        int bottom = clampScroll(scroll) + viewportHeight;
        return Math.clamp(Math.ceilDiv(bottom, rowHeight), 0, rowCount);
    }

    public int yOf(int index, int scroll) {
        return index * rowHeight - clampScroll(scroll);
    }

    /** The row under a point in the viewport, if the point is on a row at all. */
    public OptionalInt indexAt(int y, int scroll) {
        if (y < 0 || y >= viewportHeight) {
            return OptionalInt.empty();
        }
        int index = (y + clampScroll(scroll)) / rowHeight;
        return index >= 0 && index < rowCount ? OptionalInt.of(index) : OptionalInt.empty();
    }

    /** Equal columns across the available width, with a gap between each pair. */
    public static int columnWidth(int available, int columns, int gap) {
        if (columns < 1) {
            return 0;
        }
        return Math.max(0, (available - (columns - 1) * gap) / columns);
    }

    public static int columnLeft(int left, int columnWidth, int gap, int index) {
        return left + index * (columnWidth + gap);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests '*ScanLayoutTest'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/gui/layout/ScanLayout.java \
        src/test/java/com/w0x7y/justtiers/gui/layout/ScanLayoutTest.java
git commit -m "Row geometry and scrolling for the scan screen"
```

---

### Task 6: Enumerating the lobby, and the session that scans it

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/api/OnlinePlayers.java`
- Create: `src/main/java/com/w0x7y/justtiers/gui/ScanSession.java`
- Test: `src/test/java/com/w0x7y/justtiers/api/OnlinePlayersTest.java` — **not created.**
  `OnlinePlayers` reads `Minecraft.getInstance()`, so it has no unit test today and gains
  none here; `ScanSession` is likewise client-thread-bound. Both are verified manually in
  Task 8. Everything they decide that *can* be tested already lives in `scan`.

**Interfaces:**
- Consumes: `ScanQueue`, `ScanRow`, `ScanReport`, `TierResolver.activeOnly`,
  `LookupReport.section`, `JustTiersClient.cache()`, `PlayerRef`.
- Produces:
  - `OnlinePlayers.all() -> List<PlayerRef>`
  - `ScanSession.start() -> ScanSession`
  - `ScanSession.rows() -> List<ScanRow>` (pre-sorted, immutable)
  - `ScanSession.answered() / total() / complete() / error()`

- [ ] **Step 1: Add `OnlinePlayers.all()`**

```java
    /**
     * Everyone on the server holding a real account UUID, in tab-list order — the set a
     * scan covers. The v4 filter is the one {@link #find} already applies: offline-mode
     * and proxy servers mint v3 UUIDs, which the leaderboards are not keyed by, so those
     * players are left out rather than listed as permanently unranked.
     */
    public static List<PlayerRef> all() {
        List<PlayerRef> players = new ArrayList<>();
        for (GameProfile profile : profiles()) {
            if (profile.name() != null && !profile.name().isBlank()
                    && profile.id() != null && profile.id().version() == 4) {
                players.add(new PlayerRef(profile.name(), profile.id()));
            }
        }
        return List.copyOf(players);
    }
```

- [ ] **Step 2: Write `ScanSession`**

```java
package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.api.OnlinePlayers;
import com.w0x7y.justtiers.api.PlayerRef;
import com.w0x7y.justtiers.lookup.LookupReport;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.resolve.TierResolver;
import com.w0x7y.justtiers.scan.ScanQueue;
import com.w0x7y.justtiers.scan.ScanReport;
import com.w0x7y.justtiers.scan.ScanRow;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One run of {@code /justtiers scan}: every player on the server, what each site has said
 * about them so far, and the order that puts the dangerous ones on top.
 *
 * <p>Threading follows {@link LookupSession} exactly — written and read on the client
 * thread, with answers handed over by {@link Minecraft#execute} — so nothing here needs
 * synchronising and the screen can draw {@link #rows()} without copying it.
 *
 * <p>NovaTiers is answered from the index already in memory, so the screen opens
 * populated and sorted. MCTiers and SubTiers are per-player HTTP calls and go through a
 * {@link ScanQueue}: a full lobby is several hundred requests, and arriving all at once
 * is how a client mod gets a leaderboard's rate limiter pointed at it.
 */
public final class ScanSession {

    /** Enough that a full lobby lands in seconds; few enough to stay a polite guest. */
    private static final int MAX_IN_FLIGHT = 6;

    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final ScanQueue queue = new ScanQueue(MAX_IN_FLIGHT);

    private List<ScanRow> rows = List.of();
    private Component error;
    private int answered;

    /** One player's answers as they arrive, before they are frozen into a {@link ScanRow}. */
    private record Entry(PlayerRef player, Map<Source, LookupSection> sections) {
    }

    private ScanSession() {
    }

    public static ScanSession start() {
        ScanSession session = new ScanSession();
        session.begin();
        return session;
    }

    private void begin() {
        if (Minecraft.getInstance().getConnection() == null) {
            error = Component.translatable("justtiers.scan.empty.noServer");
            return;
        }

        List<PlayerRef> players = OnlinePlayers.all();
        if (players.isEmpty()) {
            error = Component.translatable("justtiers.scan.empty.noPlayers");
            return;
        }
        for (PlayerRef player : players) {
            entries.put(player.uuid(), new Entry(player, new EnumMap<>(Source.class)));
        }
        rebuild();

        // NovaTiers first and unqueued: it is a map lookup, and putting it behind the
        // in-flight cap would make the screen open blank for no reason at all.
        for (PlayerRef player : players) {
            request(Source.NOVATIERS, player, false);
        }
        for (PlayerRef player : players) {
            queue.submit(() -> request(Source.MCTIERS, player, true));
            queue.submit(() -> request(Source.SUBTIERS, player, true));
        }
    }

    private void request(Source site, PlayerRef player, boolean queued) {
        JustTiersClient.cache().load(site, player.uuid()).handle((tiers, failure) -> {
            Optional<Map<String, Tier>> answer;
            if (failure != null) {
                // Same rule as a lookup: nothing will peek at these players to clear a
                // cached failure, so drop it and let re-opening the screen retry.
                JustTiersClient.cache().forgetFailed(site, player.uuid());
                answer = Optional.empty();
            } else {
                answer = Optional.of(TierResolver.activeOnly(tiers));
            }
            onClient(() -> {
                accept(site, player, answer);
                if (queued) {
                    queue.completed();
                }
            });
            return null;
        });
    }

    /**
     * Retired placements were stripped before this ran, so they can neither score nor
     * reach the grid. A scan asks who is a threat now; nobody is defending a retired tier.
     */
    private void accept(Source site, PlayerRef player, Optional<Map<String, Tier>> answer) {
        Entry entry = entries.get(player.uuid());
        if (entry == null) {
            return;
        }
        boolean wasComplete = entry.sections().size() == Source.ALL.size();
        entry.sections().put(site, LookupReport.section(site, answer));
        if (!wasComplete && entry.sections().size() == Source.ALL.size()) {
            answered++;
        }
        rebuild();
    }

    /** Rebuilt and re-sorted on every answer, which is cheap next to one HTTP round trip. */
    private void rebuild() {
        List<ScanRow> built = new ArrayList<>(entries.size());
        for (Entry entry : entries.values()) {
            built.add(ScanRow.of(entry.player(), entry.sections()));
        }
        rows = ScanReport.sorted(built);
    }

    /** Pre-sorted and immutable: the screen draws this straight, every frame. */
    public List<ScanRow> rows() {
        return rows;
    }

    /** Players every site has now answered for. */
    public int answered() {
        return answered;
    }

    public int total() {
        return entries.size();
    }

    public boolean complete() {
        return !entries.isEmpty() && answered == entries.size();
    }

    /** The message to show instead of any rows, when there is nobody to scan. */
    public Optional<Component> error() {
        return Optional.ofNullable(error);
    }

    private static void onClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL. (No new unit tests here; the suite must still pass, which
Step 4 checks.)

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test`
Expected: PASS — nothing regressed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/api/OnlinePlayers.java \
        src/main/java/com/w0x7y/justtiers/gui/ScanSession.java
git commit -m "Drive a lobby-wide scan through a bounded queue"
```

---

### Task 7: The screen

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/ScanScreen.java`
- Modify: `src/main/resources/assets/justtiers/lang/en_us.json`

**Interfaces:**
- Consumes: `ScanSession`, `ScanRow`, `ScanLayout`, `GridLayout`, `SkinLayout`,
  `PlayerSkins`, `Colors`, `PlayerLookupScreen`.
- Produces: `new ScanScreen()`.

- [ ] **Step 1: Add the strings**

Add to `en_us.json`:

```json
  "justtiers.scan.title": "Lobby scan",
  "justtiers.scan.progress": "%s / %s",
  "justtiers.scan.points": "Points",
  "justtiers.scan.waiting": "Looking up...",
  "justtiers.scan.unavailable": "site unavailable",
  "justtiers.scan.empty.noServer": "Join a server to scan the players on it.",
  "justtiers.scan.empty.noPlayers": "There is nobody here to scan.",
  "justtiers.command.scan": "Scanning %s players..."
```

- [ ] **Step 2: Write the screen**

```java
package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.gui.layout.GridLayout;
import com.w0x7y.justtiers.gui.layout.ScanLayout;
import com.w0x7y.justtiers.gui.layout.SkinLayout;
import com.w0x7y.justtiers.lookup.LookupCell;
import com.w0x7y.justtiers.lookup.LookupSection;
import com.w0x7y.justtiers.scan.ScanRow;
import com.w0x7y.justtiers.tier.Gamemodes;
import com.w0x7y.justtiers.tier.Source;
import com.w0x7y.justtiers.tier.Tier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The screen {@code /justtiers scan} opens: everyone on the server, scored out of every
 * placement they hold on all three sites, most dangerous first.
 *
 * <p>Everything drawn lives in a {@link ScanSession}, which hands over a list that is
 * already sorted and immutable, so this class never sorts, never waits and never copies.
 * The list re-orders itself under the cursor for the first seconds of a scan; the
 * progress readout in the header is what explains that.
 */
public final class ScanScreen extends Screen {

    private static final int MARGIN = 8;
    private static final int HEADER_HEIGHT = 34;
    private static final int FOOTER_HEIGHT = 32;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_PADDING = 4;
    private static final int COLUMN_GAP = 6;
    private static final int CELL_GAP = 2;
    private static final int CELL_TEXT_GAP = 2;
    private static final int CELL_SIDE_PADDING = 3;
    private static final int NAME_GAP = 4;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int HEAD_SCALE = 2;
    private static final int HEAD_PIXELS = 8;
    private static final int SKIN_SIZE = 64;

    private static final int PANEL_BACKGROUND = 0xC0000000;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int SEPARATOR = 0xFF3A3A3A;
    private static final int CELL_BACKGROUND = 0x40000000;
    private static final int ROW_HOVERED = 0x20FFFFFF;
    private static final int SCROLLBAR = 0xFF6A6A6A;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int POINTS_COLOR = 0xFFFFFFFF;
    private static final int UNAVAILABLE_COLOR = 0xFFFF5555;

    private static final String WIDEST_LABEL = "RHT5";
    private static final String NOT_TESTED = "---";

    private final ScanSession session;
    private final Map<UUID, PlayerSkin> skins = new ConcurrentHashMap<>();
    private final Map<Source, GridLayout> grids = new EnumMap<>(Source.class);

    private ScanLayout layout = ScanLayout.of(0, 1, 0);
    private int scroll;
    private int listTop;
    private int listBottom;
    private int listLeft;
    private int listRight;
    private int nameColumnWidth;
    private int columnWidth;
    private int rowHeight;
    private int cellWidth;
    private int cellHeight;
    private int iconWidth;

    public ScanScreen() {
        super(Component.translatable("justtiers.scan.title"));
        this.session = ScanSession.start();
    }

    @Override
    protected void init() {
        measure();
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .pos(width / 2 - 50, height - MARGIN - BUTTON_HEIGHT)
                .size(100, BUTTON_HEIGHT).build());
    }

    // ---------------------------------------------------------------- layout

    private void measure() {
        iconWidth = font.width("█");
        cellWidth = iconWidth + CELL_TEXT_GAP + font.width(WIDEST_LABEL)
                + 2 * CELL_SIDE_PADDING;
        cellHeight = font.lineHeight + 4;

        // The name column has to hold the widest name a lobby can throw at it, and a
        // three-digit points total underneath.
        nameColumnWidth = HEAD_PIXELS * HEAD_SCALE + NAME_GAP + font.width("MMMMMMMMMMMMMMMM");

        listLeft = MARGIN;
        listRight = width - MARGIN;
        listTop = MARGIN + HEADER_HEIGHT;
        listBottom = height - FOOTER_HEIGHT - MARGIN;

        int available = listRight - listLeft - nameColumnWidth - SCROLLBAR_WIDTH
                - 2 * ROW_PADDING;
        columnWidth = ScanLayout.columnWidth(available, Source.ALL.size(), COLUMN_GAP);

        int tallest = 0;
        grids.clear();
        for (Source source : Source.ALL) {
            int count = Gamemodes.of(source).size();
            GridLayout grid = GridLayout.of(count, columnWidth, cellWidth, cellHeight,
                    CELL_GAP, count);
            grids.put(source, grid);
            tallest = Math.max(tallest, grid.rows() * (cellHeight + CELL_GAP) - CELL_GAP);
        }

        rowHeight = Math.max(tallest, SkinLayout.HEIGHT) + 2 * ROW_PADDING;
        rebuildLayout();
    }

    private void rebuildLayout() {
        layout = ScanLayout.of(session.rows().size(), rowHeight, listBottom - listTop);
        scroll = layout.clampScroll(scroll);
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics,
                                   int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        rebuildLayout();

        graphics.fill(listLeft, listTop, listRight, listBottom, PANEL_BACKGROUND);
        graphics.outline(listLeft, listTop, listRight - listLeft, listBottom - listTop,
                PANEL_BORDER);
        drawHeader(graphics);

        Optional<Component> error = session.error();
        if (error.isPresent()) {
            graphics.centeredText(font, error.get(), width / 2,
                    (listTop + listBottom) / 2, Colors.SECONDARY);
            return;
        }

        graphics.enableScissor(listLeft + 1, listTop + 1, listRight - 1, listBottom - 1);
        drawRows(graphics, mouseX, mouseY);
        graphics.disableScissor();
        drawScrollbar(graphics);
    }

    private void drawHeader(GuiGraphicsExtractor graphics) {
        graphics.centeredText(font, title, width / 2, MARGIN + 2, NAME_COLOR);

        if (!session.complete() && session.total() > 0) {
            Component progress = Component.translatable("justtiers.scan.progress",
                    String.valueOf(session.answered()), String.valueOf(session.total()));
            graphics.text(font, progress,
                    listRight - font.width(progress), MARGIN + 2, Colors.SECONDARY);
        }

        int labelY = listTop - font.lineHeight - 2;
        int columnsLeft = listLeft + ROW_PADDING + nameColumnWidth + COLUMN_GAP;
        for (int i = 0; i < Source.ALL.size(); i++) {
            Source source = Source.ALL.get(i);
            int left = ScanLayout.columnLeft(columnsLeft, columnWidth, COLUMN_GAP, i);
            String name = source.displayName();
            graphics.text(font, name, left + (columnWidth - font.width(name)) / 2, labelY,
                    Colors.opaque(source.color()));
        }
    }

    private void drawRows(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<ScanRow> rows = session.rows();
        OptionalInt hovered = rowAt(mouseX, mouseY);

        for (int i = layout.firstVisible(scroll); i < layout.lastVisible(scroll); i++) {
            ScanRow row = rows.get(i);
            int y = listTop + layout.yOf(i, scroll);

            if (hovered.isPresent() && hovered.getAsInt() == i) {
                graphics.fill(listLeft + 1, y, listRight - SCROLLBAR_WIDTH - 1,
                        y + rowHeight, ROW_HOVERED);
            }
            if (i > 0) {
                graphics.horizontalLine(listLeft + 1, listRight - 2, y, SEPARATOR);
            }

            drawIdentity(graphics, row, listLeft + ROW_PADDING, y + ROW_PADDING);
            drawSections(graphics, row,
                    listLeft + ROW_PADDING + nameColumnWidth + COLUMN_GAP, y + ROW_PADDING);
        }
    }

    /** The head, the name, and what the whole scan is sorted by. */
    private void drawIdentity(GuiGraphicsExtractor graphics, ScanRow row, int x, int y) {
        drawHead(graphics, row, x, y);

        int textX = x + HEAD_PIXELS * HEAD_SCALE + NAME_GAP;
        graphics.text(font, row.player().name(), textX, y, NAME_COLOR, true);
        graphics.text(font, String.valueOf(row.points()), textX, y + font.lineHeight + 2,
                POINTS_COLOR, true);
    }

    /**
     * Only the head, drawn flat and face on: the two rectangles {@link SkinLayout}
     * already describes for it, face then hat. Every scanned player is on the server, so
     * their skin is in the player list and nothing is fetched.
     */
    private void drawHead(GuiGraphicsExtractor graphics, ScanRow row, int x, int y) {
        PlayerSkin skin = skins.get(row.player().uuid());
        if (skin == null) {
            PlayerSkins.resolve(row.player())
                    .thenAccept(loaded -> skins.put(row.player().uuid(), loaded));
            return;
        }
        Identifier texture = skin.body().texturePath();
        boolean slim = skin.model() == PlayerModelType.SLIM;
        for (SkinLayout.Piece piece : SkinLayout.pieces(slim)) {
            if (piece.part() != SkinLayout.Part.HEAD) {
                continue;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, texture,
                    x, y, piece.u(), piece.v(),
                    piece.width() * HEAD_SCALE, piece.height() * HEAD_SCALE,
                    piece.width(), piece.height(), SKIN_SIZE, SKIN_SIZE);
        }
    }

    private void drawSections(GuiGraphicsExtractor graphics, ScanRow row, int left, int y) {
        for (int i = 0; i < Source.ALL.size(); i++) {
            Source source = Source.ALL.get(i);
            int x = ScanLayout.columnLeft(left, columnWidth, COLUMN_GAP, i);
            Optional<LookupSection> section = row.section(source);

            if (section.isEmpty()) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.scan.waiting"),
                        x + columnWidth / 2, y, Colors.SECONDARY);
            } else if (section.get().status() == LookupSection.Status.UNAVAILABLE) {
                graphics.centeredText(font,
                        Component.translatable("justtiers.scan.unavailable"),
                        x + columnWidth / 2, y, UNAVAILABLE_COLOR);
            } else {
                drawCells(graphics, section.get(), grids.get(source), x, y);
            }
        }
    }

    private void drawCells(GuiGraphicsExtractor graphics, LookupSection section,
                           GridLayout grid, int left, int top) {
        List<LookupCell> cells = section.cells();
        int inset = (columnWidth - grid.contentWidth()) / 2;
        for (int i = 0; i < cells.size() && i < grid.itemCount(); i++) {
            drawCell(graphics, cells.get(i), section.source(),
                    left + inset + grid.xOf(i), top + grid.yOf(i));
        }
    }

    private void drawCell(GuiGraphicsExtractor graphics, LookupCell cell, Source source,
                          int x, int y) {
        graphics.fill(x, y, x + cellWidth, y + cellHeight, CELL_BACKGROUND);

        Optional<Tier> tier = cell.tier();
        String label = tier.map(Tier::label).orElse(NOT_TESTED);
        String icon = String.valueOf(cell.gamemode().icon());
        int contentWidth = iconWidth + CELL_TEXT_GAP + font.width(label);
        int textX = x + (cellWidth - contentWidth) / 2;
        int textY = y + (cellHeight - font.lineHeight) / 2 + 1;

        // Bitmap glyphs are multiplied by the text colour, so the icon stays white.
        graphics.text(font, icon, textX + (iconWidth - font.width(icon)) / 2, textY,
                0xFFFFFFFF, false);
        graphics.text(font, label, textX + iconWidth + CELL_TEXT_GAP, textY,
                tier.isPresent() ? Colors.opaque(source.color()) : Colors.DISABLED, false);
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics) {
        if (layout.maxScroll() == 0) {
            return;
        }
        int track = listBottom - listTop;
        int thumb = Math.max(16, track * track / Math.max(1, layout.contentHeight()));
        int travel = track - thumb;
        int y = listTop + (travel * scroll) / layout.maxScroll();
        graphics.fill(listRight - SCROLLBAR_WIDTH - 1, y,
                listRight - 1, y + thumb, SCROLLBAR);
    }

    // ---------------------------------------------------------------- input

    private OptionalInt rowAt(double mouseX, double mouseY) {
        if (mouseX < listLeft || mouseX >= listRight - SCROLLBAR_WIDTH) {
            return OptionalInt.empty();
        }
        return layout.indexAt((int) mouseY - listTop, scroll);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= listLeft && mouseX < listRight
                && mouseY >= listTop && mouseY < listBottom) {
            scroll = layout.clampScroll(scroll - (int) (scrollY * rowHeight / 2));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            OptionalInt index = rowAt(event.x(), event.y());
            if (index.isPresent()) {
                ScanRow row = session.rows().get(index.getAsInt());
                minecraft.setScreen(new PlayerLookupScreen(row.player().name()));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
```

- [ ] **Step 3: Compile**

Run: `./gradlew build -x test`
Expected: BUILD SUCCESSFUL.

If `Gamemode.icon()` returns something other than a `char`, or `Source.displayName()` /
`Source.color()` are named differently, match `PlayerLookupScreen` — it uses all four and
is the authority.

- [ ] **Step 4: Run the whole suite**

Run: `./gradlew test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/gui/ScanScreen.java \
        src/main/resources/assets/justtiers/lang/en_us.json
git commit -m "Draw the lobby scan screen"
```

---

### Task 8: The command, the docs, and seeing it work

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/command/JustTiersCommands.java`
- Modify: `README.md`

**Interfaces:**
- Consumes: `ScanScreen`.

- [ ] **Step 1: Register the command**

In `JustTiersCommands.register()`, alongside `literal("gui")`:

```java
                        .then(literal("scan").executes(JustTiersCommands::openScan))
```

And, next to the existing `openGui` method — note the deferred `setScreen`, which is the
pattern `openGui` and `lookup` already use, because a screen cannot be opened from inside
command execution:

```java
    private static int openScan(CommandContext<FabricClientCommandSource> context) {
        net.minecraft.client.Minecraft.getInstance()
                .execute(() -> net.minecraft.client.Minecraft.getInstance()
                        .setScreen(new ScanScreen()));
        return 1;
    }
```

Match however `openGui` actually spells this — if it holds a `Minecraft` local or imports
the class, do the same rather than fully-qualifying.

- [ ] **Step 2: Compile and run the suite**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 3: Verify it in game**

Run: `./gradlew runClient`, join a populated server, run `/justtiers scan`.

Confirm each of these:
- The screen opens already populated and sorted, on NovaTiers data alone.
- The `answered / total` readout climbs and then disappears.
- Rows re-order as MCTiers and SubTiers land, then settle.
- No retired tier (`R`-prefixed) appears anywhere, and toggling `/justtiers retired`
  changes nothing on this screen.
- Scrolling works by wheel; the scrollbar tracks it; the list clamps at both ends.
- Clicking a row opens that player's lookup screen; Escape returns to the scan with its
  rows and progress intact.
- On the title screen, `/justtiers scan` is unavailable; in singleplayer it says there is
  nobody to scan.

- [ ] **Step 4: Document it**

In `README.md`:
- Add to the Commands table: `` `/justtiers scan` `` — "Rank everyone on the server by
  their tiers across all three sites".
- Add a `## Scanning a lobby` section after `## Looking a player up`, covering: the points
  scale (HT1 = 10 down to LT5 = 1, summed over every gamemode on every site), that retired
  placements are excluded here and why, that the list fills and re-sorts live, that the row
  set is fixed when the screen opens, and that clicking a row opens the lookup screen.
- Add to the Features list: one line for the scan screen.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/command/JustTiersCommands.java README.md
git commit -m "Add /justtiers scan and document it"
```

---

## Self-Review Notes

Spec coverage: scoring (Task 1), retired stripping (Tasks 2, 6), row model and ordering
(Task 3), the six-at-a-time cap (Tasks 4, 6), `OnlinePlayers.all` and the session
(Task 6), layout and scrolling (Task 5), the screen and its strings (Task 7), the command
and README (Task 8).

Deviation from the spec, deliberate: the spec listed two `TierPoints.total` overloads. Only
the `Collection<LookupSection>` one is built — the session assembles sections before it
scores, so the raw-map overload would have no caller.
