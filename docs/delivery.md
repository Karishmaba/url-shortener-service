# Delivery and verification record

The url-shortener-sdlc feature is INTEGRATED. Its 41-file reviewed manifest was
`8029eaa974415cbd24a3405949d2933a6f2b613e506af4d8dcedd39df79f2b02`.
This describes the application candidate before subsequent publication documentation
and CI additions; it is not a hash of the current repository or a Git commit.

Saved verify-2 evidence reports `./mvnw verify`, exit 0, 91 unit tests and 54 integration
tests, zero failures/errors/skips. One restart-persistence and two concurrency tests
ran. Attempt 1 failed before tests because the sandbox blocked Maven's wrapper cache;
attempt 2 passed without candidate changes. This record summarizes local evidence;
raw private workspace paths and runtime state are excluded from Git.

The feature contains build-setup, domain-contract, persistence, service, http-api,
unit-tests, integration-tests, documentation, verify and final-review. All succeeded,
and the user authorized exact-version integration. No push or deployment was part
of that workflow. Reproduce verification from a fresh clone with `./mvnw verify`.

## Architecture

```text
HTTP request → UrlController → LinkService → LinkRepository → JDBC → file H2
                     ↓             ↓
             ApiExceptionHandler   validation and code generation
```

The native coding agent reasons and edits; the separate agentkit CLI manages dependency
readiness, scoped snapshots, fingerprints, validation commands and approval records.
It is a development tool, not a runtime application dependency. See the sibling
agentic-orchestrator-kit repository's docs/deliverables.md for its orchestration
architecture and reproducible greenfield/brownfield/ambiguous fixtures.

## Limitations and decisions

JDBC makes transaction boundaries explicit; an atomic SQL increment avoids lost
updates. The database primary key enforces uniqueness, with at most ten code attempts.
Explicit HEAD routing avoids increments. A configured public origin avoids trusting
request Host headers. File H2 is convenient for a single local process; production
multi-instance deployment would need a different operational design. There is no
login, rate limiting, link deletion, expiration, or destination safety scanning.

The recorded run used JDK 25 with Java 21 output and MockMvc. Execution on JDK 21,
Windows, and live TLS/proxy behavior was not established by that evidence. CI is
configured for JDK 21; its result must be checked after upload. A separate live packaged-JAR HTTP check passed on 2026-11-08: health 200, creation 201,
redirect 302 with the exact destination, and persisted count 1, using an isolated
temporary database and port. It did not fetch the destination. TLS, proxies and
Windows remain untested. See [verification summary](evidence/verification-summary.json).

## Scenario scope

This repository supplies the native-agent greenfield case study. Expiration and
management endpoints discussed as future brownfield/ambiguous examples are not
implemented features. The toolkit's three small executable scenarios are scripted
fixtures and explicitly identify their validation and clarification boundaries.

## Publication verification

The integrated checkout was verified again on 2026-11-08: 91 unit and 54 integration
tests passed with no failures, errors or skips. The portable JSON summary lists each
suite without exporting Maven environment properties or private local paths.
