# Runtime smoke checklist

Use this before releasing client-facing changes. Unit tests and compilation do not launch
Minecraft or prove mixin application, font inheritance, widget focus or narration.

A repeatable optional Minecraft 26.2 harness is available in
[verification-tools.md](verification-tools.md). It exercises transformed remote-player
display names in a controlled integrated world and renders the three main screens.
Its fake tier answers and development profile do not substitute for authenticated
multiplayer or the full manual checklist below.

## September 30 repeatable harness

The optional harness passed on Minecraft 26.2, Java 25.0.4.1, Loader 0.19.3,
Fabric API 0.157.0+26.2 and YACL 3.9.4+26.2-fabric on a local X11 display.
It completed 28 assertions against the actual transformed `Player.getDisplayName`
path, component styles and fonts, settings changes and screen initialization.
The remote entity lived in a newly created integrated client world with controlled
cached tiers. The local player's UUID was temporarily changed to a controlled v4
UUID to exercise both hide-own states, then restored.

The config, gamemode grid and invalid-name lookup screens initialized at 320x240,
427x240 and 480x270 GUI pixels, each rendering for 40 client ticks at 480x270.
The lookup's translated error narration was checked. The run ended with
`JUSTTIERS_SMOKE_SUCCESS`, and the Gradle task exited successfully.
This run did not perform authenticated multiplayer, full keyboard navigation,
ModMenu, authenticated skins, spoken narration, live recovery or visual glyph
inspection. The development profile's authentication requests returned 401, and
the host still lacked the speech library. The manual checklist remains pending.

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

## September 25 compatibility checks

All six targets, 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3, passed a local
development-client smoke run with the dependencies in `gradle/targets/`. The 1.21.11
client used Java 21; the others used Java 25. Loader was 0.19.3 except on 26.3, which
used 0.19.5. A temporary test mod in a disposable instance loaded the player class to
exercise mixin transformation, then opened the configuration screen, gamemode grid
and invalid-name lookup screen, leaving each visible for 40 client ticks before
exiting. Every client reached the final success marker without a rendering crash.

The first 26.3 run failed with Loader 0.19.3's MixinExtras 0.5.4 during YACL mixin
transformation. Loader 0.19.5's MixinExtras 0.5.5 passed the same run; the 26.3 mod
metadata now requires that loader version. The smoke test mod was not packaged in
any distributable JAR. The host still lacks `flite`, so audible narration was not
tested. These checks did not exercise multiplayer nametags, authenticated skin
lookups, ModMenu, or the full keyboard and resize checklist below.

## September 25 architecture refactor checks

After the settings, lookup and target-policy refactors, all six targets passed again
with 444 JUnit tests per target, packaged metadata/bytecode validation and a fresh
development-client run. The disposable test instance opened the config screen, grid
and invalid-name lookup and loaded the player class to exercise mixin transformation.

The extended client harness changed a real YACL option and checked draft isolation,
then deliberately blocked config-file replacement. The failed Save preserved active
settings and kept the screen retryable. After removing the obstruction, retry changed
both active settings and the saved file and cleared the pending state. The harness
also checked the translated lookup error in the screen's narration text. These checks
passed on 1.21.11, 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. The harness and disposable game
directories remained outside the repository and distributable JARs.

The previously listed limits still apply: multiplayer rendering, ModMenu, authenticated
skins, audible narration and the full keyboard/resize checklist were not exercised by
this run.

## October 9 tier-site update checks

PvPTiers and PvPHQ replaced MCTiers, and individual site toggles replaced display modes.
All six supported targets passed 507 JUnit tests each and packaged-artifact verification.
The tooling suite passed 11 tests. Live lookups through the production HTTP client and
parsers returned PvPTiers and PvPHQ placements, including PvPHQ's profile cart alias.

The Minecraft 26.2 controlled-client smoke run passed 32 assertions. It checked gray
PvPHQ MT tiers, mixed site selections, disabled sites, all sites disabled, and actual
nametag mixin behavior. Config, gamemode-grid and lookup screens initialized at 320x240,
427x240 and 480x270 GUI pixels and rendered at 480x270 without failures. The existing
limits on authenticated multiplayer, ModMenu, skins, audible narration and full keyboard
navigation still apply.

## Version 1.1.7 release checks

All seven stable targets from 1.21.10 through 26.3 passed a fresh build with 507
JUnit tests each, without failures, errors or skips. Each installable 1.1.7 JAR
passed metadata, Java bytecode and nametag mixin namespace checks. The tooling
suite passed 18 tests, including target discovery and project-update validation.
Actionlint 1.7.7 and `git diff --check` passed.

Minecraft 1.21.10 and 26.2 each passed 32 controlled runtime assertions. The new
1.21.10 target used Java 21, Fabric Loader 0.19.3, Fabric API 0.138.4+1.21.10 and
YACL 3.8.2+1.21.10-fabric. A temporary auxiliary test mod adapted the existing
controlled-world harness to its older world-creation and screen APIs. It exercised
the production nametag mixin, all four sites, middle-tier colors and settings, then
initialized and rendered configuration, grid and lookup screens. The auxiliary
mod remained outside the repository and installable JARs.

The 26.2 run used the repository's optional smoke harness. Reports are in
`build/1.21.10/compat-smoke-instance/justtiers-smoke-report.txt` and
`build/26.2/smoke-instance/justtiers-smoke-report.txt`. Both ended with
`JUSTTIERS_SMOKE_SUCCESS`. Other targets were rebuilt and artifact-checked; their
previous runtime evidence is recorded above. Authenticated multiplayer, ModMenu,
spoken narration, authenticated skins and the full manual checklist remain untested
in this release-preparation run. The maintainer approved publication and live page
changes on October 9, 2026; see the [publication review](releases/v1.1.7-publishing.md).

## Setup

Use a disposable instance of the Minecraft target with the Java, Fabric Loader, Fabric
API and YACL versions in `gradle/targets/`. Test once without ModMenu, then with it. Keep a copy of
any config you care about before testing write failures. A development instance can be
started with `./gradlew runClient -Pminecraft_version=<version>` after a JDK is available
on PATH or through JAVA_HOME.

## Nametags and assets

- Join a world/server with real account UUIDs. Confirm the log shows successful initialization
  and no mixin failures. Confirm a ranked player's badge appears as sites answer.
- Check individual sites and mixed site selections, fallback from an unranked selected gamemode, and hidden retired
  placements. Verify before/after position, brackets and icons.
- Inspect all four sites' glyphs. Labels and names must remain readable, without missing-glyph
  boxes. Custom palettes recolor tier text while preserving icon artwork.
- Toggle nametag display and hide-own-badge. Verify other players retain the intended settings;
  tab list and chat remain unchanged. Offline-mode/NPC world nametags should not trigger
  account-UUID leaderboard lookups.

## Config input and saving

- Open configuration through the command, assigned keybind and optional ModMenu button.
- With keyboard only, Tab to a gamemode picker and activate with Enter and Space. Move across
  tiles with arrows and Tab. Preview must follow focus. Activate a tile, then return and
  activate Back with Enter: Back must not select a gamemode. Escape also cancels the picker.
- Check disabled descriptions for multiple sites enabled, a disabled site, nametag display off,
  and custom colors under a preset. They must explain the current restriction.
- Toggle each site individually, then try PvPTiers + PvPHQ and all sites disabled.
  Check the preview and saved nametags. The old mode selector must be absent.
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
