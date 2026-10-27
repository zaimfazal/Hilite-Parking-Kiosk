# HiLITE Mall Smart Parking — Phase Plan

Status legend: `Complete` = implemented and verified · `Not started` · `Blocked` (reason required).
This file is updated at the end of every phase with real results.

---

## Environment baseline (audited 2026-10-24)

| Item | Observed |
|---|---|
| OS | Windows 11 Home Single Language `10.0.26300` (amd64) |
| Display | 1920 × 1080, single primary display |
| Java | OpenJDK 21.0.11 (JetBrains Runtime JBR 21.0.11+1-1163.116) |
| `JAVA_HOME` | `C:\Users\Zaim\.jdks\jbr-21.0.11` |
| JavaFX in JDK | Not bundled — Maven must supply JavaFX 21 |
| Maven | Apache Maven 3.9.16 |
| Git | 2.53.0.windows.2; directory is **not** a Git repo |
| Locale / encoding | `en_GB` / UTF-8 |
| Maven Central | Reachable (HTTP 200 for `javafx-controls` POM) |
| Local Maven cache | OpenJFX 21.0.5 (Phase 1), JUnit 5.11.4 + Surefire 3.5.2 (Phase 2), SQLite JDBC 3.53.4.0 (Phase 3) |
| Free disk | ~135 GB on C: |
| Project contents at Phase 0 | `Design References/` only; no `pom.xml`, no source |

**Design references present** (all five, under `Design References/`; note the folder/filenames
differ from the paths suggested in the master prompt — no reference is missing):

| Prompt path | Actual file | Used for |
|---|---|---|
| `entry-reference.png` | `HiLITE Mall Smart Parking Kiosk.png` | Entry kiosk |
| `allocation-reference.png` | `Smart Parking Bay Allocation.png` | Allocation confirmation |
| `exit-reference.png` | `Smart Parking Exit Kiosk Interface.png` | Exit kiosk |
| `admin-pin-reference.png` | `HiLITE Mall Parking Admin Login.png` | Admin PIN |
| `admin-dashboard-reference.png` | `HiLITE Mall Parking Dashboard.png` | Admin dashboard |

**First-run prerequisite:** the first Maven build needs internet access to download JavaFX 21
and (from Phase 2) JUnit 5.11.4 artifacts. SQLite JDBC will be added in Phase 3. If the
demonstration machine is offline, run one online `mvn -q dependency:go-offline` (or the first
`mvn verify`) before the demo.

---

## Phase 0 — Environment audit, rules, documentation

**Status: Complete (2026-10-24)**

Deliverables:

- `PROJECT_RULES.md` — stack, phase protocol, architecture, frozen rules, quality gates.
- `PHASE_PLAN.md` — this file.
- `UI_GUIDE.md` — visual design system derived from the five inspected references.
- `README.md` — project overview, prerequisites, planned run/test commands, demo caveats.
- `.gitignore` — build output, databases, logs, local config, IDE noise.

Acceptance:

- [x] Environment audited with real commands; versions recorded.
- [x] All five design references located and visually inspected.
- [x] Fixed requirements, architecture, and UI direction documented.
- [x] Missing prerequisites identified (JavaFX/SQLite artifacts not cached; no Git repo).
- [x] No application implementation created in this phase.

Phase report: delivered in chat, including actual command output and unresolved items.

---

## Phase 1 — JavaFX shell and visual design system

**Status: Complete (2026-10-25) · Depends on: Phase 0**

Scope:

- Create `pom.xml` with pinned Java 21 / JavaFX 21 versions, `javafx-maven-plugin`, and a
  non-modular launch path. SQLite JDBC and JUnit 5 are deliberately deferred to the phases
  that need them (2 and 3).
- `MainApp.java`, a `Navigator` screen host, and five FXML screens + controllers with real
  navigation.
- Shared CSS tokens and kiosk/dashboard stylesheets; vector/gradient background fallback.
- All five screens visually complete using **explicitly isolated preview data**.
- Visible interaction states: vehicle type selection, charging toggle, scan animation,
  PIN keypad, mode selector, disabled/error/success samples where relevant.
- Admin access entry point: discreet lock control + `Ctrl+Shift+A`.

### Actual deliverables

- `pom.xml` — Java release 21, JavaFX **21.0.5**, `javafx-maven-plugin` **0.0.8**,
  compiler plugin **3.13.0**. Only `javafx-controls` and `javafx-fxml` dependencies.
- `src/main/java/com/hilite/parking/` — `MainApp`, `Navigator`, **20 Java files** total:
  7 controllers, 7 preview records/data classes, 4 utilities.
- `src/main/resources/fxml/` — `entry.fxml`, `allocation.fxml`, `exit.fxml`,
  `admin-pin.fxml`, `dashboard.fxml`, shared `KioskHeader.fxml`.
- `src/main/resources/css/` — `app.css` (tokens/shared), `kiosk.css`, `admin.css`.
- Preview data isolated under `com.hilite.parking.preview`; `PreviewData` is the single
  source of sample values, including `ADMIN_PIN = "1234"` (never displayed).
- Hand-built vector icons (`Icons`) and a resize-aware `Canvas` garage scene
  (`GarageBackdrop`) replace unavailable photography. No runtime images or remote assets.
- Responsive `compact` / `wide` root style classes; app verified at 1600 × 900 and
  1366 × 768.

### Verified acceptance

