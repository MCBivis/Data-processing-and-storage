package org.example.service;

import org.example.dto.BookingRequest;
import org.example.dto.BookingResponse;
import org.example.dto.CheckInRequest;
import org.example.dto.CheckInResponse;
import org.example.repository.BookingRepository;
import org.example.repository.BookingRepository.BookingLegPrice;
import org.example.repository.BookingRepository.BoardingPassRow;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class BookingService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final BookingRepository bookingRepository;

    public BookingService(BookingRepository bookingRepository) {
        this.bookingRepository = bookingRepository;
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
        List<BookingLegPrice> legs = new ArrayList<>();
        for (int flightId : flightIds) {
            BookingLegPrice lp = loadLeg(flightId, fare);
            legs.add(lp);
            total = total.add(lp.price());
        }

        for (int i = 0; i < legs.size(); i++) {
            BookingLegPrice current = legs.get(i);
            if (i > 0) {
                BookingLegPrice prev = legs.get(i - 1);
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

        lockFlightsForUpdate(flightIds);

        String bookRef = allocateBookRef();
        String ticketNo = allocateTicketNo();

        bookingRepository.insertBooking(bookRef, total);
        bookingRepository.insertTicket(
                ticketNo,
                bookRef,
                request.passengerId().trim(),
                request.passengerName().trim()
        );

        for (BookingLegPrice lp : legs) {
            bookingRepository.insertSegment(ticketNo, lp.flightId(), fare, lp.price());
            String seat = pickSeat(lp.flightId(), lp.airplaneCode(), fare);
            bookingRepository.insertBoardingPass(ticketNo, lp.flightId(), seat);
        }

        return new BookingResponse(bookRef, ticketNo, total);
    }

    @Transactional
    public CheckInResponse checkIn(CheckInRequest request) {
        String ticketNo = request.ticketNo().trim();
        int flightId = request.flightId();

        BoardingPassRow row = bookingRepository.lockBoardingPassForUpdate(ticketNo, flightId)
                .orElseThrow(() -> new IllegalArgumentException("Segment not found for ticket and flight"));
        if (row.boardingNo() != null) {
            throw new IllegalStateException("Passenger is already checked in for this flight");
        }

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Timestamp boardingTime = Timestamp.from(now.toInstant());
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                Integer nextBoarding = bookingRepository.findNextBoardingNo(flightId)
                        .orElseThrow(() -> new IllegalStateException("Could not allocate boarding number"));

                int updated = bookingRepository.updateCheckIn(ticketNo, flightId, nextBoarding, boardingTime);

                if (updated == 1) {
                    return new CheckInResponse(nextBoarding, now);
                }
            } catch (DuplicateKeyException e) {
                // Concurrent check-in on the same flight got this boarding number first; retry.
            }
        }

        throw new IllegalStateException("Could not perform check-in due to concurrent updates, please retry");
    }

    private BookingLegPrice loadLeg(int flightId, String fare) {
        return bookingRepository.findLeg(flightId, fare)
                .orElseThrow(() -> new IllegalArgumentException("Unknown or cancelled flight: " + flightId));
    }

    private int freeSeats(int flightId, String airplaneCode, String fare) {
        int total = bookingRepository.countSeatsForFare(airplaneCode, fare);
        int used = bookingRepository.countBoardedSeatsForFareOnFlight(flightId, airplaneCode, fare);
        return total - used;
    }

    private String pickSeat(int flightId, String airplaneCode, String fare) {
        return bookingRepository.pickFreeSeat(flightId, airplaneCode, fare)
                .orElseThrow(() -> new IllegalStateException("No free seat in " + fare + " for flight " + flightId));
    }

    private void lockFlightsForUpdate(List<Integer> flightIds) {
        flightIds.stream()
                .distinct()
                .sorted(Comparator.naturalOrder())
                .forEach(this::lockFlightForUpdate);
    }

    private void lockFlightForUpdate(int flightId) {
        if (!bookingRepository.lockFlightForUpdate(flightId)) {
            throw new IllegalArgumentException("Unknown flight: " + flightId);
        }
    }

    private String allocateBookRef() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String ref = randomAlnum(6);
            if (bookingRepository.countBookRef(ref) == 0) {
                return ref;
            }
        }
        throw new IllegalStateException("Could not allocate book_ref");
    }

    private String allocateTicketNo() {
        for (int attempt = 0; attempt < 50; attempt++) {
            String candidate = randomDigits(13);
            if (bookingRepository.countTicketNo(candidate) == 0) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not allocate ticket_no");
    }

    private static String randomAlnum(int len) {
        char[] chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(chars[RANDOM.nextInt(chars.length)]);
        }
        return sb.toString();
    }

    private static String randomDigits(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) ('0' + RANDOM.nextInt(10)));
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
}
