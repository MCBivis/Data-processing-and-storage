package org.example.web;

import org.example.dto.BookingRequest;
import org.example.dto.BookingResponse;
import org.example.dto.CheckInRequest;
import org.example.dto.CheckInResponse;
import org.example.service.BookingService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/bookings")
    public BookingResponse book(@RequestBody BookingRequest request) {
        return bookingService.createBooking(request);
    }

    @PostMapping("/check-in")
    public CheckInResponse checkIn(@RequestBody CheckInRequest request) {
        return bookingService.checkIn(request);
    }
}
