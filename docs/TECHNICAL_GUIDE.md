# Technical guide & viva handbook

Everything a teacher is likely to ask about HiLITE Mall Smart Parking: the architecture, the
"API", the Java concepts, the database, the deployment, and short answers you can say out loud.

---

## 0. Quick facts

| Question | Answer |
|---|---|
| What is it? | A college demonstration **website** for a fictional mall car park |
| Live URL | <https://hilite-mall-parking.onrender.com/> |
| Language | **Java 21** (all UI, business logic and persistence) |
| Backend | **Spring Boot 4.1.2** (embedded Tomcat) |
| UI framework | **Vaadin 25.3.4** (server-side Java UI, no hand-written HTML/JS) |
| Database | **SQLite 3** in one file (`data/hilite-parking.db` locally) |
| Build tool | **Maven** (`mvn -B clean verify`) |
| Tests | **289 automated tests in 33 test classes — 0 failures, 0 errors, 0 skipped** |
| Code size | 64 main Java files + 41 test/support Java files |
| Deployment | Docker image on **Render**; static landing page on **Vercel** |
| Source control | Git + GitHub |

Three screens/modes: **Entry kiosk** (`/entry`), **Exit kiosk** (`/exit`), **Admin panel**
(`/admin`, dashboard at `/admin/dashboard`). `/` opens whichever kiosk the admin selected.

---

## 1. Architecture

### 1.1 Layered architecture

```text
Browser (HTML/CSS + Vaadin JS client)
   │  HTTP requests / polling
   ▼
Spring Boot 4.1.2  (embedded Tomcat, port 8080 / $PORT)
   ├─ web/kiosk     EntryView, ExitView, AllocationView, KioskHomeView
   ├─ web/admin     AdminPinView, DashboardView
   ├─ web/ui        shared components (KioskPage, ScanCard, PlateInput, Icons, …)
   ├─ web/security  AdminAuthorization (session unlock + lock-out), AdminOperations gate
   ├─ service       ParkingService, ExitService, FeeCalculator, StatisticsService,
   │                LongStayService, AdminAccessService, DemoResetService
   ├─ repository    interfaces + jdbc/ implementations (plain JDBC, prepared statements)
   ├─ config        ParkingConfiguration (bean wiring), HiliteProperties (env config)
   └─ health        DatabaseHealthIndicator (Actuator)
   │
   ▼
SQLite file  (WAL mode, foreign keys, explicit transactions)
```

**Rules that make the layering real:**

- A **view never talks to a repository**. Views call services only.
- A **service contains no Vaadin or Spring UI code** (they are plain Java classes with
  constructor injection). They can be tested without starting a web server.
- Only `repository/jdbc/` contains SQL.
- Only `FeeCalculator` contains fee arithmetic.
- Admin actions go through `AdminOperations`, which checks the server-side unlock flag first.

### 1.2 Why this architecture?

- **Separation of concerns**: UI, rules, persistence can change independently.
- **Testability**: 289 tests include service tests with a real temporary SQLite file and
  browserless UI tests — no manual clicking needed.
- **Safety**: business invariants are enforced twice (Java validation **and** database
  constraints), so a bug in one layer cannot silently corrupt data.

### 1.3 Example request flows

**Entry:** browser clicks *Find My Parking* → `EntryView` → `ParkingService.allocateParking()`
→ reads slots + active sessions → picks a bay → `ParkingSessionRepository.insertActive()` →
returns `AllocationOutcome.Allocated(session, slot)` → view navigates to
`/allocation/{sessionId}` (read-only).

**Exit:** `ExitView` → `ExitService.quote()` (no writes) → shows fee → *Confirm Demo Payment* →
`ExitService.confirmPayment(quote)` → re-validates the quote → `JdbcExitCompletionRepository`
updates the session **and** inserts the payment in one transaction → `ExitOutcome.Completed`.

**Dashboard:** `DashboardView` → `AdminOperations.statistics()` → unlock check →
`StatisticsService.snapshot()` → four KPI cards, 24 bay tiles, hourly chart, long-stay list.

