# Specification: url-shortener-sdlc

## Goal and scope

Provide a persistent URL-shortening HTTP service in `shortener-service` so a
caller can create a link, follow it, and inspect its successful GET redirect
count. Source requirement: `requirements/01-url-shortener.md`. The API and
database contract below is normative; this specification does not assert that
application code, build tooling, or tests already exist.

This stage creates and synchronizes the specification only. Architecture,
implementation planning, task execution, and application code are deferred.

# URL shortener requirements

Build the URL shortener in `shortener-service`.

- `POST /api/urls` accepts JSON containing an HTTP or HTTPS destination in `url`
  and returns `201` with `code`, `url`, and `shortUrl`.
- `GET /{code}` redirects to the stored destination with `302`.
- `GET /api/urls/{code}/stats` returns `code` and `redirectCount`, the number of
  successful GET redirects. New links start at zero; reading stats adds no visits.
- Persist links and counters across restarts using file-backed H2.
- Return `404` for unknown codes and `400` for invalid destination input.
- Generate unique, case-sensitive, eight-character alphanumeric codes. Database
  uniqueness must prevent collisions from overwriting existing links.
- Preserve destinations exactly and update counters atomically under concurrency.
- Include an API contract, database schema, unit tests, integration tests and
  setup instructions. Verify with `./mvnw verify` from the repository root.

## Detailed acceptance contract

The following defines expected behavior for the new application; it does not
claim that any code or tests already exist in this learning project.

# URL shortener API

The service accepts and returns JSON. Examples assume `http://localhost:8080`.
Short codes are case-sensitive, eight-character alphanumeric strings. Each POST
creates a new link, even when the destination has already been shortened.

## Health

`GET /api/health` returns `200` with `{"status":"UP"}` after checking database
connectivity. It does not create links or increment redirect counters.

## Create a URL

`POST /api/urls` with `Content-Type: application/json`:

```json
{"url":"https://example.com/articles?source=email#overview"}
```

Success: `201 Created`, with a `Location` header containing the short URL:

```json
{
  "code":"aB3dE6gH",
  "url":"https://example.com/articles?source=email#overview",
  "shortUrl":"http://localhost:8080/aB3dE6gH"
}
```

`url` must be a string containing an absolute HTTP or HTTPS URL with a host.
The maximum length is 2,048 characters. Credentials, whitespace, control
characters, invalid URI syntax, and ports above 65535 are rejected. Destinations
are preserved, including path, query and fragment. The service never fetches a
destination to validate it. Extra JSON fields are ignored.

Invalid destinations, missing or non-string `url` values, and malformed or trailing
JSON return `400 Bad Request`. Unsupported request media types return `415`.

## Redirect

`GET /{code}` returns `302 Found` with the original destination in the `Location`
header and `Cache-Control: no-store`. A successful redirect increments the stored
counter once. An unknown or malformed code returns `404` and changes no counter.

A successful redirect means the service commits a counter increment and produces
a 302 response. It does not mean that the client followed the link, received all
response bytes, or successfully reached the destination. HEAD requests return
the redirect headers without incrementing the counter.

## Statistics

`GET /api/urls/{code}/stats` returns `200 OK`:

```json
{"code":"aB3dE6gH","redirectCount":3}
```

A new link has zero redirects. Reading stats does not increment the count.
Unknown and malformed codes return `404 Not Found`.

## Errors

Errors use JSON with a stable `code` and a readable `message`. URL validation
errors also include `field`:

```json
{"code":"INVALID_URL","message":"Provide an absolute HTTP or HTTPS URL with a host, without credentials or whitespace (maximum 2048 characters).","field":"url"}
```

| HTTP status | Error code | Meaning |
|---|---|---|
| 400 | `INVALID_URL` | Missing or invalid destination |
| 400 | `INVALID_JSON` | Missing, malformed or trailing JSON |
| 404 | `NOT_FOUND` | Unknown or malformed short code |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Creation requires JSON |
| 503 | `CODE_UNAVAILABLE` | Ten generated codes collided; retry the request |
| 500 | `INTERNAL_ERROR` | Unexpected server failure; internal details are hidden |

## Database schema and consistency

The required schema is:

```sql
CREATE TABLE IF NOT EXISTS short_links (
    code VARCHAR(8) PRIMARY KEY,
    destination VARCHAR(2048) NOT NULL,
    redirect_count BIGINT NOT NULL DEFAULT 0 CHECK (redirect_count >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

Spring JDBC accesses this table using parameterized SQL. Spring initializes it
idempotently on startup; Hibernate schema generation is disabled for normal
runs. Existing links and counters remain intact when the same database is reopened.

SecureRandom generates codes. Each creation attempt performs an INSERT; the
primary key rejects collisions without replacing or updating the existing row.
The service retries a collision in a fresh autocommit transaction, up to ten
attempts. Redirects increment counters with `redirect_count = redirect_count + 1`
and retrieve the destination within one transaction, preventing lost increments
under concurrent requests. There is no in-memory link cache or reset on startup.

This initial API has no authentication, custom aliases, link deletion, expiration,
or browser UI.

## Verification

Run `./mvnw verify` from the repository root. Unit tests cover URL validation,
code generation, collision retries and exhaustion. MockMvc integration tests
exercise HTTP contracts, errors, stats, HEAD behavior, real database collisions,
concurrent creation and concurrent redirects. A separate integration test closes
and reopens two application contexts against the same temporary file-backed H2
database, proving link and counter persistence across restarts. All tests isolate
their databases from development data and require no destination network access.

## Observable acceptance criteria

1. Creating a valid destination returns 201, an eight-character code matching
   `[A-Za-z0-9]{8}`, the exact input destination, and a short URL matching the
   response Location header. Creating the same destination again produces a
   separate link with a distinct code and a zero counter.
2. Validation accepts absolute HTTP/HTTPS URLs with a host up to 2,048
   characters, including paths, queries and fragments. It rejects relative
   URLs, other schemes, absent hosts, credentials, whitespace, control
   characters, invalid URI syntax, excessive ports and overlength values.
   Accepted values are never trimmed, normalized or fetched.
3. Missing or non-string `url` values return INVALID_URL with `field: url`.
   Missing, malformed or trailing JSON returns INVALID_JSON. Unsupported
   media types return UNSUPPORTED_MEDIA_TYPE. Extra object fields are ignored.
   Rejected creation requests create no link.
4. A known code returns 302 with the exact destination and `Cache-Control:
   no-store`. Each successful GET increments only that link once. HEAD
   returns the same redirect headers without incrementing. Stats reads and
   health checks change no redirect count.
5. Codes are case-sensitive. Unknown or malformed codes return NOT_FOUND for
   redirects and stats and change no stored counter. A casing change must
   never resolve a different-cased stored code through case-insensitive lookup.
6. Stats initially reports zero and subsequently reports the committed number
   of successful GET redirects. After N concurrent successful GET redirects
   have completed, the count increases by exactly N without lost updates.
7. A generated collision leaves the existing destination and counter intact.
   A later noncolliding attempt succeeds; ten colliding attempts return 503
   CODE_UNAVAILABLE without creating a link. Concurrent successful creations
   retain distinct codes and their respective exact destinations.
8. Closing and reopening application contexts using the same temporary
   file-backed H2 database retains links and counters. Repeated schema
   initialization preserves existing rows. Tests use isolated databases.
9. Health reports 200 with `{"status":"UP"}` only after a successful database
   connectivity check. Unexpected failures use 500 INTERNAL_ERROR without
   exposing internal details; error bodies follow the documented JSON shape.
10. Deliverables include the API contract, required schema, unit and integration
    tests, and setup instructions covering prerequisites, startup, persistent
    database configuration, example requests and root-level `./mvnw verify`.
    Completion requires that command to pass with the verification coverage
    above, without contacting destination servers.

## Exclusions

Authentication, custom aliases, deletion, expiration and a browser UI are out
of scope. Destination reachability checks and destination fetching are excluded.
Redirect counts do not measure delivery of response bytes or destination visits.
No in-memory link cache or startup reset is permitted.

## Assumptions and remaining decisions

- The detailed acceptance contract takes precedence over the brief summary.
  Empty request bodies are INVALID_JSON; a JSON object missing `url`, or a
  non-string value for it, is INVALID_URL. Non-object JSON is assumed to be
  INVALID_JSON because creation requires an object containing `url`.
- The example origin is local development configuration. The public origin
  used to construct `shortUrl` must be documented; deployment/proxy origin
  configuration remains a later planning decision.
- Framework/JDK versions, Maven module wiring and the development H2 file
  location remain planning decisions within the mandated Spring JDBC, H2 and
  repository-root verification constraints.
- Error messages must be readable; stable codes and the validation `field`
  are contractual, while exact message wording is not assumed to be fixed.
- No blocking questions remain for specification. Non-object JSON handling
  and origin configuration should be confirmed during subsequent review.
