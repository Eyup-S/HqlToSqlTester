# HQL Tester — drop-in package for Oracle → Postgres migration

A self-contained Spring package that lets you test HQL queries and inspect
the SQL Hibernate generates, without restarting the application.

## How to use

### 1. Copy the package into your project

Copy the `com/example/hqltester` folder into your `src/main/java`.  
**Replace `com.example` with your actual base package everywhere.**

### 2. Add configuration

Merge `hql-tester-application.yml` into your `application-dev.yml`:

```yaml
hql-tester:
  enabled: true
  query-folder: ./hql-test-queries   # path to your .hql files
  max-results: 100
```

### 3. Drop `.hql` files into the query folder

No restart needed. Files are re-read on every request.

```
hql-test-queries/
├── my-query.hql          # the HQL
└── my-query.json         # optional: bind parameter values
```

**File format:**
- First line starting with `-- ` is used as the description.
- The rest is the raw HQL.

**Params sidecar (`.json`):**
```json
{
  "status": "ACTIVE",
  "fromDate": "2024-01-01",
  "ids": [1, 2, 3]
}
```

### 4. Use the REST API

```
GET  /hql-tester/health             — check config
GET  /hql-tester/files              — list available .hql files
GET  /hql-tester/run/{filename}     — run a file (e.g. 01-to-char-date.hql)
POST /hql-tester/run                — run inline HQL (JSON body)
```

**Inline run (curl):**
```bash
curl -X POST http://localhost:8080/hql-tester/run \
  -H 'Content-Type: application/json' \
  -d '{
    "hql": "SELECT e.id, e.name FROM Employee e WHERE e.status = :s",
    "params": {"s": "ACTIVE"}
  }'
```

**Plain text shorthand:**
```bash
curl -X POST http://localhost:8080/hql-tester/run \
  -H 'Content-Type: text/plain' \
  --data 'SELECT e FROM Employee e'
```

### 5. Read the response

```json
{
  "source": "01-to-char-date.hql",
  "hql": "SELECT e.id, function('to_char', e.createdAt, 'YYYY-MM-DD') ...",
  "queryType": "SELECT",
  "generatedSql": [
    "select e1_0.id, to_char(e1_0.created_at,'YYYY-MM-DD') from employees e1_0 ..."
  ],
  "executed": true,
  "executionNote": "SELECT executed — results capped at 100 rows",
  "columns": ["id", "function('to_char', e.createdAt, 'YYYY-MM-DD')"],
  "rows": [[1, "2024-03-15"], [2, "2024-04-01"]],
  "rowCount": 2,
  "executionTimeMs": 43,
  "error": null
}
```

For DML (UPDATE / DELETE / INSERT):

```json
{
  "queryType": "UPDATE",
  "generatedSql": ["update employees set status=?, updated_at=current_timestamp where ..."],
  "executed": true,
  "executionNote": "DML translated and ROLLED BACK — would have affected 5 row(s). No data was committed.",
  "rows": [],
  "error": null
}
```

## What each file tests

| File | What to watch in generatedSql |
|------|-------------------------------|
| `01-to-char-date.hql` | `to_char(...)` — Oracle-specific, must change for Postgres |
| `02-coalesce-nvl.hql` | `coalesce(...)` — should be identical on both |
| `03-trunc-date.hql` | `trunc(...)` — Oracle vs `date_trunc('day',...)` in Postgres |
| `04-case-when.hql` | `CASE WHEN` — portable |
| `05-current-date-timestamp.hql` | `SYSDATE` vs `CURRENT_DATE` / `NOW()` |
| `06-string-functions.hql` | `concat`, `substr`, `instr` — check for dialect differences |
| `07-aggregate-having.hql` | Standard aggregates — should be identical |
| `08-to-number-cast.hql` | `cast(... AS integer)` — Hibernate 6 dialect-native |
| `09-join-fetch.hql` | Verify JOIN is generated, not N+1 subselects |
| `10-sample-update-dml.hql` | DML — always rolled back, safe to run |

## Important notes

- **DML safety**: UPDATE/DELETE/INSERT queries are always rolled back. The SQL is
  sent to the database so Hibernate compiles it, then the transaction is rolled back
  immediately. **Oracle sequences** (NEXTVAL) may advance even on rollback — this is
  normal Oracle behaviour.

- **SELECT results**: Rows are returned from the real database, capped at `max-results`.
  Use this to compare Oracle vs Postgres output side-by-side.

- **Hibernate 6 only**: The `createMutationQuery()` and `SessionFactory.withOptions()`
  APIs used here require Hibernate 6.x. The `StatementInspector` interface is in
  `org.hibernate.resource.jdbc.spi.StatementInspector`.

- **No production use**: The `ConditionalOnProperty` guard ensures nothing is
  registered unless `hql-tester.enabled=true`. Keep that out of your prod profile.
