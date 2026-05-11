package com.example.hqltester;

import com.example.hqltester.config.HqlTesterProperties;
import com.example.hqltester.model.HqlTestResult;
import com.example.hqltester.model.QueryType;
import com.example.hqltester.service.HqlFileLoader;
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
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.UncheckedIOException;
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
 * HQL inspection test suite.
 *
 * Runs every .hql file in the configured query folder as a separate
 * named test case. Also runs any inline queries defined in inlineHql().
 *
 * After all tests finish, results are written to hql-test-results/ as a
 * dated JSON file — run against Oracle and again against Postgres, then
 * diff the two files to spot dialect differences in generated SQL.
 *
 * HOW TO ADD A NEW QUERY (no restart, no code change):
 *   Drop a new .hql file into the query folder. It becomes a test case
 *   on the next test run automatically.
 *
 * FOR INLINE AD-HOC QUERIES:
 *   Add an entry to the inlineHql() stream below.
 *
 * SETUP:
 *   This test needs the full Spring context and a live DB connection.
 *   If your project has a dev profile, uncomment @ActiveProfiles below.
 *   The test sets hql-tester.enabled=true automatically via @SpringBootTest.
 *
 * NOTE: If @SpringBootTest cannot find your @SpringBootApplication class,
 *   add: @SpringBootTest(classes = YourApplication.class, properties = {...})
 */
