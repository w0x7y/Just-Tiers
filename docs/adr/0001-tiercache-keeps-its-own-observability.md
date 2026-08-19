# 1. TierCache keeps its own observability accessors

Date: 2026-08-19

## Status

Accepted

## Context

`TierCache` exposes five accessors that answer nothing about tiers:

```java
SiteHealth.Snapshot health(Source source);
SiteGate.Status     gateStatus(Source source);
int                 cachedPlayers(Source source);
int                 pendingLookups(Source source);
int                 playersAwaitingRetry(Source source);
```

Exactly one production caller uses them, and it folds all five straight
back into a single record:

```java
sites.add(new SiteDiagnostics(source,
        cache.health(source), cache.gateStatus(source),
        cache.cachedPlayers(source), cache.pendingLookups(source),
        cache.playersAwaitingRetry(source)));
```

Read that way it looks like an interface widened for one consumer, and
the obvious repair is `TierCache.diagnostics(source)` returning the
record the caller was going to build anyway.

An architecture review raised it as a candidate on exactly that reading.

## Decision

The accessors stay. `TierCache` does not learn what a `SiteDiagnostics`
is.

## Consequences

**They are the test surface, not debug leakage.** Seventeen call sites
across seven `TierCacheTest` cases assert the cache's asynchronous
behaviour through them, and could not assert it any other way:

- `inFlightLookupsAreCountedSeparatelyFromSettledOnes`
- `playersWaitingOutARetryAreCountedWhileTheyWait`
- `theGateIsVisibleOnceItHasGivenUpOnASite`
- `refreshingReopensTheGateWithoutRewritingHistory`
- `aFailureIsTimedAndItsReasonKept`
- `aSuccessIsTimedFromWhenTheRequestWentOut`
- `aSiteNobodyHasAskedReportsNothingRatherThanThrowing`

A module whose state is a set of in-flight futures, retry deadlines and
a circuit breaker is only verifiable if it will say what it is holding.
Counting production callers alone gets this backwards.

**The repair inverts a dependency.** `TierCache.diagnostics(source)`
would make `cache/` import `debug/` — the module that fetches and caches
depending on the module that formats a bug report. Moving
`SiteDiagnostics` into `cache/` instead would hand a low-level module
ownership of a reporting concept. Either trade buys about six lines in
one caller.

**What the review did find.** `SiteDiagnostics` ends in three adjacent
`int` parameters and nothing tested the mapping, so transposing
`pendingLookups` and `playersAwaitingRetry` would have left every test
green and the debug report quietly wrong. That is a defect in the
assembly, not in the interface, so the assembly moved to
`debug/CacheDiagnostics` where it is tested against a real cache. The
accessors were not touched.
