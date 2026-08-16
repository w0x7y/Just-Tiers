# NovaTiers Download Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a small progress indicator in the bottom-left corner, above the chat, while the NovaTiers bulk list is downloading — so a user whose NovaTiers badges have not appeared can tell "still downloading" from "site is down".

**Architecture:** A counting `BodySubscriber` feeds byte counts into one thread-safe `DownloadProgress` holder, which the renderer snapshots each frame. Both Fabric entry points — the in-game HUD layer and the screen overlay — receive a `GuiGraphicsExtractor`, so one drawing routine serves both. State, geometry and formatting live in Minecraft-free packages and are unit-tested; only `DownloadHud` touches `net.minecraft.*`.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API `0.157.0+26.2`, JUnit 5. **No new dependencies.**

**Spec:** [`docs/superpowers/specs/2026-08-14-nova-download-progress-design.md`](../specs/2026-08-14-nova-download-progress-design.md)

## Global Constraints

- **No disk cache.** Nothing is written to disk beyond the one new config key. Tier data stays session-only.
- **No behaviour change to the download.** `NovaTiersSource` keeps its semantics in full: a failed refresh keeps the previous index, refreshes do not blank badges mid-flight, `refresh()` still returns a future that never fails. The only change is the body handler.
- **Minecraft-free packages stay Minecraft-free.** `tier`, `api`, `cache`, `resolve`, `render.model`, `preview`, `gui.layout`, `gui.state` — and the new `download` package — must not import `net.minecraft.*`. Only `gui` is Minecraft-facing.
- **The render thread never blocks.** `snapshot()` is a read of atomic fields returning an immutable record. No locks, no I/O, no collection walks on the render path.
- **One drawing routine, two registrations.** The HUD layer and the screen overlay must not grow separate ideas of what the bar looks like.
- **Color discipline.** The fill is NovaTiers purple `0xAA55FF`, because this indicator is about NovaTiers and color in this UI means exactly one thing: which leaderboard. Text white, failure `0xFF5555`.
- **Every existing test stays green.** The baseline is **130 tests across 13 classes** (verified by running `./gradlew test` on 2026-08-14; the config-GUI plan's "126 across 12" was already stale). Nothing in this plan changes nametag behaviour.

---

## Divergences from the spec

Two design details changed once the 26.2 jars were inspected. **Where this plan and the spec disagree, this plan is correct.**

- **Chat focus is not detectable.** The spec's placement rule reads
  `focused ? chatHeightFocused : chatHeightUnfocused`. In 26.2 `Gui` exposes no
  `ChatComponent` accessor and `Minecraft` exposes no current-screen accessor, so
  `ChatComponent.isChatFocused()` is unreachable without a mixin or bookkeeping through
  `ScreenEvents`. **This plan reserves the focused height unconditionally.** It costs a few
  pixels of extra clearance when chat is closed and buys a bar that never jumps when chat
  opens — arguably the better behaviour anyway.
- **`finished()` takes no argument.** The spec listed `finished(long)`. Deriving the total
  from the counter that was just incremented removes the chance of the two disagreeing.

### Changed during execution, on 2026-08-14

The code below shows the bar in the **bottom-left**. It was moved to the **bottom-right**
after seeing it in-game, at the author's request. Where the code blocks below and the
implementation disagree, **the implementation is correct**.

- **Bottom-right, not bottom-left.** Purely a look-and-feel call, but it made the design
  simpler: chat grows upward from the bottom-*left*, so the whole chat-reserve mechanism
  became unnecessary. `chatReserve()`, `CHAT_INPUT_ALLOWANCE` and the `ChatComponent`
  import were deleted, and with them the one approximation this feature carried. Placement
  is now `guiWidth() - RIGHT_MARGIN - boxWidth` and `guiHeight() - BOTTOM_GAP`.
- **`TRACK_WIDTH` is 180, not 120.** It reads better wider.
- **F1 needs no guard — confirmed in-game.** The assumption recorded below held: Minecraft
  skips the HUD render when the GUI is hidden, so Fabric never invokes the element. No
  hide-GUI flag had to be found.

---

## Verified API Reference

Confirmed on 2026-08-14 against `fabric-api-0.157.0+26.2.jar` and `~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`. **Do not re-derive these.**

```java
// net.fabricmc.fabric.api.client.rendering.v1.hud
static void HudElementRegistry.addLast(Identifier, HudElement);
interface HudElement { void extractRenderState(GuiGraphicsExtractor, DeltaTracker); }

// net.fabricmc.fabric.api.client.screen.v1
static final Event<AfterInit> ScreenEvents.AFTER_INIT;      // (Minecraft, Screen, int, int)
static Event<AfterForeground> ScreenEvents.afterForeground(Screen);
                                                            // (Screen, GuiGraphicsExtractor, int, int, float)

// net.minecraft.client.gui.GuiGraphicsExtractor
int guiWidth(); int guiHeight();
void fill(int x1, int y1, int x2, int y2, int argb);
void text(Font, String, int x, int y, int argb);
void text(Font, Component, int x, int y, int argb);

// net.minecraft.client.gui.Font
public final int lineHeight;
int width(String);

// net.minecraft.client.gui.components.ChatComponent
static int getHeight(double);            // static — no instance needed

// net.minecraft.client.Options
OptionInstance<Double> chatScale(); chatHeightFocused(); chatHeightUnfocused();

// net.minecraft.client.Minecraft
static Minecraft getInstance();
public final Font font;
public ClientLevel level;                // null on the title screen / main menu

// net.minecraft.resources.Identifier
static Identifier fromNamespaceAndPath(String, String);   // NOT Identifier.of — that does not exist here
```

**The endpoint sends no size.** `curl` against `https://novatiers.com/users`, with and without `--compressed`, returned no `content-length`, no compression, and an identical **1,736,861 bytes**. This is why the first download of a session cannot show a percentage.

### Not verified — confirm at implementation time

- ~~**F1 (hide GUI).**~~ **Resolved 2026-08-14:** confirmed in-game that the bar hides on F1 with no guard, as expected. Nothing to do.
- **HUD draw order.** `addLast` is expected to put the element above chat. If it renders beneath, switch to `HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, id, element)`.
- ~~**Chat reserve constants.**~~ **Resolved 2026-08-14:** moot. The bar moved to the bottom-right, so there is no chat to clear and no constant to tune.

---

## File Structure

| File | Responsibility |
|---|---|
| `download/DownloadProgress.java` *(new)* | Thread-safe download state + self-calibrating total. Minecraft-free. |
| `download/ProgressBodyHandler.java` *(new)* | `BodyHandler<String>` that counts bytes as they arrive. Minecraft-free. |
| `gui/layout/ProgressBarLayout.java` *(new)* | Fill fraction, marquee offset, byte/percent formatting. Minecraft-free. |
| `gui/DownloadHud.java` *(new)* | The single drawing routine and both registrations. Minecraft-facing. |
| `api/NovaTiersSource.java` *(modify)* | Reports to `DownloadProgress`; swaps the body handler. |
| `JustTiersClient.java` *(modify)* | Owns the `DownloadProgress`, wires it, registers the HUD. |
| `config/JustTiersConfig.java` *(modify)* | The `showDownloadProgress` key. |
| `gui/JustTiersScreens.java` *(modify)* | Tick box on the Data category. |
| `assets/justtiers/lang/en_us.json` *(modify)* | Strings. |

---

### Task 1: `DownloadProgress` — the state holder

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/download/DownloadProgress.java`
- Test: `src/test/java/com/w0x7y/justtiers/download/DownloadProgressTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `DownloadProgress` with `started()`, `advanced(long)`, `finished()`, `failed()`, `snapshot()`; nested `enum State { IDLE, DOWNLOADING, FAILED }` and `record Snapshot(State state, long bytesRead, long total)` with `determinate()`. A package-private constructor `DownloadProgress(LongSupplier clock)` exists for tests.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.download;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class DownloadProgressTest {

    /** A clock the test drives by hand, so the failure timeout needs no sleeping. */
    private final AtomicLong now = new AtomicLong();
    private final DownloadProgress progress = new DownloadProgress(now::get);

    @Test
    void startsIdle() {
        assertEquals(DownloadProgress.State.IDLE, progress.snapshot().state());
    }

    @Test
    void reportsBytesWhileDownloading() {
        progress.started();
        progress.advanced(100);
        progress.advanced(50);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.DOWNLOADING, snapshot.state());
        assertEquals(150, snapshot.bytesRead());
    }

    @Test
    void firstDownloadIsIndeterminate() {
        progress.started();
        progress.advanced(100);
        assertFalse(progress.snapshot().determinate());
        assertEquals(0, progress.snapshot().total());
    }

    @Test
    void successCalibratesTheNextDownload() {
        progress.started();
        progress.advanced(1_000);
        progress.finished();

        progress.started();
        progress.advanced(400);

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertTrue(snapshot.determinate());
        assertEquals(1_000, snapshot.total());
        assertEquals(400, snapshot.bytesRead());
    }

    @Test
    void startedResetsTheByteCount() {
        progress.started();
        progress.advanced(1_000);
        progress.finished();

        progress.started();
        assertEquals(0, progress.snapshot().bytesRead());
    }

    @Test
    void failureDoesNotCalibrate() {
        progress.started();
        progress.advanced(1_000);
        progress.failed();

        progress.started();
        assertFalse(progress.snapshot().determinate());
    }

    @Test
    void failureIsShownThenExpires() {
        progress.started();
        progress.failed();
        assertEquals(DownloadProgress.State.FAILED, progress.snapshot().state());

        now.addAndGet(DownloadProgress.FAILURE_DISPLAY_NANOS + 1);
        assertEquals(DownloadProgress.State.IDLE, progress.snapshot().state());
    }

    @Test
    void aNewDownloadClearsAStandingFailure() {
        progress.started();
        progress.failed();
        progress.started();
        assertEquals(DownloadProgress.State.DOWNLOADING, progress.snapshot().state());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "com.w0x7y.justtiers.download.DownloadProgressTest"`
Expected: FAIL — compilation error, `DownloadProgress` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.w0x7y.justtiers.download;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Progress of the NovaTiers bulk download, written by the HTTP thread and read by the
 * render thread every frame.
 *
 * <p>novatiers.com sends no {@code content-length}, so the total size is unknown until a
 * download has finished once. The first download of a session is therefore indeterminate;
 * afterwards {@link Snapshot#total()} carries the previous download's size and the bar can
 * show a real percentage. Nothing here is persisted, so every session starts uncalibrated.
 */
public final class DownloadProgress {

    public enum State { IDLE, DOWNLOADING, FAILED }

    /** How long a failed download is reported before the indicator gives up on it. */
    static final long FAILURE_DISPLAY_NANOS = Duration.ofSeconds(4).toNanos();

    /**
     * @param total the previous download's size, or {@code 0} when none has completed yet.
     */
    public record Snapshot(State state, long bytesRead, long total) {
        /** Whether a percentage can be shown, or only a byte count. */
        public boolean determinate() {
            return total > 0;
        }
    }

    private final LongSupplier clock;
    private final AtomicLong bytesRead = new AtomicLong();
    private volatile long lastKnownTotal;
    private volatile boolean downloading;
    private volatile boolean failed;
    private volatile long failedAt;

    public DownloadProgress() {
        this(System::nanoTime);
    }

    DownloadProgress(LongSupplier clock) {
        this.clock = clock;
    }

    public void started() {
        bytesRead.set(0);
        failed = false;
        downloading = true;
    }

    public void advanced(long bytes) {
        bytesRead.addAndGet(bytes);
    }

    /**
     * Calibrates from the bytes just counted rather than from a figure passed in, so the
     * total and the counter can never disagree.
     */
    public void finished() {
        lastKnownTotal = bytesRead.get();
        downloading = false;
    }

    /** A failed download does not calibrate: a truncated body is not a size. */
    public void failed() {
        downloading = false;
        failed = true;
        failedAt = clock.getAsLong();
    }

    public Snapshot snapshot() {
        if (downloading) {
            return new Snapshot(State.DOWNLOADING, bytesRead.get(), lastKnownTotal);
        }
        if (failed && clock.getAsLong() - failedAt < FAILURE_DISPLAY_NANOS) {
            return new Snapshot(State.FAILED, bytesRead.get(), lastKnownTotal);
        }
        return new Snapshot(State.IDLE, 0, lastKnownTotal);
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "com.w0x7y.justtiers.download.DownloadProgressTest"`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/download/DownloadProgress.java \
        src/test/java/com/w0x7y/justtiers/download/DownloadProgressTest.java
git commit -m "Add DownloadProgress, the self-calibrating download state holder"
```

---

### Task 2: `ProgressBodyHandler` — counting bytes as they arrive

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/download/ProgressBodyHandler.java`
- Test: `src/test/java/com/w0x7y/justtiers/download/ProgressBodyHandlerTest.java`

**Interfaces:**
- Consumes: nothing from Task 1 (it takes a plain `LongConsumer`, so it is testable without `DownloadProgress`).
- Produces: `ProgressBodyHandler implements HttpResponse.BodyHandler<String>`, constructed as `new ProgressBodyHandler(LongConsumer onBytes)`.

**Why this class exists:** `BodyHandlers.ofString()` gives no visibility into chunks, so there is no way to observe progress with it. This wraps `BodySubscribers.ofString(UTF_8)` and counts each chunk on the way through.

**The one trap:** count `ByteBuffer.remaining()` **before** handing the buffers to the delegate. The delegate consumes them and advances their positions, so counting afterwards yields zero.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.download;

import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBodyHandlerTest {

    private final AtomicLong counted = new AtomicLong();

    /** The subscriber only needs a subscription that does not explode when requested from. */
    private static final Flow.Subscription NO_OP_SUBSCRIPTION = new Flow.Subscription() {
        @Override public void request(long n) { }
        @Override public void cancel() { }
    };

    private static final HttpResponse.ResponseInfo RESPONSE_INFO = new HttpResponse.ResponseInfo() {
        @Override public int statusCode() { return 200; }
        @Override public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
        @Override public HttpClient.Version version() { return HttpClient.Version.HTTP_2; }
    };

    private HttpResponse.BodySubscriber<String> subscriber() {
        HttpResponse.BodySubscriber<String> subscriber =
                new ProgressBodyHandler(counted::addAndGet).apply(RESPONSE_INFO);
        subscriber.onSubscribe(NO_OP_SUBSCRIPTION);
        return subscriber;
    }

    private static ByteBuffer bytes(String text) {
        return ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void countsBytesAndStillDeliversTheBody() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("hello")));
        subscriber.onComplete();

        assertEquals("hello", subscriber.getBody().toCompletableFuture().get());
        assertEquals(5, counted.get());
    }

    @Test
    void sumsAcrossChunks() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("abc"), bytes("de")));
        subscriber.onNext(List.of(bytes("fgh")));
        subscriber.onComplete();

        assertEquals("abcdefgh", subscriber.getBody().toCompletableFuture().get());
        assertEquals(8, counted.get());
    }

    @Test
    void countsNothingForAnEmptyBody() throws Exception {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onComplete();

        assertEquals("", subscriber.getBody().toCompletableFuture().get());
        assertEquals(0, counted.get());
    }

    @Test
    void propagatesErrors() {
        HttpResponse.BodySubscriber<String> subscriber = subscriber();
        subscriber.onNext(List.of(bytes("partial")));
        subscriber.onError(new java.io.IOException("connection reset"));

        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "com.w0x7y.justtiers.download.ProgressBodyHandlerTest"`
Expected: FAIL — compilation error, `ProgressBodyHandler` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.w0x7y.justtiers.download;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.function.LongConsumer;

/**
 * Reads a response body as a string while reporting how many bytes have arrived.
 *
 * <p>{@code BodyHandlers.ofString()} exposes no chunk boundaries, so progress cannot be
 * observed through it. This delegates to the same subscriber and counts on the way past.
 */
public final class ProgressBodyHandler implements HttpResponse.BodyHandler<String> {

    private final LongConsumer onBytes;

    public ProgressBodyHandler(LongConsumer onBytes) {
        this.onBytes = onBytes;
    }

    @Override
    public HttpResponse.BodySubscriber<String> apply(HttpResponse.ResponseInfo responseInfo) {
        return new CountingSubscriber(
                HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8), onBytes);
    }

    private static final class CountingSubscriber implements HttpResponse.BodySubscriber<String> {

        private final HttpResponse.BodySubscriber<String> delegate;
        private final LongConsumer onBytes;

        CountingSubscriber(HttpResponse.BodySubscriber<String> delegate, LongConsumer onBytes) {
            this.delegate = delegate;
            this.onBytes = onBytes;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            // Counted before the delegate sees them: it consumes the buffers, so reading
            // remaining() afterwards would report zero.
            long total = 0;
            for (ByteBuffer item : items) {
                total += item.remaining();
            }
            onBytes.accept(total);
            delegate.onNext(items);
        }

        @Override
        public void onError(Throwable throwable) {
            delegate.onError(throwable);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
        }

        @Override
        public CompletionStage<String> getBody() {
            return delegate.getBody();
        }
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "com.w0x7y.justtiers.download.ProgressBodyHandlerTest"`
Expected: PASS, 4 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/download/ProgressBodyHandler.java \
        src/test/java/com/w0x7y/justtiers/download/ProgressBodyHandlerTest.java
git commit -m "Add ProgressBodyHandler, a counting body subscriber"
```

---

### Task 3: `ProgressBarLayout` — geometry and formatting

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/layout/ProgressBarLayout.java`
- Test: `src/test/java/com/w0x7y/justtiers/gui/layout/ProgressBarLayoutTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ProgressBarLayout.fraction(long bytesRead, long total)` → `double`; `marqueeStart(long nanoTime)` → `double`; `formatBytes(long)` → `String`; `formatPercent(double)` → `String`; constants `MAX_FRACTION` and `MARQUEE_WIDTH_FRACTION`.

Sits in `gui.layout` beside the existing `GridLayout`, which is the established home for Minecraft-free layout maths.

- [ ] **Step 1: Write the failing test**

```java
package com.w0x7y.justtiers.gui.layout;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProgressBarLayoutTest {

    @Test
    void fractionIsTheRatioOfBytesToTotal() {
        assertEquals(0.5, ProgressBarLayout.fraction(500, 1_000), 1e-9);
    }

    @Test
    void fractionIsZeroWhenTheTotalIsUnknown() {
        assertEquals(0.0, ProgressBarLayout.fraction(500, 0), 1e-9);
    }

    @Test
    void fractionNeverReachesOneWhileDownloading() {
        // The leaderboard grows, so a download can outrun last session's size. The bar
        // holds just short of full rather than overflowing or claiming to be done.
        assertEquals(ProgressBarLayout.MAX_FRACTION,
                ProgressBarLayout.fraction(2_000, 1_000), 1e-9);
        assertEquals(ProgressBarLayout.MAX_FRACTION,
                ProgressBarLayout.fraction(1_000, 1_000), 1e-9);
    }

    @Test
    void marqueeStaysWithinItsTravel() {
        for (long nanos = 0; nanos < 5_000_000_000L; nanos += 7_000_000L) {
            double start = ProgressBarLayout.marqueeStart(nanos);
            assertTrue(start >= -ProgressBarLayout.MARQUEE_WIDTH_FRACTION,
                    "start " + start + " at " + nanos);
            assertTrue(start <= 1.0, "start " + start + " at " + nanos);
        }
    }

    @Test
    void marqueeWrapsAndHandlesNegativeClocks() {
        // System.nanoTime() has an arbitrary origin and may be negative.
        double start = ProgressBarLayout.marqueeStart(-1_234_567_890L);
        assertTrue(start >= -ProgressBarLayout.MARQUEE_WIDTH_FRACTION && start <= 1.0);
    }

    @Test
    void formatsBytesAsMegabytes() {
        assertEquals("1.7 MB", ProgressBarLayout.formatBytes(1_736_861));
        assertEquals("0.0 MB", ProgressBarLayout.formatBytes(0));
    }

    @Test
    void formatsPercentWithoutRoundingUp() {
        assertEquals("50%", ProgressBarLayout.formatPercent(0.509));
        assertEquals("99%", ProgressBarLayout.formatPercent(ProgressBarLayout.MAX_FRACTION));
        assertEquals("0%", ProgressBarLayout.formatPercent(0.0));
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "com.w0x7y.justtiers.gui.layout.ProgressBarLayoutTest"`
Expected: FAIL — compilation error, `ProgressBarLayout` does not exist.

- [ ] **Step 3: Write the implementation**

```java
package com.w0x7y.justtiers.gui.layout;

import java.util.Locale;

/** Geometry and formatting for the download indicator. No Minecraft types. */
public final class ProgressBarLayout {

    /**
     * The bar stops just short of full while downloading. The total is last session's
     * download size and the leaderboard grows, so a download can legitimately exceed it;
     * holding at 99% is honest, whereas 100% would claim a completion that has not happened.
     */
    public static final double MAX_FRACTION = 0.99;

    /** Width of the sliding segment shown when the total is unknown. */
    public static final double MARQUEE_WIDTH_FRACTION = 0.25;

    private static final long MARQUEE_PERIOD_NANOS = 1_200_000_000L;

    public static double fraction(long bytesRead, long total) {
        if (total <= 0) {
            return 0.0;
        }
        return Math.min((double) bytesRead / total, MAX_FRACTION);
    }

    /**
     * Left edge of the sliding segment, as a fraction of the track, travelling from just
     * off the left edge to the right edge and wrapping. Driven by {@code System.nanoTime()}
     * rather than tick counts so it animates on the title screen, where nothing ticks.
     */
    public static double marqueeStart(long nanoTime) {
        double phase = (double) Math.floorMod(nanoTime, MARQUEE_PERIOD_NANOS) / MARQUEE_PERIOD_NANOS;
        return phase * (1.0 + MARQUEE_WIDTH_FRACTION) - MARQUEE_WIDTH_FRACTION;
    }

    public static String formatBytes(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1_000_000.0);
    }

    /** Floors rather than rounds, so the bar never reads 100% before it is finished. */
    public static String formatPercent(double fraction) {
        return (int) Math.floor(fraction * 100) + "%";
    }

    private ProgressBarLayout() {
    }
}
```

- [ ] **Step 4: Run the test and confirm it passes**

Run: `./gradlew test --tests "com.w0x7y.justtiers.gui.layout.ProgressBarLayoutTest"`
Expected: PASS, 7 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/gui/layout/ProgressBarLayout.java \
        src/test/java/com/w0x7y/justtiers/gui/layout/ProgressBarLayoutTest.java
git commit -m "Add ProgressBarLayout for bar geometry and readout formatting"
```

---

### Task 4: Report progress from `NovaTiersSource`

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/api/NovaTiersSource.java`
- Modify: `src/test/java/com/w0x7y/justtiers/api/TierSourceTest.java`

**Interfaces:**
- Consumes: `DownloadProgress` (Task 1), `ProgressBodyHandler` (Task 2).
- Produces: `NovaTiersSource(HttpClient, String, DownloadProgress)`. The existing two-argument constructor stays, delegating with a fresh `DownloadProgress`, so no existing caller or test has to change.

- [ ] **Step 1: Write the failing test**

Append to `TierSourceTest`:

```java
    // --- download progress ---

    @Test
    void reportsProgressForASuccessfulBulkDownload() throws Exception {
        respond("/users", 200, "[]");
        DownloadProgress progress = new DownloadProgress();

        new NovaTiersSource(client, baseUrl, progress).fetch(PLAYER).get();

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.IDLE, snapshot.state());
        // Calibrated by the download that just finished, so the next one can show a percentage.
        assertEquals(2, snapshot.total());
        assertTrue(snapshot.determinate());
    }

    @Test
    void reportsFailureWhenTheBulkDownloadFails() {
        respond("/users", 500, "");
        DownloadProgress progress = new DownloadProgress();

        assertThrows(ExecutionException.class,
                () -> new NovaTiersSource(client, baseUrl, progress).fetch(PLAYER).get());

        DownloadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(DownloadProgress.State.FAILED, snapshot.state());
        // A failed download is not a measurement, so it must not calibrate the next one.
        assertFalse(snapshot.determinate());
    }
```

Add the imports at the top of the file:

```java
import com.w0x7y.justtiers.download.DownloadProgress;
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "com.w0x7y.justtiers.api.TierSourceTest"`
Expected: FAIL — no three-argument `NovaTiersSource` constructor.

- [ ] **Step 3: Write the implementation**

In `NovaTiersSource`, add the imports:

```java
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.download.ProgressBodyHandler;
```

Add the field beside `client` and `baseUrl`:

```java
    private final DownloadProgress progress;
```

Replace the constructor with the pair:

```java
    public NovaTiersSource(HttpClient client, String baseUrl) {
        this(client, baseUrl, new DownloadProgress());
    }

    public NovaTiersSource(HttpClient client, String baseUrl, DownloadProgress progress) {
        this.client = client;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.progress = progress;
    }
```

In `download()`, mark the start, swap the body handler, and settle the state at the end. Everything inside `thenApply` is unchanged:

```java
        progress.started();
        return client.sendAsync(request, new ProgressBodyHandler(progress::advanced))
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new TierLookupException(
                                "NovaTiers returned HTTP " + response.statusCode());
                    }
                    Map<UUID, Map<String, Tier>> parsed = NovaParser.parseUsers(response.body());
                    if (parsed.isEmpty() && response.body() != null && response.body().length() > 2) {
                        JustTiers.LOGGER.warn(
                                "NovaTiers answered HTTP 200 but nothing was understood; "
                                        + "the response schema may have changed");
                    }
                    JustTiers.LOGGER.info("Indexed {} NovaTiers players", parsed.size());
                    indexedPlayerCount = parsed.size();
                    return parsed;
                })
                // whenComplete passes the result and the failure straight through, so the
                // caller's error handling - including loadIndex keeping the previous index -
                // is untouched.
                .whenComplete((parsed, error) -> {
                    if (error != null) {
                        progress.failed();
                    } else {
                        progress.finished();
                    }
                });
```

- [ ] **Step 4: Run the full suite and confirm everything passes**

Run: `./gradlew test`
Expected: PASS. 130 existing + 8 + 4 + 7 + 2 = **151 tests across 16 classes**.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/api/NovaTiersSource.java \
        src/test/java/com/w0x7y/justtiers/api/TierSourceTest.java
git commit -m "Report NovaTiers download progress from NovaTiersSource"
```

---

### Task 5: The `showDownloadProgress` setting

**Files:**
- Modify: `src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java`
- Modify: `src/main/java/com/w0x7y/justtiers/gui/JustTiersScreens.java:138-168` (`dataCategory`)
- Modify: `src/main/resources/assets/justtiers/lang/en_us.json`
- Test: `src/test/java/com/w0x7y/justtiers/config/JustTiersConfigTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `JustTiersConfig.isShowDownloadProgress()` / `setShowDownloadProgress(boolean)`, used by `DownloadHud` in Task 6.

- [ ] **Step 1: Write the failing test**

Append to `JustTiersConfigTest`:

```java
    @Test
    void downloadProgressIsShownByDefault() {
        assertTrue(new JustTiersConfig().isShowDownloadProgress());
    }

    @Test
    void downloadProgressRoundTripsThroughDisk(@TempDir Path dir) {
        Path file = dir.resolve("justtiers.json");
        JustTiersConfig config = new JustTiersConfig();
        config.setShowDownloadProgress(false);
        config.save(file);

        assertFalse(JustTiersConfig.load(file).isShowDownloadProgress());
    }

    @Test
    void aConfigWrittenBeforeTheSettingExistedStillShowsProgress(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("justtiers.json");
        Files.writeString(file, "{\"enabled\":true,\"displayMode\":\"all\"}");

        assertTrue(JustTiersConfig.load(file).isShowDownloadProgress());
    }
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `./gradlew test --tests "com.w0x7y.justtiers.config.JustTiersConfigTest"`
Expected: FAIL — `isShowDownloadProgress()` does not exist.

- [ ] **Step 3: Write the implementation**

In `JustTiersConfig`, add the field beside `novaRefreshMinutes`:

```java
    private boolean showDownloadProgress = true;
```

and the accessors after `setNovaRefreshMinutes`:

```java
    /**
     * Whether the download indicator is drawn. It appears for every download the moment one
     * starts, including the timed background refresh, so this is the escape hatch for anyone
     * who finds that intrusive.
     */
    public boolean isShowDownloadProgress() {
        return showDownloadProgress;
    }

    public void setShowDownloadProgress(boolean showDownloadProgress) {
        this.showDownloadProgress = showDownloadProgress;
    }
```

Gson leaves the field initialiser in place when the key is absent, which is what makes the third test pass — the same mechanism `enabled` already relies on. No `load()` change is needed.

In `JustTiersScreens.dataCategory`, add the option before the `return`:

```java
        Option<Boolean> showProgress = Option.<Boolean>createBuilder()
                .name(Component.translatable("justtiers.option.downloadProgress"))
                .description(description("justtiers.option.downloadProgress.desc"))
                .binding(true, config::isShowDownloadProgress, config::setShowDownloadProgress)
                .controller(TickBoxControllerBuilder::create)
                .build();
```

and register it on the category, after `refreshMinutes`:

```java
        return ConfigCategory.createBuilder()
                .name(Component.translatable("justtiers.config.category.data"))
                .option(refreshMinutes)
                .option(showProgress)
                .option(refresh)
                .option(LabelOption.create(Component.translatable("justtiers.data.indexed",
                        String.valueOf(JustTiersClient.novaSource().indexedPlayerCount()))))
                .build();
```

In `en_us.json`, add beside the other `justtiers.option.*` entries:

```json
  "justtiers.option.downloadProgress": "Show download progress",
  "justtiers.option.downloadProgress.desc": "Shows a progress bar in the bottom-left corner while the NovaTiers list is downloading. Appears for every download, including the timed refresh.",
```

and the indicator's own strings, beside the `justtiers.data.*` entries:

```json
  "justtiers.download.title": "Downloading NovaTiers",
  "justtiers.download.failed": "NovaTiers unavailable",
```

- [ ] **Step 4: Run the tests and confirm they pass**

Run: `./gradlew test --tests "com.w0x7y.justtiers.config.JustTiersConfigTest"`
Expected: PASS. Then `./gradlew build` to confirm `JustTiersScreens` still compiles.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java \
        src/main/java/com/w0x7y/justtiers/gui/JustTiersScreens.java \
        src/main/resources/assets/justtiers/lang/en_us.json \
        src/test/java/com/w0x7y/justtiers/config/JustTiersConfigTest.java
git commit -m "Add the showDownloadProgress setting and its tick box"
```

---

### Task 6: `DownloadHud` — draw it and register it

**Files:**
- Create: `src/main/java/com/w0x7y/justtiers/gui/DownloadHud.java`
- Modify: `src/main/java/com/w0x7y/justtiers/JustTiersClient.java`

**Interfaces:**
- Consumes: `DownloadProgress` and its `Snapshot` (Task 1), `ProgressBarLayout` (Task 3), `JustTiersConfig.isShowDownloadProgress()` (Task 5).
- Produces: `DownloadHud.register()`, called once from `onInitializeClient`; `JustTiersClient.downloadProgress()`.

This task has no unit test: it is entirely Minecraft-facing drawing, and the maths it relies on is already covered by Tasks 1 and 3. It is verified in-game in Task 7.

- [ ] **Step 1: Write `DownloadHud`**

```java
package com.w0x7y.justtiers.gui;

import com.w0x7y.justtiers.JustTiers;
import com.w0x7y.justtiers.JustTiersClient;
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.gui.layout.ProgressBarLayout;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Draws a small progress indicator in the bottom-left corner while the NovaTiers list is
 * downloading. It is deliberately transient: it exists only so a user whose badges have not
 * appeared can tell "still downloading" from "the site is down".
 */
public final class DownloadHud implements HudElement {

    private static final int LEFT_MARGIN = 4;
    private static final int BOTTOM_GAP = 4;
    /** Rough height of the chat input box, which sits below the message area. Tune by eye. */
    private static final int CHAT_INPUT_ALLOWANCE = 14;

    private static final int TRACK_WIDTH = 120;
    private static final int TRACK_HEIGHT = 4;
    private static final int PADDING = 3;
    private static final int LINE_GAP = 2;

    private static final int BACKDROP = 0x90000000;
    private static final int TRACK_COLOR = 0xFF3F3F3F;
    /** NovaTiers purple: this indicator is about NovaTiers, and color here means the site. */
    private static final int FILL_COLOR = 0xFFAA55FF;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int FAILURE_COLOR = 0xFFFF5555;

    public static void register() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(JustTiers.MOD_ID, "download_progress"),
                new DownloadHud());

        ScreenEvents.AFTER_INIT.register((minecraft, screen, width, height) ->
                ScreenEvents.afterForeground(screen).register(
                        (openScreen, graphics, mouseX, mouseY, delta) -> {
                            // The in-game HUD keeps drawing behind an open screen, so drawing
                            // here as well would blend the backdrop twice. This path exists for
                            // the title screen and main menu, where there is no HUD at all -
                            // and where the launch download actually runs.
                            if (Minecraft.getInstance().level == null) {
                                draw(graphics);
                            }
                        }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        draw(graphics);
    }

    private static void draw(GuiGraphicsExtractor graphics) {
        if (!JustTiersClient.config().isShowDownloadProgress()) {
            return;
        }
        DownloadProgress.Snapshot snapshot = JustTiersClient.downloadProgress().snapshot();
        if (snapshot.state() == DownloadProgress.State.IDLE) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        boolean failed = snapshot.state() == DownloadProgress.State.FAILED;

        int boxWidth = TRACK_WIDTH + PADDING * 2;
        int contentHeight = failed ? font.lineHeight : font.lineHeight + LINE_GAP + TRACK_HEIGHT;
        int boxHeight = contentHeight + PADDING * 2;

        int left = LEFT_MARGIN;
        int bottom = graphics.guiHeight() - chatReserve(minecraft) - BOTTOM_GAP;
        int top = bottom - boxHeight;

        graphics.fill(left, top, left + boxWidth, bottom, BACKDROP);
        graphics.text(font,
                Component.translatable(failed ? "justtiers.download.failed" : "justtiers.download.title"),
                left + PADDING, top + PADDING, failed ? FAILURE_COLOR : TEXT_COLOR);

        if (failed) {
            return;
        }

        int trackLeft = left + PADDING;
        int trackTop = top + PADDING + font.lineHeight + LINE_GAP;
        graphics.fill(trackLeft, trackTop, trackLeft + TRACK_WIDTH, trackTop + TRACK_HEIGHT,
                TRACK_COLOR);

        String readout;
        if (snapshot.determinate()) {
            double fraction = ProgressBarLayout.fraction(snapshot.bytesRead(), snapshot.total());
            int filled = (int) Math.round(TRACK_WIDTH * fraction);
            graphics.fill(trackLeft, trackTop, trackLeft + filled, trackTop + TRACK_HEIGHT,
                    FILL_COLOR);
            readout = ProgressBarLayout.formatPercent(fraction);
        } else {
            // No content-length from novatiers.com, so the first download of a session can
            // only show movement and a byte count.
            int segmentWidth =
                    (int) Math.round(TRACK_WIDTH * ProgressBarLayout.MARQUEE_WIDTH_FRACTION);
            int segmentLeft = trackLeft
                    + (int) Math.round(TRACK_WIDTH * ProgressBarLayout.marqueeStart(System.nanoTime()));
            int clampedLeft = Math.max(trackLeft, segmentLeft);
            int clampedRight = Math.min(trackLeft + TRACK_WIDTH, segmentLeft + segmentWidth);
            if (clampedRight > clampedLeft) {
                graphics.fill(clampedLeft, trackTop, clampedRight, trackTop + TRACK_HEIGHT,
                        FILL_COLOR);
            }
            readout = ProgressBarLayout.formatBytes(snapshot.bytesRead());
        }

        // Right-aligned on the label's line, inside the backdrop.
        graphics.text(font, readout,
                left + boxWidth - PADDING - font.width(readout), top + PADDING, TEXT_COLOR);
    }

    /**
     * How much room to leave at the bottom for chat. Vanilla chat renders upward from this
     * exact corner, so a bar flush to the bottom would sit on the newest message.
     *
     * <p>26.2 exposes no way to ask whether chat is focused - {@code Gui} has no
     * {@code ChatComponent} accessor and {@code Minecraft} no current-screen accessor - so
     * the focused height is reserved unconditionally. That costs a few pixels of clearance
     * when chat is closed and buys a bar that does not jump when chat opens.
     */
    private static int chatReserve(Minecraft minecraft) {
        if (minecraft.level == null) {
            return 0;
        }
        double heightPct = minecraft.options.chatHeightFocused().get();
        double scale = minecraft.options.chatScale().get();
        return (int) Math.ceil(ChatComponent.getHeight(heightPct) * scale) + CHAT_INPUT_ALLOWANCE;
    }
}
```

- [ ] **Step 2: Wire it into `JustTiersClient`**

Add the import:

```java
import com.w0x7y.justtiers.download.DownloadProgress;
import com.w0x7y.justtiers.gui.DownloadHud;
```

Add the field beside `novaSource`:

```java
    private static DownloadProgress downloadProgress;
```

In `onInitializeClient`, create it before the source and hand it over:

```java
        downloadProgress = new DownloadProgress();
        novaSource = new NovaTiersSource(
                JustTiers.httpClient(), Source.NOVATIERS.baseUrl(), downloadProgress);
```

Register the HUD beside the other registrations:

```java
        JustTiersCommands.register();
        JustTiersKeybinds.register();
        DownloadHud.register();
```

Add the accessor beside `novaSource()`:

```java
    public static DownloadProgress downloadProgress() {
        return downloadProgress;
    }
```

- [ ] **Step 3: Build and confirm it compiles**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests still passing.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/w0x7y/justtiers/gui/DownloadHud.java \
        src/main/java/com/w0x7y/justtiers/JustTiersClient.java
git commit -m "Draw the NovaTiers download indicator above the chat"
```

---

### Task 7: In-game verification and documentation

**Files:**
- Modify: `README.md`

Everything up to here is unit-tested or compile-checked. This task is the part that can only be done by eye.

- [ ] **Step 1: Launch the client and watch the launch download**

Run: `./gradlew runClient`

On the title screen, confirm the indicator appears in the bottom-left, the marquee slides, and the byte readout climbs toward `1.7 MB`. Confirm it disappears when the download finishes. **If nothing appears on the title screen**, the screen path is not firing — check that `Minecraft.getInstance().level == null` really holds there and that `AFTER_INIT` fired for `TitleScreen`.

- [ ] **Step 2: Confirm the calibrated bar**

Join any world or server, run `/justtiers refresh`, and confirm the second download shows a **percentage** rather than a byte count, filling smoothly and holding at 99% at most before vanishing.

- [ ] **Step 3: Confirm it clears the chat**

With a download running, confirm the bar sits above the chat message area and does not overlap the newest message. Press `T` to open chat and confirm the bar is still clear of the input box. Adjust `CHAT_INPUT_ALLOWANCE` and `BOTTOM_GAP` in `DownloadHud` until it looks right at GUI scale 2 and 3.

- [ ] **Step 4: Confirm there is no double draw**

With a download running in-world, open the inventory (`E`) and the Just-Tiers config screen. The backdrop must look identical to how it looks with no screen open — if it looks darker, the `level == null` guard is not working and both paths are drawing.

- [ ] **Step 5: Confirm F1 hides it**

With a download running in-world, press F1. If the bar still draws, the assumption recorded in the spec is wrong: find where 26.2 keeps the hide-GUI flag and guard `draw` on it, then note the finding in the plan's divergences section.

- [ ] **Step 6: Confirm the setting works**

Open the config screen, Data category, untick **Show download progress**, press Save, then run `/justtiers refresh`. Nothing should appear. Re-tick it and confirm the bar returns.

- [ ] **Step 7: Update the README**

In the **Configuration** section, add the key to the JSON example and to the table:

```json
  "novaRefreshMinutes": 30,
  "showDownloadProgress": true
```

| `showDownloadProgress` | Whether a progress bar is shown in the bottom-left while the NovaTiers list downloads |

Add a bullet to **Features**:

```markdown
- **Visible downloads** — the NovaTiers list is large and has to be fetched in full, so a small
  progress bar appears in the bottom-left corner while it downloads, rather than leaving you
  wondering whether the mod is working.
```

In **How it works**, correct the size: the measured body is **1.74 MB**, not the ~1.9 MB currently documented. Update both places it appears (the NovaTiers paragraph and the `justtiers.option.novaRefresh.desc` string in `en_us.json`, which also says ~1.9 MB).

- [ ] **Step 8: Run the full suite one last time**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, **154 tests across 16 classes**.

- [ ] **Step 9: Commit**

```bash
git add README.md src/main/resources/assets/justtiers/lang/en_us.json src/main/java/com/w0x7y/justtiers/gui/DownloadHud.java
git commit -m "Document the download indicator and correct the NovaTiers list size"
```

---

## Verification Checklist

- [ ] `./gradlew build` passes with 154 tests across 16 classes.
- [ ] The `download` package imports nothing from `net.minecraft.*`.
- [ ] `ProgressBarLayout` imports nothing from `net.minecraft.*`.
- [ ] The indicator appears on the title screen during the launch download and animates.
- [ ] A second download shows a real percentage.
- [x] The bar sits in the bottom-right, clear of chat by construction.
- [ ] No double-draw with a screen open in-world.
- [x] F1 hides it, with no guard needed.
- [ ] `showDownloadProgress = false` suppresses it entirely.
- [ ] A failed download (point `Source.NOVATIERS.baseUrl()` at a dead host, or pull the network) shows the red message and clears after ~4 seconds.
- [ ] Nametag rendering is unchanged: `NametagModelTest` and `TierResolverTest` pass untouched.

---

## Known Constraints and Follow-ups

Deliberately out of scope, recorded so they are not mistaken for oversights:

- **The first download of a session can never show a percentage.** novatiers.com sends no `content-length`. A shipped size estimate was considered and rejected: it would drift as the leaderboard grows and would be a hardcoded number pretending to be a measurement.
- **`lastKnownTotal` is not persisted**, so every session's first download is indeterminate. Persisting one integer would fix it, but this design writes nothing to disk by choice.
- **The indicator covers NovaTiers only.** MCTiers and SubTiers are small per-player requests that resolve in milliseconds; there is nothing to watch.
- **No delay threshold.** The bar appears the instant any download starts, including the timed refresh. Chosen deliberately; `showDownloadProgress` is the escape hatch.
- **novatiers.com ignores `Accept-Encoding: gzip`.** A JSON list of this shape would likely compress to a fraction of 1.74 MB. Nothing can be done client-side; worth raising with the site.
