package org.example.service;

import org.example.dto.AirportListsResponse;
import org.example.dto.AirportSummary;
import org.example.dto.CityListsResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReferenceDataService {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceDataService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CityListsResponse listCities() {
        List<String> sources = jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT a.city->>'en' AS city
                        FROM bookings.airports_data a
                        WHERE EXISTS (
                            SELECT 1 FROM bookings.routes r
                            WHERE r.departure_airport = a.airport_code
                              AND r.validity @> now()
                        )
                        ORDER BY 1
                        """,
                String.class
        );
        List<String> destinations = jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT a.city->>'en' AS city
                        FROM bookings.airports_data a
                        WHERE EXISTS (
                            SELECT 1 FROM bookings.routes r
                            WHERE r.arrival_airport = a.airport_code
                              AND r.validity @> now()
                        )
                        ORDER BY 1
                        """,
                String.class
        );
        return new CityListsResponse(sources, destinations);
    }

    public AirportListsResponse listAirports() {
        List<String> sources = jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT a.airport_name->>'en' AS airport_name
                        FROM bookings.airports_data a
                        WHERE EXISTS (
                            SELECT 1 FROM bookings.routes r
                            WHERE r.departure_airport = a.airport_code
                              AND r.validity @> now()
                        )
                        ORDER BY 1
                        """,
                String.class
        );
        List<String> destinations = jdbcTemplate.queryForList(
                """
                        SELECT DISTINCT a.airport_name->>'en' AS airport_name
                        FROM bookings.airports_data a
                        WHERE EXISTS (
                            SELECT 1 FROM bookings.routes r
                            WHERE r.arrival_airport = a.airport_code
                              AND r.validity @> now()
                        )
                        ORDER BY 1
                        """,
                String.class
        );
        return new AirportListsResponse(sources, destinations);
    }

    public List<AirportSummary> airportsInCity(String cityName) {
        return jdbcTemplate.query(
                """
                        SELECT airport_code,
                               airport_name->>'en' AS airport_name_en,
                               city->>'en' AS city_en
                        FROM bookings.airports_data
                        WHERE lower(city->>'en') = lower(?)
                        ORDER BY airport_code
                        """,
                (rs, rowNum) -> new AirportSummary(
                        rs.getString("airport_code").trim(),
                        rs.getString("airport_name_en"),
                        rs.getString("city_en")
                ),
                cityName.trim()
        );
    }
}
