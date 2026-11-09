# URL shortener

A Spring Boot HTTP service in `shortener-service` that creates short links,
redirects callers, and counts successful GET redirects. Links and counters are
stored in file-backed H2 and survive restarts against the same database file.

## Prerequisites and pinned build

Use a JDK capable of compiling Java 21 sources; JDK 21 is the project's target.
Check `java -version` and `javac -version`. Set `JAVA_HOME` to the JDK you want
Maven to use. The recorded implementation checks used JDK 25 with Java 21 output;
execution on a JDK 21 installation has not yet been verified.

The checked-in Maven wrapper downloads Maven when needed; a separate Maven
installation is unnecessary. The first build needs network access to Maven
Central. On macOS/Linux the wrapper needs a shell, a downloader such as `curl`,
and archive/checksum tools. Windows uses `mvnw.cmd` and PowerShell.

| Component | Pinned version |
|---|---|
| Java compilation target | 21 |
| Spring Boot parent and packaging plugin | 4.1.2 |
| Maven distribution | 3.9.12 |
| Maven wrapper scripts | 3.3.4 |
| Surefire and Failsafe | 3.5.6 |

The Spring Boot parent manages dependency and other build-plugin versions.
`.mvn/wrapper/maven-wrapper.properties` pins Maven's distribution URL and SHA-256
checksum. The root `pom.xml` builds the `shortener-service` module.

## Build and run

Run these commands from the repository root (the directory containing `mvnw`
and the root `pom.xml`):

```sh
./mvnw verify
java -jar shortener-service/target/shortener-service-0.0.1-SNAPSHOT.jar
```

On Windows, use `./mvnw.cmd verify` in PowerShell in place of `./mvnw verify`.
Stop the running service with Ctrl+C. The default HTTP port is 8080.

For development, this alternative explicitly keeps the application's working
directory at the repository root:

```sh
./mvnw -pl shortener-service spring-boot:run -Dspring-boot.run.workingDirectory="$PWD"
```

Without the working-directory option, the Maven run goal launches from
`shortener-service`, so its default database would be under
`shortener-service/data` instead of root `data`. See [database details](docs/database.md).

## Try the API

These examples use a shell with `curl`, assume the service is running locally,
and do not follow destination redirects.

Check database connectivity:

```sh
curl -i http://localhost:8080/api/health
```

Create a link:

```sh
curl -i -X POST http://localhost:8080/api/urls \
  -H 'Content-Type: application/json' \
  --data '{"url":"https://example.com/articles?source=email#overview"}'
```

The response is `201`, with `code`, `url`, `shortUrl`, and a `Location` header
matching `shortUrl`. Substitute the returned code below; `aB3dE6gH` is an example,
not a preloaded link:

```sh
CODE='aB3dE6gH'
curl -sS "http://localhost:8080/api/urls/$CODE/stats"
curl -I "http://localhost:8080/$CODE"
curl -sS -D - -o /dev/null "http://localhost:8080/$CODE"
curl -sS "http://localhost:8080/api/urls/$CODE/stats"
```

For a new link, the first stats response shows zero. HEAD (`curl -I`) leaves it
at zero; the GET using `-D - -o /dev/null` produces a 302 and raises it to one.
Adding `-L` would tell curl to follow the destination; it is unnecessary here.

See the [complete API contract](docs/api.md) for validation, errors, HEAD behavior
and the meaning of a successful redirect.

## Configuration

| Environment variable | Default | Purpose |
|---|---|---|
| `SHORTENER_DB_URL` | `jdbc:h2:file:./data/shortener` | H2 JDBC URL; relative file paths use the application process's working directory. |
| `SHORTENER_DB_USERNAME` | `sa` | Database username. |
| `SHORTENER_DB_PASSWORD` | Empty | Database password; provide through the environment when needed. |
| `SHORTENER_PUBLIC_BASE_URL` | `http://localhost:8080` | Public HTTP(S) origin used in returned short URLs. |
| `SERVER_PORT` | `8080` | Spring Boot HTTP listener port. |

The public base URL must have a host and may have a port. It cannot contain
credentials, a non-root path, a query, or a fragment. A single trailing `/` is
removed. Request `Host` and forwarded headers do not determine generated links.
Changing the public base URL changes returned link text; it does not configure
TLS or the HTTP listener port.

For example, run the packaged service on port 8081 with matching short URLs:

```sh
SERVER_PORT=8081 SHORTENER_PUBLIC_BASE_URL=http://localhost:8081 \
  java -jar shortener-service/target/shortener-service-0.0.1-SNAPSHOT.jar
```

Reopen the same `SHORTENER_DB_URL` to retain links and counters. Changing the
file path selects a different database; it does not migrate existing data.

## Tests and verification

Run `./mvnw verify` from the repository root. Surefire runs unit classes named
`*Test`; Failsafe runs integration classes named `*IT` and fails the build if
those tests fail during `verify`. Do not use skip-test flags for acceptance.

Unit tests cover URL validation, code generation, collision retries/exhaustion
and service behavior. Integration tests exercise the complete application with
MockMvc and real isolated H2 databases, including HTTP errors, case sensitivity,
HEAD/statistics/health, database collisions, concurrent requests and two application
contexts reopening the same temporary file database. Tests do not fetch destinations
or use the development database.

Reports are written to `shortener-service/target/surefire-reports` and
`shortener-service/target/failsafe-reports`. Check that both suites were discovered;
a build that reports no tests does not prove acceptance behavior. Agent Kit task
workspaces contain only their prerequisite outputs, so a documentation workspace
can lack the separate test tasks. Full verification requires the combined outputs.

## Scope

This initial service has no authentication, custom aliases, link deletion,
expiration, browser UI, destination fetching or in-memory link cache. Repeated
creation requests always create separate links. GET counts do not prove that a
client followed the redirect or reached the destination.

## Delivery evidence

See [delivery, architecture and limitations](docs/delivery.md). Agent Kit is optional
for running this application; Python and an agent account are not runtime requirements.
GitHub Actions verifies the checked-in application on JDK 21 and retains test reports.

For upload commands and repository boundaries, see [GitHub upload handoff](docs/github-upload.md).
