# Engineering summary

## What was built

A runnable URL shortener service: create short links (generated code or custom
alias, optional expiry), redirect, and per-link click analytics, with rate
limiting, caching, input safety rules, consistent errors, versioned schema
migrations, automated tests and CI.

It was built with AI assistance in three stages that map to the three required
scenarios. Each stage is one commit.

| Stage | Scenario | Requirement | Commit |
| --- | --- | --- | --- |
| 1 | [Greenfield](scenarios/01-greenfield.md) | Core service from scratch | `feat: URL shortener core (greenfield)` |
| 2 | [Brownfield](scenarios/02-brownfield.md) | Add custom aliases to existing code | `feat: custom aliases (brownfield enhancement)` |
| 3 | [Ambiguous](scenarios/03-ambiguous.md) | "Links shouldn't live forever" | `feat: link expiry (ambiguous requirement ...)` |

## Plan and rationale

1. **Understand before building.** Each requirement was turned into an explicit
   problem statement with a list of ambiguities and the decision taken on each.
2. **Decompose with dependencies.** Tasks were ordered so that the pieces with
   no dependencies (domain rules) came first and could be verified alone.
3. **Keep the risky parts small and visible.** Uniqueness is one database
   constraint. All SQL is in two classes. All destination rules are in one class.
4. **Change safely.** Stages 2 and 3 are additive: new optional fields, new
   nullable or defaulted columns, existing tests untouched.
5. **Use AI as an accelerator with controls.** See the [AI usage log](AI_USAGE_LOG.md)
   for what the assistant produced, what was changed or rejected and why, and
   exactly what was verified where.

## Artifacts

| Artifact | Location |
| --- | --- |
| Service code | `src/main/java` |
| Schema definitions | `src/main/resources/db/migration` (V1 to V3) |
| API definition | [`docs/openapi.yaml`](openapi.yaml); generated at `/v3/api-docs` and `/swagger-ui.html` when running |
| Unit and integration tests | `src/test/java` |
| Architecture overview | [`docs/ARCHITECTURE.md`](ARCHITECTURE.md) |
| Scenario write-ups | `docs/scenarios/` |
| AI traceability | [`docs/AI_USAGE_LOG.md`](AI_USAGE_LOG.md) |
| Setup instructions | [`README.md`](../README.md) |
| CI and dependency updates | `.github/workflows/ci.yml`, `.github/dependabot.yml` |
| Container build | `Dockerfile`, `docker-compose.yml` |

## Testing approach

| Level | Scope | Count | Notes |
| --- | --- | --- | --- |
| Unit | `domain` package: code generator, URL policy, alias policy, token bucket, referrer parsing, expiry boundary | 6 classes, 74 cases | No Spring context. Time is injected, so nothing sleeps. |
| Integration | Whole application through HTTP (MockMvc): filter, controllers, service, SQL, migrations on in-memory H2 | 2 classes, 22 tests | Analytics are switched to synchronous mode so counts are deterministic. The purge job is called directly. |

What the tests are designed to catch:

- Wrong behaviour at boundaries: alias length 3/4/32/33, TTL 59/60 and the
  maximum plus one, expiry one millisecond before and at the instant
- Security regressions in the URL policy: each bypass is a named test input
- Data integrity: a duplicate alias cannot overwrite a link; a purge removes
  only what it should, including click events
- Backward compatibility: stage 1 tests run unchanged after stages 2 and 3

What is not tested, stated plainly:

- Concurrency under real parallel load (the atomic SQL increment and the
  primary-key constraint are relied on by design, not demonstrated by a test)
- The asynchronous click path's behaviour when the queue overflows
- PostgreSQL: the integration tests run on H2 only. The SQL was written to be
  portable, but that is a claim until it is run. Testcontainers is the next step.
- Performance: no load test was run

Verification status at submission is recorded in the
[AI usage log](AI_USAGE_LOG.md#verification-record).

## Risks and trade-offs

| Risk or failure scenario | Likelihood | Impact | Mitigation in place | Residual |
| --- | --- | --- | --- | --- |
| Short-code collision | Very low (62^7 space) | Create fails | Retry up to 5 times on the primary-key violation; 503 with a clear message after that | Retry rate grows as the space fills; lengthen codes via `app.codes.length` |
| Database slow or down | Medium | Redirects for uncached links fail; clicks queue up | Cached links keep redirecting; click queue is bounded and drops instead of exhausting memory; health endpoint reports database state | No circuit breaker; uncached lookups wait for the connection timeout |
| Click under-counting | Medium under overload | Analytics slightly low | Drops are counted and logged | Counts are approximate under overload and after a crash. Accepted: redirect availability comes first |
| Abuse: mass link creation | Medium | Database growth | Per-client token bucket, 429 with `Retry-After` | Per instance, per address; a botnet or a shared NAT defeats or over-triggers it |
| Abuse: malicious destinations | High for any public shortener | Phishing through a trusted short domain | Scheme, credential, private-host and self-reference rules | No reputation or malware check on the destination |
| Enumeration of links | Low | Discovery of private URLs | Random codes from `SecureRandom` | Anyone holding a code can read its stats |
| Alias look-alikes (`Sale` vs `sale`) | Low | Confusion or spoofing | None | Case-insensitive uniqueness would need a normalized column |
| Purge deletes wanted data | Low | Data loss | Only rows expired beyond the retention period; batched; can be disabled | No soft delete or backup strategy defined here |
| AI-written code with a subtle defect | Medium | Wrong behaviour | Tests at two levels, compiler lint, CI, explicit verification record, small readable classes | Limited by what the tests cover; see "not tested" above |

Main trade-offs, in one line each:

- **Availability of redirects over accuracy of analytics** (async, bounded, droppable)
- **Simplicity over horizontal scale features** (in-process cache and rate limiter)
- **Explicit SQL over ORM convenience**
- **Additive changes over cleaner but breaking redesigns**
- **Leaving out delete/update over shipping them unauthenticated**

## Assumptions

- One deployment region; a single relational database is acceptable.
- No user accounts: links are anonymous and public.
- Read-heavy workload: many redirects per link created.
- Click counts may be approximate; redirects must be fast and reliable.
- UTC is the reporting time zone for analytics.
- The service sits behind TLS termination and, in production, an API gateway.

## Limitations

1. **No authentication or ownership.** Anyone can create links and read any
   link's stats. No update or delete endpoints for that reason.
2. **Rate limiting is per instance and per remote address.**
3. **Cache and rate-limit state are in process.** Fine for one to a few
   instances; a shared store is needed beyond that.
4. **Analytics are eventually consistent and can lose clicks** under overload or
   on an unclean shutdown.
5. **Private-host detection is textual.** A public DNS name that resolves to a
   private address is not detected.
6. **PostgreSQL path is configured but not covered by automated tests.**
7. **`click_events` grows without bound** for links that never expire. It needs
   partitioning or roll-up before real traffic.
8. **Dependency versions are a known-good set, not the latest.** Dependabot is
   configured to propose upgrades.
9. **No load test, no metrics dashboards, no tracing.**

## Next steps, in priority order

1. Authentication and link ownership (API keys or OAuth2), then delete and update
2. Testcontainers-based PostgreSQL integration tests in CI
3. Shared rate limiter and optional shared cache
4. Micrometer metrics for redirect latency, cache hit rate, dropped clicks; alerts
5. Click pipeline through a durable queue; roll up old click events
6. Destination reputation check; abuse reporting and link takedown
7. Load test to establish a baseline and capacity model
