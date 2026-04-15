package org.example.service;

import org.example.dto.RouteLeg;
import org.example.dto.RouteOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

@Service
public class RouteSearchService {

    private static final Duration MIN_CONNECTION = Duration.ofMinutes(30);
    private static final int SEARCH_WINDOW_DAYS = 2;
    private static final int UNBOUND_MAX_LEGS = 16;
    private static final int MAX_RESULTS = 200;

    private final JdbcTemplate jdbcTemplate;
    private final PointResolver pointResolver;
    private final ZoneId routeZone;

    public RouteSearchService(
            JdbcTemplate jdbcTemplate,
            PointResolver pointResolver,
            @Value("${app.route-search.time-zone:UTC}") String zoneId
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.pointResolver = pointResolver;
        this.routeZone = ZoneId.of(zoneId);
    }

    public List<RouteOption> findRoutes(
            String fromPoint,
            String toPoint,
            LocalDate departureDate,
            String bookingClass,
            Integer maxConnections
    ) {
        String fare = normalizeFare(bookingClass);
        Set<String> origins = pointResolver.resolveAirportCodes(fromPoint);
        Set<String> destinations = pointResolver.resolveAirportCodes(toPoint);

        Instant windowStart = departureDate.atStartOfDay(routeZone).toInstant();
        Instant windowEnd = windowStart.plusSeconds(Duration.ofDays(SEARCH_WINDOW_DAYS).toSeconds());

        int maxLegs = maxConnections == null
                ? UNBOUND_MAX_LEGS
                : Math.min(UNBOUND_MAX_LEGS, maxConnections + 1);

        List<CandidateFlight> all = loadFlights(windowStart, windowEnd);
        Map<String, List<CandidateFlight>> byDeparture = new HashMap<>();
        for (CandidateFlight f : all) {
            byDeparture.computeIfAbsent(f.departureAirport(), k -> new ArrayList<>()).add(f);
        }

        List<List<CandidateFlight>> rawPaths = new ArrayList<>();
        Queue<PathState> queue = new ArrayDeque<>();

        for (String origin : origins) {
            for (CandidateFlight first : byDeparture.getOrDefault(origin, List.of())) {
                    if (destinations.contains(first.arrivalAirport())) {
                        rawPaths.add(List.of(first));
                        if (rawPaths.size() >= MAX_RESULTS) {
                            return finalizePaths(rawPaths, fare);
                        }
                    }
                    if (maxLegs > 1) {
                        queue.add(PathState.start(first));
                    }
            }
        }

        outer:
        while (!queue.isEmpty() && rawPaths.size() < MAX_RESULTS) {
            PathState state = queue.poll();
            CandidateFlight last = state.lastFlight();
            for (CandidateFlight next : byDeparture.getOrDefault(last.arrivalAirport(), List.of())) {
                Instant earliestNext = last.scheduledArrival().toInstant().plus(MIN_CONNECTION);
                if (!next.scheduledDeparture().toInstant().isBefore(earliestNext)
                        && next.scheduledDeparture().toInstant().isBefore(windowEnd)) {
                    if (state.wouldCycle(next.arrivalAirport())) {
                        continue;
                    }
                    PathState extended = state.append(next);
                    if (destinations.contains(next.arrivalAirport())) {
                        rawPaths.add(extended.flights());
                        if (rawPaths.size() >= MAX_RESULTS) {
                            break outer;
                        }
                    }
                    if (extended.flights().size() < maxLegs) {
                        queue.add(extended);
                    }
                }
            }
        }

        rawPaths.sort(Comparator
                .<List<CandidateFlight>>comparingInt(List::size)
                .thenComparing(p -> p.get(0).scheduledDeparture()));

        return finalizePaths(rawPaths, fare);
    }

