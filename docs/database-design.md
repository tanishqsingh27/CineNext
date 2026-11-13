# Current and target MongoDB schema design

## Current physical schema — booking MVP

Eleven collections are implemented. This section describes the delivered schema; the numbered design below is the richer target. Do not assume the target's alternative field names or extra collections already exist. No automatic migration between these shapes is implemented.

- `users`: `_id` UUID string, `email` normalized string (unique), `name`, `passwordHash` BCrypt string, `role` USER or ADMIN, `createdAt` BSON date. Public DTOs omit the hash. There is no roles array, account status or refresh session yet.
- `movies`, `theatres`, `screens`, `seats`, `showtimes`: Java records in `catalogue/model`. Movie `_id` is the initial slug, stable when edited; `slug` is separately unique. `releaseDate` is a Java LocalDate serialized as an ISO date in the API but stored as BSON date by Spring Data. Shows have `endsAt` for overlap checks. Admin scheduling increments a physical `scheduleVersion` on the screen to serialize competing writes. New shows use film duration plus 20 minutes; seed slots use three hours.
- `show_seats`: `_id,showtimeId,seatId,label,row,number,category,pricePaise,status,accessible`. HELD rows also have `holdId,userId,holdExpiresAt`; BOOKED rows have `bookingId,userId`. Updates remove obsolete fields. Unique `{showtimeId:1,seatId:1}`, plus `{status:1,holdExpiresAt:1}` index. Hold requests use this document's `_id`, not its template `seatId`.
- `seat_holds`: `_id` SHA-256 of actor/hold/key; `userId,showtimeId,seatIds,seatLabels,totalPaise,currency,expiresAt,status,requestHash,createdAt`, with display snapshots `movieTitle,theatreName,screenName,startsAt`. Status ACTIVE, RELEASED, PAYMENT_FAILED or CONFIRMED; confirmed holds add `bookingId`. Expiry follows the deadline even if stored status remains ACTIVE. No TTL-based release mechanism exists.
- `bookings`: `_id` UUID, `userId,holdId,showtimeId,seatIds,seatLabels,totalPaise,currency,status,ticketCode,createdAt`, and the same display snapshots. Status CONFIRMED or CANCELLED; cancellation adds `cancelledAt`. Unique `ticketCode` index and `{userId:1,createdAt:-1}` history index. Cancelled codes remain stored for audit but are rejected and omitted from customer responses.
- `payments`: attempt `_id` hashes actor/payment/key; `holdId,userId,requestHash,totalPaise,createdAt,status` SUCCESS or DECLINED; success adds `bookingId`. Refund `_id` is `refund-<bookingId>` with `userId,bookingId,status:REFUNDED,totalPaise,createdAt`. Refund rows do not have hold/hash fields. All payments simulate INR; no gateway/card data exists.
- `audit_logs`: `_id` UUID, `actorId,action,resourceId,occurredAt`. Booking confirmation/cancellation and admin mutations write audit records inside their transaction, without credential/token data.

`BookingDatabase` creates collections and model/additional indexes before writes. DTO validation and service predicates enforce current rules; collection-level `$jsonSchema` migrations remain planned. Exact model fields/indexes are in the catalogue sources; business writes are in `BookingService`. Transactions use snapshot reads and majority writes with bounded transient retries. Twelve contenders for one seat were tested against the native replica set.

## Scope and conventions

The sections below are the **target schema contract**. Identity, ownership, payments and auditing already have a smaller implemented form described above. Collaboration, exchange, waitlist, notifications and outbox remain proposals. Read the backend records/index initialization for the exact physical schema. Do not create every target collection before its service and migration are ready.

