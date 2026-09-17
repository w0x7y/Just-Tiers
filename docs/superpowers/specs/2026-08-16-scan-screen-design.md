# Lobby scan design proposal

Status: Deferred; not implemented. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

The proposal would show a lobby roster and sum points for active placements, with a suggested
HT1=10 through LT5=1 scale and retired placements excluded. It proposed provisional sorting
as sites answered, a bounded request queue and a scrollable roster.

This score is a proposed product rule, not an established measure of player skill. Partial
results are not directly comparable to complete ones, and different sites/gamemodes do not
necessarily represent equivalent tests. Those questions remain unresolved.

No /justtiers scan command is available. Current player lookup includes retired placements,
regardless of the nametag showRetired setting. A future scan would need a fresh design based
on current LookupReport states, cache failures, request limits, keyboard access and palettes.
The old hardcoded site colors and inline type definitions are superseded.

## Current references

- [lookup](../../../src/main/java/com/w0x7y/justtiers/lookup)
- [cache](../../../src/main/java/com/w0x7y/justtiers/cache)
- [resolve](../../../src/main/java/com/w0x7y/justtiers/resolve)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
