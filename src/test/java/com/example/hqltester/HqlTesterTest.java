package com.example.hqltester;

import com.example.hqltester.config.HqlTesterProperties;
import com.example.hqltester.model.HqlTestResult;
import com.example.hqltester.model.QueryType;
import com.example.hqltester.service.HqlTesterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HQL → SQL inspection and dialect comparison test suite.
 *
 * Each test case carries:
 *   description | HQL | params | expected Oracle SQL fragment | expected Postgres SQL fragment
 *
 * Two assertions per test:
 *   1. result.error is null  → HQL compiled and ran on the DB without error
 *   2. generated SQL contains the expected fragment for the active dialect
 *      → confirms FunctionContributor / dialect mapping is applied correctly
 *
 * Switch hql-tester.active-dialect=oracle|postgres in your properties file
 * when you change the target database.
 *
 * NOTE: If @SpringBootTest cannot find your @SpringBootApplication class,
 *   add: @SpringBootTest(classes = YourApplication.class)
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("hql-tester")
// @ActiveProfiles("dev")  // ← uncomment if needed
class HqlTesterTest {

    private static final Logger log = LoggerFactory.getLogger("HqlTester");

    @Autowired
    // @Qualifier("yourEntityManagerFactory")  // ← add qualifier if multi-datasource
    private EntityManagerFactory emf;

    @Autowired private HqlTesterService service;
    @Autowired private HqlTesterProperties properties;
    @Autowired private ObjectMapper objectMapper;

    private final List<HqlTestResult> sessionResults = new CopyOnWriteArrayList<>();

    // =========================================================================
    // Main parameterized test
    // =========================================================================

    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hqlQueries")
    void testSqlGeneration(String description,
                           String hql,
                           Map<String, Object> params,
                           String expectedOracleFragment,
                           String expectedPostgresFragment) {

        HqlTestResult result = service.run(hql, params, description);
        sessionResults.add(result);
        printResult(result);

        // 1. No compilation or DB execution error
        assertThat(result.getError())
                .as("HQL error in '%s':\n%s", description, result.getError())
                .isNull();

        // 2. Generated SQL contains the expected dialect-specific fragment
        String actualSql = String.join(" ", result.getGeneratedSql())
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        String expectedFragment = isOracle()
                ? expectedOracleFragment.toLowerCase(Locale.ROOT)
                : expectedPostgresFragment.toLowerCase(Locale.ROOT);

        assertThat(actualSql)
                .as("Expected SQL fragment not found in '%s'\n  Dialect  : %s\n  Generated: %s\n  Expected : ...%s...",
                        description, activeDialectLabel(), actualSql, expectedFragment)
                .contains(expectedFragment);
    }

    // =========================================================================
    // Test cases
    //
    // Structure: tc("description", "HQL", "OracleFragment", "PostgresFragment")
    //            tc("description", "HQL", Map.of("param", val), "OracleFragment", "PostgresFragment")
    //
    // ⚠  Replace entity and field names with your own throughout.
    //    Oracle fragments reflect what Oracle dialect generates.
    //    Postgres fragments reflect what your FunctionContributor produces —
    //    adjust them if your contributor generates something different.
    // =========================================================================

