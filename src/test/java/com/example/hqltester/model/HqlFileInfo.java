package com.example.hqltester.model;

public class HqlFileInfo {

    private String filename;
    private String description;
    private boolean hasParams;
    private String hqlPreview;

    public HqlFileInfo() {}

    public HqlFileInfo(String filename, String description, boolean hasParams, String hqlPreview) {
        this.filename = filename;
        this.description = description;
        this.hasParams = hasParams;
        this.hqlPreview = hqlPreview;
    }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isHasParams() { return hasParams; }
    public void setHasParams(boolean hasParams) { this.hasParams = hasParams; }

    public String getHqlPreview() { return hqlPreview; }
    public void setHqlPreview(String hqlPreview) { this.hqlPreview = hqlPreview; }
}
