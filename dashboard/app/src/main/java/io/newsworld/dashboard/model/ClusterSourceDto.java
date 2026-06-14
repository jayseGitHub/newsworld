package io.newsworld.dashboard.model;

import java.io.Serializable;

public class ClusterSourceDto implements Serializable {
    public long id;
    public String title;
    public String countryCode;
    public String originalLanguage;
    public String sourceUrl;
    public String originalSummary;
    public String translatedSummary;

    public String displaySummary() {
        return translatedSummary != null && !translatedSummary.isBlank() ? translatedSummary : originalSummary;
    }
}
