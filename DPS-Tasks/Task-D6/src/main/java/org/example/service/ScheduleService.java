package org.example.service;

import org.example.dto.ScheduleEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Service
public class ScheduleService {

    private final JdbcTemplate jdbcTemplate;

    public ScheduleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScheduleEntry> inboundSchedule(String airportCode) {
        String code = airportCode.trim().toUpperCase();
        return jdbcTemplate.query(
                """
                        SELECT r.route_no,
                               r.days_of_week,
                               (r.scheduled_time + r.duration)::time AS arrival_time,
                               r.departure_airport AS peer_code,
                               ad.airport_name->>'en' AS peer_name
                        FROM bookings.routes r
                        JOIN bookings.airports_data ad ON ad.airport_code = r.departure_airport
                        WHERE r.arrival_airport = ?
                          AND r.validity @> now()
                        ORDER BY r.route_no
                        """,
                (rs, rowNum) -> new ScheduleEntry(
                        rs.getString("route_no").trim(),
                        intArray(rs.getArray("days_of_week")),
                        rs.getTime("arrival_time").toLocalTime().toString(),
                        rs.getString("peer_code").trim(),
                        rs.getString("peer_name")
                ),
                code
        );
    }

    public List<ScheduleEntry> outboundSchedule(String airportCode) {
        String code = airportCode.trim().toUpperCase();
        return jdbcTemplate.query(
                """
                        SELECT r.route_no,
                               r.days_of_week,
                               r.scheduled_time AS departure_time,
                               r.arrival_airport AS peer_code,
                               ad.airport_name->>'en' AS peer_name
                        FROM bookings.routes r
                        JOIN bookings.airports_data ad ON ad.airport_code = r.arrival_airport
                        WHERE r.departure_airport = ?
                          AND r.validity @> now()
                        ORDER BY r.route_no
                        """,
                (rs, rowNum) -> new ScheduleEntry(
                        rs.getString("route_no").trim(),
                        intArray(rs.getArray("days_of_week")),
                        rs.getTime("departure_time").toLocalTime().toString(),
                        rs.getString("peer_code").trim(),
                        rs.getString("peer_name")
                ),
                code
        );
    }

    private static List<Integer> intArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) {
            return List.of();
        }
        Object arr = sqlArray.getArray();
        if (arr instanceof Integer[] boxed) {
            return Arrays.asList(boxed);
        }
        if (arr instanceof int[] primitive) {
            return Arrays.stream(primitive).boxed().toList();
        }
        return List.of();
    }
}