### 1.4 Dependency injection (Spring)

`ParkingConfiguration` creates every collaborator once as a Spring bean:

```java
@Bean Clock clock() { return Clock.systemUTC(); }
@Bean Database database(HiliteProperties p) { return new Database(p.dbPath()); }
@Bean DatabaseInitializer databaseInitializer(Database db) { … initialize() … }
@Bean ParkingService parkingService(ParkingSlotRepository s, ParkingSessionRepository r, Clock c) { … }
@Bean ExitService exitService(…) { … new JdbcExitCompletionRepository(database) … }
```

Spring constructs the object graph at startup (constructor injection), so views simply declare
what they need:

```java
public ExitView(ExitService exitService, Clock clock, ZoneId mallTimeZone) { … }
```

Services are **stateless singletons**, so every browser session shares them safely. Per-user
state (admin unlock) lives in the `VaadinSession`, never in a service field.

---

## 2. "What API did you use?" — the honest answer

### 2.1 There is no hand-written REST API

This is a **server-side rendered Vaadin Flow application**. The browser does not call
`/api/...` endpoints; Vaadin automatically synchronises the Java component tree with the
browser. So:

- **No `@RestController`, no `@GetMapping`, no JSON API of our own.**
- The business "API" is the **Java service layer** inside the same JVM.
- The only public HTTP endpoint besides the pages is Spring Boot Actuator's health check.

### 2.2 HTTP surface (what the browser can request)

| URL | Purpose |
|---|---|
| `/` | Forwards to `/entry` or `/exit` according to the saved kiosk mode |
| `/entry` | Entry kiosk |
| `/allocation/{sessionId}` | Allocation confirmation (read-only) |
| `/exit` | Exit kiosk |
| `/admin` | Admin PIN screen |
| `/admin/dashboard` | Admin dashboard |
| `/actuator/health` | Deployment health check, e.g. `{"groups":["liveness","readiness"],"status":"UP"}` |
| `/VAADIN/**` | Vaadin's own static client resources (framework-managed) |

Vaadin also performs its own request handling (component updates, heartbeat every 60 s, kiosk
clock polling every 500 ms). Those are framework endpoints, not code we wrote.

### 2.3 Application service API (the important one)

| Service | Method | Returns | Writes? |
|---|---|---|---|
| `ParkingService` | `allocateParking(registration, vehicleType, chargingRequested)` | `AllocationOutcome` (Allocated / Rejected) | yes (one session) |
| `ParkingService` | `findAllocation(sessionId)` | `Optional<Allocated>` | no |
| `ExitService` | `quote(registration)` | `ExitOutcome` (Quoted / Rejected) | **no** |
| `ExitService` | `completeZeroFee(quote)` | `ExitOutcome` (Completed / Rejected) | yes |
| `ExitService` | `confirmPayment(quote)` | `ExitOutcome` (Completed / Rejected) | yes |
| `AdminAccessService` | `verifyPin(candidate)`, `currentMode()`, `changeMode(mode)` | boolean / `KioskMode` | mode only |
| `StatisticsService` | `snapshot()` | `DashboardStatistics` | no |
| `LongStayService` | `activeAlerts()` | `List<LongStayAlert>` | no |
| `DemoResetService` | `reset()` | void | yes (transaction) |

Result types are **sealed interfaces** (`ExitOutcome`, `AllocationOutcome`) with records for
each case, e.g. `ExitOutcome.Quoted`, `ExitOutcome.Completed`, `ExitOutcome.Rejected`. This makes
"success" impossible to fake: the view can only show the success screen for a `Completed`.

### 2.4 Repository API (persistence abstraction)

`ParkingSlotRepository`, `ParkingSessionRepository`, `PaymentRepository`, `SettingsRepository`,
`ExitCompletionRepository` — interfaces in `repository/`, JDBC implementations in
`repository/jdbc/`. They hide SQL from the services.

### 2.5 External APIs / libraries

No payment gateway, map, ANPR or camera API. The project uses:

