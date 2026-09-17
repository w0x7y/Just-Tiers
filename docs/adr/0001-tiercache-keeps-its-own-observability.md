# 1. Keep cache observability in the cache

Date: 2026-08-19. Status: accepted, reviewed 2026-09-17.

## Context

TierCache exposes site health, gate status, cached-player count, pending-lookup count and
players awaiting retry. CacheDiagnostics assembles these into the debug report. Returning
a SiteDiagnostics record straight from TierCache would shorten that one caller, but make
the cache depend on its reporting consumer.

## Decision

Keep the five accessors on TierCache. They expose cache state without choosing the format
of a diagnostic report. Keep assembly in debug/CacheDiagnostics and test it with distinct
cached, pending and retrying counts so accidentally swapped fields fail a test.

## Consequences

The accessors also let asynchronous cache tests observe request state without blocking or
starting another fetch. Reading diagnostics must not spend a recovery probe or mutate the
cache. Refresh resets current retry/gate state while preserving completed-request history;
obsolete callbacks may contribute history but must not reapply superseded retry decisions.

The cache remains independent of debug presentation. The count named cachedPlayers includes
entries still pending, so reports must distinguish that total from its in-flight subset.
Time-based fields describe readings and should not be compared as whole snapshots taken
at different times. Drive a clock explicitly when testing time boundaries.

## References

- [TierCache](../../src/main/java/com/w0x7y/justtiers/cache/TierCache.java)
- [CacheDiagnostics](../../src/main/java/com/w0x7y/justtiers/debug/CacheDiagnostics.java)
- [CacheDiagnosticsTest](../../src/test/java/com/w0x7y/justtiers/debug/CacheDiagnosticsTest.java)
- [Current contributor guidance](../../CLAUDE.md)
