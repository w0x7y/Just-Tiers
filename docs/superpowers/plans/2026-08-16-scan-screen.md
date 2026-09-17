# Lobby scan implementation proposal

Status: Deferred; not implemented. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

This proposal described a /justtiers scan command that would rank players in a lobby using
all their active placements. No scan command, scan screen, scoring model or queue ships in
the current mod. The existing lookup screen inspects one player and includes retired tiers.

The proposed approach was a bounded request queue, partial per-site results and deterministic
sorting. Those ideas remain unapproved future work. The detailed classes, tests, API calls
and shell steps from the old plan were removed because applying them directly would revive
obsolete interfaces and bypass the current failure and keyboard-accessibility rules.

A future design must decide how incomplete results compare, what scores mean across different
sites, how a large lobby limits requests, and how offline-mode identities are handled.
Review the current cache and lookup boundaries before implementing a new proposal.

## Current references

- [cache](../../../src/main/java/com/w0x7y/justtiers/cache)
- [lookup](../../../src/main/java/com/w0x7y/justtiers/lookup)
- [gui/LookupSession.java](../../../src/main/java/com/w0x7y/justtiers/gui/LookupSession.java)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
