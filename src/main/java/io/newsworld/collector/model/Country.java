package io.newsworld.collector.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "countries")
@Data
@NoArgsConstructor
public class Country {

    @Id
    @Column(length = 3)
    private String code;           // ISO 3166-1 alpha-2 (ex: FR, JP)

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String continent;

    private long population;

    @Column(name = "week_day", nullable = false)
    private int weekDay;           // 1=Lundi … 7=Dimanche (rotation par population)

    @Column(name = "media_name")
    private String mediaName;

    @Column(name = "media_url")
    private String mediaUrl;

    @Column(name = "rss_url")
    private String rssUrl;

    @Column(name = "rss_available", nullable = false)
    private boolean rssAvailable;

    @Column(name = "iana_timezone", nullable = false)
    private String ianaTimezone;   // ex: "Asia/Tokyo", "Europe/Paris"

    @Column(length = 10)
    private String language;       // ISO 639-1 (ex: fr, en, ar)

    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false, length = 20)
    private CollectionType collectionType = CollectionType.RSS;

    @Column(name = "api_url", length = 2048)
    private String apiUrl;
}