- **JDBC API** (`java.sql.*`) with the **SQLite JDBC driver**;
- **Spring Boot / Spring Framework** (DI, configuration, Actuator);
- **Vaadin Flow** (UI framework);
- **java.time** (dates/times), **BigDecimal** (money), JUnit 5 (tests).
- **Spring Boot Actuator** — the only "API" exposed over HTTP.

If asked "could you add a REST API?": yes — a `@RestController` could call the same services;
the service layer was deliberately kept UI-free, so adding REST would not change business code.

---

## 3. Java concepts used (with where to look)

### 3.1 Object-oriented programming

| Concept | Where | Note |
|---|---|---|
| Encapsulation | every service/repository | private fields, constructor-injected dependencies, small public methods |
| Interfaces / abstraction | `repository/*`, `DatabaseHealthIndicator` | services depend on interfaces, not JDBC classes |
| Inheritance | `KioskPage` (abstract) → `EntryView`, `ExitView`, … | shared header/footer/shortcut/polling |
| Polymorphism | `ExitOutcome`, `AllocationOutcome` | one variable, several record implementations |
| Composition | services hold repositories; views hold services | preferred over deep inheritance |
| Constructor overloading | `ParkingService` (2/3 args), `ExitService` (4/5 args) | production constructor + testable `Clock` constructor |

### 3.2 Modern Java features

| Feature | Example |
|---|---|
| **Records** (18 declarations) | `ParkingSession`, `Payment`, `ParkingSlot`, `ExitQuote`, `ExitSummary`, `DashboardStatistics`, `HiliteProperties` |
| **Compact constructors + validation** | `ParkingSession` rejects blank IDs; `Payment` enforces status/timestamp rules |
| **Sealed interfaces** | `ExitOutcome` (`Quoted`/`Completed`/`Rejected`), `AllocationOutcome` |
| **Pattern matching for `instanceof`** | `if (outcome instanceof ExitOutcome.Quoted quoted)` |
| **Switch expressions (arrow)** | `FeeCalculator.hourlyRate`: `case CAR -> …; case BIKE -> …` |
| **Text blocks** (26) | multi-line SQL in `DatabaseInitializer` and repositories |
| **Enhanced enums** | `Floor` stores code + display name; `VehicleType`, `Zone`, `KioskMode`, `PaymentStatus` |
| **Nested types** | `ExitOutcome.Quoted`; `AdminAuthorization.PinAttempt.Status` |

### 3.3 Collections, generics and functional style

- `List`, `Map`, `Set`, `EnumMap`, `HashMap`, arrays (`long[]` for hourly buckets).
- `Optional<T>` for lookups (`findById`, `findActiveByRegistration`, `findValue`).
- **Streams**: filter/map/sorted/collect (`ParkingService`, `StatisticsService`,
  `LongStayService`), e.g. `Collectors.toUnmodifiableSet()`.
- **Lambdas / method references**: button click listeners, `Comparator.comparing(...)`,
  `slots.stream().filter(slot -> …)`.
- **Functional interfaces**: `Consumer`, `Supplier`, `Comparator` (imported in `ExitView`).
- **Immutability**: `record` + `List.copyOf(...)` for returned snapshots.

### 3.4 Exceptions and resources

- Custom **unchecked** exception `DataAccessException extends RuntimeException` wraps every
  `SQLException`, keeping the original cause.
- `try-with-resources` (42 uses) closes `Connection`, `PreparedStatement`, `ResultSet`
  automatically.
- **Suppressed exceptions**: a rollback failure is attached to the original failure
  (`Database.rollback`) so the real error is not lost.
- Services distinguish *domain refusals* (returned as `Rejected`) from *real failures*
  (exceptions propagate), so the UI never shows a fake success.

### 3.5 Money and time

- **`BigDecimal`** for rupee amounts (scale 0 from the calculator; scale 2 when read back from
  paise); `Math.ceilDiv` for rounding hours up.
