# HiLITE Mall Smart Parking — Project Rules

This document is the governing contract for the project. Read it before starting any phase.
It records the fixed stack, the rules of engagement, and the quality bar. Requirements that
must not drift are summarized here and expanded in `README.md` / `PHASE_PLAN.md`.

---

## 1. Project identity

| Item | Value |
|---|---|
| Project name | HiLITE Mall Smart Parking |
| Type | Browser-based Java web application (college MVP); Phases 1–5 were a JavaFX desktop app |
| Purpose | Simulated entry/exit kiosks + admin dashboard for a fictional 24-bay mall car park |
| Audience | Teachers/demonstrators; one or a few live demonstrations |
| Root package | `com.hilite.parking` |
| Repository | `Hilite-Parking-Kiosk` (Git; GitHub remote). No machine-specific paths in code. |

**Out of scope:** real ANPR camera integration, real payment gateways, separate JavaScript
frontends (React/Angular/Vue), public REST APIs, microservices, user accounts / identity
platforms, multi-tenant logic, multiple application instances on one SQLite file,
vehicle-size rules, additional vehicle categories, additional floors/bays, report exports.

**Architecture change (2026-10-28).** The brief for Phases 6–7 superseded the original
desktop-only scope: the application is now Spring Boot + Vaadin, with Java remaining the
primary language for UI, services, rules and persistence. See `AUDIT_REPORT.md` §6.

This is a demonstration. Authentication is a demo PIN, payments are simulated, and the
parking layout is fictional. The UI must say so where relevant.

---

## 2. Fixed technical stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| Web runtime | Spring Boot 4.1.2 (embedded Tomcat, Actuator health) |
| UI | Vaadin Flow 25.3.4 (`vaadin-core`, free components), views written in Java; browser CSS |
| Build | Maven, pinned versions (Spring Boot parent manages JUnit/Surefire/compiler) |
| Database | SQLite via JDBC (`org.xerial:sqlite-jdbc` 3.53.4.0), WAL mode, single instance |
| Tests | JUnit (Spring Boot managed), `browserless-test-spring` for view logic |
| Time injection | `java.time.Clock` for testable time-based services |
| Packaging | Executable Spring Boot JAR; Dockerfile with a `/data` volume |

- Versions are pinned. No SNAPSHOT or dynamic version ranges. Framework versions are chosen
  from official compatibility tables (Vaadin 25.3: Java 21+, Spring Boot 4.1+, Node 24+).
- Business code (`model`, `service`, `repository`, `util/FormatUtil`, `util/RegistrationUtil`)
  must not import Spring, Vaadin or any UI type; Spring wiring lives in `config`, UI in `web`.
- Tests must run headless; no browser is required.
- No dependency may be added unless it is genuinely needed and justified.
- No separate JS frontend. Custom JavaScript only when a browser capability cannot be handled by
  Vaadin, and documented when used.

---

## 3. Phase protocol (non-negotiable)

The project runs in Phase 0–7 as defined in `PHASE_PLAN.md`.

1. On first run, only Phase 0 is executed.
2. After each phase, stop and report. Never auto-advance.
3. At the start of a phase: read this file, `PHASE_PLAN.md`, `UI_GUIDE.md`, then inspect
   the real repository and earlier-phase code. Confirm what is actually complete.
4. Implement only the requested phase. Fix prerequisites only when they block the phase,
   and report that you did so.
5. Preserve working code and established design decisions. Prefer targeted edits over
   rewriting whole files.
6. Compile, run applicable tests, and (where UI exists) launch and inspect the UI.
7. Report: what changed, files touched, exact commands run, real results, unresolved
   issues, run command, and unverified acceptance criteria. Stop.
8. Never mark a phase complete while it contains stubs, pseudocode, or placeholder
   business logic in production paths.

### Honesty rules

- Never claim a build/test/launch/screenshot check succeeded unless it was actually run.
- Never invent files, assets, APIs, dependencies, records, or results.
- Never describe a mockup as a working feature, or a working feature as a tested one.
  Use those four words precisely: **mockup**, **partially connected**, **working**, **tested**.
- If a requirement is ambiguous, choose the simplest reasonable interpretation, record the
  assumption in the phase report, and continue.
- Report blockers instead of fabricating a workaround.
- Do not delete user work or rewrite unrelated code.

---

## 4. Architecture rules

Package layout:

