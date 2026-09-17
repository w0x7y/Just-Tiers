# NovaTiers download progress implementation

Status: Implemented; original placement and percentage descriptions superseded. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

The feature exposes a bulk download's state and byte count through a Minecraft-free holder.
A counting HTTP body subscriber forwards the body unchanged while reporting bytes.

The shipped indicator appears at the bottom right, not the bottom left proposed in the
original plan. Its first download is indeterminate. Later downloads estimate their fraction
from the previous successful payload size; this is not a known response length or an exact
percentage. The displayed fraction stays below one until completion.

Generation tokens keep old downloads from completing or failing the newer indicator. The
NovaTiers source independently protects index ownership when refreshes overlap. The selected
palette supplies the progress color; a failed download briefly displays an unavailable state.
Disabling this indicator does not disable the downloads.

## Current references

- [download](../../../src/main/java/com/w0x7y/justtiers/download)
- [gui/DownloadHud.java](../../../src/main/java/com/w0x7y/justtiers/gui/DownloadHud.java)
- [gui/layout/ProgressBarLayout.java](../../../src/main/java/com/w0x7y/justtiers/gui/layout/ProgressBarLayout.java)
- [api/NovaTiersSource.java](../../../src/main/java/com/w0x7y/justtiers/api/NovaTiersSource.java)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
