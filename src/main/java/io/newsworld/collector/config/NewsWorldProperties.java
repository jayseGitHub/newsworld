package io.newsworld.collector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "newsworld")
public class NewsWorldProperties {

    private final Mistral mistral = new Mistral();
    private final Translation translation = new Translation();
    private final Pipelines pipelines = new Pipelines();
    private final Collector collector = new Collector();

    public Mistral getMistral() { return mistral; }
    public Translation getTranslation() { return translation; }
    public Pipelines getPipelines() { return pipelines; }
    public Collector getCollector() { return collector; }

    public static class Mistral {
        private String apiKey = "";
        private String modelTranslation = "mistral-small-latest";
        private String modelAnalysis = "mistral-medium-latest";

        public String getApiKey() { return apiKey; }
        public void setApiKey(String v) { this.apiKey = v; }
        public String getModelTranslation() { return modelTranslation; }
        public void setModelTranslation(String v) { this.modelTranslation = v; }
        public String getModelAnalysis() { return modelAnalysis; }
        public void setModelAnalysis(String v) { this.modelAnalysis = v; }
    }

    public static class Translation {
        private String targetLanguage = "fr";

        public String getTargetLanguage() { return targetLanguage; }
        public void setTargetLanguage(String v) { this.targetLanguage = v; }
    }

    public static class Pipelines {
        private int enrichmentBatchSize = 50;
        private int translationBatchSize = 20;

        public int getEnrichmentBatchSize() { return enrichmentBatchSize; }
        public void setEnrichmentBatchSize(int v) { this.enrichmentBatchSize = v; }
        public int getTranslationBatchSize() { return translationBatchSize; }
        public void setTranslationBatchSize(int v) { this.translationBatchSize = v; }
    }

    public static class Collector {
        private int purgeDays = 30;
        private final Http http = new Http();
        private final Doctor doctor = new Doctor();

        public int getPurgeDays() { return purgeDays; }
        public void setPurgeDays(int v) { this.purgeDays = v; }
        public Http getHttp() { return http; }
        public Doctor getDoctor() { return doctor; }

        public static class Http {
            private int connectTimeout = 10000;
            private int readTimeout = 15000;
            private String userAgent = "NewsWorld/1.0";

            public int getConnectTimeout() { return connectTimeout; }
            public void setConnectTimeout(int v) { this.connectTimeout = v; }
            public int getReadTimeout() { return readTimeout; }
            public void setReadTimeout(int v) { this.readTimeout = v; }
            public String getUserAgent() { return userAgent; }
            public void setUserAgent(String v) { this.userAgent = v; }
        }

        public static class Doctor {
            private int connectTimeout = 8000;
            private int readTimeout = 10000;

            public int getConnectTimeout() { return connectTimeout; }
            public void setConnectTimeout(int v) { this.connectTimeout = v; }
            public int getReadTimeout() { return readTimeout; }
            public void setReadTimeout(int v) { this.readTimeout = v; }
        }
    }
}
