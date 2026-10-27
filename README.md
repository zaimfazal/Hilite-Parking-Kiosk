# HiLITE Mall Smart Parking

A college demonstration desktop application for a fictional mall parking facility: one
JavaFX kiosk application with a vehicle-entry kiosk, an allocation confirmation screen, a
vehicle-exit kiosk with demo payment, an admin PIN gate, and an operations dashboard.

> **Current status: Phase 5 complete (2026-10-28) — entry, allocation, exit, fee quote and demo
> payment all run on the real SQLite database.**
> The Entry kiosk saves one active session per vehicle. The Exit kiosk looks up that session,
> quotes the fee with the frozen `FeeCalculator`, takes a demo payment when a fee is due, and
> closes the session (freeing the bay) in one SQLite transaction. The database is initialized
> once at launch (`data/hilite-parking.db`, created on first start). The Admin PIN and Dashboard
> still show **Phase 1 preview data**; live statistics, long-stay alerts and reset arrive in
> Phase 6. **157 JUnit 5 tests** pass (50 main + 14 test sources), including 27 exit tests on
> temporary databases. See `PHASE_PLAN.md`.

---

## 1. What works in the UI

These screens are **working JavaFX controls**, not mockup images. **Entry**, **Allocation** and
**Exit** run on persisted SQLite data; **Admin PIN** and **Dashboard** still render Phase 1
preview values.

| Screen | How to reach it | Interactions that work |
|---|---|---|
| **Entry kiosk** (default) | App launch | Car / Bike selection, EV charging Yes / No, simulated number-plate scan animation, editable plate, **Find My Parking** (real allocation), inline errors for invalid plate, duplicate active plate, full car park and no charging bay, discreet lock button, `Ctrl+Shift+A` |
| **Allocation** | Entry → **Find My Parking** | Real saved floor / bay / zone signage, session details from the database, **Print Parking Ticket** overlay built from the same record, **Simulate Print**, **Close** (or `Esc`), **Done** back to Entry |
| **Exit kiosk** | Dashboard → **Exit Kiosk**, or the kiosk's exit mode | Four sequential states on one screen: scan → session & fee → demo payment → success. **Calculate Parking Fee** (real quote, writes nothing), Back, **Pay & Exit** / **Complete Exit** (zero fee), Cancel (session stays open), **Confirm Demo Payment** (closes the session), Done; inline errors for invalid, unknown and already-closed plates |
| **Admin PIN** | Lock button or `Ctrl+Shift+A` from a kiosk screen | On-screen keypad, Clear, Backspace, physical keyboard, `Enter`; wrong PIN shows an inline error and shake; PIN `1234` opens the dashboard; **Return to kiosk** / `Esc` |
| **Dashboard** | Admin PIN → unlock | Four KPI cards, both floor maps, selectable bay tiles with a bay-detail panel, car/bike and EV summaries, peak hour, long-stay alerts, Entry/Exit kiosk switches, Reset Demo dialog, **Lock & return to kiosk** |

Kiosk mode is held in memory only. Dashboard mode switches change which kiosk screen is shown;
they do not change any record. **Reset Demo** always opens a confirmation dialog, but it still
only displays a preview notice — no data is changed (Phase 6).

### Allocation behaviour and remaining preview values

Entry and Allocation use **real saved data** from `ParkingService` and the
`parking_sessions` table:

- Allocation order is Ground Floor before First Floor, ascending bay within each floor.
- Car or bike **without** charging takes the first vacant regular bay (`G-01` on an empty
  database); a charging bay (`G-03`, `G-08`, `F1-05`, `F1-12`) is used only when no regular
  bay is free.
- **EV charging requested** takes the first vacant charging bay (`G-03` on an empty database).
  If every charging bay is occupied the request is refused and nothing is saved.
- Plates are normalized before storage (`KA 05-ZX 7788` → `KA05ZX7788`); an active plate
  cannot enter twice, and the kiosk shows a duplicate message rather than "parking full".
- The entry time is the real clock instant, stored in UTC and displayed in local time.
- Directions and signage are generated from the assigned floor and zone, and the ticket
  reuses the same saved record.

Both kiosks pre-fill the simulated scan with the demo plate `KL 11 AB 1234`; the field is
editable.

The admin screens still use `com.hilite.parking.preview.PreviewData` and show static values:

