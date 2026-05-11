# HQL Tester — drop-in package for Oracle → Postgres migration

A self-contained Spring package that runs every HQL query as a named
`@ParameterizedTest`, captures the SQL Hibernate generates, and writes a
dated JSON report per run — so you can diff Oracle vs Postgres output side
by side without any manual HTTP calls.

## Package structure

```
src/main/java/com/example/hqltester/
├── config/
│   └── HqlTesterProperties.java      # @ConfigurationProperties
├── model/
│   ├── QueryType.java
│   ├── HqlFileInfo.java
│   ├── HqlTestRequest.java
│   └── HqlTestResult.java
└── service/
    ├── SqlCaptureInspector.java       # Hibernate StatementInspector (per-session)
    ├── HqlFileLoader.java             # reads .hql / .json files on every call
    └── HqlTesterService.java          # orchestrates execution + SQL capture

src/test/java/com/example/hqltester/
└── HqlTesterTest.java                 # @SpringBootTest + @ParameterizedTest

hql-test-queries/                      # drop .hql files here — no restart needed
```

## Setup

### 1. Copy the package

Copy `com/example/hqltester` into your `src/main/java`.  
**Find-replace `com.example` → your actual base package everywhere.**

If `@SpringBootTest` cannot locate your `@SpringBootApplication` class (packages
don't overlap), add `classes = YourApplication.class` to the annotation in
`HqlTesterTest.java`:

```java
@SpringBootTest(classes = YourApplication.class, properties = "hql-tester.enabled=true")
```

### 2. Add configuration

Merge into your `application-dev.yml` (or whichever profile hits the real DB):

```yaml
hql-tester:
  enabled: true                        # keep false / absent in production
  query-folder: ./hql-test-queries     # absolute or relative to working dir
  max-results: 100                     # row cap for SELECT queries
  result-output-folder: ./hql-test-results  # where JSON reports are written
```

`hql-tester.enabled` defaults to `false`, so nothing is active in production
unless you explicitly set it.

### 3. Activate your dev Spring profile (if needed)

Uncomment `@ActiveProfiles` in `HqlTesterTest.java`:

```java
@ActiveProfiles("dev")   // ← set the profile that wires up your real DB
```

### 4. Add your HQL files

Drop `.hql` files into `hql-test-queries/`. Each file becomes one named test
case automatically on the next run — no code changes required.

```
hql-test-queries/
├── my-query.hql          # HQL query
└── my-query.json         # optional: bind parameter values
```

**`.hql` format:**
```sql
-- Description shown in the test report (first line starting with "-- ")
SELECT e.id, e.name FROM Employee e WHERE e.status = :status
```

**`.json` params sidecar:**
```json
{
  "status": "ACTIVE",
  "fromDate": "2024-01-01",
  "ids": [1, 2, 3]
}
```

### 5. Add inline ad-hoc queries (optional)

For quick one-off queries that don't need a file, add entries to the
`inlineHql()` stream in `HqlTesterTest.java`:

```java
static Stream<Arguments> inlineHql() {
    return Stream.of(
        Arguments.of(
            "my-quick-check",
            "SELECT e.id, e.name FROM Employee e WHERE e.active = true",
            Map.of()
        )
    );
}
```

## Running the tests

```bash
# Run only HQL tester tests (tagged with @Tag("hql-tester"))
mvn test -Dgroups=hql-tester
./gradlew test --tests "*.HqlTesterTest"

# Or run from your IDE — each .hql file appears as a named test case
```

## Console output

Each test prints a formatted block showing the HQL, the generated SQL, and
results for SELECT queries:

```
══════════════════════════════════════════════════════════════════════
  01-to-char-date.hql                                        [SELECT]
══════════════════════════════════════════════════════════════════════
HQL:
  SELECT e.id AS id, function('to_char', e.createdAt, 'YYYY-MM-DD') AS formattedDate
  FROM Employee e

GENERATED SQL (1 statement):
  [1] select e1_0.id, to_char(e1_0.created_at,'YYYY-MM-DD') from employees e1_0

RESULTS: 2 row(s)  |  43ms
  +----+---------------+
  | id | formattedDate |
  +----+---------------+
  | 1  | 2024-01-15    |
  | 2  | 2024-02-20    |
  +----+---------------+

NOTE: SELECT executed — results capped at 100 rows
══════════════════════════════════════════════════════════════════════
```

For DML (UPDATE / DELETE / INSERT):
```
NOTE: DML translated and ROLLED BACK — would have affected 5 row(s). No data was committed.
```

## JSON reports for Oracle vs Postgres comparison

After every run a file is written to `hql-test-results/`:

```
hql-test-results/
├── hql-results-2024-05-11_10-30-00-OracleDialect.json
└── hql-results-2024-05-11_15-45-00-PostgreSQLDialect.json
```

Run against Oracle, switch the DB config/profile, run again against Postgres,
then diff the two files — the `generatedSql` fields show every dialect
difference directly.

Report structure:
```json
{
  "runAt": "...",
  "dialect": "OracleDialect",
  "totalTests": 10,
  "passed": 10,
  "failed": 0,
  "results": [
    {
      "source": "01-to-char-date.hql",
      "hql": "...",
      "queryType": "SELECT",
      "generatedSql": ["select to_char(e1_0.created_at,...) from employees e1_0"],
      "executed": true,
      "columns": ["id", "formattedDate"],
      "rows": [[1, "2024-01-15"]],
      "rowCount": 1,
      "executionTimeMs": 43,
      "error": null
    }
  ]
}
```

## What the sample files test

| File | What to watch in `generatedSql` |
|------|----------------------------------|
| `01-to-char-date.hql` | `to_char(...)` — Oracle-specific, must change for Postgres |
| `02-coalesce-nvl.hql` | `coalesce(...)` — portable, should match on both |
| `03-trunc-date.hql` | `trunc(...)` — Oracle vs `date_trunc('day',...)` in Postgres |
| `04-case-when.hql` | `CASE WHEN` — portable |
| `05-current-date-timestamp.hql` | `SYSDATE` vs `CURRENT_DATE` / `NOW()` |
| `06-string-functions.hql` | `concat`, `substring`, `locate` — check for dialect differences |
| `07-aggregate-having.hql` | Standard aggregates — should be identical |
| `08-to-number-cast.hql` | `cast(... AS integer)` — Hibernate 6 dialect-native |
| `09-join-fetch.hql` | Verify JOIN SQL is generated, not N+1 subselects |
| `10-sample-update-dml.hql` | DML — always rolled back, never committed |

## Important notes

- **DML safety**: UPDATE / DELETE / INSERT queries are executed inside a
  transaction that is **always rolled back**. The statement must reach the DB
  so Hibernate compiles it and the `StatementInspector` can capture it, but no
  data is ever committed. **Oracle sequences** (NEXTVAL) may still advance on
  rollback — that is normal Oracle behaviour.

- **SQL capture mechanism**: A `SqlCaptureInspector` is attached per-session via
  `SessionFactory.withOptions().statementInspector(...)`, so it never interferes
  with the rest of the application.

- **Hibernate 6 only**: `createMutationQuery()`, `SessionFactory.withOptions()`,
  and `StatementInspector` (`org.hibernate.resource.jdbc.spi`) all require
  Hibernate 6.x.

- **No production impact**: All service beans carry
  `@ConditionalOnProperty(prefix = "hql-tester", name = "enabled", havingValue = "true")`.
  Nothing is created unless that property is explicitly set.
