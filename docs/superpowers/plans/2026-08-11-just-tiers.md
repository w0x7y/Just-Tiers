# Initial Just-Tiers implementation

Status: Implemented; original scaffold superseded. Reviewed on 2026-09-17.

This is an archived record of the August 2026 work, not a runnable implementation plan.
The original code listings remain in Git history. The current source and tests are the
implementation reference; use the [README](../../../README.md) for supported behavior.

## Decisions and changes

The first design established per-site tier parsing, asynchronous player lookup, a cache,
selection of one or all leaderboards, and a nametag decoration hook. It also established
separate gamemode registries, including NovaTiers aliases and retired placements.

The original recipe included generated placeholder NovaTiers artwork, earlier cache/error
semantics and older render APIs. Those instructions no longer describe the application.
The obsolete artwork generator was removed; the checked-in NovaTiers artwork is retained.

Current parsing rejects malformed successful responses, the cache distinguishes failures
from unranked answers, and obsolete asynchronous callbacks cannot reset current retry state.
`Badge` owns badge construction, `TierView` supplies live settings and answers, and `Icons`
isolates the private icon font. The Minecraft 26.2 build uses its ordinary JAR without remapping.

## Current references

- [api](../../../src/main/java/com/w0x7y/justtiers/api)
- [cache](../../../src/main/java/com/w0x7y/justtiers/cache)
- [resolve](../../../src/main/java/com/w0x7y/justtiers/resolve)
- [render/model](../../../src/main/java/com/w0x7y/justtiers/render/model)
- [tier](../../../src/main/java/com/w0x7y/justtiers/tier)
- [Current audit plan](../plans/2026-09-17-audit-fixes.md)
- [Runtime smoke checklist](../../runtime-smoke-test.md)

The September audit fixes supersede the old implementation details. This document does not
claim that historical manual checks were run against the current build.