- **Integer paise** in SQLite — avoids floating-point errors (`₹5.00` ↔ `500`).
- **java.time**: `Instant` (UTC event time), `Duration` (stays), `ZoneId` (`Asia/Kolkata` display),
  `DateTimeFormatter`, `Clock`.
- **Injected `Clock`**: production uses `Clock.systemUTC()`, tests use a fixed clock, so
  timestamps and fees are deterministic.

### 3.6 Persistence / JDBC

- `DriverManager.getConnection("jdbc:sqlite:…")`.
- `PreparedStatement` for **every** value — prevents SQL injection.
- `ResultSet` mapping to records.
- `PRAGMA foreign_keys = ON` and `PRAGMA busy_timeout = 5000` on every connection; the foreign
  key state is read back and verified.
- `BEGIN IMMEDIATE` / `COMMIT` / `ROLLBACK` transactions with correct rollback on failure.
- `journal_mode = WAL` for concurrent readers with one writer.

### 3.7 Concurrency and data integrity

- Services are stateless; each database call opens its own connection (no shared mutable state).
- Database-level guards: **partial unique indexes** (one active session per plate, one per bay,
  one confirmed payment per session), **conditional UPDATE** (`WHERE exit_time IS NULL`),
  `CHECK` constraints.
- Allocation retries the next candidate only when SQLite reports a **constraint** race; BUSY/I/O
  failures propagate instead of being misreported as "car park full".
- `busy_timeout` makes a writer wait instead of failing immediately.

### 3.8 Spring and Vaadin (framework concepts)

- `@SpringBootApplication`, `@Configuration`, `@Bean`, `@Component`,
  `@ConfigurationProperties` + `@DefaultValue`, constructor **dependency injection**,
  `HealthIndicator` (Actuator), externalised configuration via `application.properties` /
  environment variables.
- `@Route`, `@PageTitle`, `BeforeEnterObserver`, `RouterLink`, server-side components
  (`Div`, `Span`, `Button`, `NativeButton`, `TextField`, `H1/H2`, `Paragraph`), event listeners,
  `Shortcuts` (Ctrl+Shift+A), `UI` polling, **`VaadinSession`** attributes.

### 3.9 Testing concepts

- **JUnit 5**: `@Test`, `@TempDir`, assertions (`assertEquals`, `assertThrows`,
  `assertInstanceOf`), `@SpringBootTest` deployment tests.
- **browserless Vaadin tests** (`browserless-test-spring`) drive views without a browser.
- Temporary SQLite databases per test; **fixed/mutable clocks**; **SQL triggers** to inject
  failures and prove rollback; concurrency tests; restart-persistence test.

---

## 4. Business logic (what to explain on the board)

### 4.1 Bay allocation (`ParkingService`)

1. Normalize the plate (`RegistrationUtil`: remove spaces/hyphens, uppercase) and validate the
   demo format `AA00AAA0000`.
2. Reject a duplicate active plate.
3. Read the 24 bays + active sessions. A bay is **occupied iff an active session references it**.
4. Candidate order: Ground Floor first, ascending bay number.
   - Charging request → only charging bays.
   - Normal request → ordinary bays first, charging bays only as a fallback.
5. Insert one session with one `Instant` and one UUID.
6. If another kiosk stole the bay, SQLite's unique index refuses the insert; the service retries
   the next candidate. Any other database failure is surfaced, not hidden.

### 4.2 Fee calculation (`FeeCalculator` — the only fee code)

- First **120 minutes free**, inclusive.
- Time is measured in **whole seconds** (`Duration.toSeconds()` truncates sub-seconds first):
  120 min + 500 ms is free; 120 min + 1 s is charged.
- Any positive remainder rounds **up** to whole hours (`Math.ceilDiv`).
- Car ₹5/hour, bike ₹2/hour.

| Stay | Car | Bike |
|---|---:|---:|
| 1 h 30 min | ₹0 | ₹0 |
| exactly 2 h | ₹0 | ₹0 |
| 2 h + 1 s | ₹5 | ₹2 |
| 2 h 20 min | ₹5 | ₹2 |
| exactly 3 h | ₹5 | ₹2 |
| 3 h 10 min | ₹10 | ₹4 |
| 4 h 24 min | ₹15 | ₹6 |
| 24 h | ₹110 | ₹44 |

