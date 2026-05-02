package org.example.service;

import org.example.dto.RouteLeg;
import org.example.dto.RouteOption;
import org.example.repository.RouteSearchRepository;
import org.example.repository.RouteSearchRepository.ScheduledFlightRow;
import org.springframework.beans.factory.annotation.Value;
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

    private final RouteSearchRepository routeSearchRepository;
    private final PointResolver pointResolver;
    private final ZoneId routeZone;

    public RouteSearchService(
            RouteSearchRepository routeSearchRepository,
            PointResolver pointResolver,
            @Value("${app.route-search.time-zone:UTC}") String zoneId
    ) {
        this.routeSearchRepository = routeSearchRepository;
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

        List<ScheduledFlightRow> all = routeSearchRepository.findFlightsDepartingBetween(
                Timestamp.from(windowStart),
                Timestamp.from(windowEnd)
        );
        Map<String, List<ScheduledFlightRow>> byDeparture = new HashMap<>();
        for (ScheduledFlightRow f : all) {
            byDeparture.computeIfAbsent(f.departureAirport(), k -> new ArrayList<>()).add(f);
        }

        List<List<ScheduledFlightRow>> rawPaths = new ArrayList<>();
        Queue<PathState> queue = new ArrayDeque<>();

        for (String origin : origins) {
            for (ScheduledFlightRow first : byDeparture.getOrDefault(origin, List.of())) {
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
            ScheduledFlightRow last = state.lastFlight();
            for (ScheduledFlightRow next : byDeparture.getOrDefault(last.arrivalAirport(), List.of())) {
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
                .<List<ScheduledFlightRow>>comparingInt(List::size)
                .thenComparing(p -> p.get(0).scheduledDeparture()));

        return finalizePaths(rawPaths, fare);
    }

    private List<RouteOption> finalizePaths(List<List<ScheduledFlightRow>> paths, String fare) {
        if (paths.isEmpty()) {
            return List.of();
        }
        Set<Integer> ids = new HashSet<>();
        for (List<ScheduledFlightRow> p : paths) {
            for (ScheduledFlightRow f : p) {
                ids.add(f.flightId());
            }
        }
        Map<Integer, BigDecimal> prices = routeSearchRepository.loadPricesByFlightIds(ids, fare);
        Map<Integer, Integer> availability = routeSearchRepository.loadFreeSeatsByFlightIds(ids, fare);

        List<RouteOption> out = new ArrayList<>();
        for (List<ScheduledFlightRow> p : paths) {
            boolean ok = true;
            BigDecimal total = BigDecimal.ZERO;
            List<RouteLeg> legs = new ArrayList<>();
            for (ScheduledFlightRow f : p) {
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

    private static final class PathState {
        private final List<ScheduledFlightRow> flights;
        private final Set<String> visited;

        private PathState(List<ScheduledFlightRow> flights, Set<String> visited) {
            this.flights = flights;
            this.visited = visited;
        }

        static PathState start(ScheduledFlightRow first) {
            Set<String> v = new HashSet<>();
            v.add(first.departureAirport());
            v.add(first.arrivalAirport());
            return new PathState(List.of(first), v);
        }

        ScheduledFlightRow lastFlight() {
            return flights.get(flights.size() - 1);
        }

        boolean wouldCycle(String nextArrival) {
            return visited.contains(nextArrival);
        }

        PathState append(ScheduledFlightRow next) {
            List<ScheduledFlightRow> nf = new ArrayList<>(flights.size() + 1);
            nf.addAll(flights);
            nf.add(next);
            Set<String> nv = new HashSet<>(visited);
            nv.add(next.arrivalAirport());
            return new PathState(List.copyOf(nf), nv);
        }

        List<ScheduledFlightRow> flights() {
            return flights;
        }
    }
}