- IDs and references are strings. Catalogue seed IDs are readable slugs; user/business IDs will be random UUIDs. MongoDB does not enforce foreign keys: services must verify references.
- Instants are BSON dates in UTC; cinema dates are interpreted using the theatre's IANA timezone. Date-only release dates use `YYYY-MM-DD` strings. Example `{"$date":"..."}` denotes a BSON date in Extended JSON.
- Prices use integer minor units, such as `35000` paise, with explicit `currency: "INR"`. No floating-point arithmetic for money. API bounds keep amounts within safe integer limits.
- Future mutable aggregates have integer `version`, plus `createdAt` and `updatedAt` dates. Conditional updates include the expected version where concurrent changes matter.
- Unless described otherwise, all fields shown are required; optional fields are explicitly named. Enums reject unknown values, strings have application-enforced maximum lengths, and arrays have bounded lengths.
- Validate incoming DTOs and business rules in Java. Add versioned MongoDB `$jsonSchema` validators before enabling each writable module; use `validationLevel: "strict"` and `validationAction: "error"`. Validators are a planned database migration, not a claim that every rule below is installed in Phase 1.
- `_id` is automatically unique. Additional unique indexes below are essential and must be created before writes are exposed. Build/migrate indexes deliberately; do not assume an annotation has already created one in production.
- Examples are illustrative documents, not a seed script. Security fields are deliberately nonfunctional examples.

## 1. `users` — planned identity aggregate

**Fields:** `_id:string`, `emailNormalized:string`, `displayName:string`, `passwordHash:string`, `roles:string[]`, `managedTheatreIds:string[]`, `status:string`, `preferences:object`, `version:int`, `createdAt:date`, `updatedAt:date`. `preferences` may hold `category:string`, `accessible:boolean`, and `preferredRow:int` without recording medical explanations.

**Indexes:** unique `{emailNormalized:1}`; `{status:1,createdAt:-1}` for administration. Normalize email consistently before lookup/write; do not combine a case-sensitive unique index with inconsistent normalization.

**Validation and consistency:** email length ≤254, display name 1–80, hash never plaintext, roles limited to `USER|ADMIN|MANAGER`, status `ACTIVE|DISABLED`; the public registration DTO cannot set roles or managed theatres. Profile update uses an expected version. Refresh sessions belong in a separate bounded `refresh_sessions` collection, storing token hashes and rotation-family state, not an ever-growing array in this document.

```json
{"_id":"usr-101","emailNormalized":"arun@example.test","displayName":"Arun","passwordHash":"REDACTED_HASH_EXAMPLE","roles":["USER"],"managedTheatreIds":[],"status":"ACTIVE","preferences":{"category":"PREMIUM","accessible":false},"version":0,"createdAt":{"$date":"2026-11-12T09:00:00Z"},"updatedAt":{"$date":"2026-11-12T09:00:00Z"}}
```

## 2. `movies` — catalogue

**Fields:** `_id:string`, `slug:string`, `title:string`, `tagline:string`, `synopsis:string`, `genres:string[]`, `language:string`, `durationMinutes:int`, `certificate:string`, `releaseDate:string`, `rating:double`, `posterUrl:string`, optional `backdropUrl:string`, `featured:boolean`. Future optional fields: licensed `trailerUrl:string`, `status:DRAFT|PUBLISHED|ARCHIVED`, and editorial timestamps/version.

**Indexes:** unique `{slug:1}`; `{language:1,title:1}`; `{genres:1}`; `{featured:1}`. Search initially uses bounded, escaped literal matching; evaluate a dedicated search index once dataset size warrants it. Do not claim that a normal title index accelerates every unanchored case-insensitive substring search.

**Validation and consistency:** nonempty title/slug; duration 1–600; rating 0–10; genres 1–8; ISO release date; local image path or approved HTTPS source; movie deletion prohibited while future/past showtimes reference it—archive instead. Seed artwork and films are fictional.

```json
{"_id":"orbit-nine","slug":"orbit-nine","title":"Orbit Nine","tagline":"Some journeys begin where the signal ends.","synopsis":"An engineer follows an impossible transmission beyond the last mapped orbit.","genres":["Sci-Fi","Adventure"],"language":"English","durationMinutes":142,"certificate":"UA","releaseDate":"2026-11-06","rating":8.7,"posterUrl":"/posters/orbit-nine.svg","featured":true}
```

## 3. `theatres` — catalogue

**Fields:** `_id:string`, `name:string`, `city:string`, `address:string`, `amenities:string[]`. Target additions: `timezone:string`, `status:ACTIVE|INACTIVE`, and timestamps/version.

**Indexes:** `{city:1,name:1}`; unique `{city:1,name:1,address:1}` where business identity requires it.

**Validation and consistency:** name/address/city nonempty with length bounds; supported timezone (initially `Asia/Kolkata`); amenities from a controlled list. Archive only after checking future shows. User-facing lookup contains no manager private details.

