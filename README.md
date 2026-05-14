# HQL Tester — Oracle → Postgres migration SQL inspection

A test-scope Spring package that verifies HQL queries produce the correct
dialect-specific SQL. Each test runs the HQL against the real database and
makes two assertions:

1. **No error** — the generated SQL executed on the DB without failure
2. **Fragment check** — the generated SQL contains the expected function/keyword
   for the active dialect (e.g. `trunc(` for Oracle, `date_trunc(` for Postgres)

Switching between Oracle and Postgres is one property change. Everything lives
in `src/test/java` — zero footprint on the production build.

## Package structure

```
src/test/java/com/example/hqltester/
├── config/
│   └── HqlTesterProperties.java      # hql-tester.* configuration
├── model/
│   ├── QueryType.java
│   └── HqlTestResult.java
├── service/
│   ├── SqlCaptureInspector.java       # Hibernate StatementInspector (per-session)
│   └── HqlTesterService.java          # executes HQL, captures SQL, rolls back DML
└── HqlTesterTest.java                 # @SpringBootTest + @ParameterizedTest

src/test/resources/
└── logback-test.xml

hql-test-results/                      # written at runtime, gitignored
```

## Setup

### 1. Copy the package

Copy `com/example/hqltester` into `src/test/java` of your project.  
**Find-replace `com.example` → your actual base package everywhere.**

If `@SpringBootTest` cannot find your `@SpringBootApplication` class:
```java
@SpringBootTest(classes = YourApplication.class)
```

If your app has multiple datasources, qualify the `EntityManagerFactory`:
```java
@Autowired
@Qualifier("yourEntityManagerFactory")
private EntityManagerFactory emf;
```
(Same qualifier also needed in `HqlTesterService` constructor.)

### 2. Add configuration

Merge into your `src/test/resources/application.properties`:

```properties
# "oracle" or "postgres" — switch this when you change the target DB
hql-tester.active-dialect=oracle

hql-tester.max-results=100

# JSON export — disabled by default, enable locally for inspection
hql-tester.export-results=false
hql-tester.result-output-folder=./hql-test-results
```

### 3. Activate your DB profile (if needed)

```java
@ActiveProfiles("dev")   // uncomment in HqlTesterTest if needed
```

### 4. Write your test cases

Add entries to `hqlQueries()` in `HqlTesterTest.java`.

Each test case is an `HqlTestCase` that can carry a **SQL assertion**, a **result assertion**, or both.

#### SQL assertion modes

| Mode | Method | Behaviour |
|------|--------|-----------|
| `CONTAINS` | `.sqlContains(oracle, postgres)` | generated SQL contains the fragment *(default, most robust)* |
| `EXACT` | `.sqlExact(oracle, postgres)` | normalized SQL equals the full expected string |
| `REGEX` | `.sqlRegex(oracle, postgres)` | generated SQL matches the regex pattern |

#### Result assertion options

**Row-level:**

| Method | Behaviour |
|--------|-----------|
| `.resultNotEmpty()` | at least one row returned |
| `.resultEmpty()` | zero rows returned |
| `.resultRowCount(n)` | exactly n rows |
| `.resultRowCountAtLeast(n)` | at least n rows |

**Value-level** (comparison via `toString()`):

| Method | Behaviour |
|--------|-----------|
| `.resultFirstValueEquals(expected)` | first row / first column equals the value |
| `.resultFirstValueContains(str)` | first row / first column contains the substring |
| `.resultAnyValueContains(str)` | any cell in any row contains the substring |

Combine multiple checks with `.resultChecks(ResultCheck...) `.

#### Examples

```java
static Stream<Arguments> hqlQueries() {
    return Stream.of(

        // ── Shorthand tc() — SQL fragment check (most common) ────────────
        tc("trunc-date",
            "SELECT trunc(m.creationDate) FROM Message m",
            "trunc(",        // fragment expected in Oracle SQL
            "date_trunc("),  // fragment expected in Postgres SQL

        // ── With bind params ─────────────────────────────────────────────
        tc("to-date",
            "SELECT m FROM Message m WHERE m.creationDate > to_date(:d, 'YYYY-MM-DD')",
            Map.of("d", "2024-01-01"),
            "to_date(", "to_date("),

        // ── SQL check + result row check ──────────────────────────────────
        tc(HqlTestCase.of("messages-exist", "SELECT m FROM Message m")
            .sqlContains("from message", "from message")
            .resultNotEmpty()
            .build()),

        // ── Scalar value check — query always returns a known constant ────
        tc(HqlTestCase.of("order-count-total", "SELECT count(m) FROM Message m WHERE m.orderCount = 123")
            .resultFirstValueEquals(123L)
            .build()),

        // ── First value contains substring ────────────────────────────────
        tc(HqlTestCase.of("sender-prefix", "SELECT m.senderReference FROM Message m")
            .resultFirstValueContains("REF")
            .build()),

        // ── Any cell in results contains substring ────────────────────────
        tc(HqlTestCase.of("any-ok-message", "SELECT m.returnMessage FROM Message m")
            .resultAnyValueContains("OK")
            .build()),

        // ── Combine multiple result checks ────────────────────────────────
        tc(HqlTestCase.of("count-with-value", "SELECT count(m) FROM Message m")
            .resultChecks(
                ResultCheck.rowCountAtLeast(1),
                ResultCheck.firstValueEquals(5L))
            .build()),

        // ── Exact SQL match ───────────────────────────────────────────────
        tc(HqlTestCase.of("sysdate-exact", "SELECT sysdate() FROM Message m")
            .sqlExact(
                "select sysdate from message m1_0",   // Oracle
                "select now() from message m1_0")     // Postgres
            .build())
    );
}
```