```
src/main/java/com/hilite/parking/
  HiliteParkingApplication.java  // Spring Boot entry point + Vaadin app shell
  config/                  // Spring wiring of the plain-Java business layer, typed properties
  model/                   // records/enums: vehicle, bay, session, payment
  repository/              // JDBC SQL, one concern per repository (+ jdbc/ implementations)
  service/                 // allocation, fees, exit workflow, admin access, statistics, alerts, reset
  health/                  // Actuator health indicator for the database
  web/                     // Vaadin views (kiosk/, admin/), shared UI (ui/), admin guard (security/)
  util/                    // FormatUtil (display), RegistrationUtil (plate key)
src/main/resources/
  application.properties   // hilite.* settings, overridable by environment variables
  META-INF/resources/      // browser CSS (styles.css) — the design system
src/test/java/com/hilite/parking/
  ...                      // JUnit tests, temporary databases only
data/                      // local runtime SQLite database (gitignored); /data volume in Docker
```

Rules:

- Views handle UI events and display state. They do **not** contain SQL or fee math, and never
  inject a repository: they call application services. Admin views go through
  `web.security.AdminOperations`, which enforces the server-side unlock check.
- Services enforce business rules and own workflow boundaries/transactions.
- Repositories encapsulate SQL only. Prepared statements only. No string-concatenated SQL values.
- Models are plain Java. No UI or framework imports (Vaadin, Spring) in `model`, `service`,
  `repository`, or fee logic.
- Fee arithmetic lives in exactly one service, uses `BigDecimal` for money, and is shared by
  the exit screen, dashboard, and tests.
- UI formatting (₹, dates, durations) may live in a small `util` formatter used everywhere.
- Prefer readable Java and small methods over clever abstractions. A college viva must be
  explainable from the code.
- No static global mutable state. Inject dependencies through constructors.
- Inject `Clock` into time-sensitive services so boundary tests are deterministic.

---

## 5. Frozen business rules (must not drift)

**Layout:** 24 bays, two floors: G-01…G-12 and F1-01…F1-12.
Charging bays: G-03, G-08, F1-05, F1-12.
Zones: bays 01–06 = Zone A; bays 07–12 = Zone B (per floor).

**Vehicles:** Car or Bike/Scooter only. Either may request EV charging.
Both types fit every bay; no size/height constraints.

**Allocation:**
- One active session per normalized registration.
- Charging request ⇒ only a vacant charging bay.
- No charging request ⇒ prefer vacant non-charging bay; may take a charging bay only if no
  regular bay is free.
- Deterministic order: Ground Floor before First Floor; ascending bay number within a floor.
- Never occupy an occupied bay; never create a session when allocation fails.
- Allocation + session insert + duplicate checks must be concurrency/repeat safe, backed
  by database constraints.

**Fees:** Car ₹5/hour, Bike ₹2/hour. First 120 elapsed minutes free. After that, any
positive partial hour rounds up. Fee = ceil((elapsed − 120 min)/1 h) × rate, money safe.
Reference examples (must be covered by tests): 1h30 → ₹0; 2h exactly → ₹0;
2h+1s → ₹5/₹2; 2h20 → ₹5/₹2; 3h → ₹5/₹2; 3h10 → ₹10/₹4; 4h24 → ₹15/₹6.

**Exit:** Look up active session; quote from stored entry time to exit quote time using the
clock. Fee > 0 requires explicit demo-payment confirmation; cancel keeps session active and
bay occupied. Fee = 0 exits without payment. Close session + record payment (only if paid)
+ release bay transactionally. Repeated exits must not double-charge.

**Alerts:** Active sessions strictly elapsed > 12 hours. Not at exactly 12 hours. Show
registration, bay, floor, entry time, elapsed duration.

**Statistics:** Derived from persisted sessions only. No fabricated metrics. Entries, exits,
per-type counts, current occupancy, available bays, per-floor occupancy, entries by local
hour, peak hour (graceful on empty data).

**Reset:** Requires admin auth and explicit confirmation. Clears sessions and payments,
restores exactly 24 bays (with the 4 charging flags), frees all bays, resets kiosk mode to
Entry, refreshes open views. Never on normal startup; transaction-safe; schema preserved.

**Kiosk modes:** Start in Entry. Admin can switch Entry/Exit. The mode persists in
`admin_settings.kiosk.mode` (invalid or missing → Entry) and decides what the kiosk home page
(`/`) shows; changing it must not modify parking records.

---

## 6. Database rules

- SQLite file: `hilite.db-path` / `HILITE_DB_PATH` (default `data/hilite-parking.db`,
  relative to the working directory; `/data/hilite-parking.db` on a volume in Docker),
  gitignored. WAL journal mode; write transactions use `BEGIN IMMEDIATE`.
- One application instance per database file. SQLite is not shared between instances.
- Enable `PRAGMA foreign_keys = ON` per connection. Use sensible constraints:
  primary keys, foreign keys, non-null fields, and partial unique indexes for
  one active session per plate and one active session per bay.
