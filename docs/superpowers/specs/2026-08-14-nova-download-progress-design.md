# NovaTiers download progress design

Status: Historical design; implemented with revisions. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

The design aimed to distinguish a bulk download still running from a failed request. It
chose a byte-counting subscriber, a thread-safe state holder and shared HUD/screen rendering.

Current behavior differs from the early notes: the indicator is at the bottom right,
percentages use the previous completed payload as an estimate, and current palette colors
apply. Download tokens reject stale progress callbacks. Success calibrates the next attempt;
failure does not replace that measurement and appears briefly before the indicator hides.

Endpoint payload size and response headers can change. No observation recorded in August is
an ongoing API guarantee. Runtime checks, including HUD/screen double drawing and background
refresh failure, are in the smoke checklist.

## Current references

- [download](../../../src/main/java/com/w0x7y/justtiers/download)
- [gui/DownloadHud.java](../../../src/main/java/com/w0x7y/justtiers/gui/DownloadHud.java)
- [api/NovaTiersSource.java](../../../src/main/java/com/w0x7y/justtiers/api/NovaTiersSource.java)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
