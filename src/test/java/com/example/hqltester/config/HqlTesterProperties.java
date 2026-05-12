package com.example.hqltester.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "hql-tester")
public class HqlTesterProperties {

    /** Absolute or relative path to the folder containing .hql files. */
    private String queryFolder = "./hql-test-queries";

    /** Maximum rows returned for SELECT queries. */
    private int maxResults = 100;

    /** Folder where per-run JSON result files are written. */
    private String resultOutputFolder = "./hql-test-results";

    public String getQueryFolder() { return queryFolder; }
    public void setQueryFolder(String queryFolder) { this.queryFolder = queryFolder; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public String getResultOutputFolder() { return resultOutputFolder; }
    public void setResultOutputFolder(String resultOutputFolder) { this.resultOutputFolder = resultOutputFolder; }
}
