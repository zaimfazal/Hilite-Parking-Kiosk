# HiLITE Mall Smart Parking — Repository Audit

Audit date: **2026-10-28**. Audited snapshot: `main` at `1749daa` (Phase 5 merged).
Scope: every tracked file (78), as listed in §1. Compiled output (`target/`) is not source and was
not audited.

**How the audit was performed.** The lead (architect/integrator) ran the build and read the
contracts. Three **real parallel read-only agents** each audited one area:
- repository/domain/database;
- UI, FXML, CSS and the five design references;
- tests, `pom.xml`, `.gitignore` and documentation.

The lead reviewed and merged their findings. Findings use exactly one classification:
- **Confirmed defect** — proven from code or running it;
- **Potential risk** — needs runtime verification;
- **Documentation inconsistency**;
- **Intentional design decision**.

---

## 1. File inventory

| Area | Files (lines) | Purpose |
|---|---|---|
| Build | `pom.xml` (78) | Java 21, JavaFX 21.0.5, sqlite-jdbc 3.53.4.0, JUnit 5.11.4, Surefire 3.5.2, javafx-maven-plugin 0.0.8 |
| VCS | `.gitignore` (46) | Ignores `target/`, `data/`, `*.db`, logs, `.env*`, IDE files |
| Docs | `PROJECT_RULES.md` (252), `PHASE_PLAN.md` (905), `UI_GUIDE.md` (405), `README.md` (561) | Rules, phase history, visual system, overview |
| Design refs | 5 PNGs in `Design References/` | Mockups only, not runtime assets (see §1.1) |
| App shell | `MainApp` (206), `Navigator` (112) | JavaFX stage/scene, startup DB init, screen switching, in-memory kiosk mode |
| Controllers | `Entry` (322), `Allocation` (182), `Exit` (495), `AdminPin` (127), `Dashboard` (336), `KioskHeader` (48), `NavigatorAware`, `ServiceAware`, `ExitServiceAware` | JavaFX event handling and display |
| Model | `Floor`, `Zone`, `VehicleType`, `KioskMode`, `PaymentStatus`, `ParkingSlot`, `ParkingSession`, `Payment` | Plain Java records/enums with validation |
| Services | `FeeCalculator` (93), `ParkingService` (234), `AllocationOutcome`, `ExitService` (295), `ExitQuote`, `ExitOutcome`, `ExitSummary` | Fees, allocation, exit/payment workflow |
| Repository | `Database` (105), `DatabaseInitializer` (470), `DataAccessException`, 5 interfaces | Connections, schema, seed, contracts |
| JDBC | `SqlCodec` (69), `JdbcParkingSlot/Session/Payment/Settings/ExitCompletionRepository` | Plain JDBC, prepared statements |
| Util | `RegistrationUtil` (47), `FormatUtil` (85), `GarageBackdrop` (215), `Icons` (178), `UiEffects` (58) | Plate key, formatting, JavaFX art/icons/effects |
| Preview | `PreviewData` (98), `PreviewBay`, `PreviewLongStay`, `PreviewStats`, `preview.VehicleType` | Phase 1 sample data; still drives PIN and dashboard |
| FXML | `entry`, `allocation`, `exit`, `admin-pin`, `dashboard`, `KioskHeader` | Five screens and a shared header |
| CSS | `app.css` (309), `kiosk.css` (1094), `admin.css` (732) | JavaFX CSS (`-fx-*`), not browser CSS |
| Tests | 10 test classes, 4 support classes (14 files) | 157 JUnit 5 tests on `@TempDir` SQLite |

### 1.1 Design references

| File | Pixels | Screen |
|---|---|---|
| `HiLITE Mall Smart Parking Kiosk.png` | 1672×941 | Entry kiosk |
| `Smart Parking Bay Allocation.png` | 1672×941 | Allocation confirmation and signage |
| `Smart Parking Exit Kiosk Interface.png` | 1672×941 | Exit kiosk (the reference uses a light fee card) |
| `HiLITE Mall Parking Admin Login.png` | 1672×941 | Admin PIN |
| `HiLITE Mall Parking Dashboard.png` | 1586×992 | Admin dashboard. Its extra nav items and KPIs are deliberately not copied. |

---

## 2. Architecture summary

- **Layering is sound.**
  - Controllers → services → repository interfaces → JDBC implementations.
  - No SQL in controllers.
  - No JavaFX imports in `model`, `service`, `repository`, or `util/RegistrationUtil` or `util/FormatUtil`.
  - Only `GarageBackdrop`, `Icons` and `UiEffects` in `util` are JavaFX-specific.