- [x] `mvn -B clean verify` → **BUILD SUCCESS** (20 sources, no test sources yet, 3.3 s).
- [x] `mvn javafx:run` launches the real application on the audited machine.
- [x] All five views reachable; every button/selector responds visually.
- [x] Kiosk and admin design languages match `UI_GUIDE.md` and the references.
- [x] No full-screen mockup used as UI; no remote or bundled photo assets.
- [x] Preview data isolated in the preview package; previews are clearly labelled in the UI.
- [x] Exit workflow's four sequential states reachable: scan → details → payment → success.
- [x] Admin PIN accepts `1234`, rejects a wrong PIN with inline feedback, and never shows the
      PIN. `Ctrl+Shift+A` was exercised with the real keyboard.
- [x] Dashboard kiosk-mode switches (Entry/Exit), bay selection, and Reset dialog behave as
      UI-only previews.
- [x] Layout checked at 1600 × 900 and 1366 × 768 with 100% crops; no unintended text
      clipping in the primary layouts. The compact dashboard scrolls vertically for its lower
      cards.

Verification evidence:

- A temporary scripted UI walkthrough (deleted before delivery) drove the real screen
  controllers with `ButtonBase.fire()` and captured 15 window states, including both window
  sizes. All 15 were reviewed. No "button not found" or load exceptions remained.
- The final deliverable was then relaunched with `mvn javafx:run`; Entry opened cleanly, and
  the admin path was repeated with real keyboard input to the dashboard.
- The temporary verification classes (`VisualHarness`, `HarnessLauncher`) are **not** part of
  the delivered source tree.

### Defects found and fixed during verification

- JavaFX FXML `styleClass` lists must be comma-separated; all views were corrected.
- `GarageBackdrop` was rewritten as a resizable `StackPane` containing a `Canvas`.
- Missing `fx:id` bindings for the allocation success badge and direction icon.
- Ticket overlay and glass panels stretched to the full viewport; capped with
  `maxHeight="-Infinity"` where appropriate.
- PIN keypad keys and shield badge now fill/size correctly.
- PIN error was immediately hidden by the field's change listener; the unlock error path was
  reordered so the message stays visible.
- Exit details **Back** icon changed from up-arrow to a real left arrow.
- Compact dashboard bay tiles no longer truncate the bay id or secondary metadata; the zone
  and a shortened vehicle-type label fit within each tile.
- Preview consistency: the charging allocation uses F1-05, so the dashboard preview now
  marks F1-05 free and shows its occupied EV sample at G-03 (counts unchanged at
  9 occupied / 15 available, 1 / 4 EV bays in use).

### Deviations / notes

- No SQLite JDBC or JUnit dependency is present yet. Phase 1 has no database or business
  logic, and the project rule forbids unused dependencies. These are added in Phases 3 and 2
  respectively.
- `mvn verify` runs **no tests** in Phase 1 ("No tests to run"); that is expected, not a
  passing test suite.
- Preview data is a static snapshot, not a simulated timeline: interactions do not update
  the dashboard. This is documented in the README and in the UI copy.
- The main window is 1600 × 900 (design size) with a 1280 × 720 minimum; the OS window chrome
  reduces the scene to roughly 1584 × 861 on the audited machine.
- Phase 1 stopped here; Phase 2 began only on explicit request.


---

## Phase 2 — Domain models and fee logic

**Status: Complete (2026-10-25) · Depends on: Phase 1**

Scope (as planned):

- `model`: domain types for vehicle, floor, zone, slot, session, payment; immutable records
  where practical.
- `service/FeeCalculator` with `BigDecimal` money and frozen rounding rules.
- Registration normalization utility (`normalizePlate`) used by all layers.
- `util` formatters for ₹ amounts, timestamps, durations (shared with UI).
- Unit tests for fee rules and model validation.

### Actual deliverables

- **Domain package** `com.hilite.parking.model` — 8 files:
  `VehicleType`, `KioskMode`, `Floor`, `Zone`, `ParkingSlot`, `ParkingSession`,
  `PaymentStatus`, `Payment`.
- **Service** `com.hilite.parking.service.FeeCalculator` — one public method:
  `BigDecimal calculateFee(VehicleType, Instant, Instant)`.
- **Tests** under `src/test/java` — 4 classes, **68 JUnit 5 tests**:
  `FeeCalculatorTest` (26), `ParkingSessionTest` (18), `PaymentTest` (13),
  `ParkingSlotTest` (11).
- `pom.xml` — added JUnit Jupiter **5.11.4** (test scope) and Maven Surefire **3.5.2**.
  JavaFX 21.0.5 / plugin versions unchanged.
- Phase 1 preview, controller, FXML, CSS, and utility files were not modified.

### Public model contracts

- `ParkingSlot(slotId, floor, bayNumber, zone, charging)` uses `Floor`/`Zone` enums and has
  **no occupancy field**; occupancy will be derived from active sessions.
- `ParkingSession(sessionId, registration, vehicleType, chargingRequested, slotId, entryTime,
  exitTime, finalFee)` uses UTC `Instant` timestamps and `BigDecimal` rupees. Active = both
  `exitTime` and `finalFee` null; completed = both present. Registration is expected to be
  normalized by the caller with `FormatUtil.normalizePlate`.
- `Payment(paymentId, sessionId, amount, status, confirmedAt)` uses `PaymentStatus`
  (`PENDING`, `CONFIRMED`); `CONFIRMED` requires a timestamp and `PENDING` forbids one.