    static Stream<Arguments> hqlQueries() {
        return Stream.of(

            // ── DATE : TRUNC ─────────────────────────────────────────────────

            tc("trunc-date",
                "SELECT trunc(m.creationDate) FROM Message m",
                "trunc(",
                "date_trunc("),

            tc("trunc-date-in-where",
                "SELECT m FROM Message m WHERE trunc(m.creationDate) = trunc(sysdate())",
                "trunc(",
                "date_trunc("),

            tc("trunc-number",
                // trunc on a numeric — both dialects keep trunc() for numbers
                "SELECT trunc(m.orderCount) FROM Message m",
                "trunc(",
                "trunc("),

            // ── DATE : SYSDATE / SYSTIMESTAMP ────────────────────────────────

            tc("sysdate",
                "SELECT m FROM Message m WHERE m.creationDate < sysdate()",
                "sysdate",
                "now()"),

            tc("systimestamp",
                "SELECT m FROM Message m WHERE m.creationDate < systimestamp()",
                "systimestamp",
                "current_timestamp"),

            tc("sysdate-in-select",
                "SELECT m.senderReference, sysdate() FROM Message m",
                "sysdate",
                "now()"),

            // ── DATE : ADD_MONTHS ────────────────────────────────────────────

            tc("add-months",
                "SELECT m FROM Message m WHERE m.creationDate > add_months(sysdate(), 3)",
                "add_months(",
                "interval"),   // adjust: contributor typically generates + interval '3 months'

            tc("add-months-negative",
                "SELECT m FROM Message m WHERE m.creationDate > add_months(sysdate(), -6)",
                "add_months(",
                "interval"),

            // ── DATE : MONTHS_BETWEEN ────────────────────────────────────────

            tc("months-between",
                "SELECT months_between(sysdate(), m.creationDate) FROM Message m",
                "months_between(",
                "date_part("),  // adjust: depends on your contributor implementation

            // ── DATE : LAST_DAY ──────────────────────────────────────────────

            tc("last-day",
                "SELECT last_day(m.creationDate) FROM Message m",
                "last_day(",
                "date_trunc("), // adjust: contributor likely generates date_trunc('month', ...) + interval

            // ── DATE : TO_DATE ───────────────────────────────────────────────

            tc("to-date",
                "SELECT m FROM Message m WHERE m.creationDate > to_date(:dateStr, 'YYYY-MM-DD')",
                Map.of("dateStr", "2024-01-01"),
                "to_date(",
                "to_date("),

            // ── TO_CHAR : WITH DATE FORMAT ───────────────────────────────────

            tc("to-char-date-yyyymmdd",
                "SELECT to_char(m.creationDate, 'YYYY-MM-DD') FROM Message m",
                "to_char(",
                "to_char("),

            tc("to-char-date-with-time",
                "SELECT to_char(m.creationDate, 'YYYY-MM-DD HH24:MI:SS') FROM Message m",
                "to_char(",
                "to_char("),

            tc("to-char-date-month-year",
                "SELECT to_char(m.creationDate, 'MM/YYYY') FROM Message m",
                "to_char(",
                "to_char("),

            // ── TO_CHAR : NUMBER ─────────────────────────────────────────────

            tc("to-char-number-with-format",
                "SELECT to_char(m.orderCount, '999,999') FROM Message m",
                "to_char(",
                "to_char("),

            tc("to-char-number-no-format",
                // Oracle: to_char(col), Postgres: contributor likely emits cast(col as text)
                "SELECT to_char(m.orderCount) FROM Message m",
                "to_char(",
                "cast("),      // adjust if your contributor generates something else

            // ── CAST ─────────────────────────────────────────────────────────

            tc("cast-to-string",
                "SELECT cast(m.orderCount as string) FROM Message m",
                "cast(",
                "cast("),

            tc("cast-to-long",
                "SELECT cast(m.version as long) FROM Message m",
                "cast(",
                "cast("),

            // ── AGGREGATE : LISTAGG ──────────────────────────────────────────

            tc("listagg-basic",
                "SELECT m.senderReference, listagg(m.returnMessage, ',') " +
                "FROM Message m GROUP BY m.senderReference",
                "listagg(",
                "string_agg("),

            tc("listagg-in-having",
                "SELECT m.senderReference, listagg(m.returnMessage, ',') " +
                "FROM Message m GROUP BY m.senderReference " +
                "HAVING count(m) > :minCount",
                Map.of("minCount", 1),
                "listagg(",
                "string_agg("),

            // ── STRING FUNCTIONS ─────────────────────────────────────────────

            tc("coalesce",
                "SELECT coalesce(m.returnMessage, 'N/A') FROM Message m",
                "coalesce(",
                "coalesce("),

            tc("lpad",
                "SELECT lpad(m.senderReference, 20, '0') FROM Message m",
                "lpad(",
                "lpad("),

            tc("rpad",
                "SELECT rpad(m.senderReference, 20, ' ') FROM Message m",
                "rpad(",
                "rpad("),

            tc("instr",
                // Oracle: instr(), Postgres: strpos()
                "SELECT instr(m.senderReference, '-') FROM Message m",
                "instr(",
                "strpos("),    // adjust if your contributor uses position() instead

            tc("substr",
                "SELECT substr(m.senderReference, 1, 10) FROM Message m",
                "substr(",
                "substr("),

            // ── CASE WHEN ────────────────────────────────────────────────────

            tc("case-when-simple",
                "SELECT CASE m.returnMessage " +
                "WHEN 'OK' THEN 'Success' " +
                "WHEN 'ERR' THEN 'Error' " +
                "ELSE 'Unknown' END FROM Message m",
                "case",
                "case"),

            tc("case-when-searched",
                "SELECT CASE " +
                "WHEN m.orderCount > 1000 THEN 'High' " +
                "WHEN m.orderCount > 100  THEN 'Mid' " +
                "ELSE 'Low' END FROM Message m",
                "case",
                "case"),

            // ── NVL / NVL2 ───────────────────────────────────────────────────

            tc("nvl-string",
                "SELECT nvl(m.returnMessage, 'N/A') FROM Message m",
                "nvl(",
                "coalesce("),  // adjust if your contributor generates something else

            tc("nvl-number",
                "SELECT nvl(m.orderCount, 0) FROM Message m",
                "nvl(",
                "coalesce("),

            tc("nvl2",
                // nvl2(expr, value_if_not_null, value_if_null)
                "SELECT nvl2(m.returnMessage, 'Has value', 'No value') FROM Message m",
                "nvl2(",
                "case when")   // adjust: contributor likely generates CASE WHEN ... END
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** No-params shorthand. */
    private static Arguments tc(String desc, String hql, String oraFrag, String pgFrag) {
        return Arguments.of(desc, hql, Map.of(), oraFrag, pgFrag);
    }

    /** With bind params. */
    private static Arguments tc(String desc, String hql,
                                 Map<String, Object> params,
                                 String oraFrag, String pgFrag) {
        return Arguments.of(desc, hql, params, oraFrag, pgFrag);
    }

    private boolean isOracle() {
        String prop = properties.getActiveDialect();
        if (prop != null && !prop.isBlank()) {
            return prop.toLowerCase(Locale.ROOT).contains("oracle");
        }
        // Fallback: auto-detect from actual Hibernate dialect
        return resolveDialectName().toLowerCase(Locale.ROOT).contains("oracle");
    }

    private String activeDialectLabel() {
        return isOracle() ? "Oracle" : "Postgres";
    }

    // =========================================================================
    // Lifecycle — write JSON report after all tests finish
    // =========================================================================

    @AfterAll
    void writeSessionResultsToFile() throws IOException {
        if (sessionResults.isEmpty()) return;

        String dialect = resolveDialectName();
        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = "hql-results-" + timestamp + "-" + dialect + ".json";

        Path outputDir = Paths.get(properties.getResultOutputFolder());
        Files.createDirectories(outputDir);
        Path outputFile = outputDir.resolve(filename);

        long passed = sessionResults.stream().filter(r -> r.getError() == null).count();
        long failed = sessionResults.size() - passed;

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("runAt", LocalDateTime.now().toString());
        report.put("dialect", dialect);
        report.put("activeDialectLabel", activeDialectLabel());
        report.put("totalTests", sessionResults.size());
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("results", sessionResults);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), report);
        log.info("═".repeat(70));
        log.info("Results written to: {}", outputFile.toAbsolutePath());
        log.info("Dialect: {}  |  Passed: {} / {}  |  Failed: {}",
                dialect, passed, sessionResults.size(), failed);
        log.info("═".repeat(70));
    }