```json
{"_id":"theatre-101","name":"CineNext Observatory","city":"Bengaluru","address":"42 Residency Road, Bengaluru","amenities":["Dolby Atmos","Wheelchair access"],"timezone":"Asia/Kolkata","status":"ACTIVE"}
```

## 4. `screens` — catalogue layout

**Fields:** `_id:string`, `theatreId:string`, `name:string`, `formats:string[]`, `rowCount:int`, `seatsPerRow:int`, `totalSeats:int`, `layoutVersion:int`; target optional `screenGeometry:object` for recommendation/view simulation.

**Indexes:** unique `{theatreId:1,name:1}`. `theatreId` references `theatres`.

**Validation and consistency:** positive row/seat counts; supported formats; total seats equals valid physical seat definitions. Do not edit a published layout in place while it has active showtimes: create a new layout version. Screen geometry is simulated unless a theatre supplies verified measurements.

```json
{"_id":"screen-101","theatreId":"theatre-101","name":"Screen 1","formats":["2D","Dolby Atmos"],"rowCount":8,"seatsPerRow":12,"totalSeats":96,"layoutVersion":1}
```

## 5. `seats` — physical seats

**Fields:** `_id:string`, `screenId:string`, `row:string`, `number:int`, `label:string`, `category:STANDARD|PREMIUM|RECLINER`, `accessible:boolean`, `blocked:boolean`; target `layoutVersion:int`, `position:{x:double,y:double}` when scoring geometry is introduced.

**Indexes:** unique `{screenId:1,row:1,number:1}` (include `layoutVersion` when multiple versions are stored together). Reference to `screens`.

**Validation and consistency:** positive seat number; row label and seat label consistent; bounded coordinates; category valid. Physical seats have no customer ownership. Maintenance is reflected in future show inventory with policy-defined handling for existing holds/bookings.

```json
{"_id":"screen-101-D7","screenId":"screen-101","row":"D","number":7,"label":"D7","category":"PREMIUM","accessible":false,"blocked":false,"layoutVersion":1,"position":{"x":7.0,"y":4.0}}
```

## 6. `showtimes` — scheduled screening

**Fields:** `_id:string`, `movieId:string`, `theatreId:string`, `screenId:string`, `startsAt:date`, `city:string`, `language:string`, `format:string`, `priceFromPaise:long`, `currency:string`. Target additions: `endsAt:date`, `layoutVersion:int`, `prices:object` keyed by category, `status:SCHEDULED|CANCELLED|FINISHED`, `cancellationPolicy:object`, `version:int`.

**Indexes:** `{movieId:1,city:1,startsAt:1}`; `{theatreId:1,startsAt:1}`; unique `{screenId:1,startsAt:1}`. That unique index prevents identical starts but **does not** prevent overlapping show intervals. Publishing schedules must serialize/conditionally version a per-screen schedule record and check `[startsAt, endsAt)` plus turnaround time.

**Validation and consistency:** referenced movie/screen/theatre exist and screen belongs to theatre; valid language/format; nonnegative prices; `endsAt > startsAt`; price/cancellation snapshots become immutable after sales start unless an explicit migration policy applies. `availableSeats` is a read projection, never the authoritative reservation gate.

```json
{"_id":"show-101","movieId":"orbit-nine","theatreId":"theatre-101","screenId":"screen-101","startsAt":{"$date":"2026-11-14T13:00:00Z"},"endsAt":{"$date":"2026-11-14T15:22:00Z"},"city":"Bengaluru","language":"English","format":"2D","priceFromPaise":25000,"currency":"INR","prices":{"STANDARD":25000,"PREMIUM":35000,"RECLINER":55000},"layoutVersion":1,"status":"SCHEDULED","cancellationPolicy":{"cutoffMinutes":120,"refundPercent":100},"version":0}
```

## 7. `show_seats` — planned authoritative seat inventory

**Fields:** `_id:string`, `showtimeId:string`, `seatId:string`, `label:string`, `category:string`, `pricePaise:long`, `currency:string`, `status:AVAILABLE|HELD|BOOKED|BLOCKED`, `version:int`; conditional fields `holdId:string`, `ownerId:string`, `holdExpiresAt:date`, `bookingId:string`.