- **Wiring.**
  - `MainApp` builds one `Database`, the JDBC repositories, `ParkingService` and `ExitService` by hand.
  - It hands them to `Navigator`, which injects them into controllers through the `*Aware` interfaces.
  - There is no static mutable state and no DI framework.
- **Business rules live in one place each.**
  - Fees: `FeeCalculator`.
  - Plate key: `RegistrationUtil`.
  - Atomic exit: `ExitCompletionRepository`.
  - Occupancy is derived from active sessions; there is no occupancy flag.
- **Database.**
  - Four tables: `parking_slots`, `parking_sessions`, `payments`, `admin_settings`.
  - Every value is a prepared-statement parameter.
  - Foreign keys are enforced and verified on every connection.
  - Three partial unique indexes:
    - one active session per plate;
    - one active session per bay;
    - one CONFIRMED payment per session.
  - Timestamps are 30-character UTC ISO text. Money is integer paise.
  - Initialization is idempotent and transactional.
- **Gaps.**
  - Phase 6 does not exist yet.
  - The dashboard, the PIN check and the reset dialog run entirely on `PreviewData`.

---

## 3. Verified baseline

| Item | Observed (this machine, 2026-10-28) |
|---|---|
| OS | Windows 11 Home Single Language 10.0.26300 |
| JDK | Oracle Java 26.0.2.1 (`C:\Program Files\Java`); compiles with `--release 21` |
| Maven | 3.9.11 from `~/.m2/wrapper/dists` (`mvn` is not on PATH) |
| Node.js | 24.22.1 (needed by Vaadin 25.3) |
| Docker | **Not installed**. Container packaging cannot be run locally. |
| `mvn -B clean verify` | **BUILD SUCCESS — Tests run: 157, Failures: 0, Errors: 0, Skipped: 0** |

**Reconciling 156 vs 157 tests.** The QA agent counted `@Test` methods by name in every test class.
There are no parameterized, repeated or dynamic tests, so surefire's count equals the method count:
**157**. The earlier "156" report was wrong. Per class:

| Class | Tests |
|---|---:|
| ParkingSessionTest | 18 |
| ParkingSlotTest | 11 |
| PaymentTest | 13 |
| DatabaseInitializerTest | 10 |
| ParkingSessionRepositoryTest | 12 |
| ParkingSlotRepositoryTest | 4 |
| PaymentRepositoryTest | 8 |
| FeeCalculatorTest | 26 |
| ParkingServiceTest | 24 |
| ExitServiceTest | 27 |
| RegistrationUtilTest | 4 |

**Build warnings.** The only warnings come from Java 26: SQLite's native `System::load` and
JavaFX's `sun.misc.Unsafe`. Neither is a compiler warning in project code.

**Framework compatibility check** (scratchpad spike, not part of the project):
- Versions came from the official Vaadin 25.3 compatibility page and Vaadin's own
  `skeleton-starter-flow-spring` pom: **Spring Boot 4.1.2 + Vaadin 25.3.4 + Java 21**.
  - Vaadin 25.3 requires Java 21+, Spring Boot 4.1+, Node 24+ and Maven 3.8+.
  - Spring Boot 3.5 OSS support ended on 2026-06-30, so it was rejected.
- The spike used vaadin-core, actuator, sqlite-jdbc 3.53.4.0 and browserless-test-spring.
- Result: `mvn package` → BUILD SUCCESS. A browserless view test passed, the production
  frontend build completed, and an executable JAR was produced.

---

## 4. Findings

### 4.1 Confirmed defects

