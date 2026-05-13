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
hql-tester.result-output-folder=./hql-test-results
```

### 3. Activate your DB profile (if needed)

```java
@ActiveProfiles("dev")   // uncomment in HqlTesterTest if needed
```

### 4. Write your test cases

Add entries to `hqlQueries()` in `HqlTesterTest.java`:

```java
static Stream<Arguments> hqlQueries() {
    return Stream.of(

        tc("trunc-date",
            "SELECT function('trunc', e.createdAt) FROM YourEntity e",
            "trunc(",        // fragment expected in Oracle SQL
            "date_trunc("),  // fragment expected in Postgres SQL

        tc("listagg",
            "SELECT e.dept, function('listagg', e.name, ',') FROM YourEntity e GROUP BY e.dept",
            "listagg(",
            "string_agg("),

        tc("update-sysdate",     // DML — always rolled back
            "UPDATE YourEntity e SET e.updatedAt = function('sysdate') WHERE e.id = :id",
            Map.of("id", 1L),
            "sysdate",
            "now()")
    );
}
```

Helper methods:
```java
// no params
tc("description", "HQL", "oracle fragment", "postgres fragment")

// with params
tc("description", "HQL", Map.of("param", value), "oracle fragment", "postgres fragment")
```

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

| Test case | Oracle fragment | Postgres fragment |
|-----------|----------------|-------------------|
| `trunc-date` | `trunc(` | `date_trunc(` |
| `trunc-number` | `trunc(` | `trunc(` |
| `sysdate` | `sysdate` | `now()` |
| `systimestamp` | `systimestamp` | `current_timestamp` |
| `add-months` | `add_months(` | `interval` |
| `months-between` | `months_between(` | `date_part(` |
| `last-day` | `last_day(` | `date_trunc(` |
| `to-char-date-*` | `to_char(` | `to_char(` |
| `to-char-number-no-format` | `to_char(` | `cast(` |
| `listagg-*` | `listagg(` | `string_agg(` |
| `instr` | `instr(` | `strpos(` |
| `update-set-sysdate` | `sysdate` | `now()` |
| `update-trunc-in-where` | `trunc(` | `date_trunc(` |
| `delete-with-trunc` | `trunc(` | `date_trunc(` |

Postgres fragments assume typical `FunctionContributor` output — adjust them
in `hqlQueries()` to match what your contributor actually generates.

## Important notes

- **DML is always rolled back** — UPDATE/DELETE/INSERT never commits. Oracle
  sequences may advance on rollback; that is normal Oracle behaviour.

- **Fragment check, not exact match** — whitespace, alias numbering (`e1_0`),
  and table name quoting vary between Hibernate versions. Checking for the key
  function fragment is more stable than asserting the full SQL string.

- **Hibernate 6 only** — uses `createMutationQuery()`,
  `SessionFactory.withOptions()`, and `StatementInspector`.

- **No production footprint** — everything is in `src/test/java`.
