package io.newsworld.collector.repository;

import io.newsworld.collector.model.Country;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CountryRepository extends JpaRepository<Country, String> {

    List<Country> findByWeekDayOrderByPopulationDesc(int weekDay);

    List<Country> findByContinentOrderByPopulationDesc(String continent);

    List<Country> findByWeekDayAndContinentOrderByPopulationDesc(int weekDay, String continent);

    List<Country> findByRssAvailableTrue();
}