- Dashboard preview → **9 occupied / 15 available** of 24 bays, 38 entries, 29 exits,
  **1 / 4 EV bays in use**, peak hour 4 PM, long-stay samples G-07 and F1-11.
- Demo admin PIN: **`1234`** (never shown in the UI).

Preview content is a static snapshot, not a simulated timeline, and the dashboard does **not**
reflect sessions created at the entry kiosk yet — that integration belongs to Phase 6.

### Phase 2 domain layer

Phase 2 added the business foundation; Phase 4 connected the first workflow to it:

| Type | Purpose |
|---|---|
| `model.VehicleType` | `CAR` / `BIKE` (business enum; used by the Entry kiosk since Phase 4) |
| `model.KioskMode` | `ENTRY` / `EXIT` (domain value; `Navigator` still uses its Phase 1 nested enum) |
| `model.Floor`, `model.Zone` | Ground/First and Zone A/B |
| `model.ParkingSlot` | Slot id, floor, bay number, zone, charging flag; **no occupancy field** |
| `model.ParkingSession` | Session id, normalized plate, vehicle, charging request, slot id, UTC `Instant` entry/exit, nullable `BigDecimal` final fee |
| `model.Payment`, `model.PaymentStatus` | Demo payment id, session id, amount, `PENDING` / `CONFIRMED`, optional confirmation instant |

`FeeCalculator.calculateFee(VehicleType, Instant, Instant)` implements the frozen tariff:
first 120 elapsed minutes free, then any positive remainder rounded up to whole hours at
₹5/hour (car) or ₹2/hour (bike). It is a pure function — no system clock, JavaFX, SQLite, or
preview data — and uses whole elapsed seconds; sub-second remainders are truncated and this
is documented in the class.

The domain layer is **working and tested** (68 tests). The Entry/Allocation workflow now uses
`model.VehicleType`, `ParkingSlot` and `ParkingSession` directly; `FeeCalculator` and the
payment model stay unused by the screens until Phase 5.

### Phase 3 persistence layer (initialized at startup since Phase 4)

Phase 3 added a small SQLite layer; Phase 4 calls it from the application:

| Component | Purpose |
|---|---|
| `repository.Database` | Opens a fresh connection per call, always with `PRAGMA foreign_keys = ON` and a 5 s busy timeout |
| `repository.DatabaseInitializer` | Creates the schema, seeds the fixed 24-bay layout and demo settings in one transaction; safe to run repeatedly |
| `repository.ParkingSlotRepository` (+ JDBC implementation) | Read the layout, charging bays, and slot count in deterministic floor/bay order |
| `repository.ParkingSessionRepository` (+ JDBC implementation) | Insert active sessions, find by plate/slot/id, list, complete once, count entries/exits |
| `repository.PaymentRepository` (+ JDBC implementation) | Insert demo payments, list by session, count confirmed payments |
| `repository.SettingsRepository` (+ JDBC implementation) | Minimal key/value store for the demo PIN and last kiosk mode |
| `util.RegistrationUtil` | The single normalization rule used for the persisted plate key |

**Database location:** `data/hilite-parking.db`, relative to the working directory, excluded by
`.gitignore`. `DatabaseInitializer.initialize()` creates the directory and schema; since
Phase 4 `MainApp` calls it **once at startup**, before the Entry screen loads, so the first real
launch creates the file. Re-launching re-validates the layout without duplicating slots and
never deletes existing sessions, payments or settings. For an isolated demo or test run the path
can be overridden with `-Dhilite.db=<path>` (see §3).

**Schema:** four tables.

- `parking_slots` — 24 rows, unique `(floor, bay_number)`, zone constrained to the frozen
  layout, charging flag 0/1.
- `parking_sessions` — `slot_id` foreign key; active/completed enforced by a paired-nullability
  `CHECK`; partial unique indexes allow at most one active session per registration and per bay.
- `payments` — session foreign key, non-negative `amount_paise`, `PENDING`/`CONFIRMED` check,
  and at most one confirmed payment per session.
- `admin_settings` — `admin.pin = 1234` and `kiosk.mode = ENTRY` defaults, inserted only when
  missing.

