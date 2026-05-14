package com.example.hqltester.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable descriptor for a single parameterized HQL test case.
 *
 * Build via the fluent builder:
 *
 *   // SQL fragment check (most common)
 *   HqlTestCase.of("trunc-date", "SELECT trunc(m.creationDate) FROM Message m")
 *       .sqlContains("trunc(", "date_trunc(")
 *       .build()
 *
 *   // SQL check + result check
 *   HqlTestCase.of("messages-exist", "SELECT m FROM Message m")
 *       .sqlContains("from message", "from message")
 *       .resultNotEmpty()
 *       .build()
 *
 *   // Result check only (no SQL assertion)
 *   HqlTestCase.of("count-messages", "SELECT count(m) FROM Message m")
 *       .resultNotEmpty()
 *       .build()
 *
 *   // With bind params
 *   HqlTestCase.of("by-ref", "SELECT m FROM Message m WHERE m.senderReference = :ref")
 *       .params(Map.of("ref", "REF-001"))
 *       .sqlContains("senderreference", "senderreference")
 *       .resultNotEmpty()
 *       .build()
 */
public class HqlTestCase {

    private final String              description;
    private final String              hql;
    private final Map<String, Object> params;
    private final SqlCheck            sqlCheck;      // null → no SQL assertion
    private final List<ResultCheck>   resultChecks;  // empty → no result assertions

    private HqlTestCase(Builder b) {
        this.description  = b.description;
        this.hql          = b.hql;
        this.params       = b.params;
        this.sqlCheck     = b.sqlCheck;
        this.resultChecks = b.resultChecks;
    }

    public static Builder of(String description, String hql) {
        return new Builder(description, hql);
    }

    public String              getDescription()  { return description; }
    public String              getHql()          { return hql; }
    public Map<String, Object> getParams()       { return params; }
    public SqlCheck            getSqlCheck()     { return sqlCheck; }
    public List<ResultCheck>   getResultChecks() { return resultChecks; }

    @Override
    public String toString() { return description; }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static class Builder {
        private final String description;
        private final String hql;
        private Map<String, Object> params       = Map.of();
        private SqlCheck            sqlCheck     = null;
        private List<ResultCheck>   resultChecks = Collections.emptyList();

        private Builder(String description, String hql) {
            this.description = description;
            this.hql         = hql;
        }

        public Builder params(Map<String, Object> params) {
            this.params = params;
            return this;
        }

        // ── SQL assertion shortcuts ───────────────────────────────────────

        public Builder sqlContains(String oraFrag, String pgFrag) {
            this.sqlCheck = SqlCheck.contains(oraFrag, pgFrag);
            return this;
        }

        public Builder sqlExact(String oraFull, String pgFull) {
            this.sqlCheck = SqlCheck.exact(oraFull, pgFull);
            return this;
        }

        public Builder sqlRegex(String oraPattern, String pgPattern) {
            this.sqlCheck = SqlCheck.regex(oraPattern, pgPattern);
            return this;
        }

        public Builder sqlCheck(SqlCheck check) {
            this.sqlCheck = check;
            return this;
        }

        // ── Result assertion shortcuts ────────────────────────────────────

        public Builder resultNotEmpty() {
            this.resultChecks = List.of(ResultCheck.notEmpty());
            return this;
        }

        public Builder resultEmpty() {
            this.resultChecks = List.of(ResultCheck.empty());
            return this;
        }

        public Builder resultRowCount(int n) {
            this.resultChecks = List.of(ResultCheck.rowCount(n));
            return this;
        }

        public Builder resultRowCountAtLeast(int n) {
            this.resultChecks = List.of(ResultCheck.rowCountAtLeast(n));
            return this;
        }

        public Builder resultChecks(ResultCheck... checks) {
            this.resultChecks = Arrays.asList(checks);
            return this;
        }

        public HqlTestCase build() {
            return new HqlTestCase(this);
        }
    }
}
