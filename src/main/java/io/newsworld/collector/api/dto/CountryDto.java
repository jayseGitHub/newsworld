package io.newsworld.collector.api.dto;

import io.newsworld.collector.model.Country;

public record CountryDto(
        String code,
        String name,
        String continent,
        long population,
        int weekDay,
        String language,
        String collectionType,
        String ianaTimezone,
        long articleCount
) {
    public static CountryDto from(Country c, long articleCount) {
        return new CountryDto(
                c.getCode(), c.getName(), c.getContinent(), c.getPopulation(),
                c.getWeekDay(), c.getLanguage(),
                c.getCollectionType() != null ? c.getCollectionType().name() : "RSS",
                c.getIanaTimezone(), articleCount
        );
    }
}
