package org.example.repository;

import org.example.dto.AirportSummary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ReferenceDataRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReferenceDataRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> listDistinctSourceCitiesEn() {
        return jdbcTemplate.queryForList(
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
    }

    public List<String> listDistinctDestinationCitiesEn() {
        return jdbcTemplate.queryForList(
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
    }

    public List<String> listDistinctSourceAirportNamesEn() {
        return jdbcTemplate.queryForList(
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
    }

    public List<String> listDistinctDestinationAirportNamesEn() {
        return jdbcTemplate.queryForList(
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
    }

    public List<AirportSummary> findAirportsInCity(String cityNameTrimmed) {
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
                cityNameTrimmed
        );
    }
}
