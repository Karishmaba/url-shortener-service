# Database and consistency

The application uses Spring JDBC and H2. Normal runs default to a file database,
not an in-memory link map. The schema source is
`shortener-service/src/main/resources/schema.sql`:

```sql
CREATE TABLE IF NOT EXISTS short_links (
    code VARCHAR(8) PRIMARY KEY,
    destination VARCHAR(2048) NOT NULL,
    redirect_count BIGINT NOT NULL DEFAULT 0 CHECK (redirect_count >= 0),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

`code` is the unique, case-sensitive primary key. `destination` holds the exact
accepted URL. `redirect_count` starts at zero and cannot be negative. `created_at`
is populated by H2; it is not exposed by the current API.

## Files and working directory

The default JDBC URL is `jdbc:h2:file:./data/shortener`. H2's main file is
`data/shortener.mv.db`, relative to the **application process's working directory**.
The application does not use a fixed repository-relative path.

| Launch method | Default application working directory | Default main database file |
|---|---|---|
| Run the packaged JAR from repository root | Repository root | `data/shortener.mv.db` |
| Maven run with `-Dspring-boot.run.workingDirectory="$PWD"` from root | Repository root | `data/shortener.mv.db` |
| Maven run without a working-directory override | `shortener-service` module | `shortener-service/data/shortener.mv.db` |

Use the [documented startup commands](../README.md#build-and-run) consistently,
or set an absolute file URL to use the same database across launch methods. For
example, from the repository root in a POSIX shell:

```sh
SHORTENER_DB_URL="jdbc:h2:file:$PWD/data/shortener" \
  java -jar shortener-service/target/shortener-service-0.0.1-SNAPSHOT.jar
```

The path names a database prefix; do not append `.mv.db` to the JDBC URL. Changing
it opens a different database. Run one application process against the default
embedded file database at a time; stop the old process before reopening it.
Database files and `data` directories are excluded from Git.

## Startup and preservation

`spring.sql.init.mode=always` runs the schema initialization for file databases.
`CREATE TABLE IF NOT EXISTS` preserves existing rows when the database is reopened.
Initialization errors fail startup rather than being silently ignored.
`spring.jpa.hibernate.ddl-auto=none` disables Hibernate schema generation, and the
application does not include JPA. The H2 browser console is disabled.

There is no startup reset, drop, truncate, automatic link expiration or in-memory
link cache. Links and counters persist when the same file is reopened. `created_at`
values are not regenerated for existing links. Future schema changes would require
an explicit migration; the current initializer only creates the table if absent.

## Creation and collisions

`SecureRandomCodeGenerator` creates eight-character ASCII alphanumeric candidates.
Random generation alone does not guarantee uniqueness: the database primary key
enforces it. Every attempt performs a parameterized INSERT. A duplicate key maps
to `CodeCollisionException` and leaves the existing row untouched; it is never
replaced or updated.

Each attempt runs in autocommit mode, suspending a caller transaction if one
exists. The service tries at most ten candidates in total. It can succeed on the
tenth attempt; ten collisions produce `503 CODE_UNAVAILABLE`. Other failures are
propagated rather than treated as collisions. Repeated destinations are not
looked up or deduplicated.

## Redirect transactions

For GET redirects, the repository opens an independent transaction and performs:

```sql
UPDATE short_links
SET redirect_count = redirect_count + 1
WHERE code = ?;
```

If the update finds no row, it returns no destination and the API returns 404.
Otherwise the repository reads the destination while holding the row lock, then
commits before returning. A failure in the transaction rolls the increment back.
This avoids a read-modify-write counter race under concurrent GET requests.

HEAD and stats use a case-sensitive SELECT without an increment. Health executes
`SELECT 1`. All user-provided values in data-access statements are bound as
parameters. See [redirect semantics](api.md#redirect-and-head) for what the counter
measures.

## Test isolation

Integration tests use separate H2 databases rather than development data. The
restart test creates a temporary file database, closes the first application
context and connection pool, and opens a second context against the same file.
It checks original destinations, counters, idempotent startup and continued link
creation. Concurrency tests use bounded executors and verify persisted results
once requests complete. MockMvc does not follow Location headers or fetch destinations.
