package com.example.hqltester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hql-tester")
public class HqlTesterProperties {

    /**
     * Which dialect is currently active: "oracle" or "postgres".
     * Controls which expected SQL fragment is asserted in each test.
     */
    private String activeDialect = "oracle";

    /** Maximum rows returned for SELECT queries. */
    private int maxResults = 100;

    /**
     * Whether to write a JSON result file after each test run.
     * Disabled by default — enable locally for inspection, keep off in CI/deployments.
     */
    private boolean exportResults = false;

    /** Folder where per-run JSON result files are written (only used when exportResults=true). */
    private String resultOutputFolder = "./hql-test-results";

    public String getActiveDialect() { return activeDialect; }
    public void setActiveDialect(String activeDialect) { this.activeDialect = activeDialect; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public boolean isExportResults() { return exportResults; }
    public void setExportResults(boolean exportResults) { this.exportResults = exportResults; }

    public String getResultOutputFolder() { return resultOutputFolder; }
    public void setResultOutputFolder(String resultOutputFolder) { this.resultOutputFolder = resultOutputFolder; }
}
