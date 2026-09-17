# Palette and own-badge design

Status: Historical design; implemented with revisions. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

The design separated which tiers appear from how their badges are presented. It proposed
preset and custom site colors and an option to hide the local player's world badge.

Palette owns color rules in ints; JustTiersConfig owns file representation and resolves
the active site color. SiteColors is a thin rendering adapter to that config rule. NametagStyle
carries colors into badge composition. Rendering an icon does not tint its artwork.

Saved custom colors survive switching to a preset. Unknown palette IDs and malformed custom
colors fall back during config loading. The preview follows pending settings, including the
own-badge setting. The original universal colorblind-safety claim has been removed; the
presets provide alternatives whose usefulness depends on vision and background.

## Current references

- [config](../../../src/main/java/com/w0x7y/justtiers/config)
- [render/model](../../../src/main/java/com/w0x7y/justtiers/render/model)
- [gui](../../../src/main/java/com/w0x7y/justtiers/gui)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