    // =========================================================================
    // Debug helpers — run individually, not part of the main suite
    // =========================================================================

    @Test
    @Tag("debug")
    void listKnownEntities() {
        System.out.println("Entity count: " + emf.getMetamodel().getEntities().size());
        emf.getMetamodel().getEntities().stream()
                .sorted(Comparator.comparing(e -> e.getName()))
                .forEach(e -> System.out.println(e.getName() + "  →  " + e.getJavaType().getSimpleName()));
    }

    @Test
    @Tag("debug")
    void checkFunctionRegistry() {
        SessionFactoryImplementor sfi = emf.unwrap(SessionFactoryImplementor.class);
        var registry = sfi.getQueryEngine().getSqmFunctionRegistry();

        System.out.println("Dialect: " + sfi.getJdbcServices().getDialect().getClass().getName());

        List.of("trunc", "sysdate", "systimestamp", "add_months", "months_between",
                "last_day", "to_char", "to_date", "listagg", "instr", "lpad", "rpad", "substr",
                "coalesce")
            .forEach(fn -> {
                var descriptor = registry.findFunctionDescriptor(fn);
                System.out.printf("%-20s → %s%n", fn,
                        descriptor == null ? "NOT REGISTERED"
                                           : descriptor.getClass().getSimpleName());
            });
    }