**Indexes:** unique `{showtimeId:1,seatId:1}`; `{showtimeId:1,status:1,category:1}`; `{status:1,holdExpiresAt:1}`; `{bookingId:1}`. No TTL index on this collection.

**Validation and consistency:** AVAILABLE/BLOCKED have no owner/hold/booking; HELD requires owner, hold, expiry; BOOKED requires booking and owner and has no active hold. Claim with a conditional `findAndModify`/update; confirm only with exact `holdId`, owner, unexpired timestamp and HELD state. Multi-seat operations run in one transaction and require every requested row to match. Physical identity and price snapshot remain stable. Unique rows plus transition predicates together enforce ownership.

```json
{"_id":"inventory-101","showtimeId":"show-101","seatId":"screen-101-D7","label":"D7","category":"PREMIUM","pricePaise":35000,"currency":"INR","status":"HELD","holdId":"hold-101","ownerId":"usr-101","holdExpiresAt":{"$date":"2026-11-12T09:08:00Z"},"version":1}
```

## 8. `bookings` — planned ticket aggregate

**Fields:** `_id:string`, `userId:string`, `showtimeId:string`, `holdId:string`, `seats:array<{seatId,label,category,pricePaise}>`, `totalPaise:long`, `currency:string`, `status:CONFIRMED|CANCELLED`, `paymentId:string`, `ticketVersion:int`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `groupRoomId:string`, `cancelledAt:date`, `cancellationReason:string`.

**Indexes:** unique `{holdId:1}` for initial one-hold/one-booking model; `{userId:1,createdAt:-1}`; `{showtimeId:1,status:1}`. Group booking uses one aggregate with participant ownership per seat added in Phase 4; it does not silently violate the hold uniqueness rule.

**Validation and consistency:** 1–8 unique seats initially, exact sum and currency, ticket version positive; all confirmed seats must point back to this booking in `show_seats`. Creation, payment success, inventory confirmation, and outbox event commit together. Cancellation uses the stored policy and transactional release; no physical delete. Exchange updates both booking snapshots and inventory with ticket-version increments.

```json
{"_id":"booking-101","userId":"usr-101","showtimeId":"show-101","holdId":"hold-101","seats":[{"seatId":"screen-101-D7","label":"D7","category":"PREMIUM","pricePaise":35000}],"totalPaise":35000,"currency":"INR","status":"CONFIRMED","paymentId":"payment-101","ticketVersion":1,"version":0,"createdAt":{"$date":"2026-11-12T09:04:00Z"},"updatedAt":{"$date":"2026-11-12T09:04:00Z"}}
```

## 9. `seat_holds` — planned bounded reservation

**Fields:** `_id:string`, `userId:string`, `showtimeId:string`, `seatIds:string[]`, `status:ACTIVE|CONSUMED|EXPIRED|RELEASED`, `expiresAt:date`, `idempotencyKey:string`, `requestHash:string`, `totalPaise:long`, `currency:string`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `purgeAt:date` after terminal retention.

**Indexes:** unique `{userId:1,idempotencyKey:1}`; `{status:1,expiresAt:1}`; TTL `{purgeAt:1}` only for already-terminal records after retention. Choose the idempotency retention window longer than the permitted client retry window.

**Validation and consistency:** 1–8 distinct seat IDs; positive bounded lifetime; hash of canonical request; server price; holds and inventory claim are in one transaction. A cleanup worker conditionally releases only rows still carrying the same hold ID. Repeated release is safe. Expiry checks happen on requests, independent of TTL cleanup delays.

```json
{"_id":"hold-101","userId":"usr-101","showtimeId":"show-101","seatIds":["screen-101-D7"],"status":"ACTIVE","expiresAt":{"$date":"2026-11-12T09:08:00Z"},"idempotencyKey":"d7d6c16a-0090-4b82-b448-f117419848de","requestHash":"SHA256_OF_CANONICAL_REQUEST","totalPaise":35000,"currency":"INR","version":0,"createdAt":{"$date":"2026-11-12T09:03:00Z"},"updatedAt":{"$date":"2026-11-12T09:03:00Z"}}
```

## 10. `payments` — planned simulated payment ledger

**Fields:** `_id:string`, `userId:string`, `holdId:string`, `idempotencyKey:string`, `requestHash:string`, `amountPaise:long`, `currency:string`, `provider:SIMULATED`, `status:PENDING|SUCCEEDED|FAILED|REFUND_PENDING|REFUNDED`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `bookingId:string`, `failureCode:string`, `providerReference:string`.