- `KioskMode` (`ENTRY`, `EXIT`) and `VehicleType` (`CAR`, `BIKE`) are the business enums.
  The Phase 1 UI still uses `Navigator.KioskMode` and `preview.VehicleType`; the duplicates
  are intentional until the controllers are wired in later phases.

### Fee rules and tests

`FeeCalculator` implements: first 120 elapsed minutes free (inclusive); any positive
remainder rounded up to whole hours; car ₹5/hour; bike ₹2/hour; `BigDecimal` money; no
system clock, JavaFX, SQLite, or preview data.

Required cases, all passing:

| Elapsed | Car | Bike |
|---|---:|---:|
| 0 | ₹0 | ₹0 |
| 1 h 30 min | ₹0 | ₹0 |
| exactly 120 min | ₹0 | ₹0 |
| 120 min + 1 s | ₹5 | ₹2 |
| 2 h 00 min | ₹0 | ₹0 |
| 2 h 20 min | ₹5 | ₹2 |
| 3 h 00 min | ₹5 | ₹2 |
| 3 h 10 min | ₹10 | ₹4 |
| 4 h 24 min | ₹15 | ₹6 |
| exit before entry | rejected (`IllegalArgumentException`) | same |
| null inputs | rejected (`NullPointerException`) | same |

Additional tests cover 120 min + 500 ms (whole-second truncation → ₹0), 24 h (22 chargeable
hours → ₹110 car / ₹44 bike), and model validation for blank IDs, null values, negative
money, and inconsistent exit/fee or payment-status timestamps.

### Verified acceptance

- [x] All fee tests pass with no JavaFX, JDBC, or database dependency.
- [x] Fee calculator is the only place fee arithmetic exists. A static audit found no rate
      or 3600-second arithmetic elsewhere in `src/main/java`.
- [x] Time boundaries use whole elapsed seconds from `Duration.toSeconds()`, not truncated
      clock hours or minutes. Sub-second remainders are truncated before the free allowance
      and this is documented in the class and covered by a test.
- [x] Domain models validate required identifiers, nulls, negative money, and timestamp
      ordering.
- [x] Existing JavaFX application still launches and Phase 1 navigation/admin access still
      work.

Verification evidence (actual commands and results):

- `mvn -B clean verify` → **BUILD SUCCESS**; 29 main sources + 4 test sources;
  **Tests run: 68, Failures: 0, Errors: 0, Skipped: 0**; total time 5.253 s on the first
  integration run and 4.465 s on the final clean run.
- `mvn -B test -Dtest=FeeCalculatorTest` → **BUILD SUCCESS**;
  **Tests run: 26, Failures: 0, Errors: 0, Skipped: 0**.
- `mvn javafx:run` → window **HiLITE Mall Smart Parking** opened on the Entry kiosk with no
  console errors. The lock control / `Ctrl+Shift+A` path and PIN `1234` opened the dashboard.
- No database file or `data/` folder was created.

### Multi-agent workstreams actually used

- **Lead**: sourced the requirements, inspected the Phase 1 tree, froze the package/API
  contracts, owned `pom.xml`, integrated all changes, ran the build/tests/launch, inspected
  the screenshots, and updated this plan and the README.
- **Agent A**: implemented the eight `model` files under exclusive ownership.
- **Agent B**: implemented `service/FeeCalculator` under exclusive ownership.
- **Agent C**: wrote the four permanent JUnit test classes under exclusive ownership.
- The three agents ran in parallel after the API was frozen; no two agents edited the same
  file. The lead inspected every integrated file before building.

### Deviations / notes

- `AdminStatistics` and `LongStayAlert` were **not** added. They belong to Phase 6, where the
  statistics and long-stay services exist; adding empty models now would be premature.
- No `SessionStatus` enum was added. Active/completed is derived from `exitTime`/`finalFee`,
  avoiding two sources of truth.
- `FeeCalculator` has no `Clock` field. It is a pure function of explicit entry/exit
  instants, as required by the Phase 2 prompt. The Phase 5 exit workflow will inject a
  `Clock` to obtain the quote time and will call this calculator.
- Whole-second precision was chosen (the recommended option) rather than nanosecond billing;
  a stay of 120 min + 500 ms is deliberately treated as exactly 120 min and is free.
- `Floor` and `Zone` enums were added to keep slot data typed, matching the Phase 2 plan.
- The two Phase 1 UI enums (`Navigator.KioskMode`, `preview.VehicleType`) remain untouched;
  unifying them is integration work for Phases 4–6.
- Phase 2 stopped here; Phase 3 began only on explicit request.

---

## Phase 3 — SQLite schema, repositories, initialization

**Status: Complete (2026-10-26) · Depends on: Phase 2**

Scope (as planned):

- `Database`/`DatabaseInitializer`: `data/hilite-parking.db`, `PRAGMA foreign_keys=ON`,
  idempotent schema, seed 24 bays + 4 charging flags + admin settings.
- Tables: `parking_slots`, `parking_sessions`, `payments`, `admin_settings`; FKs, checks,
  partial unique indexes for one active session per plate and per bay.
- Repositories: slot, session, payment, settings. Prepared statements, explicit transactions.
- Test infrastructure: temp database per test via `@TempDir`; test clock.
- PIN default seeded as `admin_settings('admin.pin','1234')` (demo only).
- Kiosk mode stored in `admin_settings` (default `ENTRY`), changed only via admin action.