**Storage rules:** timestamps are ISO-8601 UTC text with exactly nine fractional digits
(30 characters); money is integer paise (`final_fee_paise`, `amount_paise`), never SQLite
floating point. Fee values are converted in the repository layer, and an amount with fractional
paise is rejected rather than rounded. A bay is occupied **only** when an active session
references it; there is no editable occupancy flag.

The persistence layer is **working and tested** and is the source of truth for the
Entry, Allocation and Exit workflows. The dashboard still displays static preview values;
connecting it is Phase 6.

### Phase 4 allocation service (connected to the kiosk)

| Type | Purpose |
|---|---|
| `service.ParkingService` | `allocateParking(registration, VehicleType, chargingRequested)`; validates, checks duplicate active plate, picks a vacant bay, saves one active session. JavaFX-free; repositories and an optional `Clock` are injected into the constructor |
| `service.AllocationOutcome` | Sealed result: `Allocated(ParkingSession, ParkingSlot)` or `Rejected(Reason, message)` with reasons `INVALID_REGISTRATION`, `INVALID_REQUEST`, `DUPLICATE_ACTIVE_SESSION`, `PARKING_FULL`, `CHARGING_UNAVAILABLE` |
| `controller.ServiceAware` | Marker interface the `Navigator` uses to hand the shared service to a loaded screen (no DI framework, static singleton or service locator) |
| `util.FormatUtil.dateTime(Instant)` | Converts the stored UTC entry instant to the machine's local time for display |

Domain refusals are `Rejected` values shown as one short sentence on the Entry screen. A real
database failure is never turned into a parking result: `DataAccessException` propagates and the
kiosk shows "Could not save your parking session. Please try again." without navigating.

Startup order is: resolve the database → `DatabaseInitializer.initialize()` → build the JDBC
repositories → build one `ParkingService` → create the `Navigator` → show Entry. If
initialization fails, the window shows a readable error card with the database path and cause
instead of the kiosk. Opening the Allocation screen only reads the `Allocated` value passed to
it, and **Done** simply returns to Entry, so neither revisiting nor refreshing can allocate a
second bay or create a second session.

### Phase 5 exit service (connected to the kiosk)

| Type | Purpose |
|---|---|
| `service.ExitService` | `quote(registration)`, `completeZeroFee(ExitQuote)`, `confirmPayment(ExitQuote)`. JavaFX-free; repositories, `FeeCalculator` and an optional `Clock` are injected |
| `service.ExitQuote` | Immutable quote: session id, plate, vehicle, bay, floor, entry time, **quoted exit time (captured once)**, total duration, 120-minute free allowance, chargeable hours, hourly rate, amount due |
| `service.ExitOutcome` | Sealed result: `Quoted(ExitQuote)`, `Completed(ExitSummary)` or `Rejected(Reason, message)` with reasons `INVALID_REGISTRATION`, `SESSION_NOT_FOUND`, `ALREADY_COMPLETED`, `STALE_QUOTE`, `PAYMENT_REQUIRED`, `NO_PAYMENT_REQUIRED` |
| `service.ExitSummary` | Saved completion shown on the success state; `paymentId` is null exactly for a zero-fee exit |
| `repository.ExitCompletionRepository` (+ `JdbcExitCompletionRepository`) | One connection, one transaction: conditional `UPDATE … WHERE exit_time IS NULL`, then the confirmed payment insert; commit only if both succeed, otherwise roll back |
| `controller.ExitServiceAware` | Hand-off interface the `Navigator` uses to give the Exit screen the shared `ExitService` |

Guarantees:

- The fee comes only from `FeeCalculator`, using the stored entry time and the quote's own exit
  time. Confirmation reuses that exit time and amount, so the saved fee always equals the fee
  shown, however long the customer stays on the payment panel. A new quote reads the clock
  again.
- Calculating a fee, opening the payment panel and **Cancel** write nothing: the session stays
  active and the bay stays occupied.
- A paid exit writes the completed session **and** one `CONFIRMED` payment in the same
  transaction, or neither. A zero-fee exit closes the session with ₹0 and creates no payment.
- Before writing, the service re-reads the session and rejects a quote that no longer matches
  it (`STALE_QUOTE`) or a session that is already closed (`ALREADY_COMPLETED`). The conditional
  update and the one-confirmed-payment-per-session index stop a racing second attempt. The
  Exit screen also disables its buttons while a request runs.
