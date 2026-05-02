package org.example.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AirportRepository {

    private final JdbcTemplate jdbcTemplate;

    public AirportRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int countByAirportCode(String airportCodeUpperCase) {
        Integer n = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings.airports_data WHERE airport_code = ?",
                Integer.class,
                airportCodeUpperCase
        );
        return n == null ? 0 : n;
    }

    public List<String> findAirportCodesByCityNameEn(String trimmedPoint) {
        return jdbcTemplate.queryForList(
                """
                        SELECT airport_code
                        FROM bookings.airports_data
                        WHERE lower(city->>'en') = lower(?)
                        ORDER BY airport_code
                        """,
                String.class,
                trimmedPoint
        );
    }

    public List<String> findAirportCodesByAirportNameEn(String trimmedPoint) {
        return jdbcTemplate.queryForList(
                """
                        SELECT airport_code
                        FROM bookings.airports_data
                        WHERE lower(airport_name->>'en') = lower(?)
                        ORDER BY airport_code
                        """,
                String.class,
                trimmedPoint
        );
    }
}