### Actual deliverables

- **Dependency:** Xerial `org.xerial:sqlite-jdbc:3.53.4.0`, explicitly pinned. JavaFX 21.0.5,
  JUnit 5.11.4, Surefire 3.5.2 and the compiler/JavaFX plugin versions are unchanged.
- **Connection layer:** `repository.Database` opens a fresh connection per call and applies
  `PRAGMA foreign_keys = ON` plus `PRAGMA busy_timeout = 5000`. It reads the pragma back and
  refuses to hand out a connection if foreign keys are not actually active.
- **Initialization:** `repository.DatabaseInitializer` creates the parent directory, then runs
  schema creation, schema verification (`PRAGMA table_info`), layout seeding/validation, and
  settings seeding in **one transaction**. A failure rolls everything back. Re-running is
  non-destructive.
- **Repositories** (interfaces in `repository`, JDBC implementations in `repository.jdbc`):
  - `ParkingSlotRepository`: `findById`, `findAll` (Ground then First, ascending bay),
    `findCharging`, `count`.
  - `ParkingSessionRepository`: `insertActive`, `findActiveByRegistration`, `findById`,
    `findActive`, `findAll` (entry-time order), `updateCompleted`, `countEntries`,
    `countCompletedExits`, `findActiveBySlot`.
  - `PaymentRepository`: `insert`, `findBySessionId`, `findAll`, `countConfirmed`.
  - `SettingsRepository`: `findValue`, `findAll`, `saveValue`; keys `admin.pin` and
    `kiosk.mode`.
- **Shared helpers:** `repository.DataAccessException` wraps `SQLException` without losing the
  cause; `repository.jdbc.SqlCodec` is the only timestamp/money converter;
  `util.RegistrationUtil` is the single persisted registration rule.
- **No UI wiring:** `MainApp`, `Navigator`, every controller, FXML, CSS and preview class were
  left untouched. No `data/` directory or database file exists after a normal app launch.

### Schema

| Table | Key constraints |
|---|---|
| `parking_slots` | 24 rows; PK `slot_id`; unique `(floor, bay_number)`; floor/zone enums; `charging` 0/1; zone must match bay 01–06 = A / 07–12 = B |
| `parking_sessions` | PK `session_id`; FK `slot_id`; active means both `exit_time` and `final_fee_paise` null; paired-nullability CHECK; exit ≥ entry; partial unique indexes on active registration and active slot |
| `payments` | PK `payment_id`; FK `session_id`; `amount_paise >= 0`; `PENDING`/`CONFIRMED` CHECK; CONFIRMED requires `confirmed_at`, PENDING forbids it; partial unique index allows one confirmed payment per session |
| `admin_settings` | PK `setting_key`; non-blank key; non-null value; defaults seeded with `INSERT OR IGNORE` |

**Storage policy:** timestamps are ISO-8601 UTC text with exactly nine fractional digits
(30 characters, e.g. `2026-10-25T08:00:00.000000000Z`). Money is integer paise; a `BigDecimal`
with fractional paise is rejected by `SqlCodec.toPaise` rather than rounded. Fee reads return
`BigDecimal` with scale 2, so callers compare with `compareTo`.

**Seed:** `G-01`…`G-12` then `F1-01`…`F1-12`; Zone A for 01–06 and B for 07–12 on each floor;
charging only for `G-03`, `G-08`, `F1-05`, `F1-12`. An existing non-empty layout is compared
with this exact list; a mismatch throws `DataAccessException` and leaves every row untouched.

### Tests and acceptance

New test classes (38 tests, all using a fresh `@TempDir` SQLite file; never
`data/hilite-parking.db`):

| Class | Tests | Protects |
|---|---:|---|
| `repository.DatabaseInitializerTest` | 10 | tables/indexes, 24 slots, 4 charging bays, mapping, idempotent re-init, restart persistence, invalid-layout failure, transactional rollback, FK pragma, temp isolation |
| `repository.ParkingSlotRepositoryTest` | 4 | deterministic ordering, enum/int mapping, charging list, count/lookup |
| `repository.ParkingSessionRepositoryTest` | 12 | all lookups, entry order, counts, duplicate plate/bay rejection, freed bay after completion, single completion, input validation, DB CHECKs, FK, stored timestamp, restart |
| `repository.PaymentRepositoryTest` | 8 | empty counts, ordering, confirmed count, paise round-trip, sub-paise rejection, FK, status/timestamp CHECKs, one confirmed payment, restart |
| `util.RegistrationUtilTest` | 4 | separator/case normalization, empty rejection, demo plate validity |
| **Total new** | **38** | |

All Phase 2 tests (68) are unchanged. Combined suite: **106 tests**.

Acceptance evidence:

- [x] Exactly 24 slots; `G-01`…`G-12`, `F1-01`…`F1-12`; exactly 4 charging bays.
- [x] Zones: 01–06 = A, 07–12 = B per floor.
- [x] Re-running initialization keeps existing sessions, payments, and changed settings.
- [x] Reopening the same DB file preserves sessions, payments, and fees.
- [x] Duplicate active plate rejected by a partial unique index (`DataAccessException`).
- [x] Duplicate active bay assignment rejected by a partial unique index.
- [x] Completed sessions do not block a later session for the same plate/bay.
- [x] FK violations, invalid session null pairings, invalid payment status/timestamp values,
      invalid enums, and sub-paise amounts are rejected.
