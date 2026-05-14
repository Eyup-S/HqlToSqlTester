package com.example.hqltester.model;

/**
 * Asserts something about the result-set returned by a SELECT query.
 * Result checks are ignored for DML queries (row count is always 0 after rollback).
 *
 * Row-level checks:
 *   NOT_EMPTY            — at least one row was returned
 *   EMPTY                — no rows were returned
 *   ROW_COUNT_EXACT      — exactly N rows
 *   ROW_COUNT_AT_LEAST   — at least N rows
 *
 * Value-level checks (string comparison after toString()):
 *   FIRST_VALUE_EQUALS   — first row / first column equals the expected value
 *   FIRST_VALUE_CONTAINS — first row / first column contains the expected substring
 *   ANY_VALUE_CONTAINS   — any cell in any row contains the expected substring
 *
 * Combine multiple checks via HqlTestCase.Builder#resultChecks(ResultCheck...).
 */
public class ResultCheck {

    public enum Type {
        NOT_EMPTY, EMPTY, ROW_COUNT_EXACT, ROW_COUNT_AT_LEAST,
        FIRST_VALUE_EQUALS, FIRST_VALUE_CONTAINS, ANY_VALUE_CONTAINS
    }

    private final Type   type;
    private final int    count;    // used by ROW_COUNT_* checks
    private final Object expected; // used by *_VALUE_* checks

    private ResultCheck(Type type, int count, Object expected) {
        this.type     = type;
        this.count    = count;
        this.expected = expected;
    }

    // ── Row-level ─────────────────────────────────────────────────────────────

    public static ResultCheck notEmpty()            { return new ResultCheck(Type.NOT_EMPTY,          0, null); }
    public static ResultCheck empty()               { return new ResultCheck(Type.EMPTY,               0, null); }
    public static ResultCheck rowCount(int n)       { return new ResultCheck(Type.ROW_COUNT_EXACT,     n, null); }
    public static ResultCheck rowCountAtLeast(int n) { return new ResultCheck(Type.ROW_COUNT_AT_LEAST, n, null); }

    // ── Value-level ───────────────────────────────────────────────────────────

    /** First row, first column — toString() must equal expected.toString(). */
    public static ResultCheck firstValueEquals(Object expected) {
        return new ResultCheck(Type.FIRST_VALUE_EQUALS, 0, expected);
    }

    /** First row, first column — toString() must contain the expected substring. */
    public static ResultCheck firstValueContains(String expected) {
        return new ResultCheck(Type.FIRST_VALUE_CONTAINS, 0, expected);
    }

    /** Any cell in any row — toString() must contain the expected substring. */
    public static ResultCheck anyValueContains(String expected) {
        return new ResultCheck(Type.ANY_VALUE_CONTAINS, 0, expected);
    }

    public Type   getType()     { return type; }
    public int    getCount()    { return count; }
    public Object getExpected() { return expected; }
}