@SpringBootTest(properties = {
        "hql-tester.enabled=true"
        // Override defaults if needed:
        // "hql-tester.query-folder=./hql-test-queries",
        // "hql-tester.max-results=50",
        // "hql-tester.result-output-folder=./hql-test-results"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Tag("hql-tester")
// @ActiveProfiles("dev")  // ← uncomment if you need a specific Spring profile
class HqlTesterTest {

    private static final Logger log = LoggerFactory.getLogger("HqlTester");

    @Autowired private HqlTesterService service;
    @Autowired private HqlFileLoader fileLoader;
    @Autowired private HqlTesterProperties properties;
    @Autowired private EntityManagerFactory emf;
    @Autowired private ObjectMapper objectMapper;

    /** Accumulates results across all parameterized invocations for the JSON report. */
    private final List<HqlTestResult> sessionResults = new CopyOnWriteArrayList<>();

    // =========================================================================
    // File-based tests — auto-discovers every .hql file in the query folder
    // =========================================================================

    /**
     * One test case per .hql file. The test name in the IDE/report is the
     * filename, so results are easy to navigate.
     *
     * Drop a new .hql file into the folder and rerun — no code changes needed.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("hqlFiles")
    void testFromFile(String filename, String hql, Map<String, Object> params) {
        HqlTestResult result = service.run(hql, params, filename);
        sessionResults.add(result);
        printResult(result);

        assertThat(result.getError())
                .as("HQL compilation/execution error in '%s'", filename)
                .isNull();
    }

    // =========================================================================
    // Inline tests — add ad-hoc queries directly here
    // =========================================================================

    /**
     * Inline parameterized queries. Useful for quick experiments that don't
     * warrant a dedicated file, or for testing specific keyword combinations.
     *
     * Format: Arguments.of("description", "HQL here", Map.of("param", value))
     * Use Map.of() for queries with no bind parameters.
     */
    @ParameterizedTest(name = "[{index}] {0}")
    @MethodSource("inlineHql")
    void testInline(String description, String hql, Map<String, Object> params) {
        HqlTestResult result = service.run(hql, params, description);
        sessionResults.add(result);
        printResult(result);

        assertThat(result.getError())
                .as("HQL compilation/execution error in '%s'", description)
                .isNull();
    }

    // =========================================================================
    // MethodSource providers
    // =========================================================================

    /**
     * Non-static: uses the autowired fileLoader bean.
     * Works with @TestInstance(PER_CLASS).
     * Streams all .hql files — if the folder is empty, 0 test cases are generated.
     */
    Stream<Arguments> hqlFiles() throws IOException {
        List<com.example.hqltester.model.HqlFileInfo> files = fileLoader.listFiles();
        if (files.isEmpty()) {
            log.warn("No .hql files found in: {}", fileLoader.resolveFolder());
        }
        return files.stream().map(info -> {
            try {
                return Arguments.of(
                        info.getFilename(),
                        fileLoader.loadHql(info.getFilename()),
                        fileLoader.loadParams(info.getFilename())
                );
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /**
     * Add your inline queries here.
     * Each Arguments entry becomes a separate named test case.
     */
    static Stream<Arguments> inlineHql() {
        return Stream.of(

                // ---- Replace "Employee" with one of your actual entity names ----
                Arguments.of(
                        "count-all",
                        "SELECT count(e) FROM Employee e",
                        Map.of()
                )

                // ---- More examples (uncomment and adapt) ----

                // Arguments.of(
                //     "select-by-status",
                //     "SELECT e.id, e.name FROM Employee e WHERE e.status = :status",
                //     Map.of("status", "ACTIVE")
                // ),
                // Arguments.of(
                //     "to-char-inline",
                //     "SELECT function('to_char', e.createdAt, 'YYYY-MM-DD') FROM Employee e",
                //     Map.of()
                // ),
                // Arguments.of(
                //     "update-dry-run",                          // DML: rolled back, never committed
                //     "UPDATE Employee e SET e.status = :s WHERE e.id = :id",
                //     Map.of("s", "INACTIVE", "id", 1L)
                // )
        );
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
        report.put("totalTests", sessionResults.size());
        report.put("passed", passed);
        report.put("failed", failed);
        report.put("results", sessionResults);

        objectMapper.writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), report);
        log.info("═".repeat(70));
        log.info("Results written to: {}", outputFile.toAbsolutePath());
        log.info("Passed: {} / {}  |  Failed: {}", passed, sessionResults.size(), failed);
        log.info("═".repeat(70));
    }

    // =========================================================================
    // Console output helpers
    // =========================================================================

    private void printResult(HqlTestResult result) {
        StringBuilder sb = new StringBuilder();
        String sep = "═".repeat(70);

        sb.append("\n").append(sep).append("\n");
        sb.append(String.format("  %-58s [%s]%n", result.getSource(), result.getQueryType()));
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

    /**
     * Simple fixed-width ASCII table.
     * Displays at most 20 rows in the console (full data still in the JSON file).
     */
    private String formatTable(List<String> columns, List<List<Object>> rows) {
        if (columns.isEmpty()) return "";

        int[] widths = new int[columns.size()];
        for (int i = 0; i < columns.size(); i++) {
            widths[i] = columns.get(i).length();
        }
        for (List<Object> row : rows) {
            for (int i = 0; i < Math.min(row.size(), widths.length); i++) {
                widths[i] = Math.max(widths[i], Math.min(cellText(row.get(i)).length(), 40));
            }
        }

        String rowSeparator = "  +" + Arrays.stream(widths)
                .mapToObj(w -> "-".repeat(w + 2))
                .collect(Collectors.joining("+")) + "+\n";

        StringBuilder sb = new StringBuilder();
        sb.append(rowSeparator);

        // Header
        sb.append("  |");
        for (int i = 0; i < columns.size(); i++) {
            sb.append(String.format(" %-" + widths[i] + "s |", columns.get(i)));
        }
        sb.append("\n").append(rowSeparator);

        // Data rows (capped for console readability; JSON file has all rows)
        int displayLimit = Math.min(rows.size(), 20);
        for (int r = 0; r < displayLimit; r++) {
            sb.append("  |");
            for (int c = 0; c < columns.size(); c++) {
                String val = (r < rows.size() && c < rows.get(r).size())
                        ? truncate(cellText(rows.get(r).get(c)), 40)
                        : "";
                sb.append(String.format(" %-" + widths[c] + "s |", val));
            }
            sb.append("\n");
        }
        if (rows.size() > displayLimit) {
            sb.append("  ... (").append(rows.size() - displayLimit).append(" more rows — see JSON file)\n");
        }
        sb.append(rowSeparator);
        return sb.toString();
    }

    private String cellText(Object value) {
        return value == null ? "null" : value.toString();
    }

    private String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max - 3) + "..." : s;
    }

    private String resolveDialectName() {
        try {
            return emf.unwrap(SessionFactoryImplementor.class)
                    .getJdbcServices()
                    .getDialect()
                    .getClass()
                    .getSimpleName();
        } catch (Exception e) {
            // Fallback: check JPA properties map
            Object dialect = emf.getProperties().get("hibernate.dialect");
            if (dialect != null) {
                String name = dialect.toString();
                return name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : name;
            }
            return "UnknownDialect";
        }
    }
}
