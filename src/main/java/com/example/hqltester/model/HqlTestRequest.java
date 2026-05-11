package com.example.hqltester.model;

import java.util.Map;

public class HqlTestRequest {

    private String hql;
    private Map<String, Object> params;

    public HqlTestRequest() {}

    public String getHql() { return hql; }
    public void setHql(String hql) { this.hql = hql; }

    public Map<String, Object> getParams() { return params; }
    public void setParams(Map<String, Object> params) { this.params = params; }
}