    private List<RouteOption> finalizePaths(List<List<CandidateFlight>> paths, String fare) {
        if (paths.isEmpty()) {
            return List.of();
        }
        Set<Integer> ids = new HashSet<>();
        for (List<CandidateFlight> p : paths) {
            for (CandidateFlight f : p) {
                ids.add(f.flightId());
            }
        }
        Map<Integer, BigDecimal> prices = loadPrices(ids, fare);
        Map<Integer, Integer> availability = loadAvailability(ids, fare);

        List<RouteOption> out = new ArrayList<>();
        for (List<CandidateFlight> p : paths) {
            boolean ok = true;
            BigDecimal total = BigDecimal.ZERO;
            List<RouteLeg> legs = new ArrayList<>();
            for (CandidateFlight f : p) {
                if (availability.getOrDefault(f.flightId(), 0) == 0) {
                    ok = false;
                    break;
                }
                BigDecimal price = prices.get(f.flightId());
                total = total.add(price);
                legs.add(new RouteLeg(
                        f.flightId(),
                        f.routeNo(),
                        f.departureAirport(),
                        f.arrivalAirport(),
                        f.scheduledDeparture(),
                        f.scheduledArrival(),
                        price
                ));
            }
            if (ok) {
                out.add(new RouteOption(legs, total));
            }
        }
        return out;
    }

    private List<CandidateFlight> loadFlights(Instant from, Instant to) {
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
                (rs, rowNum) -> new CandidateFlight(
                        rs.getInt("flight_id"),
                        rs.getString("route_no"),
                        rs.getString("departure_airport"),
                        rs.getString("arrival_airport"),
                        toOffset(rs.getTimestamp("scheduled_departure")),
                        toOffset(rs.getTimestamp("scheduled_arrival"))
                ),
                Timestamp.from(from),
                Timestamp.from(to)
        );
    }

    private Map<Integer, BigDecimal> loadPrices(Set<Integer> flightIds, String fare) {
        if (flightIds.isEmpty()) {
            return Map.of();
        }
        String inClause = flightIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
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
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getInt("flight_id"), rs.getBigDecimal("price"));
        }, args.toArray());
        return map;
    }

    private Map<Integer, Integer> loadAvailability(Set<Integer> flightIds, String fare) {
        Map<Integer, Integer> map = new HashMap<>();
        if (flightIds.isEmpty()) {
            return map;
        }
        String inClause = flightIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
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
        jdbcTemplate.query(sql, rs -> {
            map.put(rs.getInt("flight_id"), rs.getInt("free_seats"));
        }, args.toArray());
        return map;
    }

    private static String normalizeFare(String bookingClass) {
        if (bookingClass == null || bookingClass.isBlank()) {
            throw new IllegalArgumentException("bookingClass is required");
        }
        String f = bookingClass.trim();
        if (!List.of("Economy", "Comfort", "Business").contains(f)) {
            throw new IllegalArgumentException("bookingClass must be Economy, Comfort, or Business");
        }
        return f;
    }

    private static java.time.OffsetDateTime toOffset(Timestamp ts) {
        if (ts == null) {
            return null;
        }
        return ts.toInstant().atOffset(java.time.ZoneOffset.UTC);
    }

    private record CandidateFlight(
            int flightId,
            String routeNo,
            String departureAirport,
            String arrivalAirport,
            java.time.OffsetDateTime scheduledDeparture,
            java.time.OffsetDateTime scheduledArrival
    ) {
    }

    private static final class PathState {
        private final List<CandidateFlight> flights;
        private final Set<String> visited;

        private PathState(List<CandidateFlight> flights, Set<String> visited) {
            this.flights = flights;
            this.visited = visited;
        }

        static PathState start(CandidateFlight first) {
            Set<String> v = new HashSet<>();
            v.add(first.departureAirport());
            v.add(first.arrivalAirport());
            return new PathState(List.of(first), v);
        }

        CandidateFlight lastFlight() {
            return flights.get(flights.size() - 1);
        }

        boolean wouldCycle(String nextArrival) {
            return visited.contains(nextArrival);
        }

        PathState append(CandidateFlight next) {
            List<CandidateFlight> nf = new ArrayList<>(flights.size() + 1);
            nf.addAll(flights);
            nf.add(next);
            Set<String> nv = new HashSet<>(visited);
            nv.add(next.arrivalAirport());
            return new PathState(List.copyOf(nf), nv);
        }

        List<CandidateFlight> flights() {
            return flights;
        }
    }
}