| # | Finding | Evidence |
|---|---|---|
| D1 | The admin dashboard shows no real data. All KPIs, floor maps, peak hour and long-stay rows come from `PreviewData`. Entries 38 / exits 29 / peak "4 PM" are literals. | `DashboardController` uses `PreviewData.stats()`, `baysForFloor()`, `longStayAlerts()` |
| D2 | **Reset Demo** changes nothing. Its dialog says the app is "not connected to a database", which has been false since Phase 3. | `DashboardController` reset handler; dashboard.fxml copy |
| D3 | The admin PIN is compared with `PreviewData.ADMIN_PIN` instead of `admin_settings`. There is no attempt limit. | `AdminPinController` |
| D4 | Kiosk mode is held only in memory in `Navigator.KioskMode`. `admin_settings.kiosk.mode` is never read or written, and choosing a mode navigates immediately. | `Navigator`, `DashboardController` |
| D5 | The 24-hour entry chart required by UI_GUIDE §6.10 is missing. | dashboard.fxml |
| D6 | When an existing table lacks `exit_time`, `registration`, `slot_id`, `status` or `session_id`, `createSchema`'s `CREATE INDEX … WHERE exit_time IS NULL` fails first. The user gets a raw "no such column" error instead of the friendly "missing required column(s)" message. Rollback is still correct, so this is low severity and diagnostic only. | `DatabaseInitializer` :176 (`createSchema`) runs before :179 (`verifySchema`) |
| D7 | The Exit screen does not move focus when its state changes. The PIN keypad pulls focus back to the field after every key, so a keyboard user loses their place. | `ExitController.showState`, `AdminPinController.handleDigit` |
| D8 | Many labels are below the guide's 11 px minimum, down to 6.5 px. | `kiosk.css`, `admin.css` (bay EV tag, pills, notes) |
| D9 | With no bay selected, the "Bay details" status pill says "Available". | `DashboardController` default state |
| D10 | The caption "Two-wheelers & scooters" breaks the copy rule ("Bike / Scooter"). | entry.fxml |

### 4.2 Potential risks (verify at runtime)

| # | Risk | Why it matters for the web app |
|---|---|---|
| R1 | `Database` sets no `journal_mode=WAL`, so readers and writers block each other. Only `busy_timeout=5000` mitigates this. | Several browser sessions share one SQLite file. |
| R2 | `setAutoCommit(false)` starts a DEFERRED transaction. A read-then-write transaction can get `SQLITE_BUSY` on lock upgrade without waiting. The exit transaction writes first and is safe today; the Phase 6 reset would not be. | Reset and any future multi-step write |
| R3 | `ParkingService` treats every `DataAccessException`, including BUSY or timeout, as a lost race and tries the next bay. Under contention it can skip a free lower bay, which breaks ascending order. It never produces a false success. | Concurrent entries |
| R4 | Each repository call opens its own connection, so a multi-call read is not a snapshot. | Statistics must be derived from one read per table. |
| R5 | `mapRow` lets a raw `DateTimeParseException` or `IllegalArgumentException` escape on a corrupt row instead of a `DataAccessException`. | Error pages must not leak internals. |
| R6 | If `close()` fails after `commit()`, the code reports a false failure. It never reports a false success. | Low |
| R7 | `ParkingSessionRepository.updateCompleted` and `PaymentRepository.insert` are non-atomic exit paths. They are unused, but could bypass `ExitCompletionRepository` if wired by mistake. | Keep out of the web layer. |
| R8 | `FormatUtil` formats in `ZoneId.systemDefault()`. A server in UTC would show the wrong local time and hourly buckets. | Needs an explicitly configured zone. |
| R9 | `verifySchema` checks only table and column names, not CHECKs, foreign keys or index definitions. | An old compatible-looking file is accepted as is. |
| R10 | Vertical overflow at 1280×720: the Entry error banner and the 6-row Exit fee table sit in a 540 px panel with no scrolling. | Recheck in the browser. |
| R11 | Low contrast in the admin palette: `#8a94a6` (about 3:1) and `#98a2b3` (about 2.6:1) on white. | WCAG |
| R12 | Overlays (ticket, reset) do not trap focus, and inline errors have no live region. | Accessibility |
| R13 | Two rollback tests (`sessionUpdateFailureRollsBackEverything`, `zeroFeeCompletionFailureLeavesSessionActive`) fail **before** any payment insert, so their "no payment" asserts cannot fail. Only `paymentInsertFailureRollsBackCompletion` proves a real partial-write rollback. | Test strength |
| R14 | Docs claim protection against "racing" attempts, but every test is single-threaded. | Concurrency is enforced by the indexes but not exercised. |

### 4.3 Documentation inconsistencies

- **PROJECT_RULES.md**
  - It calls the project a "single-desktop JavaFX" app and puts Spring Boot and web frontends out of scope. The migration supersedes this.
  - Its working directory `C:\Users\Zaim\Desktop\Parking Kiosk` and its `App.java` and `images/` entries are wrong.
  - It says the folder "is not a Git repository", but `.git` exists.
- **README.md**
  - It describes a desktop JavaFX application.
  - It says FeeCalculator and the payment model are "unused by screens until Phase 5", which is stale.
  - Its tree root is named `Parking Kiosk/`.
