# Plan: url-shortener-sdlc

## Baseline and delivery boundary

The approved spec and requirements are the implementation contract. The repository
contains only requirements and Agent Kit artifacts: no Java source, POM, wrapper,
tests or documentation. Create the application under `shortener-service` and a
root Maven reactor so `./mvnw verify` runs all tests from the repository root.
This plan authorizes no task execution. All later edits use the isolated workspace
returned by `agentkit task start`; no worker edits Agent Kit configuration or specs.

## Architecture and shared implementation contract

Use Java 21, Spring Boot, Spring MVC, Spring JDBC and file-backed H2, with no JPA.
The build task selects and pins a supported Java-21-compatible Spring Boot release,
Maven distribution and plugin versions, recording the choices for other workers.
Use package `com.example.shortener` and constructor injection. The bootstrap class
is `ShortenerApplication`. Build dependencies include web, JDBC, H2 and the Spring
Boot test starter. Include executable root `mvnw`, `mvnw.cmd` and wrapper metadata
with a pinned distribution and checksum; configure Surefire for `*Test` and
Failsafe for `*IT`, bound to integration-test and verify. No skipped tests.

Production code ownership and interfaces:

- `domain/`: `UrlValidator.validate(String)` returns the unchanged string or throws
  `InvalidUrlException`; `CodeGenerator.nextCode()` returns a String, implemented
  with SecureRandom by `SecureRandomCodeGenerator`. Shared records are
  `ShortLink(String code, String destination, long redirectCount)` and
  `LinkStats(String code, long redirectCount)`. `InvalidUrlException`,
  `LinkNotFoundException`, `CodeUnavailableException` and `CodeCollisionException`
  are unchecked domain exceptions with no framework dependencies.
- `persistence/`: `LinkRepository` and its JDBC implementation expose
  `insert(String code, String destination)`, `find(String code)` returning
  `Optional<ShortLink>`, `incrementAndGetDestination(String code)` returning
  `Optional<String>`, and `checkConnectivity()`. Insert is a fresh autocommit
  attempt; only primary-key collisions map to CodeCollisionException. Atomic
  increment and destination retrieval share one committed transaction. Missing
  codes never increment. Connectivity executes a real query.
- `service/`: `LinkService.create(String)` validates, tries up to ten generated
  INSERTs and returns ShortLink; `redirect(String)` increments and returns the
  destination; `resolve(String)` returns the destination without incrementing;
  `stats(String)` returns LinkStats; `health()` checks connectivity. Validate
  codes against `[A-Za-z0-9]{8}` before lookup. Unknown/malformed codes throw
  LinkNotFoundException; collision exhaustion throws CodeUnavailableException.
- `web/`: MVC controllers and JSON error advice implement the exact spec. Creation
  parses an object with one string `url`; extra fields are ignored. Empty,
  non-object, malformed or trailing JSON is INVALID_JSON; missing/non-string
  `url` is INVALID_URL. Use explicit GET and HEAD handling so HEAD never invokes
  the increment path. Return the exact stored destination in Location without
  URI normalization. Produce 302 only after the increment transaction commits.
- `config/`: configure an explicit absolute HTTP(S) `shortener.public-base-url`,
  default `http://localhost:8080`, with no trailing slash; build short URLs from
  it, independent of untrusted forwarded headers. Configure development H2 via
  `SHORTENER_DB_URL`, default `jdbc:h2:file:./data/shortener`; relative database
  paths resolve against the process working directory. Document this behavior.

Resources: `schema.sql` contains the spec's exact idempotent schema. Set SQL
initialization to always, disable Hibernate schema generation, and never drop or
truncate on startup. No cache, destination requests or URL normalization.
Code and schema lookups remain case-sensitive. Health database failures follow
the spec's generic 500 INTERNAL_ERROR behavior. Error messages are readable,
without internal exception details; validation errors include `field: url`.

## Task graph and isolated ownership

Every task consumes spec and plan. Direct prerequisites are entry gates; workers
must not start before all are successful. The graph is:

`build-setup -> domain-contract -> persistence -> service -> http-api`

`domain-contract + build-setup -> unit-tests`

`http-api + build-setup -> integration-tests`

`http-api + build-setup -> documentation`

`build-setup + domain-contract + persistence + service + http-api + unit-tests
 + integration-tests + documentation -> verify -> final-review`

The definitive scopes and instructions are in tasks.json. Production owners do
not edit tests; test owners do not edit production sources, POMs or resources.
Unit tests can be authored independently from persistence/service using the
shared contract; they do not need to pass until all production prerequisites of
verify are combined. Documentation and integration tests can proceed independently
once the HTTP implementation exists. No overlapping writable scopes are granted.
Validation and review have empty write scopes. Repairs must return to the owner
of the affected files and respect the constitution's two-attempt limit; never
weaken tests or broaden scopes. Graph changes after registration need a new feature.

## Verification and review gates

Required owner validation ID: `maven-verify`, argv `["./mvnw", "verify"]`, executed
at repository root. The current allowlist is empty. The owner must configure this
entry before plan registration; the agent must not configure it. No test command
is run in the planning phase. First verification may download pinned dependencies;
network permission and available JDK are execution prerequisites.

Unit tests cover URL boundaries, syntax, credentials, schemes, whitespace/control
characters, preservation, code shape, collision retry/exhaustion and noncollision
failure propagation. Service tests use controlled generators/repositories.

MockMvc integration tests exercise every endpoint/status/error field/header,
strict JSON handling, duplicate destinations, case sensitivity, malformed codes,
HEAD/no count, stats/no count and health/no mutation. Use real H2 primary-key
collisions with an injected deterministic generator, including ten collisions
and preservation of existing rows. Concurrent creation and redirect tests use
bounded executors and assert final persisted results after all successful calls.
A separate restart IT closes then reopens two contexts against the same temporary
file H2 database and checks destination and counter retention. Every test isolates
its database from development data and performs no destination network access.
The root verify must discover and run both unit and integration suites and fail
on errors; inspect actual test reports, not merely the process exit code.

The final read-only review depends directly on verify and every deliverable task.
Check spec coverage, build reproducibility, data preservation, parameterized SQL,
atomicity, exact destinations, test discovery and documentation accuracy. Record
actual evidence and remaining risk. Any required approval belongs to the user;
never approve or integrate a candidate as part of this planning request.

## Risks and resolved assumptions

- Persistence/transaction boundaries must survive collisions without poisoning
  later attempts; do not wrap the whole creation retry loop in one transaction.
- Spring's implicit HEAD behavior could count visits; require explicit HEAD tests.
- URI/header builders can encode destinations; assert exact Location values.
- H2 connection pooling/file locks require full context closure in restart tests.
- Use bounded concurrency tests and temporary file paths to avoid flaky hangs.
- Non-object JSON is INVALID_JSON, as assumed in the approved specification.
  Configured public origin resolves the remaining origin decision.
- Exact dependency versions are pinned by the build owner before downstream work;
  no generated application, wrapper, or dependency download occurs at this stage.
