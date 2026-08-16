# NovaTiers Download Progress Indicator — Design

> **Amended 2026-08-14, after seeing it in-game.** The indicator sits in the **bottom-right**
> corner, and its track is 180 px rather than the 120 px described below. *Placement* has
> been rewritten to match; the move deleted its chat-reserve rule entirely, because chat
> grows upward from the bottom-*left* and there is nothing to clear. The implementation is
> the authority.

**Goal:** Make the NovaTiers bulk download visible. Today it runs at launch and every
`novaRefreshMinutes` thereafter, downloading ~1.7 MB with no indication that anything is
happening; a user whose NovaTiers badges have not appeared yet cannot tell the difference
between "still downloading", "site is down" and "this mod is broken". A small progress
indicator in the bottom-right corner, present only while a download is in flight, removes
that ambiguity.

**Architecture:** Byte counts flow from the HTTP body subscriber into one thread-safe state
holder, which the renderer snapshots each frame. The two Fabric entry points — the in-game
HUD layer and the screen overlay — both receive a `GuiGraphicsExtractor`, so a single
drawing routine serves both. All state, geometry and formatting live in Minecraft-free
packages and are unit-tested; only the drawing routine and its registration touch
`net.minecraft.*`.

**Tech Stack:** Java 25, Fabric Loom, Minecraft 26.2 (unobfuscated), Fabric API
`0.157.0+26.2`, JUnit 5. No new dependencies.

---

## Global Constraints

- **No disk cache.** This design deliberately replaces the persistent-cache proposal. Nothing
  is written to disk beyond the one new config key. Tier data remains session-only, exactly
  as it is today.
- **No behaviour change to the download itself.** `NovaTiersSource` keeps its current
  semantics in full: a failed refresh keeps the previous index, refreshes do not blank
  badges mid-flight, and the returned future still never fails. The only change is that the
  body is read through a counting subscriber instead of `BodyHandlers.ofString()`.
- **Minecraft-free packages stay Minecraft-free.** `tier`, `api`, `cache`, `resolve`,
  `render.model`, `preview`, `gui.layout` and `gui.state` already are. This design adds
  `download` to that list and extends `gui.layout`. They must not import `net.minecraft.*`.
- **The render thread never blocks and never allocates per-frame state it can avoid.**
  `DownloadProgress.snapshot()` is a single read of atomic fields returning an immutable
  record. No locks, no I/O, no collection walks on the render path.
- **One drawing routine, two registrations.** The HUD layer and the screen overlay must not
  each grow their own idea of what the bar looks like.
- **Color discipline, per the config-GUI plan.** The bar's fill is NovaTiers purple
  (`0xAA55FF`), because this indicator is about NovaTiers specifically and color in this
  mod's UI carries exactly one meaning: which leaderboard this is. Text is white, secondary
  text `0xA0A0A0`, failure text `0xFF5555`.

---

## Verified API Reference

Confirmed on 2026-08-14 against `fabric-api-0.157.0+26.2.jar` and
`~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar`. **Do not re-derive these.**

### The endpoint sends no size

`curl` against `https://novatiers.com/users`, both with and without `--compressed`:

```
HTTP/2 200
server: nginx/1.24.0 (Ubuntu)
content-type: application/json
```

- **No `content-length` header.** The response is streamed chunked over HTTP/2, so the total
  size is unknowable in advance. A `HEAD` request returns the same headers and no size either.
- **No compression.** The server ignores `Accept-Encoding: gzip`; both requests returned an
  identical **1,736,861 bytes** (1.74 MB). Requesting compression is therefore pointless.

This single fact drives the whole design: **a true percentage cannot be shown on the first
download of a session.** See *Self-calibration* below.

### Fabric API

Bundled inside `fabric-api-0.157.0+26.2.jar`:

| Module | Version |
|---|---|
| `fabric-rendering-v1` | `25.3.2+515ac5339e` |
| `fabric-screen-api-v1` | `5.2.0+58e078ad9e` |

```java
// net.fabricmc.fabric.api.client.rendering.v1.hud
interface HudElementRegistry {
    static void addLast(Identifier, HudElement);
    static void attachElementAfter(Identifier, Identifier, HudElement);
    // also: addFirst, attachElementBefore, removeElement, replaceElement
}
interface HudElement {
    void extractRenderState(GuiGraphicsExtractor, DeltaTracker);
}
// VanillaHudElements exposes CHAT, PLAYER_LIST, HOTBAR, ... as Identifier constants.

// net.fabricmc.fabric.api.client.screen.v1
final class ScreenEvents {
    static final Event<AfterInit> AFTER_INIT;                 // (Minecraft, Screen, int, int)
    static Event<AfterForeground> afterForeground(Screen);    // (Screen, GuiGraphicsExtractor, int, int, float)
}
```

