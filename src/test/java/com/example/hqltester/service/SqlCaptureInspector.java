package com.example.hqltester.service;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-session StatementInspector that intercepts every SQL statement
 * Hibernate sends to JDBC. One instance is created per test run and
 * attached to a dedicated Session via SessionFactory.withOptions(),
 * so it never interferes with the rest of the application.
 */
public class SqlCaptureInspector implements StatementInspector {

    private final List<String> capturedSql = new ArrayList<>();

    @Override
    public String inspect(String sql) {
        capturedSql.add(sql);
        return sql; // pass through unchanged
    }

    public List<String> getCapturedSql() {
        return Collections.unmodifiableList(capturedSql);
    }
}
