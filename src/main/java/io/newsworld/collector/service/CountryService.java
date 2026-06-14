package io.newsworld.collector.service;

import io.newsworld.collector.model.Country;
import io.newsworld.collector.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository countryRepository;

    public List<Country> getTodayCountries() {
        int weekDay = currentWeekDay();
        return countryRepository.findByWeekDayOrderByPopulationDesc(weekDay);
    }

    public List<Country> getTodayCountriesForContinent(String continent) {
        int weekDay = currentWeekDay();
        return countryRepository.findByWeekDayAndContinentOrderByPopulationDesc(weekDay, continent);
    }

    public List<Country> getCountriesForDay(int weekDay) {
        return countryRepository.findByWeekDayOrderByPopulationDesc(weekDay);
    }

    public List<Country> getAll() {
        return countryRepository.findAll();
    }

    /**
     * Returns the ISO week day based on the phone/server timezone.
     * 1 = Monday, 7 = Sunday — consistent with our rotation model.
     */
    public int currentWeekDay() {
        DayOfWeek dow = LocalDate.now(ZoneId.systemDefault()).getDayOfWeek();
        return dow.getValue(); // DayOfWeek.getValue() returns 1 (MON) to 7 (SUN)
    }
}
