package com.example.hqltester.model;

/**
 * Asserts something about the result-set returned by a SELECT query.
 * Result checks are ignored for DML queries (row count is always 0 after rollback).
 *
 * Available checks:
 *   NOT_EMPTY        — at least one row was returned
 *   EMPTY            — no rows were returned
 *   ROW_COUNT_EXACT  — exactly N rows
 *   ROW_COUNT_AT_LEAST — at least N rows
 */
public class ResultCheck {

    public enum Type { NOT_EMPTY, EMPTY, ROW_COUNT_EXACT, ROW_COUNT_AT_LEAST }

    private final Type type;
    private final int  count;

    private ResultCheck(Type type, int count) {
        this.type  = type;
        this.count = count;
    }

    public static ResultCheck notEmpty()           { return new ResultCheck(Type.NOT_EMPTY,          0); }
    public static ResultCheck empty()              { return new ResultCheck(Type.EMPTY,               0); }
    public static ResultCheck rowCount(int n)      { return new ResultCheck(Type.ROW_COUNT_EXACT,     n); }
    public static ResultCheck rowCountAtLeast(int n){ return new ResultCheck(Type.ROW_COUNT_AT_LEAST, n); }

    public Type getType()  { return type; }
    public int  getCount() { return count; }
}
