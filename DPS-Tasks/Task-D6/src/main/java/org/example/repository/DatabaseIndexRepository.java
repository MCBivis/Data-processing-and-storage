package org.example.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * DDL for supporting indexes (also partly covered by Flyway migrations).
 */
@Repository
public class DatabaseIndexRepository {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseIndexRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void ensureReferenceAndSearchIndexes() {
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_flights_scheduled_departure
                    ON bookings.flights (scheduled_departure)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_flights_status_scheduled
                    ON bookings.flights (status, scheduled_departure)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_routes_arrival_airport_lower_validity
                    ON bookings.routes (arrival_airport, lower(validity))
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_boarding_passes_flight_id
                    ON bookings.boarding_passes (flight_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_airports_city_en_lower
                    ON bookings.airports_data (lower(city->>'en'))
                """);
    }
}