- After an exit, the same plate reports that no active session remains. A database failure
  shows an inline message and never a success state.

### Visual and asset notes

- Kiosk: dark, cinematic translucent-glass panels, electric-blue primary actions, green/gold
  accents, hand-drawn JavaFX vector icons and a vector garage backdrop.
- Admin: light workspace with a dark navy sidebar and restrained blue actions.
- No photography was supplied, so the background is drawn with JavaFX gradients, paths, and
  shapes (`GarageBackdrop`). No remote image, font, or CSS URL is loaded at runtime.
- The five images in `Design References/` were used as references only; no mockup is shown as
  the interface and no mockup pixels are bundled into the UI.
- Layout verified at **1600 × 900** and at **1366 × 768** (the app switches to a compact
  style below 1500 px width or 820 px height). Minimum window is 1280 × 720.

---

## 2. Verified environment

Audited 2026-10-24; Phase 1 build/launch verified 2026-10-25; Phase 2 test build and
UI regression check verified 2026-10-25; Phase 3 persistence build and UI regression check
verified 2026-10-26; Phase 4 allocation build, UI harness and real launch verified 2026-10-26.
Phase 5 was verified on a second machine (2026-10-28; see the Phase 5 notes below the table):

| Requirement | Observed |
|---|---|
| OS | Windows 11 Home Single Language `10.0.26300` (amd64) |
| Screen | 1920 × 1080, single primary display |
| Java | OpenJDK 21.0.11 (JetBrains Runtime) |
| Maven | Apache Maven 3.9.16 |
| Git | 2.53.0 — directory is **not** a Git repository |
| JavaFX | 21.0.5, supplied by Maven (not bundled with the JDK) |
| SQLite | Xerial `sqlite-jdbc` 3.53.4.0 (plain JDBC; no ORM) |
| Tests | JUnit 5.11.4 + Maven Surefire 3.5.2 |
| Maven Central | Reachable; JavaFX artifacts cached locally after the first build |

Phase 5 machine: Windows 11 `10.0.26300`, Oracle Java **26.0.2.1** (`C:\Program Files\Java`),
Maven **3.9.11** from the local Maven wrapper cache (`mvn` is not on `PATH` there), no Python.
The build still compiles with `--release 21`. JavaFX 21.0.5 printed a non-fatal "Unsupported
JavaFX configuration: classes were loaded from unnamed module" warning and `sun.misc.Unsafe`
deprecation warnings under Java 26. Neither affected the run.

To build on another machine:

- JDK **21** (`java -version`)
- Maven **3.9+** (`mvn -v`)
- Internet for the first build; afterwards the local Maven cache is enough

---

## 3. Build and run

```powershell
# Clean build and all unit, persistence and allocation tests
mvn clean verify

# Focused allocation-service tests only
mvn -q test -Dtest=ParkingServiceTest

# Focused exit, fee-quote, payment and transaction tests only
mvn -q test -Dtest='*Exit*'

# Focused persistence and registration tests only
mvn -q test -Dtest='*Repository*,*Database*,*Registration*'

# Launch the JavaFX application (creates/uses data/hilite-parking.db)
mvn javafx:run
```

All automated tests use a fresh SQLite file under JUnit's `@TempDir`; they never touch
`data/hilite-parking.db`. Running the tests therefore creates no database in the project.

The database path can be overridden with the JVM system property `hilite.db`, for example in an
IDE run configuration or a direct `java` launch. `mvn javafx:run` starts the app in a forked JVM
that does not inherit Maven's `-D` properties, so pass it through `JAVA_TOOL_OPTIONS` instead:

```powershell
$env:JAVA_TOOL_OPTIONS = "-Dhilite.db=$env:TEMP\hilite-demo.db"
mvn javafx:run
```

Phase 5 verification (2026-10-28):

- `mvn -B clean verify` → **BUILD SUCCESS**: 50 main source files + 14 test source files,
  **157 tests run, 0 failures, 0 errors, 0 skipped**. The 130 Phase 2–4 tests are unchanged;
  `ExitServiceTest` adds 27.
- `mvn javafx:run` (with `JAVA_TOOL_OPTIONS=-Dhilite.db=<temp file>`) → window **HiLITE Mall
  Smart Parking** opened with no exception. The demo database was created outside the project,
  no `data/` folder was created, and no `java` process remained after the launched process
  tree was stopped.
