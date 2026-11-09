# HTTP API contract

Examples use `http://localhost:8080`. API response bodies are JSON; successful
redirects have no response body. Short codes match `[A-Za-z0-9]{8}` and are
case-sensitive. The example code `aB3dE6gH` is illustrative, not preloaded.

| Method and path | Success | Effect |
|---|---|---|
| `GET /api/health` | `200`, `{"status":"UP"}` | Executes a database connectivity query. |
| `POST /api/urls` | `201`, link JSON and `Location` | Creates a new link with a zero counter. |
| `GET /{code}` | `302`, destination in `Location` | Commits one redirect increment. |
| `HEAD /{code}` | `302`, redirect headers, no body | Reads the destination without incrementing. |
| `GET /api/urls/{code}/stats` | `200`, statistics JSON | Reads the committed counter without incrementing. |

## Create a link

Send `Content-Type: application/json` with one JSON object:

```json
{"url":"https://example.com/articles?source=email#overview"}
```

A successful response has status `201 Created`, a `Location` header equal to
`shortUrl`, and these fields:

```json
{
  "code": "aB3dE6gH",
  "url": "https://example.com/articles?source=email#overview",
  "shortUrl": "http://localhost:8080/aB3dE6gH"
}
```

Each request creates a separate link, even if the destination already exists.
The destination is returned and stored exactly, including its case, escaped
characters, path, query and fragment. The service does not trim, normalize,
resolve or fetch it. Additional JSON fields are ignored.

`url` must be a string containing an absolute HTTP or HTTPS URI with a valid
host, at most 2,048 characters (measured by Java `String.length()`). Credentials,
raw whitespace/control characters, invalid URI syntax and ports above 65535 are
rejected. Escaped content such as `%20` in a query is preserved.

Missing or non-string `url` fields and invalid destinations return
`400 INVALID_URL` with `field: "url"`. Empty bodies, malformed JSON, non-object
JSON such as `null` or an array, and trailing JSON/tokens return
`400 INVALID_JSON`. Unsupported request media types return
`415 UNSUPPORTED_MEDIA_TYPE`.

`shortUrl` is constructed from `SHORTENER_PUBLIC_BASE_URL`, independent of request
Host or forwarded headers. See [configuration](../README.md#configuration).

## Redirect and HEAD

For a known code, GET returns:

```http
HTTP/1.1 302 Found
Location: https://example.com/articles?source=email#overview
Cache-Control: no-store
```

The `Location` value is the stored destination, without re-encoding. Each
successful GET increments only that link's counter once. The increment and
destination lookup share a transaction which commits before the service returns
the destination for the 302 response. Concurrent successful GET requests do not
lose increments.

A successful redirect means the service committed an increment and produced a
302. It does not mean the client received every response byte, followed the
redirect, or reached the destination. The server never requests the destination.

HEAD returns the same redirect headers with no body and no increment. Unknown
or malformed codes return 404 for both GET and HEAD and change no counter.
HEAD error responses also have no body.

## Statistics

`GET /api/urls/aB3dE6gH/stats` returns:

```json
{"code":"aB3dE6gH","redirectCount":3}
```

The counter starts at zero and uses a database `BIGINT` / Java `long`. Reading
stats never adds a visit. Unknown or malformed codes return `404 NOT_FOUND`.

## Health

Health returns `200` with `{"status":"UP"}` after a real database query succeeds.
It creates no links and changes no counters. A database failure follows the
generic `500 INTERNAL_ERROR` response; health is not a check of destination
reachability.

## Errors

Errors include a stable `code` and a readable `message`. URL validation errors
also include `field`; other documented error bodies have only `code` and `message`.
Exact message wording is not a compatibility promise. For example:

```json
{
  "code": "INVALID_URL",
  "message": "Provide an absolute HTTP or HTTPS URL with a host, without credentials or whitespace (maximum 2048 characters).",
  "field": "url"
}
```

| HTTP status | Code | Meaning |
|---|---|---|
| 400 | `INVALID_URL` | Missing/non-string `url` or invalid destination. |
| 400 | `INVALID_JSON` | Missing, malformed, non-object or trailing JSON. |
| 404 | `NOT_FOUND` | Unknown/malformed code or unmapped resource. |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | Creation requires JSON. |
| 503 | `CODE_UNAVAILABLE` | All ten generated code attempts collided; the caller may retry. |
| 500 | `INTERNAL_ERROR` | Unexpected failure; internal details are hidden. |

The JSON body rule excludes HEAD, which has no response body. The table describes
the implemented contract for these endpoints; it does not promise additional
routes or methods. There is no authentication, custom alias, deletion, expiration
or browser UI.