- Schema creation and seeding must be idempotent. Restarting must preserve all records.
- Startup must never drop, truncate, or reset the database. Only the explicit admin reset
  clears records, and it must preserve schema.
- Use transactions for multi-step workflows (entry allocation, exit/payment/release, reset).
- Tests must use temporary databases/directories (`@TempDir`), never `data/hilite-parking.db`.
- Timestamps stored in a single documented format (ISO-8601 UTC recommended) and converted
  for display using the local zone.
- The demo PIN default is `1234`, stored in `admin_settings` (demo auth only; never shown in UI).
- No SQL in views.

---

## 7. UI rules

The full visual contract is in `UI_GUIDE.md`. Mandatory points:

- Customer kiosk: premium dark cinematic mall experience with frosted-glass panels
  (browser CSS), electric-blue primary actions, restrained green/gold accents.
- Admin: practical light workspace, dark navy sidebar, restrained blue actions.
- Every interactive element is a real Vaadin component built in Java. No screenshot-as-UI.
- Accessibility: labelled fields, accessible names on icon-only buttons, visible keyboard
  focus, inline errors announced (`role="alert"`), no user input rendered as HTML.
- No remote images at runtime. If a suitable photo asset is unavailable, use a refined
  gradient/vector fallback; never block the build on imagery.
- Consistent typography, spacing, radii, and status colors across screens.
- Visible selected, hover, pressed, focused, disabled, loading, success, and error states.
- Status must never rely on color alone (include text labels/icon glyphs).
- Primary kiosk actions ~56–72 px high at 1920×1080, scaling sensibly down to ~1366×768.
- Correct spelling and readable contrast in every state. No overflow or clipped text.
- Minimal deliberate animation only. Browser CSS may use `backdrop-filter` and
  `letter-spacing`; keep a translucent-fill fallback.
- Keep the five required screens; routine errors stay inline, not on extra screens.
- Do not add unrequested features, charts, or decoration.
- Preview-only data (Phase 1) must be clearly isolated and removed from production
  workflows by Phase 7.

---

## 8. Testing rules

- JUnit 5. Tests must be deterministic (inject fixed `Clock` where time matters).
- Fee tests cover both types, zero duration, exactly 120 min, 120 min + 1 s, 2h20,
  3h, 3h10, 4h24, and negative elapsed time behavior.
- Persistence tests cover 24 bays, 4 charging bays, restart persistence, duplicate active
  plate/bay rejection, idempotent init, and FK/constraint behavior.
- Service tests cover allocation priority/fallback/failure, exit/payment/cancel/zero-fee,
  long-stay 12h boundary, statistics on empty and populated data, and reset effects.
- Workflow tests must assert no orphan active sessions or double releases.
- Never weaken a test to make code pass. Fix the code or document the blocker.
- View logic is tested headless with `browserless-test-spring`; deployment concerns (startup
  with a configured database path, health endpoint, restart persistence, admin authorization)
  have their own tests.
- Final phase: full suite green (`mvn clean verify`) plus a browser lifecycle check.

---

## 9. Assets and licensing

- Only bundle images/fonts that are free to use and document their origin.
- The five supplied design images were **reference mockups**, not runtime UI. They have been
  removed from the repository; the visual contract in `UI_GUIDE.md` is authoritative.
  Do not use a whole mockup as a screen, and do not crop mockup UI into assets.
- No remote URLs (images, fonts, scripts) in CSS or views at runtime.
- If photo assets are unavailable, ship the gradient fallback defined in `UI_GUIDE.md`.
- Iconography: simple inline SVG paths built in Java. Do not add an icon library without
  justification.

---

## 10. Version control hygiene

- `.gitignore` excludes `target/`, `data/`, database files, Vaadin-generated frontend files,
  `node_modules/`, logs, local config (except `.env.example`), IDE files.
- The project is a Git repository with a GitHub remote. Commit, push, merge or run any
  destructive Git command only when the owner asks.
- Design mock-ups are not shipped; the visual contract in `UI_GUIDE.md` is authoritative.
- Phase reports must list every created/modified file.

---

## 11. Definition of done (per phase)

A phase is done only when:

- [ ] The requested scope is implemented and no placeholder business logic remains.
- [ ] The project compiles from a clean state.
- [ ] Applicable automated tests pass, with real output recorded.
- [ ] Relevant UI states exist and were launched/inspected (UI phases onward).
- [ ] Earlier functionality still works (no regressions).
- [ ] Documentation reflects the actual state, including unresolved issues.
- [ ] The report distinguishes mockup / partially connected / working / tested.
