package com.example.hqltester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hql-tester")
public class HqlTesterProperties {

    /**
     * Set to true to activate the HQL tester endpoints.
     * Keep false (default) in production.
     */
    private boolean enabled = false;

    /**
     * Absolute or relative path to the folder containing .hql files.
     * New files dropped here are picked up without a restart.
     */
    private String queryFolder = "./hql-test-queries";

    /**
     * Maximum rows returned for SELECT queries to prevent memory issues.
     */
    private int maxResults = 100;

    /**
     * Folder where per-run JSON result files are written.
     * One file per test run, named hql-results-{timestamp}-{dialect}.json.
     * Useful for diffing Oracle vs Postgres SQL side by side.
     */
    private String resultOutputFolder = "./hql-test-results";

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getQueryFolder() { return queryFolder; }
    public void setQueryFolder(String queryFolder) { this.queryFolder = queryFolder; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public String getResultOutputFolder() { return resultOutputFolder; }
    public void setResultOutputFolder(String resultOutputFolder) { this.resultOutputFolder = resultOutputFolder; }
}