### 4.3 Exit and atomic payment (`ExitService` + `JdbcExitCompletionRepository`)

1. **Quote**: capture `now` **once**, calculate the fee at that instant, store it in an immutable
   `ExitQuote`. Quoting writes nothing.
2. **Confirm** (paid) or **Complete Exit** (₹0):
   - re-load the session; if it is already closed → `ALREADY_COMPLETED`;
   - check the quote still matches the stored session (registration, type, bay, entry time) and
     that the fee recalculated at the **quoted** exit time equals the quoted amount; otherwise →
     `STALE_QUOTE`;
   - run one transaction: conditional session update, then payment insert (paid only), then
     commit. Any failure → rollback (session stays active, no payment row).
   - the UI shows success only after the repository returns `true`.
3. **Cancel** returns to the details screen without any service call.
4. **₹0 stays** write no payment row at all.

This guarantees: the customer pays exactly what was displayed, and a session can never be
charged twice.

### 4.4 Dashboard statistics and long stays

- One consistent snapshot: every number comes from one `findAll()` of slots and one of sessions.
- Entries = all sessions; exits = completed sessions; occupancy = active sessions per bay.
- Peak hour = hour (in `Asia/Kolkata`) with most entries, earliest on a tie.
- Long-stay alert: active session with **more than 12 h** (43,200 whole seconds).
- Reset Demo: one transaction deletes payments then sessions, restores the 24-bay layout, keeps
  the PIN, sets kiosk mode to ENTRY.

### 4.5 Admin access

- PIN `1234` (demo), stored in `admin_settings`.
- Comparison uses `MessageDigest.isEqual` (constant-time) so timing does not leak the PIN.
- Wrong attempts are counted **per `VaadinSession`**; 5 failures lock the PIN screen for 30 s
  (configurable).
- Every admin action calls `AdminOperations` → `AdminAuthorization.requireUnlocked()`, so hiding
  a button is not the protection.
- This is **demonstration authentication**, not a user/identity system.

---

## 5. Tech stack

| Layer | Technology | Version | Role / why |
|---|---|---|---|
| Language | Java | 21 | Records, sealed types, modern APIs |
| Backend | Spring Boot | 4.1.2 | Embedded Tomcat, DI, configuration, Actuator |
| UI | Vaadin Flow | 25.3.4 | Server-side Java UI; no separate REST/SPA |
| UI theme | Vaadin Lumo + custom CSS | — | `styles.css` |
| Database | SQLite | 3 (file) | Zero-install, transactional, single file |
| JDBC driver | `org.xerial:sqlite-jdbc` | 3.53.4.0 | JDBC access to SQLite |
| Build | Maven | 3.9+ | Compile, test, package, frontend build |
| Tests | JUnit 5 + `browserless-test-spring` | Spring Boot BOM | Unit, repository, UI-view, deployment tests |
| Monitoring | Spring Boot Actuator | 4.1.2 | `/actuator/health` for Render |
| Container | Docker (multi-stage) | — | Reproducible runtime image |
| Hosting | Render (Docker web service) | free plan | Public demo URL, health check |
| Landing page | Static HTML + Vercel | — | `vercel-landing/` |
| SCM | Git + GitHub | — | History, PRs (`phase-5-exit-kiosk`, `phase-6-7-web-migration`) |

---

## 6. Deployment

- `mvn -B clean verify` builds and tests everything, producing `target/hilite-parking.jar`
  (Spring Boot executable JAR).
- Run locally: `java -jar target/hilite-parking.jar` → <http://localhost:8080>.
- Docker: multi-stage `Dockerfile` (Maven build stage → JRE runtime stage), runs as an
  unprivileged user, mounts `/data` for SQLite, health check via `curl /actuator/health`.
