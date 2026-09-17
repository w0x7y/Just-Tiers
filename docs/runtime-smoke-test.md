# Runtime smoke checklist

Use this before releasing client-facing changes. Unit tests and compilation do not launch
Minecraft or prove mixin application, font inheritance, widget focus or narration.

The September 17 audit fix verification used Minecraft 26.2, Java 25, Fabric Loader 0.19.3,
Fabric API 0.157.0+26.2 and YACL 3.9.4+26.2-fabric, without ModMenu. A temporary client mod
ran 26 assertions against the actual screens and widgets, with direct keyboard-event dispatch
and screen layouts resized to 320×240, 427×240 and 480×270 GUI pixels. It passed:

- Arrow/Enter grid selection, Enter on Back without selecting, and Enter/Space picker opening.
- Save failure with unchanged live config and existing disk contents; Undo/Cancel after failure;
  successful retry; and Cancel preserving a previously successful Save.
- Invalid-name feedback, editing/retrying in the same lookup, reachable Done controls, keyboard
  focus scrolling cells and footer links into view, and Enter opening link confirmation.
- Nonempty cell narration text and a save-error narration message.

Screenshots were inspected for glyphs, error wrapping, preview fit and the scrolled lookup
footer. The development client initialized and downloaded the live NovaTiers index. The
harness used controlled empty lookup results and a blocked temporary save path, restored the
runtime config afterward, and was removed from the development instance.

Remaining release checks: real multiplayer nametags and mixins during rendering, ModMenu,
spoken narration, authenticated profile/skin behavior, live site recovery, and the progress
indicator in-world. The host lacked the `flite` speech library, so narration content was
checked but audible output was not. This evidence does not mark the whole checklist complete.
Record new game/mod versions, GUI sizes, input method and results for future releases.

## Setup

Use a disposable Minecraft 26.2 instance with Java 25, Fabric Loader, Fabric API and YACL
matching the project dependencies. Test once without ModMenu, then with it. Keep a copy of
any config you care about before testing write failures. A development instance can be
started with `./gradlew runClient` after a JDK is available on PATH or through JAVA_HOME.

## Nametags and assets

- Join a world/server with real account UUIDs. Confirm the log shows successful initialization
  and no mixin failures. Confirm a ranked player's badge appears as sites answer.
- Check each display mode, fallback from an unranked selected gamemode, and hidden retired
  placements. Verify before/after position, brackets and icons.
- Inspect all three sites' glyphs. Labels and names must remain readable, without missing-glyph
  boxes. Custom palettes recolor tier text while preserving icon artwork.
- Toggle nametag display and hide-own-badge. Verify other players retain the intended settings;
  tab list and chat remain unchanged. Offline-mode/NPC world nametags should not trigger
  account-UUID leaderboard lookups.

## Config input and saving

- Open configuration through the command, assigned keybind and optional ModMenu button.
- With keyboard only, Tab to a gamemode picker and activate with Enter and Space. Move across
  tiles with arrows and Tab. Preview must follow focus. Activate a tile, then return and
  activate Back with Enter: Back must not select a gamemode. Escape also cancels the picker.
- Check disabled descriptions for All mode, another site's single mode, nametag display off,
  and custom colors under a preset. They must explain the current restriction.
- Change position, palette, own-badge and icons. Preview must reflect pending values. Cancel
  must preserve live settings and the file; Undo restores pending changes. Save must persist
  and survive reopening and restart.
- In the disposable instance, deny the config directory write access using the operating
  system's permissions, then attempt Save and a settings command. Expect a visible failure,
  the old file intact and a retryable screen. Screen changes must not commit live values;
  a settings command must warn that its change is session-only. Restore permissions and retry.
- Change the NovaTiers interval and save. Confirm the scheduler updates; saving unrelated
  settings must not postpone the next refresh. Check the cache interval similarly.

## Lookup sizes, focus and recovery

- Open a lookup for an online account, an off-server account, an invalid name and a valid
  but unowned name. Verify name-resolution errors are distinct from nonexistent accounts.
- Edit the name and retry from the same screen. During slow lookups, change names again;
  old answers must not overwrite the newly selected player.
- Test GUI dimensions around 320×240, 427×240 and 480×270, plus a wide window. The search,
  retry and Done controls must stay reachable. Scroll every result row and footer into view.
- Navigate cells and leaderboard links using Tab and Shift+Tab. Focused content should scroll
  into view, show its tooltip and have useful narration. Enter opens the intended link using
  Minecraft's confirmation prompt. Escape and Done close the lookup.
- With a real or controlled site failure, confirm unavailable rows are distinct from empty
  placements. After recovery, retry should update them. Skin/profile failure should leave
  tiers usable with a default skin, and a later retry should attempt the skin again.

## Refresh and progress

- Trigger a cold NovaTiers download, then refresh again. First download uses indeterminate
  progress; later percentages use the previous download size and never claim completion early.
- Check the bottom-right indicator in-world and with another screen open. It should appear
  once, use the configured NovaTiers color and disappear after completion. Disabling its
  setting hides only the indicator.
- Trigger Refresh from config and command. Confirm pending feedback, disabled repeat action
  while the same refresh is pending, and distinct success/failure feedback afterward.
- During an outage, existing NovaTiers placements must remain usable. Retry after recovery.
  Disabling nametag display must not prevent explicit lookups or scheduled NovaTiers refresh.
- Run `/justtiers debug`. Confirm its report copies to the clipboard, names the running
  version and shows failures/retry state without treating malformed data as unranked.

## Evidence to keep

Record pass/fail per section, relevant client log lines and screenshots for layout or font
failures. Note unavailable accounts/services instead of treating unexercised paths as passed.
Keep this checklist pending when a headless build environment cannot perform the game checks.
