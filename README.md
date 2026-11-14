# CineNext

**Intelligent & Collaborative Movie Reservation System**

Java 21 · Spring Boot · MongoDB · React · TypeScript. A modular monolith built in tested milestones for a final-year engineering portfolio.

## Working now

- Administrator-managed movie discovery, title/genre/language/city filters, cinema listings, showtimes and a browser-local watchlist. Fresh installations contain no movies, cinemas or showtimes.
- Customer registration and login, BCrypt password hashing, 30-minute signed JWTs, and server-enforced USER/ADMIN permissions.
- **Select 1–8 seats**, sign in without losing the selection, and acquire an atomic hold for up to 10 minutes.
- Server-calculated prices, simulated successful/declined payments, idempotent confirmation, booking history, printable QR tickets and cancellation until 30 minutes before showtime.
- Admin sign-in and dashboard: add/edit movies, add cinemas with screens, publish showtimes with overlap protection, inspect real sales summaries, and verify ticket codes.
- MongoDB transactions and conditional seat ownership, safe error responses, OpenAPI, health checks, unit/component/integration tests, and a 12-contender concurrency test.

All payments are simulated. No card details or real charges are involved. Movies, cinemas and schedules shown to customers come from administrator saves. Bundled SVG illustrations are reusable original artwork choices, not automatic catalogue entries.

## Administrator-owned catalogue

1. Sign in as an administrator and save a movie under **Movies**. It immediately appears in the customer catalogue under **All cities**.
2. Add a cinema under **Cinemas**. Saving it creates its screen and seating layout; its city becomes available in the header.
3. Under **Showtimes**, select the saved movie and cinema screen, choose the date/time and price, and publish. Customers can book only these published shows.

The app never inserts sample movies, cinemas or schedules on startup. Empty sections show a clear empty state. The homepage feature and city choices come from the saved catalogue, without a hardcoded film or city. Selecting a specific city narrows movies to future shows there; **All cities** also includes saved movies awaiting scheduling.

For older installations, a repeatable startup migration preserves catalogue records evidenced by administrator audit events, including the selected movie/cinema/screen of an administrator-created show. Demo-only records are archived in place and excluded from customer routes and new booking requests. Existing accounts, bookings, payments and ticket history are preserved. Restarting cannot recreate demo entries; sample fixtures exist only in test sources and isolated test databases.

Advanced recommendations, collaborative rooms, exchanges, waitlists, refresh-token rotation, manager-specific permissions, durable notifications, Redis, Kafka, production monitoring and large-scale performance testing remain later milestones. This is a working booking MVP, not a claim of a completed production platform.

## Windows 11 setup — native services

Open PowerShell at this project root. Required: Node.js 22.12+ or a newer supported LTS, Maven 3.9+, Java 21, MongoDB Community 8.0 and mongosh on PATH.

```powershell
node --version
npm --version
mvn --version
mongod --version
mongosh --version
```

The launcher uses Java 21 from `JAVA_HOME`, a `-JavaHome` argument, or the optional project-local JDK. It does not modify the system Java installation. To install the pinned, checksum-verified local JDK:

```powershell
./scripts/Install-LocalJdk.ps1
```

Install dependencies and build:

```powershell
./scripts/Initialize.ps1
# Or: ./scripts/Initialize.ps1 -JavaHome 'C:\path\to\jdk-21'
```

Start the dedicated native MongoDB replica set:

```powershell
./scripts/Start-Mongo.ps1
```

It runs on **127.0.0.1:27018**, binds only to loopback, and stores data/logs in `.runtime/`. The existing MongoDB service on port 27017 is not modified. Booking transactions require this replica set; do not point the booking application at a standalone server. A single-node set supports local transactions, but is not a high-availability deployment.

Start the API in terminal 1:

```powershell
./scripts/Start-Backend.ps1
```

Start the frontend in terminal 2:

```powershell
./scripts/Start-Frontend.ps1
```

