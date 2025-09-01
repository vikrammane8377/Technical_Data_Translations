package com.hackerx.translation.config;

public class CustomSafetySetting {
    private String category;
    private String threshold;

    public CustomSafetySetting(String category, String threshold) {
        this.category = category;
        this.threshold = threshold;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getThreshold() {
        return threshold;
    }

    public void setThreshold(String threshold) {
        this.threshold = threshold;
    }
}