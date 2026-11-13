# Executed verification

Latest verification: 13 November 2026, Asia/Kolkata. Windows 11; Java 21.0.13; Maven 3.9.11; Node 22.19.0; MongoDB 8.0 on a dedicated native single-node replica set at port 27018.

## Automated checks

- **Backend: 20 tests passed, 0 failures, 0 errors, 0 skipped.** Five JUnit 5/Mockito service tests and fifteen integration tests. `scripts/Test-Backend.ps1 -Integration` completed with `BUILD SUCCESS` and packaged the Spring application.
- Integration coverage includes actual MongoDB filtering/indexes, complete inventory, restart-safe seeding, real HTTP/OpenAPI, validation/CORS, password hashing, denied role escalation, invalid login/JWT, object ownership, atomic multi-seat rollback, expired-hold rejection/reclaim, duplicate payment handling, declined-payment release, cancellation/refund idempotency, invalidation of cancelled tickets, and admin movie/cinema/showtime publication with overlap rejection.
- **Contention:** twelve simultaneous workers competed for one seat using independent MongoDB transaction sessions. Exactly one hold succeeded; confirmation produced one persisted confirmed booking owner. The test inspected both booking and inventory ownership, then cancelled the fixture.
- **Frontend: 13 tests passed across 3 files.** Multiple selection/deselection and price totals, eight-seat limit, authentication before holding, transmission of all selected seat IDs, checkout navigation, keyboard dismissal/focus restoration, failed inventory retry, IST dates/year rollover, time rendering, paise formatting, valid movie submission, automatic/custom slugs, rejected slug punctuation and preserved form values on server errors.
- **Frontend production build passed.** TypeScript and Vite generated lazy-loaded route bundles. jsdom was pinned to 26.1.0 to support this installed Node runtime; the initially selected jsdom 31 required a newer Node patch.
- **API smoke checks: all 14 passed again against the final running application**, including authenticated security's 401 response on anonymous writes.

## Browser walkthrough

### Movie-form correction — 13 November

The URL slug input used an unescaped hyphen in a character class, which is invalid under HTML pattern's Unicode-sets (`v`) mode. The attribute now escapes it, generates a slug from the movie title, and normalizes spaces/capitals in a manually edited slug. The regression test compiles the rendered pattern with the `v` flag. [Browser pattern documentation](https://developer.mozilla.org/en-US/docs/Web/HTML/Reference/Attributes/pattern)

The API advice now takes precedence over Spring's default problem-detail handler. Invalid request bodies return safe field-specific details and a `fieldErrors` map; malformed dates/numbers do not echo raw submitted data. The real HTTP integration test covers invalid slug, date, duration, rating and genres, checks that no movie is created on failure, and confirms a corrected request succeeds.

Browser verification used an isolated temporary QA administrator: the title populated a valid slug, submitting empty parsed genres showed `Genres: must not be empty`, and correcting the field saved the movie. There were no pattern-attribute console errors. `screenshots/movie-form-fixed.jpg` records the successful submission. The QA movie/account were removed afterward and the test session signed out; existing user movies and accounts were preserved. The initial integration run found the local database stopped; after restarting the native replica set, the complete suite passed. Both local app services were restarted and all fourteen API smoke checks passed again.

### Booking walkthrough — 12 November

The real browser used the React frontend, Spring API and development replica set. It registered a dedicated test customer, selected D5 and D6 together (₹600), signed in during checkout, acquired a timed hold, completed a successful simulated payment, and displayed the persisted booking and QR ticket. Cancellation then showed the cancelled/refunded state and removed the QR. The test ticket in `screenshots/confirmed-booking.jpg` was captured before cancellation; it is no longer valid.

A first-load Vite dependency optimizer reload initially cleared the in-memory session on the QR route. Explicit dependency pre-bundling was added so lazy-route imports are prepared before login. This was a development-server issue; booking state was already committed and could be retrieved after signing back in.

Admin browser checks verified the overview and successful movie creation through the form. The unscheduled QA movie was removed after verification, the temporary QA administrator role was restored to USER, and the QA session was signed out. `screenshots/admin-movie-created.jpg` records the success state. At a 390×844 viewport, discovery fit the page and the seat dialog kept its summary/continue action visible, with a horizontally scrollable auditorium. Selecting D5 and D6 showed two seats and ₹600 (`screenshots/mobile-seat-selection.jpg`). The viewport override was then reset.

## Limits

No k6 benchmark, thousands-of-clients load result, production deployment, high availability/failover result, exchange/group/waitlist test or durable notification delivery is claimed. UI payment simulation is not a real payment-provider integration. QR verification does not enforce a one-time admission scan. Administrator bootstrap uses a local owner-operated command; no public admin registration is exposed.

The tests exercise real local MongoDB, not a mocked database or a container. Future performance results must retain machine configuration, workload, raw metrics and invariant queries. Test failures encountered during development were fixed before the final passing run; they were not ignored or represented as successes.
