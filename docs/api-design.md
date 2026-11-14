# REST API contract

Local API: `http://127.0.0.1:8080/api/v1`. The frontend calls relative `/api/v1` through Vite's proxy. Swagger is at `/swagger-ui.html`; `/v3/api-docs` is authoritative. `openapi.json` is the delivered snapshot. Prices are integer paise; instants are UTC ISO strings; cinema dates use Asia/Kolkata.

## Public catalogue

Only administrator-published records (`catalogueSource: ADMIN`) are visible. A fresh database returns empty lists; startup creates no sample movies, cinemas or schedules. Hidden legacy demo IDs return 404 for direct movie/seat-map reads and cannot acquire new holds or complete a new payment. Existing confirmed booking history remains available. Publication metadata is server-controlled and cannot be supplied in public requests.

- `GET /movies?q=story&genre=Drama&language=English&page=0&size=12` → `{items,page,size,totalElements,totalPages}`. Title search is a literal case-insensitive substring; other filters are exact. Page 0–10000, size 1–50, filters at most 100 characters. Optional city filtering requires a future published show there. Omit city to include every saved movie, even before scheduling. Ordering is featured first, then title.
- `GET /movies/{id}` → one published movie or 404. Use the ID returned when the administrator saves the movie.
- `GET /theatres?city=Mumbai` → `{id,name,city,address,amenities}[]`.
- `GET /catalogue/filters` → `{cities,genres,languages}`, each a string array from MongoDB.
- `GET /showtimes?movieId={savedMovieId}&date=YYYY-MM-DD` → future published shows on the cinema date, defaulting to today. Parameters are optional; maximum 250 shows per date, ordered by start/theatre.
- `GET /showtimes/{id}/seats` → `{showtimeId,rows:[{label,seats:[]}],screenLabel,currency}`. Each seat has `id,label,row,number,category,pricePaise,status,accessible`. Expired holds appear AVAILABLE. Reading inventory does not reserve it.

Showtime fields: `id,movieId,theatreId,theatreName,screenName,city,startsAt,format,language,priceFromPaise,currency,availableSeats,totalSeats`. Availability is aggregated from inventory, including reclaimable expired holds. Saving a cinema creates an 8 × 12 seating template with A5/A6 accessible. The administrator sets each show's base price; premium/recliner premiums are described below. A seating template is not a showtime and never creates automatic schedules.

`GET /actuator/health` (outside `/api/v1`) reports application/MongoDB health without connection details. CORS allows explicitly configured local frontend origins.

## Customer authentication

- `POST /auth/register` with `{name,email,password}` → 201 `{accessToken,user:{id,name,email,role},expiresIn:1800}`. Name max 80, email max 254, password at least 10 characters and at most 72 UTF-8 bytes. Email is trimmed/lowercased. Duplicate email returns 409. Registration always assigns USER; a supplied role cannot grant permissions.
- `POST /auth/login` with `{email,password}` → 200, the same session shape. Invalid credentials return a generic 401.
- `GET /auth/me` with `Authorization: Bearer <accessToken>` → `{id,name,email,role}`.

Private requests use the bearer header. JWTs expire after 30 minutes. The browser keeps tokens in memory and clears private cache on logout; reload requires sign-in. Refresh, password reset and server-side token revocation are not implemented. Registration/login are limited to 20 attempts per remote IP per minute in the API process; excess returns 429. Only the local `scripts/Create-Admin.ps1` command provisions new ADMIN accounts.

## Holds, simulated payments and tickets

These routes require authentication and enforce ownership. Another customer's resource returns 404. Hold `seatIds` are **inventory IDs returned by the seat-map endpoint**, not labels such as D5 or screen-template IDs.

1. `POST /showtimes/{id}/holds` with `{seatIds:["inventory-id-1","inventory-id-2"]}` and `Idempotency-Key` → 201 hold. Select 1–8 distinct seats. All are claimed atomically or the transaction rolls back. Unavailable seats return 409. Sales close one minute before showtime.
2. `GET /holds/{id}` → active hold; expired holds return 410, released/confirmed/failed holds return 409. `DELETE /holds/{id}` → 204 and releases the caller's active hold. Repeated release is harmless.
3. `POST /holds/{id}/payments` with `{outcome:"SUCCESS"}` or `{outcome:"DECLINED"}` and a separate `Idempotency-Key` → 200 `{status,booking,message}`. Success atomically books every seat and writes booking/payment/audit records. Decline returns `booking:null` and releases the seats. No real money moves.
4. `GET /bookings` → latest 100 owned bookings, newest first (bounded array, not yet paginated). `GET /bookings/{id}` → one owned booking.
5. `POST /bookings/{id}/cancel` → updated booking. Allowed more than 30 minutes before start. Releases all seats, creates one simulated full refund and invalidates the QR. Repeated cancellation returns the cancelled record without another refund.

