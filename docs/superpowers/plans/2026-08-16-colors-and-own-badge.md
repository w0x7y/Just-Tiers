# Palette and own-badge implementation

Status: Implemented; original APIs superseded. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

This feature added hiding the local player's nametag badge, preset palettes and per-site
custom colors. Hide-own-badge affects the world nametag, not an explicit player lookup.

Color rules live in JustTiersConfig.colorOf and the Minecraft-free Palette enum.
SiteColors is a thin rendering adapter to that same config rule. Presets ignore stored custom colors, and switching presets
keeps custom choices available for later. Palette selection reaches UI previews, lookup rows,
gamemode controls and the NovaTiers progress indicator.

Alternative palettes are options for hue/brightness contrast, not a guarantee for every
visual condition. Icon artwork stays white in the font rendering pipeline so source colors
do not tint it. The config preview also reflects hiding the local badge.

## Current references

- [config/Palette.java](../../../src/main/java/com/w0x7y/justtiers/config/Palette.java)
- [config/JustTiersConfig.java](../../../src/main/java/com/w0x7y/justtiers/config/JustTiersConfig.java)
- [render](../../../src/main/java/com/w0x7y/justtiers/render)
- [gui](../../../src/main/java/com/w0x7y/justtiers/gui)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
