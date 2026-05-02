CREATE UNIQUE INDEX IF NOT EXISTS ux_bp_flight_seat
    ON bookings.boarding_passes (flight_id, seat_no);

CREATE UNIQUE INDEX IF NOT EXISTS ux_bp_flight_boarding_no
    ON bookings.boarding_passes (flight_id, boarding_no)
    WHERE boarding_no IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_bp_ticket_flight
    ON bookings.boarding_passes (ticket_no, flight_id);
