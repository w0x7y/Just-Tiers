# Reliable tier lookups implementation plan

**Goal:** Implement the user's approved improvements 1, 2, 5 and 7: stable refreshes, bounded requests, honest partial NovaTiers data, bounded retention and repeatable verification.

**Architecture:** Keep last successful cache answers separate from pending refreshes. A per-site request scheduler bounds work and prioritizes explicit lookups. NovaTiers publishes validated snapshots independently of downloads. Minecraft-free models continue to own state; GUI code only presents it.

**Constraints:** Preserve all six Minecraft targets, existing commands, per-player retries, circuit breakers, generation isolation and distinct unranked/unavailable results. No new runtime dependency. Test asynchronous behavior with controlled futures and clocks. Do not publish, push or change the release version.

## Task 1: Cache freshness, requests and retention

- [x] Add regression tests demonstrating last successful answers survive TTL expiry and failed replacements, then implement stale-while-refresh with six hours of grace beyond the configured TTL.
- [x] Retain successful answers during manual refresh while resetting retry state. Hard invalidation remains available for a successfully replaced NovaTiers index.
- [x] Bound each site's work to four active requests and 128 waiting requests. Explicit lookups take priority over nametag work, deduplicate by UUID, and promote existing queued requests. Release capacity after both synchronous and asynchronous failures. Queued work rechecks circuit/cooldown state before starting.
- [x] Parse HTTP Retry-After as delta seconds or HTTP date; use site-wide cooldown for rate limiting, including explicit retries. Preserve generation isolation during invalidation and avoid completing futures under cache locks.
- [x] Cap retained player entries and retry records at 4096 per site; evict idle completed entries without abandoning pending futures. Maintenance removes expired idle entries even if the player never appears again.
- [x] Present source-answer age and stale/refresh-failure state in lookup tooltips, retaining last successful placements during a failed refresh.

## Task 2: NovaTiers partial data

- [x] Write failing mixed-valid/malformed-player tests using the real parser/source.
- [x] Preserve the existing parseUsers interface for callers; add a detailed immutable parse result tracking rejected identifiable players, unknown gamemodes and malformed counts.
- [x] Missing players with malformed records fail lookup instead of returning a valid empty answer. Preserve valid placements belonging to other players and distinguish malformed placements from genuinely absent ones.
- [x] Log summarized parsing diagnostics rather than one stack trace per rejected placement. Valid unknown gamemodes remain forward-compatible.
- [x] Serve the last successfully published NovaTiers snapshot during a refresh, swapping only after validation succeeds; failed refreshes keep the previous snapshot.

## Task 3: Verification and performance tools

- [x] Add a repeatable optional client smoke harness outside distributable JARs. Verify actual transformed player names, badge fonts and original-name formatting, settings changes, and GUI opening in a disposable instance. Make limitations about authenticated multiplayer explicit.
- [x] Add a reproducible warmed-cache badge benchmark with elapsed time and per-thread allocation measurements. Avoid timing thresholds in normal tests.
- [x] Run the benchmark before and after targeted allocation improvements when measurements justify them. Keep integration behavior covered by tests.
- [x] Document exact commands, evidence and remaining manual multiplayer checks.

## Task 4: Integration and review

- [x] Run focused tests after each change and the complete suite afterward.
- [x] Build and verify the actual installable JAR for every supported target; run tooling tests.
- [x] Run the new smoke harness where the environment permits, inspect runtime logs, and retain clear evidence of which checks were exercised.
- [x] Request independent review of correctness, races, limits and requirement coverage; fix findings and rerun affected checks.
- [x] Update README and Modrinth documentation with retention, freshness and request-limit behavior.

## Verified outcome

All six targets passed 479 JUnit tests each, installable metadata/bytecode/mixin validation,
and checks excluding smoke/benchmark classes from both installable and sources JARs.
The Python tooling suite passed 11 tests. The final Minecraft 26.2 controlled client
run passed 28 assertions and produced its success marker. The final warmed badge
benchmark completed without additional source requests. Independent review found
no remaining Critical or Important issues after the Nova age/cooldown/lifecycle fixes
and the explicit-request promotion race regression. Authenticated multiplayer and
the remaining manual visual/input checks are still release checks.