Note there is no `ScreenEvents.AfterRender` in this version; 26.2 uses the extract-based
pipeline, and `afterForeground` is the correct hook. It is registered **per screen instance**,
so it must be attached from an `AFTER_INIT` listener.

### Minecraft 26.2

```java
// net.minecraft.client.gui.GuiGraphicsExtractor
public int guiWidth();
public int guiHeight();
public void fill(int x1, int y1, int x2, int y2, int argb);
public void text(Font, Component, int x, int y, int argb);
public void text(Font, Component, int x, int y, int argb, boolean shadow);

// net.minecraft.client.gui.components.ChatComponent
public static int getWidth(double);
public static int getHeight(double);
public boolean isChatFocused();

// net.minecraft.client.Options
public OptionInstance<Double> chatScale();
public OptionInstance<Double> chatHeightFocused();
public OptionInstance<Double> chatHeightUnfocused();

// net.minecraft.client.Minecraft
public static Minecraft getInstance();
public ClientLevel level;          // null on the title screen / main menu
```

`fill` and `text` are the only drawing primitives this design needs. No textures, no blits,
no render pipelines.

### Not verified — confirm at implementation time

- **F1 (hide GUI).** There is no `hideGui` field on `Options`, `Gui` or `Minecraft` in 26.2;
  only `Options.keyToggleGui` exists. The expectation is that vanilla skips the entire HUD
  render when the GUI is hidden and therefore Fabric never invokes registered `HudElement`s,
  making an explicit guard unnecessary. **Verify in-game by pressing F1 during a download.**
  If the element still draws, find where 26.2 keeps the flag and guard on it.
- **`Font` access.** Assumed `Minecraft.getInstance().font`. Confirm the accessor name.
- **HUD ordering.** `addLast` should place the element above the chat in draw order.
  If it renders beneath, switch to `attachElementAfter(VanillaHudElements.CHAT, ...)`.

---

## Design

### States

`DownloadProgress` is a small state machine with three observable states:

| State | Entered when | Shows |
|---|---|---|
| `IDLE` | no download in flight | nothing at all |
| `DOWNLOADING` | `NovaTiersSource.download()` starts | the bar |
| `FAILED` | the download completes exceptionally | a red message, for ~4 s, then `IDLE` |

A successful download returns straight to `IDLE` — the bar disappears rather than flashing a
completion state. Failure is worth surfacing precisely because the current behaviour is to
silently keep stale data.

### Self-calibration

Because the server sends no size, the bar calibrates itself from the previous download:

- `lastKnownTotal` starts at `0` (unknown) each session and is **not persisted**.
- On a successful download it is set to the actual byte count just read.
- While `lastKnownTotal == 0` the bar is **indeterminate**: a marquee segment roughly a
  quarter of the bar's width, sliding left to right on a ~1.2 s loop driven by
  `System.nanoTime()` — not tick counts, so it animates on the title screen where nothing
  ticks — alongside a live `1.2 MB` byte readout.
- Once `lastKnownTotal > 0` the fill fraction is `bytesRead / lastKnownTotal`, **clamped to
  0.99** until the download actually completes. The clamp is what makes a growing leaderboard
  safe: if the list is now larger than last time, the bar holds at 99% rather than claiming
  to be finished or overflowing its track.

In practice this means the launch download is indeterminate-with-byte-count and every
subsequent refresh in that session shows a true percentage.

### Placement

Bottom-**right**, at the normal bottom margin. Vanilla chat renders upward from the
bottom-*left*, and the input box only spans the same side, so the opposite corner is clear
of both by construction:

```
left   = guiWidth() - right margin - box width
bottom = guiHeight() - gap
```

An earlier draft placed the bar bottom-left and lifted it above the chat by a computed
`chatReserve` (`ChatComponent.getHeight()` scaled, plus an input-box allowance, plus a
focused/unfocused branch). That was dropped: the reserve was an approximation of where chat
*happens* to be, and moving to the far corner removes the question entirely. Nothing about
chat state - height, scale, focus - is consulted any more, and the placement is identical
on the title screen, in the main menu, and in-world.

### The double-draw guard

The in-game HUD continues to render *behind* an open screen, so registering both entry points
naively draws the bar twice whenever an inventory or the config screen is open — same pixels,
drawn twice, which double-blends the translucent backing.

**Rule: the screen overlay draws only when `Minecraft.getInstance().level == null`.** In-world
screens are covered by the HUD element; the screen path exists for the title screen and main
menu, which is exactly where the launch download runs and where it matters most.

### Appearance