- [x] Tests never touch `data/hilite-parking.db`; no `data/` folder was created.
- [x] `MainApp` does not initialize the database and no controller imports SQL.
- [x] Database initialization never drops, truncates, or resets existing records.

Verification evidence:

- `mvn -B clean verify` → **BUILD SUCCESS**; 42 main source files + 10 test source files;
  **Tests run: 106, Failures: 0, Errors: 0, Skipped: 0**; total time 9.616 s on the final
  clean run (14.349 s on the first run, which included the one-time SQLite JDBC download).
- `mvn javafx:run` → window **HiLITE Mall Smart Parking** opened on the Entry kiosk at
  1600 × 900 with a live clock; no exception in the console; no `data/` directory created. The
  app was closed cleanly and Maven finished with `BUILD SUCCESS` (2:00 min run).
- Navigation was re-verified by driving the real `Navigator` and PIN gate from a throwaway
  harness outside the project tree: entry, exit, allocation, admin PIN, dashboard, return to
  kiosk, and `openAdminIfKiosk` all load with three stylesheets; a wrong PIN stays on the PIN
  screen; PIN `1234` opens the dashboard. Click/key injection was unavailable in the automation
  session, so this replaced manual clicking; no UI file was modified.

### Multi-agent workstreams actually used

- **Lead**: inspected Phase 2, froze the schema and repository interfaces, wrote the
  interfaces, `DataAccessException`, `RegistrationUtil`, `pom.xml` changes; reviewed all
  integrated code; ran the build, tests, and UI launch; updated this plan and the README.
- **Agent A**: `Database` and `DatabaseInitializer` (schema, seeding, validation, transaction).
- **Agent B**: `SqlCodec` and the four JDBC repository implementations.
- **Agent C**: the four persistence test classes, the shared test fixture, and
  `RegistrationUtilTest`.
- The three agents had exclusive file ownership and ran in parallel after the contracts were
  frozen. No two agents edited the same file; the lead inspected every file before building.

### Deviations / notes

- No `data/` folder is created in Phase 3. The initializer is implemented and tested but is not
  called at application startup because the UI must remain disconnected until Phase 4.
- The schema uses fixed-width UTC text (nine fractional digits) so lexical ordering in SQLite
  matches chronological ordering.
- Fee and payment money is stored as integer paise, not `REAL`. Fractional paise are rejected.
- A partial unique index on confirmed payments was added so a session can never be
  double-charged, even though the payment workflow itself belongs to Phase 5.
- `RegistrationUtil` normalizes persisted plates by removing spaces and hyphens.
  `FormatUtil.normalizePlate` remains display-only so the Phase 1 UI output is unchanged.
- Kiosk mode is seeded but never read or written by the UI in this phase. The
  `admin_settings` row is ready for Phase 6.
- No admin reset action, allocation service, exit service, or dashboard wiring was added.
- Phase 3 stopped there. Phase 4 began only on explicit request.

---

## Phase 4 — Vehicle entry, allocation, confirmation

**Status: Complete (2026-10-26) · Depends on: Phase 3**

Scope (as planned):

- Allocation service: validation, normalized duplicate check, deterministic bay choice,
  typed failure results, persisted active session.
- Entry controller wired to the service; scanning stays simulated; plate editable; vehicle
  category + EV toggle; loading and inline error states.
- Allocation view populated from the saved session (no re-allocation); ticket preview uses
  the same saved data; Done returns to a clean Entry screen.
- Database initialization at application startup; remove preview-only entry paths.

### Actual deliverables

**Frozen service contract** (`com.hilite.parking.service`):

```java
public final class ParkingService {
    public ParkingService(ParkingSlotRepository slotRepository,
                          ParkingSessionRepository sessionRepository);
    public ParkingService(ParkingSlotRepository slotRepository,
                          ParkingSessionRepository sessionRepository,
                          Clock clock);            // deterministic entry time for tests/demos
    public AllocationOutcome allocateParking(String registration,
                                             VehicleType vehicleType,
                                             boolean chargingRequested);
}

public sealed interface AllocationOutcome {
    enum Reason { INVALID_REGISTRATION, INVALID_REQUEST, DUPLICATE_ACTIVE_SESSION,
                  PARKING_FULL, CHARGING_UNAVAILABLE }
    record Allocated(ParkingSession session, ParkingSlot slot) implements AllocationOutcome {}
    record Rejected(Reason reason, String message) implements AllocationOutcome {}
}
```

- The result type is closed, so a caller cannot show a success screen without a saved session
  and assigned bay. `Allocated` checks in its compact constructor that the session's
  `slotId` matches the returned slot.
- Domain refusals (duplicate plate, full car park, no charging bay, bad input) are
  `Rejected` values with one short kiosk sentence. A genuine persistence failure is **not** a
  parking outcome: `DataAccessException` propagates with its cause preserved and the
  controller shows a generic error instead of a fake bay.
- Registration is normalized only by `RegistrationUtil` (authoritative persisted rule); the
  controller passes the raw field text. `FormatUtil.normalizePlate` remains display-only.
- Entry time is one `Instant.now(clock)` per allocation; sessions are stored as UTC `Instant`
  with null exit time and null final fee.

**Files added:**

