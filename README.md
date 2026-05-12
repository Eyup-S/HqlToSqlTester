# HQL Tester — drop-in package for Oracle → Postgres migration

A self-contained test-scope Spring package that runs every HQL query as a
named `@ParameterizedTest`, captures the SQL Hibernate generates, and writes
a dated JSON report per run — so you can diff Oracle vs Postgres output side
by side.

Everything lives in `src/test/java` — zero footprint on the production build.

## Package structure

```
src/test/java/com/example/hqltester/
├── config/
│   └── HqlTesterProperties.java      # @ConfigurationProperties (hql-tester.*)
├── model/
│   ├── QueryType.java
│   ├── HqlFileInfo.java
│   └── HqlTestResult.java
├── service/
│   ├── SqlCaptureInspector.java       # Hibernate StatementInspector (per-session)
│   ├── HqlFileLoader.java             # reads .hql / .json files on every call
│   └── HqlTesterService.java          # orchestrates execution + SQL capture
└── HqlTesterTest.java                 # @SpringBootTest + @ParameterizedTest

src/test/resources/
└── logback-test.xml                   # ensures HqlTester logger is always visible

hql-test-queries/                      # drop .hql files here — picked up on next run
hql-test-results/                      # written at runtime, gitignored
```

## Setup

### 1. Copy the package into your project

Copy the `com/example/hqltester` folder into **`src/test/java`** of your project.  
**Find-replace `com.example` → your actual base package everywhere.**

If `@SpringBootTest` cannot locate your `@SpringBootApplication` class (packages
don't overlap), specify it explicitly in `HqlTesterTest.java`:

```java
@SpringBootTest(classes = YourApplication.class)
```

### 2. Add configuration

Copy the properties from `hql-tester-application.properties` into your
`src/test/resources/application.properties` (or your dev profile properties file):

```properties
hql-tester.query-folder=./hql-test-queries
hql-tester.max-results=100
hql-tester.result-output-folder=./hql-test-results
```

### 3. Activate your DB profile (if needed)

If your real DB connection lives in a Spring profile, uncomment `@ActiveProfiles`
in `HqlTesterTest.java`:

```java
@ActiveProfiles("dev")   // ← profile that wires up Oracle or Postgres
```

### 4. Add your HQL files

Drop `.hql` files into `hql-test-queries/`. Each file becomes a named test case
on the next run — no code changes needed.

```
hql-test-queries/
├── my-query.hql          # the HQL
└── my-query.json         # optional: bind parameter values
```

**`.hql` format** — first line starting with `-- ` is shown as the description:
```sql
-- Count active employees by department
SELECT e.department, count(e) FROM Employee e WHERE e.status = :status GROUP BY e.department
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

For quick one-off queries without a file, add entries to `inlineHql()` in
`HqlTesterTest.java`:

```java
static Stream<Arguments> inlineHql() {
    return Stream.of(
        Arguments.of(
            "count-all-employees",
            "SELECT count(e) FROM Employee e",
            Map.of()
        ),
        Arguments.of(
            "employees-by-status",
            "SELECT e.id, e.name FROM Employee e WHERE e.status = :s",
            Map.of("s", "ACTIVE")
        )
    );
}
```

## Running the tests

```bash
# Maven — run only HQL tester tests
mvn test -Dgroups=hql-tester

# Gradle
./gradlew test --tests "*.HqlTesterTest"

# IDE — right-click HqlTesterTest and run, or run individual test cases
```

Each `.hql` file and each inline entry appears as a **separate named test case**
in the IDE and in Surefire reports.

## Console output

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

## Oracle vs Postgres comparison via JSON reports

After every run a file is written to `hql-test-results/`:

```
hql-test-results/
├── hql-results-2024-05-11_10-30-00-OracleDialect.json
└── hql-results-2024-05-11_15-45-00-PostgreSQLDialect.json
```

Run against Oracle → switch DB config/profile → run again against Postgres →
diff the two files. The `generatedSql` field in each result shows exactly how
Hibernate translated the same HQL for each dialect.

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
      "queryType": "SELECT",
      "generatedSql": ["select e1_0.id, to_char(e1_0.created_at,'YYYY-MM-DD') from employees e1_0"],
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
  so Hibernate compiles it and the `StatementInspector` captures the SQL, but
  no data is ever committed. **Oracle sequences** (NEXTVAL) may still advance
  on rollback — that is normal Oracle behaviour.

- **SQL capture**: `SqlCaptureInspector` is attached per-session via
  `SessionFactory.withOptions().statementInspector(...)` — it never touches
  other sessions or application code.

- **No production footprint**: The entire package is in `src/test/java` and is
  never included in the production build.

- **Hibernate 6 only**: `createMutationQuery()`, `SessionFactory.withOptions()`,
  and `StatementInspector` (`org.hibernate.resource.jdbc.spi`) require Hibernate 6.x.