- Render: `render.yaml` blueprint, Docker runtime, `/actuator/health` health check,
  `HILITE_DB_PATH=/data/hilite-parking.db`, JVM memory flags for the 512 MB free plan.
- **Free-plan caveat:** no persistent disk → the database resets on sleep/restart/redeploy.
- `vercel-landing/` is a separate static page (Vercel project), not part of the Java app.

Environment variables: `PORT`, `HILITE_DB_PATH`, `HILITE_TIME_ZONE`, `HILITE_ADMIN_MAX_ATTEMPTS`,
`HILITE_ADMIN_LOCKOUT`.

---

## 7. Tests and verification (numbers to quote)

`mvn -B clean verify` → **289 tests, 0 failures, 0 errors, 0 skipped** (33 test classes).

| Area | Tests | What it proves |
|---|---:|---|
| Model | 42 | Record validation, status rules, slot/vehicle invariants |
| Service | 125 | Allocation, fees (all boundaries), exit quote/payment/rollback, statistics, long stay, admin access, reset |
| Repository | 54 | Schema/seed, constraints, transactions, concurrency, rollback, reset, hardening |
| Web views + security | 44 | Entry/Exit/Allocation/Admin views, admin authorization gate (browserless Vaadin) |
| Deployment | 9 | App starts, health endpoint, data survives restart |
| Health | 5 | Database health indicator |
| Utilities | 10 | Plate normalization, formatting |

Notable techniques: temporary SQLite per test (`@TempDir`), fixed clocks, SQL triggers to force
a failure between writes and prove full rollback, a concurrency test, and a restart test that
reopens the database file.

Also verified manually: real headless-browser walkthrough 43/43 checks (per README), deployed
health `UP`, and local `java -jar` startup.

---

## 8. Likely viva questions (short answers)

1. **Why SQLite and not MySQL?** Zero installation, one portable file, real SQL/transactions —
   enough for a single kiosk demo. MySQL/PostgreSQL would be used for multiple servers.
2. **Where is the database?** Local: `data/hilite-parking.db`; Docker/Render:
   `/data/hilite-parking.db`. Created and seeded automatically at startup.
3. **What happens to data on Render?** The free plan has no persistent disk, so it resets after
   sleep/redeploy; a paid disk at `/data` would keep it.
4. **What is WAL?** Write-ahead logging: readers do not block the writer (and vice versa);
   better concurrency than the default rollback journal.
5. **How do you prevent double payment?** One transaction (conditional session update + payment
   insert), a unique partial index on confirmed payments, and the `exit_time IS NULL` condition.
   The UI also disables buttons while busy.
6. **How is the fee exactly what was shown?** The quote freezes the exit timestamp and amount;
   confirmation reuses them and re-validates the stored session.
7. **Why BigDecimal/paise?** Binary floating point cannot represent 0.1 exactly; money must be
   exact. Java uses `BigDecimal` rupees, SQLite stores integer paise.
8. **Why records?** Immutable data carriers with generated constructor/accessors/equals/hashCode;
   concise and thread-safe.
9. **What is a sealed interface?** It restricts which types may implement it, so the result of an
   operation is a closed set (Quoted/Completed/Rejected) and the compiler helps exhaustiveness.
10. **How do you stop SQL injection?** Every value goes through `PreparedStatement` parameters;
    no string concatenation of user input.
11. **How do you make allocation safe with two kiosks?** Database partial unique indexes are the
    final guard; the service retries the next bay only for constraint races.
12. **How is admin security implemented?** Server-side unlock flag in `VaadinSession`; every
    admin operation re-checks it; per-session attempt limit and lock-out; constant-time PIN
    comparison. It is demo auth, not production identity.
13. **Why is there no REST API?** Vaadin runs the UI in Java on the server; business calls are
    direct method calls in the same JVM. The service layer could be exposed with a REST
    controller later without changing the rules.
14. **What is Vaadin?** A Java web framework that renders the UI from server-side Java components
    and synchronises them with the browser.