A ~120 × 4 px track with a one-line label above it, on a dark translucent backing
(`0x90000000`) with a few pixels of padding:

```
Just-Tiers                                  Just-Tiers
[<###### >              ]  1.2 MB           [##############    ]  71%
   indeterminate, first download               calibrated, later refreshes
```

Failure replaces both lines with `NovaTiers unavailable` in `0xFF5555`.

### Configuration

One new key in `justtiers.json`:

| Key | Default | Meaning |
|---|---|---|
| `showDownloadProgress` | `true` | Whether the indicator is drawn at all |

Exposed as a tick box on the existing **Data** category of the YACL screen, beside the
refresh-interval slider. Unrecognised or missing values default to `true`, consistent with how
`JustTiersConfig` already corrects rather than rejects. No command; the toggle is
screen-only, as `novaRefreshMinutes` already is.

The indicator appears for **every** download, immediately, with no delay threshold — including
the background refresh every `novaRefreshMinutes` and any download triggered by
`/justtiers refresh`. This is a deliberate choice: the config toggle, not a heuristic, is the
escape hatch for anyone who finds it intrusive.

---

## Components

| Class | Package | Minecraft-free | Purpose |
|---|---|---|---|
| `DownloadProgress` | `download` | yes | Thread-safe state: `started()`, `advanced(long, long)`, `finished(long)`, `failed(long)`, `snapshot()`. Holds `lastKnownTotal`. `started()` returns a generation token that the other three carry, so a stale download's updates are dropped when downloads overlap. |
| `DownloadProgress.Snapshot` | `download` | yes | Immutable per-frame read: state, `bytesRead`, `totalOrZero`. |
| `ProgressBodyHandler` | `download` | yes | `BodyHandler<String>` delegating to `BodySubscribers.ofString(UTF_8)`, counting bytes in `onNext` before passing them on. |
| `ProgressBarLayout` | `gui.layout` | yes | Fill fraction with the 0.99 clamp, marquee offset from a nanosecond timestamp, `1.2 MB` / `71%` formatting. |
| `DownloadHud` | `gui` | no | The single drawing routine plus both registrations. |

**Modified:** `NovaTiersSource` (takes a `DownloadProgress`, swaps the body handler),
`JustTiersClient` (owns the instance, wires it, registers the HUD), `JustTiersConfig`
(the new key), the YACL screen (the tick box), `en_us.json` (strings).

`DownloadProgress` is passed into `NovaTiersSource` by constructor injection, matching how
`HttpClient` and `baseUrl` are already supplied, so the existing tests can pass a throwaway
instance and assert against it.

---

## Testing

Unit tests, no Minecraft on the classpath, consistent with the existing 12 test classes:

- **`DownloadProgressTest`** — state transitions; `lastKnownTotal` set only on success and
  never on failure; failure expiring back to `IDLE`; a snapshot taken mid-download reflecting
  the bytes seen so far.
- **`ProgressBodyHandlerTest`** — byte counting driven by feeding `ByteBuffer`s to the
  subscriber directly; the decoded string still arriving intact; multi-chunk bodies summing
  correctly; an empty body counting zero.
- **`ProgressBarLayoutTest`** — the 0.99 clamp when `bytesRead` exceeds `lastKnownTotal`;
  indeterminate when total is unknown; marquee wrapping cleanly across its loop; byte and
  percent formatting.
- **`JustTiersConfigTest`** — the new key round-trips, and defaults to `true` when absent.
- **`TierSourceTest`** — unchanged behaviour of `NovaTiersSource` under the new body handler,
  including that a failed download still keeps the previous index.

In-game verification via `./gradlew runClient`, which cannot be automated here:

- The bar appears on the title screen during the launch download and animates.
- It does not double-draw when a screen is open in-world.
- It clears the chat area, both with chat closed and with chat open.
- A second download (`/justtiers refresh`) shows a true percentage.
- F1 hides it (or is proven not to need a guard).
- `showDownloadProgress = false` suppresses it entirely.

---

## Known Constraints and Follow-ups

Deliberately out of scope, recorded so they are not mistaken for oversights:

- **The first download of a session can never show a percentage.** The server sends no
  `content-length`. A shipped size estimate was considered and rejected: it would drift as
  the leaderboard grows and would be a hardcoded number pretending to be a measurement.
- **`lastKnownTotal` is not persisted**, so every session's first download is indeterminate.
  Persisting one integer would fix that, but this design writes nothing to disk by choice.
- **The indicator covers NovaTiers only.** MCTiers and SubTiers are small per-player requests
  that resolve in milliseconds; there is nothing to watch.
- **No delay threshold.** Chosen deliberately, per the configuration section above.