| File | Purpose |
|---|---|
| `service/ParkingService.java` | Allocation use case, JavaFX-free, repositories injected |
| `service/AllocationOutcome.java` | Frozen sealed result type (`Allocated` / `Rejected`) |
| `controller/ServiceAware.java` | `setParkingService(ParkingService)` hand-off interface |
| `src/test/.../service/ParkingServiceTest.java` | 24 temporary-database allocation tests |
| `src/test/.../service/ServiceTestSupport.java` | Temp DB, fixed clock, raw-SQL trigger helpers |

**Files modified:**

- `MainApp` — startup database initialization, service construction, readable failure card,
  documented `-Dhilite.db` override.
- `Navigator` — holds the shared service, supplies it to `ServiceAware` screens,
  `showAllocation(AllocationOutcome.Allocated)`.
- `EntryController` — real allocation call, validation, guard against repeated submission,
  control restore on failure.
- `AllocationController` — maps the saved session + slot to every displayed value and to the
  ticket.
- `FormatUtil` — `dateTime(Instant)` local-zone display of stored UTC timestamps.
- `allocation.fxml` — wording only ("bay is reserved", "session is saved locally", ticket
  note). No structural, id, layout or styling change.
- `PreviewData` — `createEntrySession` removed; `preview/PreviewSession.java` deleted.
- `pom.xml`, JavaFX 21.0.5, JUnit 5.11.4, Surefire 3.5.2 and SQLite JDBC 3.53.4.0 unchanged.

### Allocation algorithm (as implemented)

1. Null vehicle type → `INVALID_REQUEST`.
2. Normalize with `RegistrationUtil`; null/blank/format failure → `INVALID_REGISTRATION`
   (no stricter rules than the existing demo plate format).
3. Existing active session for that normalized plate → `DUPLICATE_ACTIVE_SESSION`, before any
   insert.
4. Read the layout once (`findAll`: Ground before First, ascending bay) and active sessions
   once; a bay is vacant exactly when no active session references it.
5. Charging requested → vacant charging bays only, else `CHARGING_UNAVAILABLE`. Not
   requested → vacant regular bays, falling back to charging bays only when no regular bay is
   free, else `PARKING_FULL`. A slot's charging flag is never modified.
6. One entry instant and one session id; candidates are tried in layout order through
   `insertActive`.
7. If an insert throws `DataAccessException`, the plate is re-checked first (so a racing
   duplicate is reported as a duplicate, never "parking full"), then the next candidate is
   tried. If all candidates fail, occupancy is re-read: a suitable bay still free means a real
   persistence problem, so the last `DataAccessException` is rethrown rather than faking a
   full car park.
8. `Allocated` is returned only after `insertActive` returned normally. The partial unique
   indexes remain the final safeguard, and no transaction API was added.

### Startup behaviour

- `MainApp.start` resolves `Database.defaultDatabase()` (`data/hilite-parking.db`) unless the
  documented `-Dhilite.db=<path>` override is set, then calls
  `new DatabaseInitializer(database).initialize()` **once**, before any screen exists.
- Initialization seeds the 24-bay layout only when absent and never deletes sessions,
  payments or settings. Re-launching re-validates the layout without duplicating slots.
- If initialization fails, the window shows a readable card with the database path, the
  cause chain, and the fact that no session was created. The kiosk is not started and no
  preview or placeholder data is substituted.
- The service is then built from `JdbcParkingSlotRepository` and
  `JdbcParkingSessionRepository` and handed to the `Navigator`. There is no service locator,
  static singleton, DI framework, or per-action database initialization.

### UI behaviour

| Screen | Behaviour |
|---|---|
| Entry | Simulated scan (2.2 s) → plate editable; plate preview mirrors typing; Car/Bike and EV None/Yes; **Find My Parking** disables the controls, shows the in-button spinner and "Finding your bay…" during the 650 ms pause, then calls the service synchronously on the FX thread |
| Entry failure | Inline error, controls restored, no navigation: empty plate, invalid format, duplicate plate, full car park, charging unavailable, and generic "Could not save your parking session" for `DataAccessException` |
| Allocation | Floor name/code, bay id (`G-01`), registration, vehicle type, entry time (local display of the stored UTC value), charging status, directions from floor + zone, LEVEL signage, Print Parking Ticket, Done |
| Ticket | Overlay built from the same `Allocated` value; **Simulate Print** states that the ticket matches the saved session and that no physical printer was used |
| Done | `navigator.showEntry()` only. Re-rendering or revisiting the allocation screen cannot allocate, because the controller holds no service and only reads what `setAllocation` received |

Directions use the real zone: Ground "Remain on the Ground Floor and follow the Zone A/B signs
to your bay."; First "Proceed to the First Floor, follow the Zone A/B signs, and locate your
bay."

### Tests and acceptance (actual)

`service.ParkingServiceTest` adds 24 tests on fresh `@TempDir` SQLite files (never
`data/hilite-parking.db`), with a fixed `Clock` (`2026-10-26T10:15:30Z`) and raw-SQL triggers
for persistence-failure cases:

| Group | Tests | Protects |
|---|---:|---|
| Successful allocation | 6 | first vacant regular bay for car and bike, first charging bay for charging car and bike, injected-clock entry time, normalized plate + slot persisted |
| Charging / capacity | 5 | charging unavailable with all 4 EV bays taken, no session on failure, full car park, regular preference, charging-bay fallback |
| Duplicate plate | 4 | duplicate rejection, original session and bay unchanged, normalization consistency, five repeated calls create one session |
| Persistence / safety | 5 | survives database reopen, insert failure propagates, rejected allocations leave bays free, deterministic order, taken bay retried |
| Validation | 4 | null vehicle type, blank, null, invalid plate format |
| **Total new** | **24** | |