**Indexes:** unique `{userId:1,idempotencyKey:1}`; `{holdId:1,createdAt:-1}`; partial unique `{holdId:1}` for `{status:"SUCCEEDED"}` in the single-payer model. Phase 4 evolves the constraint to one success per participant allocation.

**Validation and consistency:** positive amount equals server-calculated obligation; currency matches; decline inputs allowed only by explicit simulation mode; never store card details. Success plus booking confirmation is transactional for simulation. Refund state is monotonic and idempotent. External-provider migration adds unique provider event IDs and reconciliation instead of assuming network/database atomicity.

```json
{"_id":"payment-101","userId":"usr-101","holdId":"hold-101","idempotencyKey":"9352a5f0-d9d0-4df5-a5ed-63976ed6387c","requestHash":"SHA256_OF_CANONICAL_REQUEST","amountPaise":35000,"currency":"INR","provider":"SIMULATED","status":"SUCCEEDED","bookingId":"booking-101","version":1,"createdAt":{"$date":"2026-11-12T09:04:00Z"},"updatedAt":{"$date":"2026-11-12T09:04:00Z"}}
```

## 11. `group_booking_rooms` — planned collaboration aggregate

**Fields:** `_id:string`, `organizerId:string`, `name:string`, `inviteTokenHash:string`, `inviteExpiresAt:date`, `status:VOTING|HOLDING|PAYING|CONFIRMED|EXPIRED|CANCELLED`, `paymentPolicy:ALL_PAID`, `candidateMovieIds:string[]`, `candidateShowtimeIds:string[]`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `showtimeId`, `holdId`, `bookingId` strings and `paymentDeadline:date`.

**Indexes:** unique `{inviteTokenHash:1}`; `{organizerId:1,createdAt:-1}`; `{status:1,paymentDeadline:1}`.

**Validation and consistency:** bounded room/member/candidate counts; only organizer changes locked showtime; store an invitation token hash, never publish it in catalogue or logs. Mutations include expected room version. All paid allocations plus unexpired hold are required to confirm. Room state and associated booking/payment changes commit together; broadcasts happen after commit.

```json
{"_id":"room-101","organizerId":"usr-101","name":"Friday cinema","inviteTokenHash":"HASH_OF_RANDOM_CAPABILITY","inviteExpiresAt":{"$date":"2026-11-13T09:00:00Z"},"status":"VOTING","paymentPolicy":"ALL_PAID","candidateMovieIds":["orbit-nine"],"candidateShowtimeIds":["show-101"],"version":0,"createdAt":{"$date":"2026-11-12T09:00:00Z"},"updatedAt":{"$date":"2026-11-12T09:00:00Z"}}
```

## 12. `group_booking_participants` — planned per-member state

**Fields:** `_id:string`, `roomId:string`, `userId:string`, `role:ORGANIZER|MEMBER`, `movieVotes:string[]`, `showtimeVotes:string[]`, `assignedSeatIds:string[]`, `amountDuePaise:long`, `paymentStatus:UNPAID|PAID|REFUNDED`, `version:int`, `joinedAt:date`, `updatedAt:date`; optional `paymentId:string`.

**Indexes:** unique `{roomId:1,userId:1}`; `{userId:1,joinedAt:-1}`. Avoid an array-unique index as a substitute for transactional seat-allocation checks.

**Validation and consistency:** votes are unique and reference room candidates; assigned seats belong to the room hold and only one participant; payment amount computed from allocation. Member may modify only their votes/payment; organizer may allocate under room rules. Disconnection is ephemeral presence, not removal of payment obligations. Mutations update the room version transactionally to serialize seat allocation.

```json
{"_id":"member-101","roomId":"room-101","userId":"usr-101","role":"ORGANIZER","movieVotes":["orbit-nine"],"showtimeVotes":["show-101"],"assignedSeatIds":[],"amountDuePaise":0,"paymentStatus":"UNPAID","version":0,"joinedAt":{"$date":"2026-11-12T09:00:00Z"},"updatedAt":{"$date":"2026-11-12T09:00:00Z"}}
```

## 13. `seat_exchange_requests` — planned exchange state machine

