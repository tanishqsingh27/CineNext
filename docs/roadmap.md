# Prioritized implementation roadmap

This is a sequence of working milestones, not a claim that every requested feature is already implemented. Estimates assume one student working approximately 12–18 focused hours per week; they are planning ranges, not delivery guarantees. Re-estimate after each acceptance gate.

## Delivery status — 12 November 2026

Phase 1 is complete. The follow-up request also brought forward the core of phases 2A/2B: customer registration/login, USER/ADMIN roles, admin movie/cinema/showtime creation, multi-seat holds, simulated payments, QR tickets, history, cancellation/refunds and an overview dashboard. Real MongoDB integration tests include a twelve-contender seat race. See `verification.md` for executed results.

These phases are not fully closed: refresh-token rotation/revocation, MANAGER scope, full catalogue deletion/layout editing, signed versioned tickets for exchanges, admission scans, durable notifications and recovery/load testing remain. Subsequent phases below are planned. The current QR uses an opaque code verified against live booking state.

## Phase 1 — Foundation and catalogue · 1–2 weeks

**Completed foundation scope:** a Java 21/Spring Boot/MongoDB backend, React/TypeScript frontend, persistent sample catalogue, responsive discovery and movie detail pages, showtime lookup, seat preview, validation, error UI, OpenAPI, environment configuration, and Windows execution instructions.

Acceptance gate:

- Start both applications using the README; MongoDB persistence is real.
- Filter and search movies, open a movie, change city/date, and inspect a showtime's seats.
- An unavailable backend produces a recoverable error, never fabricated results.
- Invalid parameters and missing resources return deliberate errors; anonymous writes are denied.
- Type checking/build and meaningful backend tests pass, with actual results recorded.

The original read-only seat preview has now been extended into the booking flow described in the delivery status above.

## Phase 2A — Identity and catalogue administration · 1–2 weeks

Implement registration, login, rotating refresh tokens, logout, USER/ADMIN/MANAGER authorization, ownership checks, account validation, manager theatre scope, catalogue CRUD, and initial admin screens. Add centralized validation, safe audit records, and rate limits. Store secrets outside Git.

Acceptance gate: duplicate registrations are rejected; passwords are hashed; expired/revoked tokens fail; refreshing twice detects replay; a user cannot access another user's records; a manager cannot alter another cinema's showtimes. Booking endpoints remain unavailable until their invariants exist.

## Phase 2B — Correct single-party booking · 2–3 weeks

Configure a dedicated local MongoDB replica set. Implement authoritative `show_seats`, idempotent multi-seat holds, expiry, server-calculated pricing, simulated success/decline payments, atomic confirmation, signed/versioned QR tickets, booking history, cancellation policies, and notifications. UI clearly displays the server hold deadline.

Acceptance gate:

- Two independent customers racing for one seat yield exactly one confirmed owner.
- A failed multi-seat claim leaves no partial hold.
- Retrying a payment/confirmation returns the same outcome; a changed payload with the same key is rejected.
- Expired holds cannot confirm, even before the cleanup worker runs.
- Failed payment, cancellation, repeated cancellation, process restart, and ticket verification behave correctly.
- No user can inspect or cancel another user's booking.

**MVP cut line:** complete Phase 2B before advanced features. This gives a demonstrable end-to-end booking product and a reliable base for the differentiators.

## Phase 3 — Intelligent seat recommendations · 1 week

Add a deterministic Java scoring service over available seats. Consider group size, adjacency, budget, category, screen distance/angle, accessibility, and optional user history. Return at most five valid combinations with a score breakdown and human-readable explanations. No AI dependency is needed for this feature.

Acceptance gate: known layout fixtures rank sensible combinations; no combination breaks budget/accessibility/adjacency constraints; invalid/oversized requests fail; changing availability invalidates stale suggestions at hold time. Measure algorithm runtime separately from database latency.

## Phase 4 — Collaborative group booking · 2 weeks

Add room creation, expiring invitation capabilities, member permissions, movie/showtime votes, versioned room updates, authenticated WebSockets, reconnect snapshots, adjacent suggestions, participant seat assignments, and split simulated payments. Start with an explicit **all-paid-before-deadline** policy.

Acceptance gate: duplicate messages do not duplicate membership, votes, allocations, or charges; unauthorized room joins fail; stale updates conflict; disconnect/reconnect restores state; one failed or late payment releases the group and compensates successful payments exactly once.

## Phase 5 — Seat exchange and waitlist · 2–3 weeks

Implement exchange request/reject/cancel/accept states, ticket ownership and showtime checks, transactionally swapped assignments, ticket version invalidation, audit/history, and notifications. Then build a fair waitlist with matching preferences, exclusive expiring offers, reclaim/reoffer, and idempotent processing.

Acceptance gate: two simultaneous acceptances involving one ticket cannot both succeed; old QR versions fail; cancellation versus exchange resolves safely; waitlist workers cannot allocate one seat twice; ineligible budgets/categories are skipped without losing FIFO position; unclaimed offers expire and progress to the next eligible entry.

## Phase 6 — Reliability, operations, and performance · 2 weeks

Add Redis cache/rate-limit/hold hints, Kafka with a transactional outbox and deduplicating consumers, Prometheus metrics, Grafana dashboards, GitHub Actions, deployment runbooks, backups and restore drills, and k6 workloads. Use native services, WSL2 where supported, or managed endpoints. The Java API and MongoDB remain the core runtime.

Acceptance gate: Redis failure cannot cause double booking; duplicate/out-of-order Kafka deliveries do not corrupt business state; outbox backlog recovers after restart; CI runs repeatable tests; load results include p50/p95/p99, throughput, status breakdown, resource use, and zero duplicate ownership violations. Report real failure rates and environment limits.

## Optional final extension · 1 week

Add a clearly labelled simulated auditorium view with React Three Fiber only after all required workflows pass. It should aid seat choice and retain a fully accessible 2D fallback. Spring AI may explain recommendations later, but must never bypass deterministic availability and budget checks.

## Test strategy without container-managed infrastructure

- **Unit:** Java business rules with JUnit 5/Mockito and a controllable `Clock`; frontend state and component behavior.
- **Integration:** an explicitly configured isolated local MongoDB database; transaction tests run against a dedicated replica set, never against development records.
- **API/security:** real HTTP requests covering successful paths, validation, ownership, roles, CORS, idempotency, and error contracts.
- **Concurrency:** independent worker connections and barriers to force contention; inspect final persisted ownership after all workers finish.
- **End-to-end:** real browser → real Java API → test database, including error recovery.
- **Load:** separate k6 read, hold-contention, confirmation, and cancellation scenarios. Keep functional correctness assertions distinct from latency thresholds.

Each milestone adds only the infrastructure its acceptance gate needs. No benchmark, test, or deployment is marked successful unless actually executed.