> **SQL assertions are always dialect-aware.** Both `.sqlContains()` and `.sqlExact()` store
> separate Oracle and Postgres expected values. The active dialect
> (`hql-tester.active-dialect`) determines which one is asserted at runtime.

## Running

```bash
# Maven
mvn test -Dgroups=hql-tester

# Gradle
./gradlew test --tests "*.HqlTesterTest"
```

Run the debug helpers individually to inspect your setup:
```bash
# lists all entity names Hibernate knows about
mvn test -Dgroups=debug -Dtest=HqlTesterTest#listKnownEntities

# prints which functions are registered and their descriptor type
mvn test -Dgroups=debug -Dtest=HqlTesterTest#checkFunctionRegistry
```

## Switching between Oracle and Postgres

Change one property and rerun:
```properties
hql-tester.active-dialect=postgres
```

The same test cases run against the Postgres DB. The fragment check switches
to the Postgres column automatically. The JSON report is named with the actual
Hibernate dialect class so you can tell the files apart:

```
hql-test-results/
├── hql-results-2024-05-11_10-00-00-OracleDialect.json
└── hql-results-2024-05-11_15-00-00-PostgreSQLDialect.json
```

## Console output per test

```
══════════════════════════════════════════════════════════════════════
  trunc-date                                          [SELECT][Oracle]
══════════════════════════════════════════════════════════════════════
HQL:
  SELECT function('trunc', e.createdAt) FROM Employee e

GENERATED SQL (1 statement):
  [1] select trunc(e1_0.created_at) from employees e1_0

RESULTS: 5 row(s)  |  38ms
══════════════════════════════════════════════════════════════════════
```

## Covered function cases (sample files)

All HQL uses the `Message` entity (`m`) with fields: `senderReference` (String),
`msgLob` (Clob), `receivedDate` (String/@Convert), `orderCount` (Long), `version` (short),
`creationDate` (Date), `returnMessage` (String).

Functions are called directly — no `function()` wrapper — because the `FunctionContributor`
registers them as named HQL functions.

| Test case | HQL call | Oracle fragment | Postgres fragment |
|-----------|----------|----------------|-------------------|
| `trunc-date` | `trunc(m.creationDate)` | `trunc(` | `date_trunc(` |
| `trunc-date-in-where` | `trunc(m.creationDate) = trunc(sysdate())` | `trunc(` | `date_trunc(` |
| `trunc-number` | `trunc(m.orderCount)` | `trunc(` | `trunc(` |
| `sysdate` | `m.creationDate < sysdate()` | `sysdate` | `now()` |
| `systimestamp` | `m.creationDate < systimestamp()` | `systimestamp` | `current_timestamp` |
| `sysdate-in-select` | `sysdate()` in SELECT | `sysdate` | `now()` |
| `add-months` | `add_months(sysdate(), 3)` | `add_months(` | `interval` |
| `add-months-negative` | `add_months(sysdate(), -6)` | `add_months(` | `interval` |
| `months-between` | `months_between(sysdate(), m.creationDate)` | `months_between(` | `date_part(` |
| `last-day` | `last_day(m.creationDate)` | `last_day(` | `date_trunc(` |
| `to-date` | `to_date(:dateStr, 'YYYY-MM-DD')` | `to_date(` | `to_date(` |
| `to-char-date-*` | `to_char(m.creationDate, 'YYYY-MM-DD')` | `to_char(` | `to_char(` |
| `to-char-number-with-format` | `to_char(m.orderCount, '999,999')` | `to_char(` | `to_char(` |
| `to-char-number-no-format` | `to_char(m.orderCount)` | `to_char(` | `cast(` |
| `listagg-*` | `listagg(m.returnMessage, ',')` | `listagg(` | `string_agg(` |
| `instr` | `instr(m.senderReference, '-')` | `instr(` | `strpos(` |
| `lpad` | `lpad(m.senderReference, 20, '0')` | `lpad(` | `lpad(` |
| `rpad` | `rpad(m.senderReference, 20, ' ')` | `rpad(` | `rpad(` |
| `substr` | `substr(m.senderReference, 1, 10)` | `substr(` | `substr(` |
| `coalesce` | `coalesce(m.returnMessage, 'N/A')` | `coalesce(` | `coalesce(` |
| `case-when-*` | `CASE WHEN ... END` | `case` | `case` |
| `nvl-string` | `nvl(m.returnMessage, 'N/A')` | `nvl(` | `coalesce(` |
| `nvl-number` | `nvl(m.orderCount, 0)` | `nvl(` | `coalesce(` |
| `nvl2` | `nvl2(m.returnMessage, 'Has value', 'No value')` | `nvl2(` | `case when` |

Postgres fragments assume typical `FunctionContributor` output — adjust them
in `hqlQueries()` to match what your contributor actually generates.

## Important notes

- **Fragment check, not exact match** — whitespace, alias numbering (`e1_0`),
  and table name quoting vary between Hibernate versions. Checking for the key
  function fragment is more stable than asserting the full SQL string.

- **Hibernate 6 only** — uses `createMutationQuery()`,
  `SessionFactory.withOptions()`, and `StatementInspector`.

- **No production footprint** — everything is in `src/test/java`.
