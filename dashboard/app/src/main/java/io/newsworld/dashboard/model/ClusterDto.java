package io.newsworld.dashboard.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ClusterDto implements java.io.Serializable {
    public long id;
    public LocalDate clusterDate;
    public String topic;
    public String synthesis;
    public double relevanceScore;
    public int articleCount;
    public int countryCount;
    public int continentCount;
    public String countriesList;
    public String continentsList;
    public LocalDateTime createdAt;
    public List<ClusterSourceDto> sources;
}
