# Configuration screen implementation

Status: Implemented; original widget and persistence recipe superseded. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

This work added YACL categories, a synthetic nametag preview, gamemode selection, availability
rules and an unbound configuration keybind. The intended rule remains: options are visible,
unavailable controls explain their state, and edits take effect on Save.

The original inline classes are obsolete. The current configuration flow works on a draft,
persists before committing live settings and reports write failures without discarding the
previous file. Gamemode controls and tiles support keyboard activation, and Back/Escape leave
the pending selection unchanged. Use the runtime checklist to verify actual widget focus and
narration; pure geometry tests cannot establish keyboard behavior.

YACL is required at runtime. ModMenu is optional and only provides another configuration
entry point. Dependency coordinates belong in gradle.properties, not this historical record.

## Current references

- [gui](../../../src/main/java/com/w0x7y/justtiers/gui)
- [gui/state](../../../src/main/java/com/w0x7y/justtiers/gui/state)
- [config](../../../src/main/java/com/w0x7y/justtiers/config)
- [preview](../../../src/main/java/com/w0x7y/justtiers/preview)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