- A throwaway harness kept outside the project drove the real `MainApp`, `Navigator` and
  `ExitController` against a seeded temporary database. It checked the database after each step.
  Results: calculate → ₹10 quote; payment → **Cancel** left the session active, the bay
  occupied and no payment; **Confirm Demo Payment**, clicked twice, wrote exactly one ₹10
  payment and freed the bay; the same plate was then refused as already closed; an unknown
  plate was refused; a bike exit inside 2 h closed at ₹0 with no payment; and an Entry
  allocation followed by an immediate exit closed at ₹0. The 1600 × 900 and 1366 × 768 layouts
  were reviewed from screenshots. The harness used `Button.fire()` and scene snapshots, not
  physical mouse or keyboard input.

Earlier verification on the audited machine (Phase 1 UI, re-checked after Phases 2, 3 and 4):

- `mvn -B clean verify` → **BUILD SUCCESS**: 44 main source files + 12 test source files,
  **130 tests run, 0 failures, 0 errors, 0 skipped** (6.517 s).
- The 106 Phase 2/3 tests are unchanged and still pass. The 24 new `ParkingServiceTest` tests
  cover successful car/bike/charging allocation, the injected clock, normalized plates,
  duplicate protection, charging exhaustion, full capacity, regular-bay preference and
  charging-bay fallback, deterministic order, retry on a taken bay, restart persistence and
  database-failure propagation.
- `mvn javafx:run` → application window **HiLITE Mall Smart Parking** opened on the Entry
  kiosk with no exception in the console. The first launch created `data/hilite-parking.db`; a
  read-only probe showed 24 slots (12 Ground, 12 First, 4 charging), 0 sessions, 0 payments,
  the two seeded settings, four tables and `PRAGMA integrity_check = ok`. The window was closed
  cleanly, Maven reported `BUILD SUCCESS`, and no `java` process remained.
- The end-to-end entry workflow was driven through the real `MainApp`, controllers and service
  against an isolated temporary database: scan → editable plate → Find My Parking → `G-01`
  Ground Floor allocation → ticket → Done → duplicate rejected. Details in `PHASE_PLAN.md`.
- All five views (entry, exit, allocation, admin PIN, dashboard) were re-loaded through the
  updated `Navigator` with the three stylesheets, and the kiosk-mode return path restored the
  Exit kiosk. (Click/key injection was unavailable in the automation session, so this replaced
  manual clicking; no UI source file was changed.)
- A temporary scripted UI walkthrough from Phase 1 (removed before delivery) exercised the
  navigation and captured 15 full-window states at 1600 × 900 and 1366 × 768. All states were
  reviewed, and representative 100% crops were checked for text, clipping, and contrast.

Phase 4 adds no new dependency: Xerial SQLite JDBC 3.53.4.0, JavaFX 21.0.5, JUnit 5.11.4 and
Surefire 3.5.2 are unchanged.

If the demonstration machine must be offline, do one online build first (or run
`mvn -q dependency:go-offline`) so JavaFX 21.0.5, JUnit 5.11.4, and SQLite JDBC 3.53.4.0 are
cached.

---

## 4. Project structure (actual, Phase 5)