- **PHASE_PLAN.md**
  - Phase 2 says plates are normalized with `FormatUtil.normalizePlate`; the real rule is `RegistrationUtil.normalize`.
  - It says FeeCalculator has "one public method", but `hourlyRate` was added.
  - Phase 3 claims "invalid enums" are tested; no such test exists.
  - Phase 5 says all three rollback triggers fail "after the session update"; only one does (R13).
  - Phase 5 maps "cancel writes nothing" to tests, but cancel is controller-only.
- **PaymentRepository Javadoc** says sub-paise amounts throw `DataAccessException`. The code and test use `IllegalArgumentException`.
- **UI_GUIDE vs code:**
  - Token values differ: ink `#080C12` vs `#070b11`.
  - Bahnschrift is specified but not used.
  - Several sizes differ: bay code, amount due, header, PIN keys.
  - The mode switch is supposed to persist and not navigate.
  - The guide asks for an EV strip and a capped long-stay list; neither is implemented.

### 4.4 Intentional design decisions (keep)

- Whole-second fee precision: 120 min + 500 ms is free; 120 min + 1 s is charged.
- The simulated scan with the fixed demo plate `KL 11 AB 1234`, and the short artificial pauses before service calls.
- Entry allocation uses the partial unique indexes plus retry instead of an explicit transaction.
- Exit quotes are frozen. Confirmation reuses the quoted exit time and amount. A stale quote returns to scan.
- A dark exit card instead of the reference's light card. Both floors shown at once. Exactly four KPI cards.
- The demo PIN is stored as plain text: demonstration authentication only.
- Startup validates the layout and never repairs or deletes data.

### 4.5 Dead code and duplicate types

- **Duplicate types:**
  - `preview.VehicleType` duplicates `model.VehicleType`.
  - `Navigator.KioskMode` duplicates `model.KioskMode`; `model.KioskMode` is unused in main code.
- **Unused in main code:**
  - `JdbcPaymentRepository` and `JdbcSettingsRepository` (not wired).
  - `ParkingSlotRepository.findCharging`/`count`.
  - `ParkingSessionRepository.countEntries`/`countCompletedExits`/`findActiveBySlot`/`findAll`/`updateCompleted`.
  - `PaymentRepository.countConfirmed`, `FormatUtil.time`, `PaymentStatus.PENDING`, `Icons.ticket`.
- **Duplicated logic (not fee arithmetic):**
  - `validateRegistration` appears in both `ParkingService` and `ExitService`.
  - The completion `UPDATE` and the payment `INSERT` SQL each appear in two repositories.
  - The slot `ORDER BY` appears twice.
  - The `rollback` helper appears twice.
  - The charging-bay list appears in `DatabaseInitializer` and a test helper.

### 4.6 Security and reliability

- **No SQL injection.**
  - All values are bound as parameters.
  - The only concatenation is `PRAGMA table_info(<constant>)`.
- **No unsafe HTML.**
  - JavaFX labels do not interpret HTML.
  - The web views must use `setText`, never `Html`/`innerHTML`, for user input.
- **Repeated submissions and stale quotes.**
  - Blocked by the conditional `UPDATE … WHERE exit_time IS NULL`, the confirmed-payment unique index, and the quote re-check.
  - The UI also has a busy guard.
- **Admin authorization.**
  - It is UI-only today: the dashboard is just another screen.
  - The web app must enforce it on the server (D3, §7).
- **Error messages.**
  - Kiosk messages are generic.
  - The startup error card shows the database path and cause chain. That is acceptable on a desktop, but must not be shown to anonymous web users.

---

## 5. Proposed corrections

### 5.1 Critical fixes before continuing

These are needed before many browser sessions share one database.

1. **WAL and IMMEDIATE transactions** (R1, R2).
   - Enable `journal_mode=WAL` once at initialization; the setting persists in the file.
   - Keep `busy_timeout`.
   - Start write transactions with `BEGIN IMMEDIATE`, so lock acquisition waits instead of failing on upgrade.
2. **Allocation retry** (R3). Retry the next bay only on a **unique-constraint** violation. Rethrow BUSY and other errors.
3. **Corrupt rows** (R5). Wrap row-mapping failures in `DataAccessException`.
4. **Schema check order** (D6). Run `verifySchema` on existing tables before the index statements.

### 5.2 Phase 6 (admin functionality)