Open [CineNext](http://127.0.0.1:5173), [Swagger UI](http://127.0.0.1:8080/swagger-ui.html), or [API health](http://127.0.0.1:8080/actuator/health).

`Start-Backend.ps1` copies `.env.example` only if needed, generates a random local JWT signing secret when missing, and imports `.env` as data. Keep `.env` private and untracked. The connection is `mongodb://127.0.0.1:27018/cinenext_dev?replicaSet=cinenext-rs`. If you previously ran the catalogue-only milestone on 27017, use the new URI; the old catalogue database is preserved separately. No user data is automatically migrated between instances.

Stop the API/frontend with Ctrl+C. When finished using the project database:

```powershell
./scripts/Stop-Mongo.ps1
```

The shutdown script checks the project-owned process before stopping it and retains database files. If an organization's PowerShell execution policy prevents scripts, use the equivalent commands below or a permitted process-scoped policy; no system-wide policy change is required.

## Create your administrator

Customer registration never grants administrator access. With the project MongoDB running, execute:

```powershell
./scripts/Create-Admin.ps1
```

Enter a **new** administrator email and a password of at least 10 characters (maximum 72 UTF-8 bytes). Password entry is hidden and is not placed on the command line. Existing users are never promoted or overwritten by this command. It starts a short-lived Java process on a random HTTP port, provisions the admin, and exits.

Then select **Admin sign in** in the website footer, or open [admin sign-in](http://127.0.0.1:5173/login?admin=1). Use the credentials you just supplied. There are no hardcoded administrator passwords.

## Try a complete booking

1. Use **Sign in / Register** to create a customer account, or sign in when continuing from the seat map.
2. Open an administrator-saved movie and choose a future date/showtime that the administrator has published. Today's elapsed shows are omitted. If no programme exists yet, complete the administrator catalogue steps above first.
3. Choose several available seats. The total updates as you select/deselect them; unavailable seats are disabled.
4. Continue to payment. The server atomically holds the selected seats and returns the authoritative price and expiry.
5. Choose successful or declined **simulated payment**. Success displays a confirmed booking and QR ticket; decline releases the seats without a charge.
6. View **My tickets**, print the ticket, or cancel it before the cutoff. Cancellation invalidates the QR and releases every seat, with one simulated refund.

Access tokens stay in memory. A page reload or an expired session requires signing in again; existing holds and tickets remain in MongoDB. Signing out clears the tab's token and cached private data. Already issued bearer tokens expire after 30 minutes; immediate server-side revocation and rotating refresh sessions are future hardening work.

Admins add a movie first and then publish a showtime to make it bookable in a city. New cinemas get a 96-seat screen; standard pricing is entered per show, with ₹80 added for premium and ₹160 for recliners. An overlapping screen schedule is rejected, including a 20-minute turnaround. Ticket verification checks current server state; it does not implement a one-time admission/scan ledger yet.

## Verification commands

```powershell
# Unit tests and backend packaging; no running database required:
./scripts/Test-Backend.ps1
# Real repository, HTTP, security, transaction, payment and contention tests:
./scripts/Start-Mongo.ps1
./scripts/Test-Backend.ps1 -Integration
# Read-only API smoke checks with the application running:
./scripts/Test-Phase1.ps1
# Frontend component tests and production bundle:
Set-Location frontend
npm test
npm run build
```

Integration tests own a random `cinenext_test_*` database on port 27018 and remove only that database. They do not mutate your development catalogue. [Executed verification](docs/verification.md) records actual results and limits; a 12-client contention test is not a thousands-of-users benchmark.

## Exact manual commands

The complete source is already present. Do not overwrite it with a new starter scaffold.

```powershell
$env:JAVA_HOME = 'C:\path\to\jdk-21'
$env:PATH = (Join-Path $env:JAVA_HOME 'bin') + ';' + $env:PATH
Set-Location frontend
npm ci
npm test
npm run build
Set-Location ../backend
mvn -B verify
# Use Start-Mongo.ps1 and Start-Backend.ps1 to configure the replica set and local secret.
```

For direct `mvn spring-boot:run '-Dspring-boot.run.profiles=local'`, first set `MONGODB_URI` and a base64 `JWT_SECRET` containing at least 32 random bytes. Raw Maven does not automatically read `.env`. Official setup references: [Maven](https://maven.apache.org/install.html), [MongoDB on Windows](https://www.mongodb.com/docs/manual/tutorial/install-mongodb-on-windows/), [Node.js](https://nodejs.org/en/download).

## Architecture and connection

React Router handles routes; Axios adds the in-memory bearer token; TanStack Query manages server state; Zustand stores session/UI state. Vite proxies `/api` to Spring at port 8080. Spring validates requests and authorizes each object/role before MongoDB reads or mutations. Multi-seat holds, payment confirmation and cancellation use MongoDB transactions. Inventory is authoritative; frontend state and cache entries never confer ownership.

- [Architecture and folder structure](docs/architecture.md)
- [Prioritized roadmap](docs/roadmap.md)
- [Initial and current MongoDB schemas](docs/database-design.md)
- [REST API design](docs/api-design.md)
- [Exported OpenAPI specification](docs/openapi.json) and [Postman collection](docs/cinenext.postman_collection.json)
- [Dependency choices and official references](docs/dependencies.md)
- [Executed verification](docs/verification.md)

For deployment, serve `frontend/dist` behind HTTPS, route `/api` to the Java service, use an authenticated MongoDB replica set and proper secret management, restrict management/documentation endpoints, and complete the production hardening milestones. Publishing static frontend files alone does not deploy the Java/MongoDB system. No cloud deployment is claimed.

## Accurate portfolio statement

Built a Java 21/Spring Boot and React movie booking MVP with MongoDB transactions, atomic multi-seat reservations, idempotent simulated payments, JWT role-based access, QR tickets, cancellation and cinema administration; verified booking correctness with real MongoDB integration tests and concurrent seat contention. Designed recommendations, collaborative booking, seat exchange and waitlists for subsequent milestones.
