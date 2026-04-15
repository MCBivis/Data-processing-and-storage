package org.example.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A point is either a 3-letter airport code or a city name (matched against {@code city->>'en'}).
 */
@Component
public class PointResolver {

    private final JdbcTemplate jdbcTemplate;

    public PointResolver(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Set<String> resolveAirportCodes(String point) {
        if (point == null || point.isBlank()) {
            throw new PointResolutionException("Point must not be blank");
        }
        String trimmed = point.trim();
        if (trimmed.length() == 3 && trimmed.chars().allMatch(Character::isLetter)) {
            String code = trimmed.toUpperCase();
            Integer n = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bookings.airports_data WHERE airport_code = ?",
                    Integer.class,
                    code
            );
            if (n != null && n > 0) {
                return Set.of(code);
            }
        }
        List<String> byCity = jdbcTemplate.queryForList(
                """
                        SELECT airport_code
                        FROM bookings.airports_data
                        WHERE lower(city->>'en') = lower(?)
                        ORDER BY airport_code
                        """,
                String.class,
                trimmed
        );
        if (!byCity.isEmpty()) {
            return new LinkedHashSet<>(byCity);
        }

        List<String> byAirport = jdbcTemplate.queryForList(
                """
                        SELECT airport_code
                        FROM bookings.airports_data
                        WHERE lower(airport_name->>'en') = lower(?)
                        ORDER BY airport_code
                        """,
                String.class,
                trimmed
        );
        if (!byAirport.isEmpty()) {
            return new LinkedHashSet<>(byAirport);
        }

        throw new PointResolutionException("Unknown airport or city: " + trimmed);
    }
}
