# Scenario 3: Ambiguous requirement

**Requirement as given:** "Links shouldn't live forever."

Commit: `feat: link expiry (ambiguous requirement: "links shouldn't live forever")`

## 1. Requirement understanding

The sentence does not say who decides the lifetime, what happens at the end of
it, or whether it applies to links that already exist. It supports at least
three different features:

| Reading | What it would mean | Assessment |
| --- | --- | --- |
| A. Per-link expiry | The creator sets a lifetime for a link (campaign links, one-time shares) | The most common product meaning; additive; reversible |
| B. Global retention | Every link is deleted N days after creation | Destructive for existing links; a policy decision that needs an owner |
| C. Inactivity cleanup | Links with no clicks for N days are removed | Needs a definition of "inactive" and deletes data silently |

Questions that would go to the product owner, with the assumption made in the
meantime:

| # | Question | Working assumption |
| --- | --- | --- |
| 1 | Who sets the lifetime: creator, operator, or both? | Creator per link (`ttlSeconds`); operator can set a default |
| 2 | Do links expire by default? | No. Changing the default would break the expectation that a link keeps working |
| 3 | Does this apply to existing links? | No. Existing links keep `expires_at = NULL` |
| 4 | What does a visitor see after expiry? | 410 Gone, which tells clients and crawlers the link is intentionally dead, unlike 404 |
| 5 | Is the data deleted at expiry? | Not immediately: kept 30 days, then deleted with its click events |
| 6 | Can an expired alias be claimed by someone else? | Only after the retention period, to prevent hijacking a link people may still follow |
| 7 | Minimum and maximum lifetime? | 60 seconds to 365 days |

**Decision: implement reading A, with an operator default that also covers the
simple form of B.** It is the only reading that is additive and loses no data if
the assumption turns out to be wrong. Readings B and C delete user data on a
schedule and should not ship on a guess; if the owner confirms B, setting
`app.expiry.default-ttl` delivers it for new links with no code change.

Normalized engineering problem: *a link may carry an optional expiry instant;
after that instant redirects answer 410 and record no click; expired links are
purged after a retention period.*

## 2. Task decomposition

| # | Task | Depends on |
| --- | --- | --- |
| A1 | Migration V3: nullable `expires_at`, index | none |
| A2 | Domain: `ResolvedLink.isExpiredAt` with boundary tests | none |
| A3 | Repository: write and read `expires_at`; batched delete | A1 |
| A4 | Service: compute expiry on create; check on resolve | A2, A3 |
| A5 | API: `ttlSeconds` with range validation; `expiresAt` in responses; 410 mapping | A4 |
| A6 | Purge job with retention, switchable per environment | A3 |
| A7 | Integration tests | A5, A6 |

## 3. Execution notes

- **Relative TTL, not an absolute timestamp, in the request.** An absolute
  `expiresAt` from the client depends on the client's clock and time zone
  handling. The server computes the instant from its own clock and returns it.
- **The cache needed a decision.** Redirects are served from a cache with a
  10-minute lifetime. If expiry were checked only when loading from the database,
  an expired link could keep redirecting for up to 10 minutes. The expiry instant
  is stored in the cached entry and compared on every request, so no
  invalidation is needed and expiry is exact on every instance.
- **Expired means no click.** A request that gets a 410 is not counted, so the
  analytics describe successful redirects only.
- **Purge in bounded batches.** A single `DELETE ... WHERE expires_at < ?` can
  lock a large range and cascade into millions of click rows. The job deletes up
  to 1000 links per statement and at most 100 batches per run.
- **The job must not run in tests.** Scheduling is enabled by a conditional
  configuration class and turned off in the test profile. The tests call
  `purgeOnce()` directly, which keeps them deterministic.

## 4. Validation

| Risk | Test |
| --- | --- |
| Existing behaviour changes | `linksNeverExpireByDefault`; all earlier tests unchanged |
| Wrong expiry computed | `aLinkWithATtlReportsItsExpiryAndRedirectsUntilThen` |
| Off-by-one at the expiry instant | `ResolvedLinkTest`: valid 1 ms before, expired at and after |
| Expired link still redirects or counts | `anExpiredLinkReturns410AndRecordsNoClick` |
| Alias hijack after expiry | `anExpiredAliasCannotBeReusedDuringTheRetentionPeriod` |
| Purge deletes too much or too little | `thePurgeDeletesOnlyLinksExpiredBeyondTheRetentionPeriod`: four links (expired 31 days, expired 29 days, active, permanent); only the first is deleted, and its click events go with it |
| Bad TTL accepted | `rejectsTtlValuesOutsideTheAllowedRange` (0, 59, negative, one over the maximum) |

## 5. Safety guardrails

- The migration is additive and nullable: old code runs against the new schema.
- No existing row is affected: `NULL` means "never expires".
- Deletion only ever touches rows that expired more than the retention period
  ago, is bounded per run, and can be disabled with `app.expiry.purge-enabled=false`.
- **Human sign-off required before:** setting a default TTL in production, or
  shortening the retention period. Both change what happens to users' links and
  are product decisions, not code decisions.