```text
Parking Kiosk/
├─ pom.xml
├─ README.md
├─ PROJECT_RULES.md
├─ PHASE_PLAN.md
├─ UI_GUIDE.md
├─ .gitignore                             # ignores data/, *.db, target/
├─ Design References/                     # five supplied mockups (not runtime UI)
├─ src/main/
│  ├─ java/com/hilite/parking/
│  │  ├─ MainApp.java                     # stage, scene, density, startup DB init
│  │  ├─ Navigator.java                   # one scene, five FXML views; supplies the service
│  │  ├─ controller/                      # event handling + display only
│  │  │  ├─ EntryController.java          # real allocation request
│  │  │  ├─ AllocationController.java     # renders the saved allocation
│  │  │  ├─ ExitController.java           # real quote, demo payment, completion
│  │  │  ├─ AdminPinController.java
│  │  │  ├─ DashboardController.java
│  │  │  ├─ KioskHeaderController.java
│  │  │  ├─ NavigatorAware.java
│  │  │  ├─ ServiceAware.java             # parking service hand-off from Navigator
│  │  │  └─ ExitServiceAware.java         # exit service hand-off from Navigator
│  │  ├─ model/                           # Phase 2 domain (no JavaFX/SQL)
│  │  │  ├─ VehicleType.java
│  │  │  ├─ KioskMode.java
│  │  │  ├─ Floor.java
│  │  │  ├─ Zone.java
│  │  │  ├─ ParkingSlot.java
│  │  │  ├─ ParkingSession.java
│  │  │  ├─ Payment.java
│  │  │  └─ PaymentStatus.java
│  │  ├─ service/
│  │  │  ├─ FeeCalculator.java            # frozen tariff, pure function
│  │  │  ├─ AllocationOutcome.java        # Allocated / Rejected result type
│  │  │  ├─ ParkingService.java           # entry allocation use case
│  │  │  ├─ ExitService.java              # exit quote + completion use case
│  │  │  ├─ ExitQuote.java                # frozen fee quote
│  │  │  ├─ ExitOutcome.java              # Quoted / Completed / Rejected
│  │  │  └─ ExitSummary.java              # saved completion for the success state
│  │  ├─ repository/                      # Phase 3 persistence (plain JDBC)
│  │  │  ├─ Database.java                 # connection + PRAGMAs
│  │  │  ├─ DatabaseInitializer.java      # transactional schema + seed
│  │  │  ├─ DataAccessException.java
│  │  │  ├─ ParkingSlotRepository.java
│  │  │  ├─ ParkingSessionRepository.java
│  │  │  ├─ PaymentRepository.java
│  │  │  ├─ SettingsRepository.java
│  │  │  ├─ ExitCompletionRepository.java # atomic exit boundary
│  │  │  └─ jdbc/
│  │  │     ├─ SqlCodec.java              # UTC text + paise conversion
│  │  │     ├─ JdbcExitCompletionRepository.java  # one-transaction close + payment
│  │  │     ├─ JdbcParkingSlotRepository.java
│  │  │     ├─ JdbcParkingSessionRepository.java
│  │  │     ├─ JdbcPaymentRepository.java
│  │  │     └─ JdbcSettingsRepository.java
│  │  ├─ preview/                         # PHASE 1 PREVIEW DATA ONLY (dashboard, PIN)
│  │  │  ├─ PreviewData.java              # single source of sample values
│  │  │  ├─ PreviewBay.java
│  │  │  ├─ PreviewLongStay.java
│  │  │  ├─ PreviewStats.java
│  │  │  └─ VehicleType.java
│  │  └─ util/
│  │     ├─ FormatUtil.java               # display formatting
│  │     ├─ RegistrationUtil.java         # persisted plate key
│  │     ├─ GarageBackdrop.java           # vector background
│  │     ├─ Icons.java                    # hand-built vector icons
│  │     └─ UiEffects.java                # clock, fades, error shake
│  └─ resources/
│     ├─ fxml/                            # entry, allocation, exit, admin-pin,
│     │                                   # dashboard, KioskHeader
│     └─ css/                             # app tokens, kiosk, admin
└─ src/test/java/com/hilite/parking/
   ├─ model/ParkingSlotTest.java
   ├─ model/ParkingSessionTest.java
   ├─ model/PaymentTest.java
   ├─ repository/DatabaseInitializerTest.java
   ├─ repository/ParkingSlotRepositoryTest.java
   ├─ repository/ParkingSessionRepositoryTest.java
   ├─ repository/PaymentRepositoryTest.java
   ├─ repository/RepositoryTestSupport.java
   ├─ service/FeeCalculatorTest.java
   ├─ service/ParkingServiceTest.java      # 24 allocation tests
   ├─ service/ServiceTestSupport.java
   ├─ service/ExitServiceTest.java         # 27 exit, payment and rollback tests
   ├─ service/ExitTestSupport.java
   └─ util/RegistrationUtilTest.java
```

`data/hilite-parking.db` is created by the first real launch. Tests never create it: every test
uses its own temporary SQLite file under JUnit's `@TempDir`. Deleting `data/` resets the parking
records; it is not tracked by version control.

Planned additions (not present yet): statistics and long-stay services, admin reset, and the
dashboard controller wiring.

---

## 5. Planned final behaviour (Phases 5–7)

