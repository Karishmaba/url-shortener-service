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