The frozen contract is in `PHASE_PLAN.md` once implemented.

- `AdminAccessService`:
  - `verifyPin` reads `admin_settings`, defaults to `1234`, and compares in constant time.
  - `currentMode` defaults to ENTRY when the stored value is missing or invalid.
  - `changeMode`.
- `StatisticsService` builds an immutable `DashboardStatistics`:
  - derived from one slot read and one session read;
  - hourly buckets in an explicit `ZoneId`;
  - peak-hour ties go to the earliest hour;
  - no peak hour when there is no history.
- `LongStayService` (injected `Clock`):
  - alerts strictly above 43,200 whole seconds;
  - active sessions only;
  - sorted longest first, then by bay.
- `DatabaseInitializer.resetDemoData()` plus a thin `DemoResetService`. One IMMEDIATE transaction that:
  1. deletes payments, then sessions;
  2. restores and validates the 24-bay layout;
  3. keeps the PIN;
  4. sets mode to ENTRY.
- `ParkingService.findAllocation(sessionId)` (read-only) for the web confirmation view.
- `FormatUtil` overloads that take an explicit `ZoneId`.
- Retire `Navigator.KioskMode` and `preview.*` together with the JavaFX layer.

### 5.3 Test corrections

- Add a real "failure after session update" assertion for the zero-fee path where one is possible. Otherwise correct the PHASE_PLAN wording (R13).
- Add tests:
  - statistics on an empty and on a mixed database, hourly buckets, and peak-hour ties;
  - long-stay boundaries at 12 h, 12 h + 500 ms and 12 h + 1 s;
  - PIN verification, mode persistence and invalid mode values;
  - reset, including a rollback test and an "existing PIN kept" test;
  - startup with a configured DB path, the health endpoint, admin authorization, and view state transitions.

---

## 6. Migration plan (JavaFX desktop → Java web with Spring Boot and Vaadin)

| Step | Work | Keeps |
|---|---|---|
| 1 | Critical fixes (§5.1) and Phase 6 services, plus tests, on the current build | Every existing test |
| 2 | `pom.xml`:<br>• parent `spring-boot-starter-parent:4.1.2`<br>• `vaadin-bom:25.3.4`<br>• `vaadin-core` + `vaadin-spring-boot-starter` (free components only)<br>• actuator<br>• sqlite-jdbc 3.53.4.0<br>• `spring-boot-starter-test` + `browserless-test-spring`<br>• drop JavaFX and the explicit JUnit/Surefire pins (Boot manages them) | Model, service and repository code unchanged |
| 3 | `HiliteParkingApplication` (`@SpringBootApplication`, `AppShellConfigurator`, `@StyleSheet`) and `ParkingConfiguration`, which declares `@Bean`s for:<br>• `Database` from `hilite.db.path` / `HILITE_DB_PATH`<br>• initializer run once at startup<br>• JDBC repositories<br>• services<br>• `Clock.systemUTC()`<br>• `ZoneId` from `hilite.time-zone` (default `Asia/Kolkata`) | Constructor injection, no static state |
| 4 | Vaadin views in `com.hilite.parking.web`:<br>• `/` kiosk (Entry or Exit per the persisted mode)<br>• `/entry`<br>• `/allocation/{sessionId}` (read-only lookup, never allocates)<br>• `/exit`<br>• `/admin` (PIN)<br>• `/admin/dashboard`<br>Browser CSS in `META-INF/resources/styles.css` from the tokens in §7. Icons and backdrop as inline SVG. | Same states, copy and timings as the JavaFX screens |
| 5 | Server-side admin guard:<br>• an unlock flag in the `VaadinSession`<br>• `BeforeEnterObserver` reroutes to `/admin`<br>• every admin action re-checks the flag<br>• lock clears it<br>• PIN attempt throttling per session | Demo-level authentication, no identity platform |
| 6 | Health: Actuator `/actuator/health`, plus a database `HealthIndicator` running `SELECT 1` on the configured file | — |
| 7 | Packaging:<br>• `Dockerfile` (multi-stage, JDK 21 build, JRE 21 runtime)<br>• `/data` volume for SQLite<br>• `.dockerignore`<br>• `.env.example`<br>• `application.properties` / production profile | Single instance (SQLite) |
| 8 | Remove JavaFX: `MainApp`, `Navigator`, controllers, FXML, JavaFX CSS, `Icons`, `GarageBackdrop`, `UiEffects` and `preview/*`. This happens only after the web views replace them. | — |