This section describes the completed product. Entry, allocation, fees, demo payment and exit
already work as described; dashboard statistics and reset are still ahead.

- Simulate entry scanning for **Car** or **Bike / Scooter**, with optional **EV charging**.
- Allocate a real bay from a fictional 24-bay, two-floor layout using deterministic rules.
  *(Implemented in Phase 4.)*
- Quote parking fees using frozen rules, take a **demo payment** when due, close the
  session, and free the bay. *(Implemented in Phase 5.)*
- Show an admin dashboard derived from persisted sessions: occupancy, availability,
  entry/exit counts, car/bike splits, EV usage, both floor maps, peak entry hour, and
  long-stay alerts (> 12 hours).
- Reset demo data explicitly from the dashboard, with PIN authentication and confirmation.

### Layout (fictional demo data)

| Floor | Bays | Zones | Charging bays |
|---|---|---|---|
| Ground | G-01 … G-12 | 01–06 = Zone A, 07–12 = Zone B | G-03, G-08 |
| First | F1-01 … F1-12 | 01–06 = Zone A, 07–12 = Zone B | F1-05, F1-12 |

### Fee rules (Phase 2 calculator, used by the Exit kiosk since Phase 5)

- Car ₹5 per chargeable hour; Bike / Scooter ₹2 per chargeable hour.
- First 120 elapsed minutes are free.
- After that, every positive partial hour rounds up.
- Examples: 2 h 20 min car = ₹5; 3 h 10 min bike = ₹4; 4 h 24 min car = ₹15.

These rules are implemented by `com.hilite.parking.service.FeeCalculator` and covered by
JUnit tests. `ExitService` calls it with the stored entry time and the quote's exit time; no
other class does fee arithmetic. Time is billed in whole elapsed seconds, so 120 min + 500 ms is
still free while 120 min + 1 s is charged one hour.

### Demo authentication and payment

- Admin PIN default: **`1234`**, seeded in `admin_settings` (demonstration only, not production
  security). The PIN is never displayed in the UI.
- Payments are simulated. No gateway, bank, card, QR, or real money is involved.
- Number-plate scanning and printing are simulated; no camera or printer is required.

---

## 6. Demonstration script (Phase 5)

Entry, allocation and exit are real and persisted. Steps 8–9 still use preview data for the
dashboard and PIN screens.

1. Launch with `mvn javafx:run`; the Entry kiosk opens with a simulated scan in progress. The
   first launch creates `data/hilite-parking.db` and seeds the 24-bay layout.
2. Wait for **Number plate ready**, choose **Car** or **Bike / Scooter**, set EV charging, edit
   the plate (for example `KA 05 ZX 7788`), then **Find My Parking**.
3. The Allocation screen shows the saved bay. On an empty database the first vehicle without
   charging gets **Ground Floor, bay G-01**; a charging request gets **G-03**. Check the entry
   time, zone, directions and signage, open **Print Parking Ticket**, try **Simulate Print**,
   close it, then press **Done**. The Entry screen returns and nothing new is saved.
4. Enter the same plate again in different case/spacing (for example `ka 05-zx 7788`): the
   kiosk shows *"This vehicle already has an active parking session."* and the controls are
   re-enabled. Only one session exists.
5. Optional: open `data/hilite-parking.db` in any SQLite browser and show the
   `parking_sessions` row that matches the ticket.
6. Switch to the Exit kiosk (lock button → `1234` → **Exit Kiosk**). After the simulated scan,
   type the plate from step 2 and press **Calculate Parking Fee**. A fresh stay is inside the
   free 120 minutes, so the fee is **₹0** and the button reads **Complete Exit**. Press it; the
   success state shows *Parking Session Closed* and no payment is recorded. **Done** returns to
   the scan state, and calculating the same plate again reports that the session is closed.
7. To show a paid exit, use a vehicle that has been parked for more than 2 h, or a database
   prepared beforehand with an older session. **Pay & Exit** opens the demo payment panel.
   **Cancel** returns to the fee screen and the session stays open. **Confirm Demo Payment**
   closes the session and records one confirmed payment for the amount shown. No real money is
   collected.
8. Click the lock button (top-right) or press `Ctrl+Shift+A`; enter `1234` on the keypad or
   keyboard. Enter a wrong PIN first if you want to show the inline error. The dashboard is
   still preview data and does **not** count the sessions above.
