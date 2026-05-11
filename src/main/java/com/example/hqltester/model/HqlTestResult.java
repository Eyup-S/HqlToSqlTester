package com.example.hqltester.model;

import java.util.List;

public class HqlTestResult {

    private String source;
    private String hql;
    private QueryType queryType;
    private List<String> generatedSql;
    private boolean executed;
    private String executionNote;
    private List<String> columns;
    private List<List<Object>> rows;
    private int rowCount;
    private long executionTimeMs;
    private String error;

    private HqlTestResult() {}

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final HqlTestResult r = new HqlTestResult();

        public Builder source(String v) { r.source = v; return this; }
        public Builder hql(String v) { r.hql = v; return this; }
        public Builder queryType(QueryType v) { r.queryType = v; return this; }
        public Builder generatedSql(List<String> v) { r.generatedSql = v; return this; }
        public Builder executed(boolean v) { r.executed = v; return this; }
        public Builder executionNote(String v) { r.executionNote = v; return this; }
        public Builder columns(List<String> v) { r.columns = v; return this; }
        public Builder rows(List<List<Object>> v) { r.rows = v; return this; }
        public Builder rowCount(int v) { r.rowCount = v; return this; }
        public Builder executionTimeMs(long v) { r.executionTimeMs = v; return this; }
        public Builder error(String v) { r.error = v; return this; }

        public HqlTestResult build() { return r; }
    }

    public String getSource() { return source; }
    public String getHql() { return hql; }
    public QueryType getQueryType() { return queryType; }
    public List<String> getGeneratedSql() { return generatedSql; }
    public boolean isExecuted() { return executed; }
    public String getExecutionNote() { return executionNote; }
    public List<String> getColumns() { return columns; }
    public List<List<Object>> getRows() { return rows; }
    public int getRowCount() { return rowCount; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public String getError() { return error; }
}
