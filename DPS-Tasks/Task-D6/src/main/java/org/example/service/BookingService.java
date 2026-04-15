package org.example.service;

import org.example.dto.BookingRequest;
import org.example.dto.BookingResponse;
import org.example.dto.CheckInRequest;
import org.example.dto.CheckInResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbcTemplate;

    public BookingService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BookingResponse createBooking(BookingRequest request) {
        if (request.flightIds() == null || request.flightIds().isEmpty()) {
            throw new IllegalArgumentException("flightIds must not be empty");
        }
        if (request.passengerId() == null || request.passengerId().isBlank()
                || request.passengerName() == null || request.passengerName().isBlank()) {
            throw new IllegalArgumentException("passengerId and passengerName are required");
        }
        String fare = normalizeFare(request.bookingClass());
        List<Integer> flightIds = request.flightIds();

        BigDecimal total = BigDecimal.ZERO;
        List<LegPrice> legs = new ArrayList<>();
        for (int flightId : flightIds) {
            LegPrice lp = loadLeg(flightId, fare);
            legs.add(lp);
            total = total.add(lp.price());
        }

        for (int i = 0; i < legs.size(); i++) {
            LegPrice current = legs.get(i);
            if (i > 0) {
                LegPrice prev = legs.get(i - 1);
                if (!prev.arrival().equals(current.departure())) {
                    throw new IllegalArgumentException("Itinerary airports do not connect");
                }
                if (current.departureTime().toInstant().isBefore(
                        prev.arrivalTime().toInstant().plusSeconds(30 * 60))) {
                    throw new IllegalArgumentException("Connection time between flights is too short");
                }
            }
            int free = freeSeats(current.flightId(), current.airplaneCode(), fare);
            if (free <= 0) {
                throw new IllegalStateException("No seats left in " + fare + " on flight " + current.flightId());
            }
        }

        String bookRef = allocateBookRef();
        String ticketNo = allocateTicketNo();

        jdbcTemplate.update(
                "INSERT INTO bookings.bookings (book_ref, book_date, total_amount) VALUES (?, now(), ?)",
                bookRef,
                total
        );
        jdbcTemplate.update(
                """
                        INSERT INTO bookings.tickets (ticket_no, book_ref, passenger_id, passenger_name, outbound)
                        VALUES (?, ?, ?, ?, true)
                        """,
                ticketNo,
                bookRef,
                request.passengerId().trim(),
                request.passengerName().trim()
        );

        for (LegPrice lp : legs) {
            jdbcTemplate.update(
                    """
                            INSERT INTO bookings.segments (ticket_no, flight_id, fare_conditions, price)
                            VALUES (?, ?, ?, ?)
                            """,
                    ticketNo,
                    lp.flightId(),
                    fare,
                    lp.price()
            );
            String seat = pickSeat(lp.flightId(), lp.airplaneCode(), fare);
            jdbcTemplate.update(
                    """
                            INSERT INTO bookings.boarding_passes (ticket_no, flight_id, seat_no, boarding_no, boarding_time)
                            VALUES (?, ?, ?, NULL, NULL)
                            """,
                    ticketNo,
                    lp.flightId(),
                    seat
            );
        }

        return new BookingResponse(bookRef, ticketNo, total);
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        String ticketNo = request.ticketNo().trim();
        int flightId = request.flightId();

        Integer existing;
        try {
            existing = jdbcTemplate.queryForObject(
                    "SELECT boarding_no FROM bookings.boarding_passes WHERE ticket_no = ? AND flight_id = ?",
                    Integer.class,
                    ticketNo,
                    flightId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Segment not found for ticket and flight");
        }

        if (existing != null) {
            throw new IllegalStateException("Passenger is already checked in for this flight");
        }

        int nextBoarding = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(boarding_no), 0) + 1 FROM bookings.boarding_passes WHERE flight_id = ?",
                Integer.class,
                flightId
        );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbcTemplate.update(
                """
                        UPDATE bookings.boarding_passes
                        SET boarding_no = ?, boarding_time = ?
                        WHERE ticket_no = ? AND flight_id = ?
                        """,
                nextBoarding,
                Timestamp.from(now.toInstant()),
                ticketNo,
                flightId
        );

        return new CheckInResponse(nextBoarding, now);
    }

    private LegPrice loadLeg(int flightId, String fare) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            SELECT f.flight_id,
                                   trim(r.departure_airport) AS dep,
                                   trim(r.arrival_airport) AS arr,
                                   r.airplane_code,
                                   f.scheduled_departure,
                                   f.scheduled_arrival,
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
                            JOIN bookings.routes r
                              ON r.route_no = f.route_no
                             AND r.validity @> f.scheduled_departure
                            WHERE f.flight_id = ?
                              AND f.status <> 'Cancelled'
                            """,
                    (rs, rowNum) -> new LegPrice(
                            rs.getInt("flight_id"),
                            rs.getString("dep"),
                            rs.getString("arr"),
                            rs.getString("airplane_code").trim(),
                            rs.getTimestamp("scheduled_departure").toInstant().atOffset(ZoneOffset.UTC),
                            rs.getTimestamp("scheduled_arrival").toInstant().atOffset(ZoneOffset.UTC),
                            rs.getBigDecimal("price")
                    ),
                    fare,
                    fare,
                    flightId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Unknown or cancelled flight: " + flightId);
        }
    }

    private int freeSeats(int flightId, String airplaneCode, String fare) {
        Integer total = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)::int FROM bookings.seats s
                        WHERE s.airplane_code = ? AND s.fare_conditions = ?
                        """,
                Integer.class,
                airplaneCode,
                fare
        );
        Integer used = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)::int
                        FROM bookings.boarding_passes bp
                        JOIN bookings.seats s2
                          ON s2.airplane_code = ?
                         AND s2.seat_no = bp.seat_no
                         AND s2.fare_conditions = ?
                        WHERE bp.flight_id = ?
                        """,
                Integer.class,
                airplaneCode,
                fare,
                flightId
        );
        return (total == null ? 0 : total) - (used == null ? 0 : used);
    }

    private String pickSeat(int flightId, String airplaneCode, String fare) {
        try {
            return jdbcTemplate.queryForObject(
                    """
                            SELECT s.seat_no
                            FROM bookings.seats s
                            WHERE s.airplane_code = ?
                              AND s.fare_conditions = ?
                              AND NOT EXISTS (
                                SELECT 1 FROM bookings.boarding_passes bp
                                WHERE bp.flight_id = ?
                                  AND bp.seat_no = s.seat_no
                              )
                            ORDER BY s.seat_no
                            LIMIT 1
                            """,
                    String.class,
                    airplaneCode,
                    fare,
                    flightId
            );
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalStateException("No free seat in " + fare + " for flight " + flightId);
        }
    }

    private String allocateBookRef() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String ref = randomAlnum(6);
            Integer cnt = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM bookings.bookings WHERE book_ref = ?",
                    Integer.class,
                    ref
            );
            if (cnt != null && cnt == 0) {
                return ref;
            }
        }
        throw new IllegalStateException("Could not allocate book_ref");
    }

    private String allocateTicketNo() {
        Long max = jdbcTemplate.queryForObject(
                "SELECT MAX(ticket_no::bigint) FROM bookings.tickets",
                Long.class
        );
        long next = (max == null ? 0L : max) + 1;
        return String.format(Locale.ROOT, "%013d", next);
    }

    private static String randomAlnum(int len) {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars[RANDOM.nextInt(chars.length)]);
        }
        return sb.toString();
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

    private record LegPrice(
            int flightId,
            String departure,
            String arrival,
            String airplaneCode,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime,
            BigDecimal price
    ) {
    }
}
