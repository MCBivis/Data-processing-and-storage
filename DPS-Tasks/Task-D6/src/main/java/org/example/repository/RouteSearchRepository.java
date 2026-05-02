package org.example.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class RouteSearchRepository {

    private final JdbcTemplate jdbcTemplate;

    public RouteSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ScheduledFlightRow> findFlightsDepartingBetween(Timestamp from, Timestamp to) {
        return jdbcTemplate.query(
                """
                        SELECT f.flight_id,
                               trim(f.route_no) AS route_no,
                               f.scheduled_departure,
                               f.scheduled_arrival,
                               trim(r.departure_airport) AS departure_airport,
                               trim(r.arrival_airport) AS arrival_airport
                        FROM bookings.flights f
                        JOIN bookings.routes r
                          ON r.route_no = f.route_no
                         AND r.validity @> f.scheduled_departure
                        WHERE f.status <> 'Cancelled'
                          AND f.scheduled_departure >= ?
                          AND f.scheduled_departure < ?
                        """,
                (rs, rowNum) -> new ScheduledFlightRow(
                        rs.getInt("flight_id"),
                        rs.getString("route_no"),
                        rs.getString("departure_airport"),
                        rs.getString("arrival_airport"),
                        toOffset(rs.getTimestamp("scheduled_departure")),
                        toOffset(rs.getTimestamp("scheduled_arrival"))
                ),
                from,
                to
        );
    }

    public Map<Integer, BigDecimal> loadPricesByFlightIds(Set<Integer> flightIds, String fare) {
        if (flightIds.isEmpty()) {
            return Map.of();
        }
        String inClause = flightIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(fare);
        args.add(fare);
        args.addAll(flightIds);
        String sql = """
                SELECT f.flight_id,
                       COALESCE(
                         (SELECT ROUND(AVG(s.price), 2)
                          FROM bookings.segments s
                          WHERE s.flight_id = f.flight_id AND s.fare_conditions = ?),
                         (SELECT u.predicted_price
                          FROM bookings.upcoming_flight_prices u
                          WHERE u.flight_id = f.flight_id AND u.fare_conditions = ?
                          LIMIT 1),
                         100.00
                       ) AS price
                FROM bookings.flights f
                WHERE f.flight_id IN (""" + inClause + ")";
        Map<Integer, BigDecimal> map = new HashMap<>();
        jdbcTemplate.query(sql, (RowCallbackHandler) rs ->
                map.put(rs.getInt("flight_id"), rs.getBigDecimal("price")), args.toArray());
        return map;
    }

    public Map<Integer, Integer> loadFreeSeatsByFlightIds(Set<Integer> flightIds, String fare) {
        Map<Integer, Integer> map = new HashMap<>();
        if (flightIds.isEmpty()) {
            return map;
        }
        String inClause = flightIds.stream().map(id -> "?").collect(Collectors.joining(","));
        List<Object> args = new ArrayList<>();
        args.add(fare);
        args.add(fare);
        args.addAll(flightIds);
        String sql = """
                SELECT f.flight_id,
                       GREATEST(0,
                         (SELECT COUNT(*)::int
                          FROM bookings.seats s
                          WHERE s.airplane_code = r.airplane_code
                            AND s.fare_conditions = ?)
                         -
                         (SELECT COUNT(*)::int
                          FROM bookings.boarding_passes bp
                          JOIN bookings.seats s2
                            ON s2.airplane_code = r.airplane_code
                           AND s2.seat_no = bp.seat_no
                           AND s2.fare_conditions = ?
                          WHERE bp.flight_id = f.flight_id)
                       ) AS free_seats
                FROM bookings.flights f
                JOIN bookings.routes r
                  ON r.route_no = f.route_no
                 AND r.validity @> f.scheduled_departure
                WHERE f.flight_id IN (""" + inClause + ")";
        jdbcTemplate.query(sql, (RowCallbackHandler) rs ->
                map.put(rs.getInt("flight_id"), rs.getInt("free_seats")), args.toArray());
        return map;
    }

    private static OffsetDateTime toOffset(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toInstant().atOffset(ZoneOffset.UTC);
    }

    public record ScheduledFlightRow(
            int flightId,
            String routeNo,
            String departureAirport,
            String arrivalAirport,
            OffsetDateTime scheduledDeparture,
            OffsetDateTime scheduledArrival
    ) {
    }
}