Acceptance checklist:

- [x] Success creates exactly one active session and marks exactly one bay occupied.
- [x] Duplicate active plate blocked (case/spacing variants), no second session.
- [x] Charging request gets a vacant charging bay only.
- [x] Non-charging prefers regular bays; uses charging only when regular bays are full.
- [x] Deterministic order: Ground first, ascending bay number.
- [x] Full car park → clear failure, no session, bay state unchanged.
- [x] Charging requested but none free (while regular bays are free) → clear failure, no
      session.
- [x] Repeated allocation cannot double-book a bay or a plate (partial unique indexes; the
      service retries and re-checks).
- [x] Confirmation screen fields and ticket match the persisted session (bay, zone, floor,
      plate, type, charging, entry time).
- [x] Existing 106 Phase 2/3 tests unchanged and still pass.

### Verification evidence

- `mvn -B clean verify` → **BUILD SUCCESS**; 44 main source files + 12 test source files;
  **Tests run: 130, Failures: 0, Errors: 0, Skipped: 0**; total time **6.517 s**.
  `ParkingServiceTest`: 24 tests, 0 failures, 0 errors, 0 skipped.
- `mvn javafx:run` (default `data/hilite-parking.db`) → window **HiLITE Mall Smart Parking**
  opened on the Entry kiosk; `data/hilite-parking.db` was created on first launch and a
  read-only probe showed `slots=24`, `ground=12`, `first=12`, `charging=4`, `sessions=0`,
  `payments=0`, `settings=2`, four tables, `PRAGMA integrity_check = ok`. The console log
  contained no exception, error or warning; the window was closed cleanly, Maven finished with
  `BUILD SUCCESS` (21.973 s total), and no `java` process remained.
- End-to-end UI harness (throwaway, outside the project tree, real `MainApp` + controllers +
  service against a temporary `-Dhilite.db`): scan completed and the plate became editable;
  preview tracked typing; allocation showed `G-01`, Ground Floor, `KA05ZX7788`, Car, entry
  `26 Oct 2026, 10:37 AM`, charging Not requested, `LEVEL 01 · GROUND FLOOR`, zone A and the
  Ground directions; the ticket matched the stored row
  (`KA05ZX7788|G-01|CAR|0|2026-10-26T05:07:03.277013300Z`) and the print status said the
  ticket matches the saved session; 1 session, 24 slots, no `data/` folder in the project;
  compact layout correct at 1366 × 768; Done returned to Entry with still 1 session;
  re-entering `ka 05-zx 7788` produced the duplicate message with controls restored and still
  1 session. Screenshots: `allocation-1600x900.png`, `ticket-1600x900.png`,
  `allocation-1366x768.png`, `entry-duplicate-1366x768.png` (captured outside the project tree;
  not bundled with the deliverable).
- Limitation: click/key injection is unavailable in this environment, so the interaction
  evidence above comes from driving the real scene graph (`Button.fire()`, field updates,
  `scene.snapshot`) rather than physical mouse clicks. The real `mvn javafx:run` launch was
  verified separately.

### Multi-agent workstreams actually used

- **Lead**: inspected the existing models/repositories/controllers, froze `AllocationOutcome`
  and the `ParkingService` signature, wrote `ServiceAware` and `FormatUtil.dateTime(Instant)`,
  rewrote `Navigator` and `MainApp`, removed the dead entry preview code, reviewed every
  integrated file, ran the build and launch, drove the UI harness, and updated this plan and
  the README.
- **Agent A**: `service/ParkingService.java` under exclusive ownership.
- **Agent B**: `service/ParkingServiceTest.java` and `service/ServiceTestSupport.java` under
  exclusive ownership.
- **Agent C**: `EntryController`, `AllocationController` and `allocation.fxml` wording under
  exclusive ownership.
- The three agents ran as parallel subagent sessions after the contract was frozen; no two
  agents edited the same file. The lead inspected the merged result before building.

### Deviations / notes

- No transaction API was added: each allocation is a single-row insert guarded by the existing
  partial unique indexes, and retry/re-check handles the race. Rollback behaviour of
  `DatabaseInitializer` is unchanged and still tested.
- `preview/PreviewSession.java` and `PreviewData.createEntrySession` were deleted because the
  allocation screen no longer consumes them. `PreviewData` itself remains for the dashboard,
  exit and admin PIN screens.
- The admin dashboard is still preview-only (Phase 6): its occupancy and KPI figures do **not**
  reflect sessions created by this phase, and this is stated in the README and in the
  dashboard copy.
- No exit/fee UI, payment, session completion, live statistics, long-stay alerts, admin reset,
  kiosk-mode persistence, camera/ANPR or physical printing was implemented. The ticket is an
  on-screen simulation.
- The `-Dhilite.db` override is a JVM system property read by `MainApp`. Because
  `mvn javafx:run` forks a new JVM, Maven's own `-D` values are not inherited; for a Maven-based
  demo run pass it via `JAVA_TOOL_OPTIONS`. This was verified: an isolated database was created
  with the 24-slot seed while `data/hilite-parking.db` stayed untouched.
