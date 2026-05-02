package org.example.repository;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Repository
public class BookingRepository {

    private final JdbcTemplate jdbcTemplate;

    public BookingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<BookingLegPrice> findLeg(int flightId, String fare) {
        try {
            BookingLegPrice leg = jdbcTemplate.queryForObject(
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
                    (rs, rowNum) -> new BookingLegPrice(
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
            return Optional.of(leg);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int countSeatsForFare(String airplaneCode, String fare) {
        Integer total = jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)::int FROM bookings.seats s
                        WHERE s.airplane_code = ? AND s.fare_conditions = ?
                        """,
                Integer.class,
                airplaneCode,
                fare
        );
        return total == null ? 0 : total;
    }

    public int countBoardedSeatsForFareOnFlight(int flightId, String airplaneCode, String fare) {
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
        return used == null ? 0 : used;
    }

    public Optional<String> pickFreeSeat(int flightId, String airplaneCode, String fare) {
        try {
            String seat = jdbcTemplate.queryForObject(
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
            return Optional.ofNullable(seat);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * @return true if the flight row exists and was locked
     */
    public boolean lockFlightForUpdate(int flightId) {
        try {
            jdbcTemplate.queryForObject(
                    "SELECT flight_id FROM bookings.flights WHERE flight_id = ? FOR UPDATE",
                    Integer.class,
                    flightId
            );
            return true;
        } catch (EmptyResultDataAccessException e) {
            return false;
        }
    }

    /**
     * Locks the boarding_pass row for this ticket segment.
     *
     * @return empty if no row; otherwise row with boarding_no (null if not checked in yet)
     */
    public Optional<BoardingPassRow> lockBoardingPassForUpdate(String ticketNo, int flightId) {
        try {
            Integer boardingNo = jdbcTemplate.queryForObject(
                    """
                            SELECT boarding_no
                            FROM bookings.boarding_passes
                            WHERE ticket_no = ? AND flight_id = ?
                            FOR UPDATE
                            """,
                    Integer.class,
                    ticketNo,
                    flightId
            );
            return Optional.of(new BoardingPassRow(boardingNo));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<Integer> findNextBoardingNo(int flightId) {
        Integer next = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(boarding_no), 0) + 1 FROM bookings.boarding_passes WHERE flight_id = ?",
                Integer.class,
                flightId
        );
        return Optional.ofNullable(next);
    }

    public int updateCheckIn(String ticketNo, int flightId, int boardingNo, Timestamp boardingTime) {
        return jdbcTemplate.update(
                """
                        UPDATE bookings.boarding_passes
                        SET boarding_no = ?, boarding_time = ?
                        WHERE ticket_no = ? AND flight_id = ?
                          AND boarding_no IS NULL
                        """,
                boardingNo,
                boardingTime,
                ticketNo,
                flightId
        );
    }

    public void insertBooking(String bookRef, BigDecimal totalAmount) {
        jdbcTemplate.update(
                "INSERT INTO bookings.bookings (book_ref, book_date, total_amount) VALUES (?, now(), ?)",
                bookRef,
                totalAmount
        );
    }

    public void insertTicket(String ticketNo, String bookRef, String passengerId, String passengerName) {
        jdbcTemplate.update(
                """
                        INSERT INTO bookings.tickets (ticket_no, book_ref, passenger_id, passenger_name, outbound)
                        VALUES (?, ?, ?, ?, true)
                        """,
                ticketNo,
                bookRef,
                passengerId,
                passengerName
        );
    }

    public void insertSegment(String ticketNo, int flightId, String fare, BigDecimal price) {
        jdbcTemplate.update(
                """
                        INSERT INTO bookings.segments (ticket_no, flight_id, fare_conditions, price)
                        VALUES (?, ?, ?, ?)
                        """,
                ticketNo,
                flightId,
                fare,
                price
        );
    }

    public void insertBoardingPass(String ticketNo, int flightId, String seatNo) {
        jdbcTemplate.update(
                """
                        INSERT INTO bookings.boarding_passes (ticket_no, flight_id, seat_no, boarding_no, boarding_time)
                        VALUES (?, ?, ?, NULL, NULL)
                        """,
                ticketNo,
                flightId,
                seatNo
        );
    }

    public long countBookRef(String bookRef) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings.bookings WHERE book_ref = ?",
                Long.class,
                bookRef
        );
        return cnt == null ? 0 : cnt;
    }

    public long countTicketNo(String ticketNo) {
        Long cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM bookings.tickets WHERE ticket_no = ?",
                Long.class,
                ticketNo
        );
        return cnt == null ? 0 : cnt;
    }

    public record BookingLegPrice(
            int flightId,
            String departure,
            String arrival,
            String airplaneCode,
            OffsetDateTime departureTime,
            OffsetDateTime arrivalTime,
            BigDecimal price
    ) {
    }

    public record BoardingPassRow(Integer boardingNo) {
    }
}