15. **What is Spring Boot?** A framework that starts an embedded server, wires beans via
    dependency injection, and provides configuration/Actuator.
16. **What is Maven?** Build/dependency tool; `verify` compiles, tests, builds the frontend and
    packages the JAR.
17. **What is Actuator?** Spring Boot's operations endpoints; here only `/actuator/health` is
    exposed, used by Render and Docker health checks.
18. **How do tests avoid your real data?** Each test gets a fresh temporary SQLite file in a
    JUnit `@TempDir`; the project database is never opened.
19. **How do you test a rollback?** A temporary SQL trigger aborts a write; the test asserts the
    session is still active and no payment row exists.
20. **Why inject a `Clock`?** Deterministic tests (fixed time) and a single source of "now" in
    production.
21. **What is a partial index?** An index with a `WHERE` clause; ours apply only to active
    sessions / confirmed payments, which enforces "only one".
22. **How does occupancy work?** Derived from active sessions (no mutable flag), so it cannot
    drift from the truth.
23. **Can it scale?** The web layer can, but SQLite allows one writer and one shared file —
    for horizontal scaling you would move to PostgreSQL/MySQL.
24. **What are the limitations?** Demo PIN, simulated scanning/payment/printing, ephemeral Render
    storage, no real camera/ANPR/gateway; `mvn spring-boot:run` dev mode not verified.
25. **What happens if the database file is deleted?** The app recreates and seeds it on the next
    startup (24 bays, default settings); sessions/payments are lost by design.

---

## 9. Where to look (file map)

| Topic | File |
|---|---|
| App entry point + Vaadin shell | `HiliteParkingApplication.java` |
| Bean wiring / configuration | `config/ParkingConfiguration.java`, `config/HiliteProperties.java` |
| Domain model | `model/` (`ParkingSession`, `Payment`, `ParkingSlot`, enums) |
| Fee rules | `service/FeeCalculator.java` |
| Entry allocation | `service/ParkingService.java`, `service/AllocationOutcome.java` |
| Exit quote/payment | `service/ExitService.java`, `ExitQuote`, `ExitSummary`, `ExitOutcome` |
| Statistics / long stay | `service/StatisticsService.java`, `LongStayService.java` |
| Admin access | `service/AdminAccessService.java`, `web/security/AdminAuthorization.java`, `AdminOperations.java` |
| Reset | `service/DemoResetService.java`, `repository/DatabaseInitializer.resetDemoData()` |
| Schema/seed | `repository/DatabaseInitializer.java` |
| Transactions / connection setup | `repository/Database.java`, `repository/jdbc/JdbcExitCompletionRepository.java` |
| Repositories | `repository/*.java`, `repository/jdbc/*.java` |
| Screens | `web/kiosk/`, `web/admin/`, `web/ui/` |
| Health | `health/DatabaseHealthIndicator.java` |
| Tests | `src/test/java/com/hilite/parking/` |
| Database viva guide | `docs/DATABASE.md` |

---

## 10. Suggested 8-minute live demo

1. Open <https://hilite-mall-parking.onrender.com/> (first load may take ~1 min on the free plan).
2. **Entry**: keep the scanned plate, choose Car, No charging → *Find My Parking* → show the bay
   and floor.
3. Open `data/hilite-parking.db` in DB Browser → `parking_sessions` → point at the new active row
   (`exit_time` NULL) and the assigned `slot_id`.
4. **Exit**: `/exit`, same plate → *Calculate Parking Fee* → show the frozen exit time and fee →
   *Pay & Exit* → *Confirm Demo Payment* → “Parking Session Closed”.
5. Back in DB Browser: the session now has `exit_time`/`final_fee_paise`, and `payments` has one
   `CONFIRMED` row with the same amount in paise.
6. **Admin**: Ctrl+Shift+A (or the lock button) → PIN `1234` → dashboard: occupancy, cars/bikes,
   peak hour, long-stay alert, kiosk-mode switch, Reset Demo.
7. Run one query from `docs/DATABASE.md` §6 (e.g. occupancy per floor) to show SQL skills.
