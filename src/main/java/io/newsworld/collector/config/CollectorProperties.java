package io.newsworld.collector.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "newsworld.collector")
@Data
public class CollectorProperties {

    private int purgeDays = 30;
    private boolean bypassTimeCheck = false;

    private Schedule schedule = new Schedule();
    private Http http = new Http();

    @Data
    public static class Schedule {
        private String cron = "0 */15 * * * *";
        private String purgeCron = "0 0 3 * * *";
        private int collectionHour = 7;
        private int collectionWindowMinutes = 15;
    }

    @Data
    public static class Http {
        private int connectTimeout = 10000;
        private int readTimeout = 15000;
        private String userAgent = "NewsWorld/1.0";
    }
}