- Phase 4 stops here. Phase 5 has **not** been started.

---

## Phase 5 — Vehicle exit, fees, demo payment, release

**Status: Not started · Depends on: Phase 4**

Scope:

- `ExitService`/workflow with states: quote → payment (if fee > 0) → confirm/close.
- Exit controller implementing four sequential states (scan, session+fee, payment, success);
  only one state visible at a time; inline errors; cancel preserves occupancy.
- Payment record created only on confirmed payment; zero-fee exits skip payment.
- Transactional close + payment + bay release; idempotent against double confirm.
- Success summary + Done returns to exit starting state.

Required tests:

- [ ] Unknown plate → not-found error, nothing changed.
- [ ] Already-completed session cannot be quoted/charged again.
- [ ] Fee quote follows frozen rules using stored entry time and injected quote time.
- [ ] Cancelled payment leaves session active, bay occupied, no payment row.
- [ ] Confirmed payment closes session, writes one payment row, frees the bay.
- [ ] Zero-fee exit closes without payment record.
- [ ] Double confirm/exit cannot double-charge or double-release.
- [ ] Free allowance, chargeable duration, rate and amount displayed consistently with service.

Acceptance: all above pass + manual lifecycle: enter → exit paid, enter → exit free,
enter → exit cancel → exit again.

Verification: `mvn -q test -Dtest='*Exit*'` + launched UI check.

---

## Phase 6 — Admin PIN, dashboard, mode, alerts, statistics, reset

**Status: Not started · Depends on: Phase 5**

Scope:

- PIN screen against `admin_settings`; keypad, clear/backspace, invalid feedback; demo PIN 1234,
  not displayed. Session-scoped admin unlock; lock on leaving admin.
- Admin dashboard: exactly four KPI cards (occupancy, available, total entries, total exits);
  car/bike counts; EV charging occupancy; both 12-bay floor maps; bay detail panel;
  peak hour + compact hourly chart; long-stay alerts; kiosk mode selector; reset.
- `StatisticsService` and `LongStayService` derived from persisted data.
- Mode switching persists setting, refreshes kiosk, never alters parking records.
- Reset: auth + confirmation, transactional clear of sessions/payments, reseed/restore bays,
  mode back to Entry, refresh open views.

Required tests:

- [ ] Statistics correct on empty DB (zeros; peak hour handled gracefully, e.g. "—").
- [ ] Statistics correct for mixed cars/bikes, active/completed sessions, per-floor occupancy.
- [ ] Entry-by-hour buckets use local hour; peak hour correct and deterministic on ties
  (document tie-break, e.g. earliest hour).
- [ ] Long-stay excludes exactly 12 h; includes 12 h + 1 s; fields complete.
- [ ] PIN accepts 1234, rejects wrong PIN; PIN never rendered in UI.
- [ ] Mode switch keeps sessions/bays unchanged; persists across restart.
- [ ] Reset cancelled changes nothing (DB + UI).
- [ ] Confirmed reset clears sessions/payments, restores 24 bays/4 charging, all free,
  keeps schema, mode = ENTRY.
- [ ] Dashboard reads the same DB as kiosk (occupancy changes reflect immediately).

Acceptance: all above pass + manual dashboard review with seeded demo activity.

Verification: `mvn -q test -Dtest='*Statistics*,*Admin*,*Reset*'` + launched UI check.

---

## Phase 7 — Integration, refinement, release

**Status: Not started · Depends on: Phase 6**

Scope:

- Full `mvn clean verify`; remove all preview-only data from production workflows.
- End-to-end manual script: car paid exit, bike free exit, EV charging allocation,
  duplicate rejection, full-capacity failure, 12h+ alert, mode switch, reset, restart persistence.
- Visual pass at 1920×1080 and a smaller window (~1366×768): overflow, clipping, contrast,
  focus states, spelling, disabled/loading/error consistency.
- Update README with final commands, demo script, PIN note, database location, reset note,
  troubleshooting, screenshot/UI evidence if captured.
- Reconcile docs with reality; list any known limitations honestly.

Acceptance:

- [ ] Full suite green from clean.
- [ ] Complete car and bike lifecycles work end-to-end.
- [ ] Charging, fees, demo payment, alerts, stats, mode switching, reset, persistence verified.
- [ ] Launch command documented and actually used.
- [ ] No known failure reported as fixed.
- [ ] README complete for a college demonstration.

Verification: clean build/test output + manual checklist + inspected UI.

---

## Cross-phase risks / watch list

| Risk | Mitigation |
|---|---|
| First build needs Maven Central (OpenJFX/SQLite not cached) | Verify download in Phase 1; document offline prep |
| JavaFX runtime compatibility with JBR 21 | Pin JavaFX 21.0.x; actual launch check in Phase 1 |
| Frosted-glass look unsupported natively | Simulated translucency/borders/scrim per `UI_GUIDE.md` |
| No supplied runtime photography | Ship refined gradient fallback; optional approved assets later |
| SQLite concurrency edge cases on repeated clicks | Transactions + partial unique indexes + disable buttons while loading |
| Time-zone/rounding mistakes | Explicit `Instant` inputs in the fee calculator; injected `Clock` in workflow services; UTC storage + local display; boundary tests |
| Scope creep in dashboard | Exactly four KPI cards; only required panels |
| Preview data leaking to demo | Single preview-only class removed/disabled by Phase 7 |
