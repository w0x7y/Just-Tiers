# Architecture deepening implementation plan

The user approved implementing all three candidates in the architecture report.

## Constraints

Preserve independent source answers, unavailable versus unranked, retained successful
answers, remote cooldowns, request limits, generation invalidation, and callbacks
outside locks. Keep the cache observation interface independent of debug presentation,
as required by ADR 0001. Preserve the existing uncommitted reliability work.

## Tasks

- [x] Deepen lookup session results. Replace separate placement/freshness/settlement
  collections with one per-source state. Publish a coherent displayed result, age
  captured answers without borrowing newer same-placement results, and centralize
  refresh-status precedence. Migrate the Minecraft adapter, screen, and session tests.
- [x] Concentrate request admission. Give claim, promotion, insertion, and generation
  cancellation one internal owner. Preserve nonblocking access and queue capacity;
  test observable order, deduplication, invalidation, and cooldown behavior without
  reaching through private fields.
- [x] Share successful lookup retention. Move the existing cache to a neutral package,
  centralize failed-future eviction ordering, and migrate both name and skin lookups.
  Keep name normalization and skin fallback with their callers. Exercise immediate
  failures, simultaneous lookups, late failure callbacks, and retry through the cache.
- [x] Review the integration against the approved scope, run the full suite and all six
  target builds, verify installable artifacts, and run the 26.2 client smoke harness.

Edits remain local; no release or publication is part of this task.

## Verification

All six target builds passed 497 JUnit tests with zero failures, errors or skips.
Each installable artifact passed metadata, bytecode and mixin verification; all
installable/source JARs exclude smoke and benchmark code. The tooling suite passed
11 tests, the 26.2 client smoke harness passed 28 assertions, and the independent
review found no remaining blocking issues after the targeted fixes.