Hold response: `id,showtimeId,seatIds,seatLabels,totalPaise,currency,expiresAt,status,movieTitle,theatreName,screenName,startsAt`. The server calculates the total from inventory. Holds last up to ten minutes, capped at thirty seconds before start; the UI displays the server deadline.

Booking response: `id,showtimeId,movieTitle,theatreName,screenName,startsAt,seatLabels,totalPaise,currency,status,ticketCode,createdAt,canCancel`. The UI renders the opaque `ticketCode` as a QR; there is no separate ticket endpoint. Cancelled responses have `ticketCode:null` and `canCancel:false`.

Keys contain 8–100 letters, digits, hyphens or underscores and are bound to actor/operation plus a canonical request hash. Retry a hold with its original key and seat IDs while active to recover it; an expired or consumed hold is not recreated. Retry payment with its original key/outcome to recover the stored result. A changed payload with the same key returns 409. Use a new key for a new intentional operation, not for a network retry.

## Administration — ADMIN only

Every `/admin/**` route requires ADMIN; a USER receives 403. The UI is at `/admin` after admin sign-in.

- `POST /admin/movies` → 201; `PUT /admin/movies/{id}` → 200. Full body: `title,slug,tagline,synopsis,genres:string[],language,durationMinutes,certificate,releaseDate,rating,posterUrl,featured`. Slug matches `[a-z0-9-]{1,100}`, duration 1–300 minutes, rating 0–10. Poster URL selects one of eight bundled original SVGs. Existing movie reads use catalogue routes. Uploads/deletion are not implemented.
- `GET /admin/screens` → screen records.
- `POST /admin/theatres` with `{name,city,address,amenities:[],screenName,format}` → 201 theatre, plus one screen and 96 template seats created atomically.
- `POST /admin/showtimes` with `{movieId,screenId,startsAt,priceFromPaise}` → 201 show with full inventory. Start at least five minutes ahead and within 366 days. Base price 1000–1000000 paise; premium adds 8000, recliner adds 16000. A transactional screen update serializes scheduling and rejects overlap, including duration plus a 20-minute turnaround.
- `GET /admin/overview` → catalogue/confirmed-booking counts, current confirmed sales total and seven-day sales series. Cancelled bookings are excluded; this is not a financial accounting ledger.
- `POST /admin/tickets/verify` with `{ticketCode}` → currently confirmed booking. Unknown/cancelled codes return 404; more than four hours after start returns 409. No one-time admission scan ledger is implemented.

## Errors

Handled errors use `application/problem+json` with `type,title,status,detail` and optional `instance`: 400 invalid input; 401 missing/invalid/expired authentication; 403 insufficient role; 404 missing/unowned resource; 409 conflicting request, unavailable seats, duplicate identity or cancellation cutoff; 410 expired hold; 429 authentication limit; 503 database/transient failure. Empty searches return 200 with no items. Unknown routes/writes are denied by default. Sensitive database/credential details are never returned.

Invalid body fields also return a `fieldErrors` object keyed by field name. For example an invalid slug returns a `slug` entry and readable `URL slug: ...` detail. Invalid date/number formats identify the field without exposing raw submitted values. The admin form preserves entered data so it can be corrected and resubmitted.

## Run the examples

Import `cinenext.postman_collection.json`. First use Admin to save a movie and cinema and publish a show. Set `showDate` to that cinema date and, if needed, `movieId` to the saved movie after the list request. When blank, `showDate` initially defaults to tomorrow in IST. Set private `email`/`password` variables before registration/login. Use a test account: hold/payment/cancellation requests mutate local data. Run numbered requests in order to capture a published show and two available inventory IDs. The seat-map step generates new keys; retrying the hold/payment request reuses its key. Returned tokens stay in local Postman variables. No request creates a demo programme.

Admin examples use separate `adminEmail`/`adminPassword` from local bootstrap. Never export populated credential/token variables into source control. `scripts/Test-Phase1.ps1` provides read-only catalogue/security smoke checks and accepts an empty catalogue; optional detail/inventory checks run when content exists. Use `-ShowDate YYYY-MM-DD` to select a scheduled day. Transaction/auth/admin scenarios are covered by backend integration tests.

## Planned APIs

Later milestones add rotating refresh sessions, manager scope and custom screen layouts; explainable seat recommendations; group rooms with invitations/votes/allocations and authenticated WebSockets; exchange with ticket-version invalidation; FIFO waitlists with exclusive expiring offers; durable notifications and analytics through an outbox. These endpoints are not implemented. State/schema proposals are in `database-design.md`; acceptance gates are in `roadmap.md`.