9. Show that **Reset Demo** is still a confirmation-only preview, then lock back to the kiosk.

To start a fresh demonstration database, close the app and delete `data/hilite-parking.db`
(the next launch recreates the layout). Phase 6 adds a proper admin reset instead.

---

## 7. Documentation map

| File | Purpose |
|---|---|
| `PROJECT_RULES.md` | Fixed stack, phase discipline, architecture, frozen rules, quality bar |
| `PHASE_PLAN.md` | Phase scope, dependencies, acceptance tests, live status |
| `UI_GUIDE.md` | Visual system derived from the references, per-screen specifications |
| `README.md` | This overview, environment, run/test commands, demo notes |
| `Design References/` | Five supplied mockups used as design references |

Design reference mapping:

| Intended name | Actual file |
|---|---|
| Entry | `Design References/HiLITE Mall Smart Parking Kiosk.png` |
| Allocation | `Design References/Smart Parking Bay Allocation.png` |
| Exit | `Design References/Smart Parking Exit Kiosk Interface.png` |
| Admin PIN | `Design References/HiLITE Mall Parking Admin Login.png` |
| Dashboard | `Design References/HiLITE Mall Parking Dashboard.png` |

---

## 8. Assumptions and limitations

- **Entry, Allocation and Exit are connected to SQLite.** Admin PIN and Dashboard still render
  Phase 1 preview values, and their numbers do not change when vehicles enter or leave.
- Durations are displayed in whole minutes, while billing uses whole seconds. A stay of
  5 h 00 min 40 s therefore shows **5 h** but bills 4 chargeable hours (3 h 00 min 40 s rounded
  up). The amount is correct; only the displayed duration is truncated.
- Payments are demonstration records only. No gateway, card, UPI or bank is contacted.
- The allocation screen only displays a saved record. Occupancy, duplicate-plate and capacity
  rules are enforced by `ParkingService`, and the partial unique indexes remain the final
  safeguard; the dashboard preview still does not reflect sessions created at the kiosk.
- One scene and one kiosk window; screen switching reloads FXML. No multi-window kiosk,
  camera, printer, NFC, or payment integration. The parking ticket is an on-screen simulation.
- Kiosk mode is UI state only and resets to Entry on relaunch. `kiosk.mode` is seeded in the
  database for Phase 6 but is not read or written by the UI yet.
- Admin PIN is demonstration navigation, not an authentication system. The PIN is seeded in
  `admin_settings`, but the current screen still compares against the preview constant.
- Fictional layout and zones; all bays accept both vehicle categories.
- No runtime photography; the garage scene is vector/gradient fallback art.
- Parking timestamps are stored as fixed ISO-8601 UTC text and converted to local time only
  for display.
- The Phase 2 domain layer uses `Instant` for event timestamps (UTC), `BigDecimal` for rupee
  money, and whole-second fee precision with documented sub-second truncation.
- Entry, Allocation and Exit use `model.VehicleType`; `preview.VehicleType` remains for the
  dashboard screen. `Navigator.KioskMode` is still the UI mode type (Phase 6).
- `ParkingSlot` intentionally has no occupancy field. Occupancy is derived from active
  sessions, not from a mutable flag.
- `MainApp` initializes the SQLite schema once at startup, so the first real launch creates
  `data/hilite-parking.db`. There is still no admin reset (Phase 6).
- Click/key injection was unavailable in the verification environment; UI evidence was
  produced by driving the real scene graph (button `fire()`, field updates, scene snapshots)
  rather than physical mouse clicks.

---

## 9. Roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Environment audit, rules, documentation | **Complete** |
| 1 | Maven JavaFX shell, five FXML screens, design system, navigation | **Complete** |
| 2 | Domain models and fee calculator + tests | **Complete** |
| 3 | SQLite schema, repositories, initialization, persistence tests | **Complete** |
| 4 | Entry, allocation, confirmation wired to real data | **Complete** |
| 5 | Exit, fee quote, demo payment, bay release | **Complete** |
| 6 | Admin PIN, dashboard, modes, statistics, alerts, reset | Not started |
| 7 | Integration, visual refinement, release verification | Not started |

Phase 5 stops here. Phase 6 begins only on explicit request.
