# HiLITE Mall Smart Parking — Project Rules

This document is the governing contract for the project. Read it before starting any phase.
It records the fixed stack, the rules of engagement, and the quality bar. Requirements that
must not drift are summarized here and expanded in `README.md` / `PHASE_PLAN.md`.

---

## 1. Project identity

| Item | Value |
|---|---|
| Project name | HiLITE Mall Smart Parking |
| Type | Single-desktop JavaFX demonstration application (college MVP) |
| Purpose | Simulated entry/exit kiosks + admin dashboard for a fictional 24-bay mall car park |
| Audience | Teachers/demonstrators; one or a few live demonstrations |
| Root package (planned) | `com.hilite.parking` |
| Working directory | `C:\Users\Zaim\Desktop\Parking Kiosk` |

**Out of scope:** real ANPR camera integration, real payment gateways, cloud hosting,
Spring Boot, REST APIs, microservices, web frontends, user accounts, multi-tenant logic,
vehicle-size rules, additional vehicle categories, additional floors/bays, report exports.

This is a demonstration. Authentication is a demo PIN, payments are simulated, and the
parking layout is fictional. The UI must say so where relevant.

---

## 2. Fixed technical stack

| Layer | Choice |
|---|---|
| Language | Java 21 |
| UI | JavaFX 21 with FXML + JavaFX CSS |
| Build | Maven, pinned dependency versions |
| Database | SQLite via JDBC (`org.xerial:sqlite-jdbc`) |
| Tests | JUnit 5 (Jupiter) |
| Time injection | `java.time.Clock` for testable time-based services |

- Versions are pinned in `pom.xml`. No SNAPSHOT or dynamic version ranges.
- The JavaFX Maven plugin is the documented launch path; tests must run headless
  (no JavaFX toolkit required for unit and persistence tests).
- A compatible JavaFX version for the installed JDK 21 must be confirmed by an actual
  compile/run in Phase 1. Do not assume runtime compatibility.
- No dependency may be added unless it is genuinely needed and justified in the phase report.

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

Planned package layout:

```
src/main/java/com/hilite/parking/
  App.java                 // JavaFX entry point, app wiring
  model/                   // records/classes/enums: vehicle, bay, session, payment, admin stats
  repository/              // JDBC SQL, one concern per repository
  service/                 // allocation, fees, exit workflow, statistics, reset
  controller/              // JavaFX event handling only
  util/                    // small justified helpers (DB path, validation, formatting)
src/main/resources/
  fxml/                    // one FXML file per screen
  css/                     // shared tokens + kiosk + dashboard stylesheets
  images/                  // approved bundled assets
src/test/java/com/hilite/parking/
  ...                      // JUnit tests, temporary databases only
data/                      // runtime SQLite database (gitignored)
```

Rules:

- Controllers handle UI events and display state. They do **not** contain SQL or fee math.
- Services enforce business rules and own workflow boundaries/transactions.
- Repositories encapsulate SQL only. Prepared statements only. No string-concatenated SQL values.
- Models are plain Java. No JavaFX imports in `model`, `service`, `repository`, or fee logic.
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

**Kiosk modes:** Start in Entry. Admin can switch Entry/Exit. Mode is UI state only; it must
not modify parking records. Kiosk mode may persist in `admin_settings`.

---

## 6. Database rules

- SQLite file: `data/hilite-parking.db` (relative to the app working directory), gitignored.
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
- No SQL in FXML/controllers.

---

## 7. UI rules

The full visual contract is in `UI_GUIDE.md`. Mandatory points:

- Customer kiosk: premium dark cinematic mall experience with frosted-glass panels
  (simulated in JavaFX CSS), electric-blue primary actions, restrained green/gold accents.
- Admin: practical light workspace, dark navy sidebar, restrained blue actions.
- Every interactive element is a real JavaFX control. No screenshot-as-UI.
- No remote images at runtime. If a suitable photo asset is unavailable, use a refined
  gradient/vector fallback; never block the build on imagery.
- Consistent typography, spacing, radii, and status colors across screens.
- Visible selected, hover, pressed, focused, disabled, loading, success, and error states.
- Status must never rely on color alone (include text labels/icon glyphs).
- Primary kiosk actions ~56–72 px high at 1920×1080, scaling sensibly down to ~1366×768.
- Correct spelling and readable contrast in every state. No overflow or clipped text.
- Minimal deliberate animation only; respect that JavaFX CSS has no `backdrop-filter`,
  `letter-spacing`, or arbitrary blur. Frosted glass is simulated with translucent fills,
  borders, shadows, and a background scrim.
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
- Final phase: full suite green (`mvn clean verify`) plus a manual UI lifecycle check.

---

## 9. Assets and licensing

- Only bundle images/fonts that are free to use and document their origin.
- The five supplied images in `Design References/` are **design mockups**, not runtime UI.
  Do not use a whole mockup as a screen, and do not crop mockup UI into assets.
- No remote URLs in CSS/FXML at runtime.
- If photo assets are unavailable, ship the gradient fallback defined in `UI_GUIDE.md`.
- Iconography: prefer JavaFX shapes, simple inline SVG paths, or a bundled permissive icon
  font. Do not add a heavyweight icon library without justification.

---

## 10. Version control hygiene

- `.gitignore` excludes `target/`, `data/`, database files, logs, local config, IDE files.
- The directory is currently **not** a Git repository. Do not run `git init`, commit, reset,
  or any destructive Git command without explicit authorization.
- Design references are kept in the repo.
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