    // =========================================================================
    // Console output
    // =========================================================================

    private void printResult(HqlTestResult result) {
        StringBuilder sb = new StringBuilder();
        String sep = "═".repeat(70);

        sb.append("\n").append(sep).append("\n");
        sb.append(String.format("  %-52s [%s][%s]%n",
                result.getSource(), result.getQueryType(), activeDialectLabel()));
        sb.append(sep).append("\n");

        sb.append("HQL:\n");
        appendIndented(sb, result.getHql(), 2);

        List<String> sqls = result.getGeneratedSql();
        if (sqls != null && !sqls.isEmpty()) {
            sb.append("\nGENERATED SQL (").append(sqls.size()).append(" statement(s)):\n");
            for (int i = 0; i < sqls.size(); i++) {
                sb.append("  [").append(i + 1).append("] ").append(sqls.get(i)).append("\n");
            }
        } else {
            sb.append("\nGENERATED SQL: (none captured)\n");
        }

        if (result.isExecuted() && result.getQueryType() == QueryType.SELECT) {
            sb.append("\nRESULTS: ")
              .append(result.getRowCount()).append(" row(s)")
              .append("  |  ").append(result.getExecutionTimeMs()).append("ms\n");
            if (result.getColumns() != null && !result.getColumns().isEmpty()) {
                sb.append(formatTable(result.getColumns(), result.getRows()));
            }
        }

        if (result.getExecutionNote() != null) {
            sb.append("\nNOTE: ").append(result.getExecutionNote()).append("\n");
        }

        if (result.getError() != null) {
            sb.append("\n⚠  ERROR: ").append(result.getError()).append("\n");
        }

        sb.append(sep);
        log.info("{}", sb);
    }

    private void appendIndented(StringBuilder sb, String text, int indent) {
        String pad = " ".repeat(indent);
        Arrays.stream(text.split("\n"))
                .map(String::strip)
                .filter(l -> !l.isBlank())
                .forEach(line -> sb.append(pad).append(line).append("\n"));
    }

    private String formatTable(List<String> columns, List<List<Object>> rows) {
        if (columns.isEmpty()) return "";

        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) widths[i] = columns.get(i).length();
        for (List<Object> row : rows) {
            for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
                widths[i] = Math.max(widths[i], Math.min(cellText(row.get(i)).length(), 40));
            }
        }

        String rowSep = "  +" + Arrays.stream(widths)
                .mapToObj(w -> "-".repeat(w + 2))
                .collect(Collectors.joining("+")) + "+\n";

        StringBuilder sb = new StringBuilder();
        sb.append(rowSep);
        sb.append("  |");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(String.format(" %-" + widths[i] + "s |", columns.get(i)));
        }
        sb.append("\n").append(rowSep);

        int limit = Math.min(rows.size(), 20);
        for (int r = 0; r < limit; r++) {
            sb.append("  |");
            for (int c = 0; c < columns.size(); c++) {
                String val = (r < rows.size() && c < rows.get(r).size())
                        ? truncate(cellText(rows.get(r).get(c)), 40) : "";
                sb.append(String.format(" %-" + widths[c] + "s |", val));
            }
            sb.append("\n");
        }
        if (rows.size() > limit) {
            sb.append("  ... (").append(rows.size() - limit).append(" more rows)\n");
        }
        sb.append(rowSep);
        return sb.toString();
    }

    private String cellText(Object v) { return v == null ? "null" : v.toString(); }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String resolveDialectName() {
        try {
            return emf.unwrap(SessionFactoryImplementor.class)
                    .getJdbcServices().getDialect().getClass().getSimpleName();
        } catch (Exception e) {
            Object d = emf.getProperties().get("hibernate.dialect");
            if (d != null) {
                String n = d.toString();
                return n.contains(".") ? n.substring(n.lastIndexOf('.') + 1) : n;
            }
            return "UnknownDialect";
        }
    }
}
