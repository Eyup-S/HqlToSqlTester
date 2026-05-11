package com.example.hqltester.service;

import com.example.hqltester.config.HqlTesterProperties;
import com.example.hqltester.model.HqlTestResult;
import com.example.hqltester.model.QueryType;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Transient;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@ConditionalOnProperty(prefix = "hql-tester", name = "enabled", havingValue = "true")
public class HqlTesterService {

    private final EntityManagerFactory emf;
    private final HqlTesterProperties properties;

    public HqlTesterService(EntityManagerFactory emf, HqlTesterProperties properties) {
        this.emf = emf;
        this.properties = properties;
    }

    public HqlTestResult run(String hql, Map<String, Object> params, String source) {
        String cleanHql = stripLineComments(hql).trim();
        QueryType queryType = detectQueryType(cleanHql);

        if (queryType == QueryType.SELECT) {
            return executeSelect(cleanHql, params, source);
        } else {
            return executeDml(cleanHql, params, source, queryType);
        }
    }

    // -----------------------------------------------------------------------
    // SELECT — execute and return rows
    // -----------------------------------------------------------------------

    @SuppressWarnings({"unchecked", "rawtypes"})
    private HqlTestResult executeSelect(String hql, Map<String, Object> params, String source) {
        SqlCaptureInspector inspector = new SqlCaptureInspector();
        long start = System.currentTimeMillis();

        try (Session session = openSession(inspector)) {
            Transaction tx = session.beginTransaction();
            try {
                Query<Object> query = session.createQuery(hql, Object.class);
                bindSelectParams(query, params);
                query.setMaxResults(properties.getMaxResults());

                List<Object> rawResults = query.list();
                tx.commit();

                long elapsed = System.currentTimeMillis() - start;
                List<String> columns = deriveColumnNames(hql, rawResults);
                List<List<Object>> rows = serializeRows(rawResults);

                return HqlTestResult.builder()
                        .source(source)
                        .hql(hql)
                        .queryType(QueryType.SELECT)
                        .generatedSql(inspector.getCapturedSql())
                        .executed(true)
                        .executionNote("SELECT executed — results capped at " + properties.getMaxResults() + " rows")
                        .columns(columns)
                        .rows(rows)
                        .rowCount(rows.size())
                        .executionTimeMs(elapsed)
                        .build();

            } catch (Exception e) {
                safeRollback(tx);
                throw e;
            }
        } catch (Exception e) {
            return HqlTestResult.builder()
                    .source(source)
                    .hql(hql)
                    .queryType(QueryType.SELECT)
                    .generatedSql(inspector.getCapturedSql())
                    .executed(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // DML — translate to SQL but ALWAYS rollback (no data changes committed)
    // -----------------------------------------------------------------------

    private HqlTestResult executeDml(String hql, Map<String, Object> params, String source, QueryType queryType) {
        SqlCaptureInspector inspector = new SqlCaptureInspector();
        long start = System.currentTimeMillis();
        Transaction tx = null;

        try (Session session = openSession(inspector)) {
            tx = session.beginTransaction();

            MutationQuery query = session.createMutationQuery(hql);
            bindMutationParams(query, params);

            // Execute to force Hibernate to compile and send the SQL so the
            // inspector captures it, then immediately roll back so no data
            // changes are ever committed.
            //
            // ⚠️  Oracle sequences: sequence.NEXTVAL calls inside INSERT INTO
            //    ... SELECT advance the sequence even on rollback — that is
            //    expected Oracle behaviour and unavoidable here.
            int affected = query.executeUpdate();

            tx.rollback(); // always — this is the contract

            long elapsed = System.currentTimeMillis() - start;
            return HqlTestResult.builder()
                    .source(source)
                    .hql(hql)
                    .queryType(queryType)
                    .generatedSql(inspector.getCapturedSql())
                    .executed(true)
                    .executionNote("DML translated and ROLLED BACK — would have affected "
                            + affected + " row(s). No data was committed.")
                    .columns(Collections.emptyList())
                    .rows(Collections.emptyList())
                    .rowCount(0)
                    .executionTimeMs(elapsed)
                    .build();

        } catch (Exception e) {
            safeRollback(tx);
            return HqlTestResult.builder()
                    .source(source)
                    .hql(hql)
                    .queryType(queryType)
                    .generatedSql(inspector.getCapturedSql())
                    .executed(false)
                    .executionTimeMs(System.currentTimeMillis() - start)
                    .error(e.getClass().getSimpleName() + ": " + e.getMessage())
                    .build();
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Session openSession(SqlCaptureInspector inspector) {
        return emf.unwrap(SessionFactory.class)
                .withOptions()
                .statementInspector(inspector)
                .openSession();
    }

    public QueryType detectQueryType(String hql) {
        String upper = hql.stripLeading().toUpperCase(Locale.ROOT);
        // HQL allows "FROM Entity" without a SELECT keyword — that is a SELECT
        if (upper.startsWith("SELECT") || upper.startsWith("FROM") || upper.startsWith("WITH")) {
            return QueryType.SELECT;
        }
        if (upper.startsWith("UPDATE")) return QueryType.UPDATE;
        if (upper.startsWith("DELETE")) return QueryType.DELETE;
        if (upper.startsWith("INSERT")) return QueryType.INSERT;
        return QueryType.UNKNOWN;
    }

    private String stripLineComments(String hql) {
        return Arrays.stream(hql.split("\n"))
                .map(line -> {
                    int idx = line.indexOf("--");
                    return idx >= 0 ? line.substring(0, idx) : line;
                })
                .collect(Collectors.joining("\n"));
    }

    // -----------------------------------------------------------------------
    // Parameter binding
    // -----------------------------------------------------------------------

    private void bindSelectParams(Query<?> query, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;

        Set<String> declared;
        try {
            declared = query.getParameterMetadata().getNamedParameterNames();
        } catch (Exception e) {
            // Metadata not always available before execution; fall through and try anyway
            declared = params.keySet();
        }

        for (String name : declared) {
            Object value = params.get(name);
            if (value == null) continue;
            if (value instanceof Collection) {
                query.setParameterList(name, (Collection<?>) value);
            } else {
                query.setParameter(name, value);
            }
        }
    }

    private void bindMutationParams(MutationQuery query, Map<String, Object> params) {
        if (params == null || params.isEmpty()) return;
        // MutationQuery (Hibernate 6) does not expose setParameterList.
        // For IN-clause parameters in DML, pass the collection directly and
        // let Hibernate's type system handle it, or expand them in the HQL itself.
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            try {
                query.setParameter(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Parameter not used in this query — skip silently
            }
        }
    }

    // -----------------------------------------------------------------------
    // Column name derivation
    // -----------------------------------------------------------------------

    private List<String> deriveColumnNames(String hql, List<Object> results) {
        if (results.isEmpty()) {
            return extractNamesFromSelectClause(hql);
        }

        Object first = results.get(0);

        if (first instanceof Object[]) {
            List<String> fromHql = extractNamesFromSelectClause(hql);
            int cols = ((Object[]) first).length;
            // Pad with positional names if HQL parsing came up short
            while (fromHql.size() < cols) {
                fromHql.add("col_" + fromHql.size());
            }
            return fromHql.subList(0, cols);
        }

        if (first != null && isJpaEntity(first)) {
            return getEntityFieldNames(first.getClass());
        }

        // Single scalar
        List<String> fromHql = extractNamesFromSelectClause(hql);
        return fromHql.isEmpty() ? Collections.singletonList("result") : fromHql.subList(0, 1);
    }

    /**
     * Parenthesis-aware SELECT clause splitter and alias extractor.
     * Handles function calls with commas inside parens correctly.
     */
    private List<String> extractNamesFromSelectClause(String hql) {
        // Collapse whitespace and strip line comments first
        String flat = hql.replaceAll("\\s+", " ");
        Pattern p = Pattern.compile("(?i)SELECT\\s+(.+?)\\s+FROM\\s+\\S", Pattern.DOTALL);
        Matcher m = p.matcher(flat);
        if (!m.find()) return new ArrayList<>();

        String selectClause = m.group(1).trim();
        return splitByTopLevelComma(selectClause).stream()
                .map(this::extractAlias)
                .collect(Collectors.toList());
    }

    private List<String> splitByTopLevelComma(String clause) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        StringBuilder current = new StringBuilder();

        for (char c : clause.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
            else if (c == ',' && depth == 0) {
                parts.add(current.toString().trim());
                current = new StringBuilder();
                continue;
            }
            current.append(c);
        }
        String last = current.toString().trim();
        if (!last.isEmpty()) parts.add(last);
        return parts;
    }

    private String extractAlias(String expr) {
        // "expr AS alias" or "expr alias"
        Matcher m = Pattern.compile("(?i)\\bAS\\s+(\\w+)\\s*$").matcher(expr);
        if (m.find()) return m.group(1);

        // Function call — use the whole expression (capped)
        if (expr.contains("(")) {
            String trimmed = expr.replaceAll("\\s+", " ").trim();
            return trimmed.length() > 40 ? trimmed.substring(0, 40) + "…" : trimmed;
        }

        // Path expression — take the last segment (e.name → name)
        String[] parts = expr.split("\\.");
        return parts[parts.length - 1].trim();
    }

    // -----------------------------------------------------------------------
    // Row serialization
    // -----------------------------------------------------------------------

    private List<List<Object>> serializeRows(List<Object> rawResults) {
        return rawResults.stream()
                .map(this::serializeRow)
                .collect(Collectors.toList());
    }

    private List<Object> serializeRow(Object result) {
        if (result instanceof Object[]) {
            return Arrays.stream((Object[]) result)
                    .map(this::serializeValue)
                    .collect(Collectors.toList());
        }
        return Collections.singletonList(serializeValue(result));
    }

    private Object serializeValue(Object value) {
        if (value == null) return null;
        if (value instanceof Number || value instanceof String || value instanceof Boolean) return value;
        if (value instanceof java.time.temporal.Temporal) return value.toString();
        if (value instanceof java.util.Date) return value.toString();
        if (isJpaEntity(value)) return serializeEntityToMap(value);
        return value.toString();
    }

    private boolean isJpaEntity(Object obj) {
        return obj.getClass().isAnnotationPresent(Entity.class);
    }

    private List<String> getEntityFieldNames(Class<?> clazz) {
        List<String> names = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!Modifier.isStatic(f.getModifiers())
                        && !f.isAnnotationPresent(Transient.class)) {
                    names.add(f.getName());
                }
            }
        }
        return names;
    }

    private Map<String, Object> serializeEntityToMap(Object entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (f.isAnnotationPresent(Transient.class)) continue;
                // Skip collection fields — accessing them risks LazyInitializationException
                if (Collection.class.isAssignableFrom(f.getType())
                        || Map.class.isAssignableFrom(f.getType())) {
                    map.put(f.getName(), "<collection — not loaded>");
                    continue;
                }
                f.setAccessible(true);
                try {
                    Object val = f.get(entity);
                    map.put(f.getName(), val == null ? null : serializeValue(val));
                } catch (Exception e) {
                    map.put(f.getName(), "<error: " + e.getMessage() + ">");
                }
            }
        }
        return map;
    }

    private void safeRollback(Transaction tx) {
        if (tx != null && tx.isActive()) {
            try { tx.rollback(); } catch (Exception ignored) {}
        }
    }
}
