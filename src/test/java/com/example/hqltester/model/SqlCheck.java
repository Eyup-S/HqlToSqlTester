package com.example.hqltester.model;

/**
 * Describes how the generated SQL should be asserted for a given test case.
 * Choose the mode that best fits the stability/precision trade-off:
 *
 *   CONTAINS — generated SQL (normalized) contains the expected fragment.
 *              Most robust: unaffected by alias numbering or whitespace changes.
 *
 *   EXACT    — generated SQL (normalized, single-space) equals the expected string.
 *              Use only when you need the full shape, e.g. verifying a complex rewrite.
 *
 *   REGEX    — generated SQL matches the given regular-expression pattern.
 *              Useful when ordering of parts matters but exact wording may vary.
 */
public class SqlCheck {

    public enum Mode { CONTAINS, EXACT, REGEX }

    private final Mode   mode;
    private final String oracleExpected;
    private final String postgresExpected;

    private SqlCheck(Mode mode, String oracleExpected, String postgresExpected) {
        this.mode             = mode;
        this.oracleExpected   = oracleExpected;
        this.postgresExpected = postgresExpected;
    }

    public static SqlCheck contains(String oracle, String postgres) {
        return new SqlCheck(Mode.CONTAINS, oracle, postgres);
    }

    public static SqlCheck exact(String oracle, String postgres) {
        return new SqlCheck(Mode.EXACT, oracle, postgres);
    }

    public static SqlCheck regex(String oracle, String postgres) {
        return new SqlCheck(Mode.REGEX, oracle, postgres);
    }

    public Mode   getMode()             { return mode; }
    public String getOracleExpected()   { return oracleExpected; }
    public String getPostgresExpected() { return postgresExpected; }
}
