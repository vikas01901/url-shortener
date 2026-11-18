# URL Shortener

A URL shortener service with click analytics and reliability features, built
with Java and Spring Boot as an AI-assisted engineering exercise.

| Document | What it covers |
| --- | --- |
| [Engineering summary](docs/ENGINEERING_SUMMARY.md) | Plan, artifacts, validation, risks, trade-offs, assumptions, limitations |
| [Architecture](docs/ARCHITECTURE.md) | Components, control flow, data model, key decisions |
| [Scenario 1: greenfield](docs/scenarios/01-greenfield.md) | Building the core service |
| [Scenario 2: brownfield](docs/scenarios/02-brownfield.md) | Adding custom aliases to the existing code |
| [Scenario 3: ambiguous](docs/scenarios/03-ambiguous.md) | "Links shouldn't live forever" |
| [AI usage log](docs/AI_USAGE_LOG.md) | How AI was used, what was accepted and rejected, controls |
| [OpenAPI spec](docs/openapi.yaml) | API contract |

## Features

- Create a short link for any public http/https URL
- Optional custom alias and optional expiry (TTL)
- 302 redirect with a cached lookup
- Analytics per link: total clicks, clicks per day, top referring hosts, last access
- Per-client rate limit on link creation
- URL safety rules (no `javascript:`, no embedded credentials, no private hosts)
- Consistent RFC 7807 error responses, health endpoint, OpenAPI UI
- Versioned schema migrations (Flyway), runs on H2 or PostgreSQL

## Requirements

- JDK 17 or newer
- Maven 3.6.3 or newer
- Docker (only for the PostgreSQL option)

## Run

```bash
mvn spring-boot:run
```

The service starts on <http://localhost:8080> with a file-backed H2 database in
`./data`, so no other setup is needed.

To run against PostgreSQL instead:

```bash
docker compose up --build
```

## Try it

```bash
# Create a link
curl -s -X POST http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/some/long/path?with=query"}'

# Create a link with a custom alias that expires in one hour
curl -s -X POST http://localhost:8080/api/v1/links \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com/sale", "alias": "spring-sale", "ttlSeconds": 3600}'

# Follow it (302 with a Location header)
curl -i http://localhost:8080/spring-sale

# Read analytics for the last 7 days
curl -s 'http://localhost:8080/api/v1/links/spring-sale/stats?days=7'
```

Interactive API docs: <http://localhost:8080/swagger-ui.html>
Health: <http://localhost:8080/actuator/health>

## API

| Method | Path | Purpose | Success | Errors |
| --- | --- | --- | --- | --- |
| POST | `/api/v1/links` | Create a link | 201 | 400 invalid input, 409 alias taken, 429 rate limited |
| GET | `/{code}` | Redirect to the original URL | 302 | 404 unknown, 410 expired |
| GET | `/api/v1/links/{code}` | Link metadata | 200 | 404 |
| GET | `/api/v1/links/{code}/stats?days=30` | Click analytics | 200 | 404 |

Request body for `POST /api/v1/links`:

| Field | Required | Rules |
| --- | --- | --- |
| `url` | yes | http or https, at most 2048 characters, public host |
| `alias` | no | 4 to 32 characters of `A-Z a-z 0-9 - _`, not a reserved word |
| `ttlSeconds` | no | 60 to 31,536,000 (365 days) |

## Test

```bash
mvn verify
```

This compiles with `-Xlint`, then runs the unit tests (domain logic, no Spring
context) and the integration tests (full application against in-memory H2
through MockMvc). The same command runs in GitHub Actions on every push.

## Configuration

All settings have defaults in [`application.yml`](src/main/resources/application.yml)
and can be overridden with environment variables (for example `APP_BASE_URL`).

| Property | Default | Meaning |
| --- | --- | --- |
| `app.base-url` | `http://localhost:8080` | Public origin used to build short URLs |
| `app.codes.length` | 7 | Length of generated codes |
| `app.cache.ttl` / `max-entries` | 10m / 100000 | Redirect cache |
| `app.rate-limit.capacity` / `refill-per-minute` | 20 / 60 | Burst and sustained creation rate per client |
| `app.analytics.async` | true | Record clicks off the request thread |
| `app.url-rules.block-private-hosts` | true | Reject loopback and private-range destinations |
| `app.expiry.default-ttl` | unset | Lifetime for links created without `ttlSeconds` |
| `app.expiry.retention` | 30d | How long expired links are kept before deletion |

## Project layout

```
src/main/java/dev/vikas/shortener
  domain/       Framework-free logic: code generation, URL and alias rules, rate limiter
  persistence/  SQL access (NamedParameterJdbcTemplate)
  service/      Use cases, cache, click recording, purge job
  web/          Controllers, DTOs, error mapping, rate-limit filter
  config/       Typed settings and bean wiring
src/main/resources/db/migration   Flyway migrations V1 to V3
src/test                          Unit and integration tests
docs/                             Architecture, scenarios, AI usage log, summary
```