**Fields:** `_id:string`, `showtimeId:string`, `requesterId:string`, `recipientId:string`, `fromBookingId:string`, `toBookingId:string`, `fromSeatId:string`, `toSeatId:string`, `status:PENDING|ACCEPTED|REJECTED|CANCELLED|EXPIRED`, `expiresAt:date`, `idempotencyKey:string`, `requestHash:string`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `resolvedAt:date`.

**Indexes:** unique `{requesterId:1,idempotencyKey:1}`; `{recipientId:1,status:1,createdAt:-1}`; `{status:1,expiresAt:1}`. Multiple pending offers may reference a seat; **only the transactional ownership/version predicates can accept one valid outcome**.

**Validation and consistency:** distinct seats and users, same future showtime, both confirmed owners, recipient-only acceptance/rejection, expiry before showtime cutoff, initial same-price/same-category policy. Acceptance checks both booking versions and both inventory owners, swaps atomically, increments ticket versions, and writes history/audit/outbox. Never exchange based solely on stale request fields.

```json
{"_id":"exchange-101","showtimeId":"show-101","requesterId":"usr-101","recipientId":"usr-102","fromBookingId":"booking-101","toBookingId":"booking-102","fromSeatId":"screen-101-D7","toSeatId":"screen-101-D8","status":"PENDING","expiresAt":{"$date":"2026-11-14T12:00:00Z"},"idempotencyKey":"988a7000-bd90-48e0-93f9-8cad2fbde9d6","requestHash":"SHA256_OF_CANONICAL_REQUEST","version":0,"createdAt":{"$date":"2026-11-12T09:10:00Z"},"updatedAt":{"$date":"2026-11-12T09:10:00Z"}}
```

## 14. `waitlist_entries` — planned fair queue

**Fields:** `_id:string`, `userId:string`, `showtimeId:string`, `groupSize:int`, `category:string`, `maxTotalPaise:long`, `accessible:boolean`, `status:WAITING|OFFERED|FULFILLED|CANCELLED|EXPIRED`, `version:int`, `createdAt:date`, `updatedAt:date`; optional `activeOfferId:string`.

**Indexes:** `{showtimeId:1,status:1,createdAt:1,_id:1}`; partial unique `{showtimeId:1,userId:1}` for active statuses (`WAITING`/`OFFERED`), using a supported partial filter; `{userId:1,createdAt:-1}`.

**Validation and consistency:** 1–8 group size, positive total budget, supported category, user-owned entry; eligibility requires matching adjacent seats/accessibility and total budget. Order eligible entries by creation time then ID. Allocation changes WAITING→OFFERED and claims inventory in one transaction. A preference change must use an explicit queue-position policy (initial policy: change creates a new queue position).

```json
{"_id":"wait-101","userId":"usr-101","showtimeId":"show-101","groupSize":2,"category":"PREMIUM","maxTotalPaise":80000,"accessible":false,"status":"WAITING","version":0,"createdAt":{"$date":"2026-11-12T09:15:00Z"},"updatedAt":{"$date":"2026-11-12T09:15:00Z"}}
```

## 15. `waitlist_offers` — planned exclusive timed offer

**Fields:** `_id:string`, `entryId:string`, `userId:string`, `showtimeId:string`, `holdId:string`, `seatIds:string[]`, `totalPaise:long`, `currency:string`, `status:ACTIVE|ACCEPTED|DECLINED|EXPIRED`, `expiresAt:date`, `version:int`, `createdAt:date`, `updatedAt:date`.

**Indexes:** unique `{holdId:1}`; partial unique `{entryId:1}` for `{status:"ACTIVE"}`; `{status:1,expiresAt:1}`. Keep terminal offers for fairness/debugging retention, not immediate TTL deletion.

**Validation and consistency:** seat count and price satisfy the entry; offer expiry equals or precedes hold expiry. Acceptance verifies owner and both offer/hold deadlines. Expiry/rejection conditionally releases the same hold, updates entry policy, and schedules the next eligible allocation. Notification retries do not create another offer or extend expiry.

```json
{"_id":"offer-101","entryId":"wait-101","userId":"usr-101","showtimeId":"show-101","holdId":"hold-offer-101","seatIds":["screen-101-D7","screen-101-D8"],"totalPaise":70000,"currency":"INR","status":"ACTIVE","expiresAt":{"$date":"2026-11-12T09:25:00Z"},"version":0,"createdAt":{"$date":"2026-11-12T09:20:00Z"},"updatedAt":{"$date":"2026-11-12T09:20:00Z"}}
```

