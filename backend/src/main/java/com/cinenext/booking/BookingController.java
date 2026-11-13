package com.cinenext.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
@io.swagger.v3.oas.annotations.security.SecurityRequirement(name="bearerAuth")
public class BookingController {
    private final BookingService bookings;
    public BookingController(BookingService bookings) { this.bookings=bookings; }
    public record HoldRequest(@NotEmpty @Size(max=8) List<@NotBlank @Size(max=200) String> seatIds) {}
    public record PaymentRequest(@NotBlank String outcome) {}
    @PostMapping("/showtimes/{id}/holds") @ResponseStatus(HttpStatus.CREATED)
    public BookingService.HoldView hold(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody HoldRequest body,@RequestHeader("Idempotency-Key") String key) { return bookings.hold(user.getSubject(),id,body.seatIds(),key); }
    @GetMapping("/holds/{id}") public BookingService.HoldView hold(@AuthenticationPrincipal Jwt user,@PathVariable String id) { return bookings.getHold(user.getSubject(),id); }
    @DeleteMapping("/holds/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void release(@AuthenticationPrincipal Jwt user,@PathVariable String id) { bookings.release(user.getSubject(),id); }
    @PostMapping("/holds/{id}/payments") public BookingService.PaymentView pay(@AuthenticationPrincipal Jwt user,@PathVariable String id,@Valid @RequestBody PaymentRequest body,@RequestHeader("Idempotency-Key") String key) { return bookings.pay(user.getSubject(),id,body.outcome(),key); }
    @GetMapping("/bookings") public List<BookingService.BookingView> bookings(@AuthenticationPrincipal Jwt user) { return bookings.history(user.getSubject()); }
    @GetMapping("/bookings/{id}") public BookingService.BookingView booking(@AuthenticationPrincipal Jwt user,@PathVariable String id) { return bookings.booking(user.getSubject(),id); }
    @PostMapping("/bookings/{id}/cancel") public BookingService.BookingView cancel(@AuthenticationPrincipal Jwt user,@PathVariable String id) { return bookings.cancel(user.getSubject(),id); }
}