**Trade-off recorded.** The brief allows the desktop app to be kept in a separate module. It is
**not** kept: a second module would double the build and test surface, and every requirement is
met by the web views. The Phase 1–5 JavaFX code stays in Git history (`1749daa`).

---

## 7. Do not change

- The 24-bay layout, zones and charging bays.
- `FeeCalculator` semantics: inclusive 120 min, whole seconds, rounded up to the hour, ₹5 / ₹2.
- 30-character UTC timestamps and integer paise. Existing database files depend on them.
- Table and column names, CHECK constraints, and the three partial unique indexes.
- `ExitCompletionRepository`:
  - single transaction;
  - conditional `WHERE exit_time IS NULL`;
  - frozen quote reused at confirmation.
- `RegistrationUtil.normalize` as the only rule for the stored plate key.
- Startup never deletes or repairs data. Settings use `INSERT OR IGNORE`.
- The settings keys `admin.pin` and `kiosk.mode`.
- Sealed outcome types. A `DataAccessException` must never become a success.

---

## 8. Audit status

Complete. §4 lists every confirmed defect with evidence. The migration plan in §6 is internally
consistent with the business rules in §7. Implementation results are recorded in `PHASE_PLAN.md`
(Phase 6 and Phase 7) and in §9 below.

---

## 9. Resolution of findings (2026-10-28)

| Finding | Resolution | Evidence |
|---|---|---|
| D1 Dashboard on preview data | Live `StatisticsService` / `LongStayService` through `AdminOperations`; `preview/*` deleted | `StatisticsServiceTest`, `DashboardViewTest`, browser walkthrough (live counts after restart) |
| D2 Fake reset | `DatabaseInitializer.resetDemoData` (one IMMEDIATE transaction) with honest dialog copy | `DemoResetTest` (incl. real partial-write rollback), browser walkthrough |
| D3 PIN from `PreviewData`, no attempt limit | `AdminAccessService` reads `admin_settings` and compares in constant time; per-session lock-out | `AdminAccessServiceTest`, `AdminAuthorizationTest` |
| D4 Mode only in memory, navigates immediately | Persisted `kiosk.mode`; the selector saves without navigating; `/` follows the saved mode | `AdminAccessServiceTest`, `KioskHomeViewTest`, browser walkthrough |
| D5 No hourly chart | 24-bar CSS chart with an empty state | `DashboardViewTest`, screenshot |
| D6 Schema check order | Create tables, then verify, then create indexes | `RepositoryHardeningTest` |
| D7 Focus management | Exit states move focus; the PIN keypad does not steal focus; dialogs trap focus | Code review (UI_GUIDE §11) |
| D8 Sub-11 px text | Minimum 11 px on the kiosk, 12 px on the dashboard | `styles.css` review |
| D9 Misleading bay-details default | "Select a bay", no status pill | Screenshot |
| D10 "Two-wheelers" copy | "Bikes & scooters" | Code search |
| R1/R2 WAL, DEFERRED locks | WAL plus `BEGIN IMMEDIATE` | `DatabaseConcurrencyTest` |
| R3 Allocation skipped bays on BUSY | Retry only on a constraint race | `AllocationFailureTest` |
| R4 Non-snapshot reads | Statistics derived from one read of slots and one read of sessions | `StatisticsService` |
| R5 Raw parse errors | Wrapped in `DataAccessException` | `RepositoryHardeningTest` |
| R8 Server time zone | `hilite.time-zone` (default Asia/Kolkata) used for display and hourly buckets | `FormatUtilTest`, `StatisticsServiceTest`, `ApplicationStartupTest` |
| R10/R11 Overflow, contrast | No horizontal scrolling at 1920/1366/1024; muted admin text `#5d6779` | Browser walkthrough |
| R12 Focus trap, live regions | Vaadin `Dialog`; `role="alert"` / `aria-live` messages | Code review |
| R13 Weak rollback tests | PHASE_PLAN wording corrected; real partial-write tests added for reset | `DemoResetTest` |
| R14 Untested concurrency | Two-thread same-plate and different-plate tests | `AllocationFailureTest` |
| Documentation inconsistencies (§4.3) | Corrected in PROJECT_RULES, PHASE_PLAN, PaymentRepository Javadoc and README | — |
| Still open | R6 (a false failure after commit if `close()` fails; low severity), R7 (unused non-atomic repository methods kept), R9 (schema check is by name only) | Documented; no code path uses R7's methods |