## 16. `notifications` — planned user inbox

**Fields:** `_id:string`, `userId:string`, `eventId:string`, `type:string`, `title:string`, `body:string`, `resourceType:string`, `resourceId:string`, `createdAt:date`; optional `readAt:date`, `purgeAt:date`.

**Indexes:** unique `{userId:1,eventId:1,type:1}`; `{userId:1,createdAt:-1}`; optional TTL `{purgeAt:1}` under retention policy.

**Validation and consistency:** type from supported event set, bounded plain text, no secrets or private details belonging to another member. Consumers deduplicate via unique event key. Mark-read operations require owner; notifications link to current authoritative resources and do not themselves confer a reservation.

```json
{"_id":"notification-101","userId":"usr-101","eventId":"event-101","type":"BOOKING_CONFIRMED","title":"Your seats are confirmed","body":"Orbit Nine · D7","resourceType":"BOOKING","resourceId":"booking-101","createdAt":{"$date":"2026-11-12T09:04:01Z"}}
```

## 17. `audit_logs` — planned restricted audit trail

**Fields:** `_id:string`, `eventId:string`, `actorId:string`, `action:string`, `resourceType:string`, `resourceId:string`, `outcome:SUCCESS|DENIED|FAILED`, `requestId:string`, `metadata:object`, `occurredAt:date`; optional privacy-filtered `clientIpHash:string`, `purgeAt:date` under explicit retention policy.

**Indexes:** unique `{eventId:1}`; `{resourceType:1,resourceId:1,occurredAt:-1}`; `{actorId:1,occurredAt:-1}`; retention index only after policy approval.

**Validation and consistency:** metadata allowlist, strict size limits, no raw JWTs/invites/passwords/payment credentials. Successful business audit records commit with the business transaction; denied attempts use a separate safe logging path. App credentials may append but should not update/delete audit records. MongoDB alone is not a tamper-proof external audit archive.

```json
{"_id":"audit-101","eventId":"event-101","actorId":"usr-101","action":"BOOKING_CONFIRMED","resourceType":"BOOKING","resourceId":"booking-101","outcome":"SUCCESS","requestId":"request-101","metadata":{"showtimeId":"show-101","seatCount":1,"ticketVersion":1},"occurredAt":{"$date":"2026-11-12T09:04:00Z"}}
```

## Additional supporting collections when needed

The requested 17 collections are not enough for every security/reliability requirement. Add these with their corresponding milestone:

- `refresh_sessions`: hashed token, family/session ID, user ID, expiry, rotation/revocation timestamps; unique token hash and expiry cleanup.
- `idempotency_records`: actor, operation, key, request hash, status, result resource ID; unique `(actorId,operation,key)` where the result is not naturally represented by a hold/payment record.
- `outbox_events`: immutable event ID, aggregate ID/version, schema version, payload, createdAt, publishedAt, retry metadata. Insert in the business transaction; the publisher may deliver more than once.
- `processed_events`: unique `(consumer,eventId)` receipt inserted in the same transaction as consumer effects.
- `screen_schedules`: one versioned record per screen/day to serialize interval checks when publishing shows.

## Migration and verification rules

Use additive migrations with explicit IDs and rollback/recovery instructions. Verify unique-index builds before exposing writes. Backfill `show_seats` from immutable screen layouts before opening sales, then assert exact expected counts. Never silently repair inconsistent ownership by choosing one claimant; stop writes for affected shows, retain evidence, and reconcile against committed payment/booking state.

The booking milestone must provide invariant queries and concurrency tests proving: one inventory row per show/seat; every BOOKED row points to exactly one confirmed booking with matching ownership; every confirmed booking seat points back; no expired hold confirms; exchange maintains both mappings; canceled bookings own no inventory.

MongoDB transactions require an appropriate deployment topology. The current booking MVP uses the isolated native replica set on port 27018; the existing service and data on 27017 are preserved. Use `scripts/Start-Mongo.ps1` rather than pointing booking at a standalone instance. [MongoDB transaction documentation](https://www.mongodb.com/docs/manual/core/transactions/)
