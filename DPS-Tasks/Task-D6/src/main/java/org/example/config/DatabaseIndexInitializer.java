package org.example.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Creates supporting indexes for reference, schedule, route search, and booking flows.
 */
@Component
@Order(0)
public class DatabaseIndexInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseIndexInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
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
