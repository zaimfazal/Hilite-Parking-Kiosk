# HiLITE Mall Smart Parking

A college demonstration **website** for a fictional HiLITE Mall car park. It is written in
**Java**: Spring Boot hosts the business layer and Vaadin renders every screen from Java code.
It has:

- an entry kiosk with simulated number-plate scanning and EV-charging requests;
- automatic bay allocation with floor and zone directions;
- an exit kiosk with fee quotes and a **demo** payment;
- a PIN-protected admin dashboard with live statistics, long-stay alerts, kiosk-mode
  switching and a transactional demo reset.

> **Status (2026-10-28): Phases 0–7 complete.**
> - `mvn -B clean verify` → **289 tests, 0 failures, 0 errors, 0 skipped**.
> - The executable JAR runs in production mode and `/actuator/health` reports `UP`.
> - A real headless-browser walkthrough passed 43/43 checks, including an application restart.
> - The **Docker image has not been built**: Docker was not available on the build machine.
>   See §6.
>
> Earlier phases (1–5) shipped a JavaFX desktop app. It was replaced by the web app in
> Phase 7, and the JavaFX code remains in Git history. Details: `AUDIT_REPORT.md`,
> `PHASE_PLAN.md`.

There is no camera, ANPR hardware, printer, charging hardware or payment gateway. Scanning,
printing and payment are simulated, and the UI says so.

---

## 1. What you can do

| Page | URL | What happens |
|---|---|---|
| Kiosk home | `/` | Opens **Vehicle Entry** or **Vehicle Exit**, depending on the kiosk mode the admin saved (Entry by default). |
| Vehicle Entry | `/entry` | 1. The simulated scan fills in `KL 11 AB 1234`; the plate stays editable.<br>2. Choose Car or Bike / Scooter and EV charging Yes / No.<br>3. **Find My Parking** saves one session and allocates a real bay. A duplicate plate, a full car park or no free charging bay gives an inline error. |
| Allocation | `/allocation/{sessionId}` | Shows the floor, the large bay number, the zone directions, the vehicle, the entry time and the EV status. **Print Parking Ticket** opens a simulated ticket. Revisiting or refreshing never allocates again. |
| Vehicle Exit | `/exit` | 1. Scan → **Calculate Parking Fee**, which writes nothing.<br>2. The session and fee breakdown appear.<br>3. **Pay & Exit** opens the demo payment; **Cancel** keeps the session and the bay. **Confirm Demo Payment** closes the session and records one payment in one transaction. A ₹0 stay uses **Complete Exit** with no payment.<br>4. The success page shows "Parking Session Closed". |
| Admin PIN | `/admin` | Lock button or **Ctrl+Shift+A** on a kiosk page. Demo PIN **`1234`**, never displayed. Five wrong PINs lock the screen for 30 s (both configurable). |
| Dashboard | `/admin/dashboard` | Live from the same database:<br>• four KPI cards<br>• Ground and First Floor maps with 24 live bay tiles (EV badges) and a bay-details panel<br>• cars and bikes entered / exited<br>• EV charging use<br>• peak entry hour with a 24-hour bar chart<br>• long-stay alerts (more than 12 h)<br>• Entry / Exit kiosk selector<br>• **Reset Demo**<br>• **Lock & return to kiosk** |

---

## 2. Business rules (frozen)

- **Vehicles:** car or bike / scooter. Either type may request EV charging.
- **Bays:** 24 in total.
  - Ground Floor G-01…G-12 and First Floor F1-01…F1-12.
  - Bays 01–06 are Zone A and 07–12 are Zone B on each floor.
  - Charging bays: G-03, G-08, F1-05 and F1-12.
- **Allocation:**
  - Ground Floor first, then ascending bay number.
  - A charging request takes only a free charging bay.
  - Other vehicles prefer an ordinary bay and use a charging bay only when no ordinary bay is
    free.
  - A plate can have only one active session.
  - A bay is occupied exactly when an active session points at it; there is no stored
    occupancy flag.
- **Fees** (`FeeCalculator`, the only fee arithmetic in the app):
  - The first 120 elapsed minutes are free.
  - After that: car ₹5 and bike ₹2 per chargeable hour, with every part-hour rounded up.
  - Time is billed in whole seconds: 120 min + 500 ms is free, 120 min + 1 s costs one hour.
  - Money is `BigDecimal` in Java and integer paise in SQLite.
- **Exit:**
  - The quote captures its exit time once, and confirmation reuses exactly that time and
    amount.
  - Closing the session and inserting the payment happen in one `BEGIN IMMEDIATE`
    transaction.
  - Out-of-date, repeated or already-completed attempts are rejected.
- **Long stay:** an alert appears only for an *active* session with elapsed time strictly over
  12 hours (43,200 whole seconds).
- **Reset:** one transaction that deletes payments and sessions, restores the exact 24-bay
  layout, keeps the configured PIN and sets the mode to Entry. It never deletes the database
  file or the schema.

---

## 3. Architecture

```
Browser ──HTTP──▶ Spring Boot 4.1.2 (embedded Tomcat)
                   ├─ web/kiosk, web/admin   Vaadin 25.3.4 views written in Java
                   ├─ web/security           AdminAuthorization (server-side unlock) + AdminOperations
                   ├─ service/               ParkingService, ExitService, FeeCalculator,
                   │                         AdminAccessService, StatisticsService, LongStayService,
                   │                         DemoResetService      (plain Java, no Spring/Vaadin imports)
                   ├─ repository/ + jdbc/    plain JDBC, prepared statements, explicit transactions
                   ├─ health/                Actuator DatabaseHealthIndicator
                   └─ config/                ParkingConfiguration (wiring), HiliteProperties
                                   │
                                   ▼
                        SQLite file (WAL), e.g. /data/hilite-parking.db
```

- **Java everywhere.** UI components, event handlers, services, rules and persistence are all
  Java.
- **Styling.** Browser CSS in `src/main/resources/META-INF/resources/styles.css`.
- **No separate frontend.** There is no React, Angular or Vue code and no custom JavaScript.
  Vaadin generates the browser side.
- **Boundaries.** Views call application services and never repositories. Every admin action
  goes through `AdminOperations`, which checks the server-side unlock flag in the user's
  `VaadinSession`. Hiding a button is never the only protection.
- **Versions** were checked against the Vaadin 25.3 compatibility page (Java 21+, Spring Boot
  4.1+, Node 24+) and Vaadin's own Spring starter on 2026-10-28.
  - Java 21 (`--release 21`)
  - Spring Boot 4.1.2
  - Vaadin 25.3.4 (`vaadin-core`, free components)
  - sqlite-jdbc 3.53.4.0
  - JUnit from the Spring Boot BOM
  - `browserless-test-spring`

---

## 4. Run it locally

**Prerequisites:** JDK 21 or newer and Maven 3.9 or newer. Node.js is optional: Vaadin
downloads it into `~/.vaadin` if it needs one. Internet access is needed for the first build.

```bash
# Build, run every test, and package the executable JAR (target/hilite-parking.jar)
mvn -B clean verify

# Run it (production mode); then open http://localhost:8080
java -jar target/hilite-parking.jar
```

The first start creates `data/hilite-parking.db` under the working directory and seeds the
24-bay layout. To use a different file or port:

```bash
# Linux / macOS
HILITE_DB_PATH=/tmp/hilite-demo.db PORT=9090 java -jar target/hilite-parking.jar
```

```powershell
# Windows PowerShell
$env:HILITE_DB_PATH = "$env:TEMP\hilite-demo.db"; $env:PORT = "9090"; java -jar target\hilite-parking.jar
```

Health check: `GET http://localhost:8080/actuator/health` → `{"groups":["liveness","readiness"],"status":"UP"}`.
Only the `health` endpoint is exposed, and without details.

`mvn spring-boot:run` (Vaadin development mode) is the usual developer workflow. It was **not**
exercised during this verification; only the packaged JAR was run.

### Tests

```bash
mvn -B clean verify                      # all 289 tests
mvn -B test -Dtest='*Exit*'              # exit, fee quote, payment, rollback
mvn -B test -Dtest='*Statistics*,*LongStay*,*AdminAccess*,*Reset*'   # admin business layer
mvn -B test -Dtest='*View*'              # browserless Vaadin view tests
mvn -B test -Dtest='*Startup*,*Health*,*Persistence*,*Authorization*' # deployment + security
```

Every test uses its own temporary SQLite file (`@TempDir`) and never touches
`data/hilite-parking.db`. Time-sensitive tests use a fixed or controllable `Clock`. No browser
is needed.

---

## 5. Configuration

Every setting has a default. Override it with an environment variable (Spring relaxed binding)
or with `--hilite.…=…` on the command line. See `.env.example`.

| Variable | Property | Default | Meaning |
|---|---|---|---|
| `PORT` | `server.port` | `8080` | HTTP port |
| `HILITE_DB_PATH` | `hilite.db-path` | `data/hilite-parking.db` | SQLite file. A relative path resolves against the working directory. |
| `HILITE_TIME_ZONE` | `hilite.time-zone` | `Asia/Kolkata` | Mall time zone for displayed times and hourly statistics |
| `HILITE_ADMIN_MAX_ATTEMPTS` | `hilite.admin-max-attempts` | `5` | Wrong PINs per browser session before lock-out (minimum 1) |
| `HILITE_ADMIN_LOCKOUT` | `hilite.admin-lockout` | `30s` | Lock-out duration |

Invalid values, such as `admin-max-attempts=0`, stop the application at startup with a clear
message.

### Database location and persistence

- **One SQLite file** holds slots, sessions, payments and settings. It uses WAL journal mode,
  so the `-wal` / `-shm` side files beside it are normal.
- **Startup never deletes, resets or repairs data.** If the file is new it creates the schema
  and the 24-bay layout. Otherwise it only validates them.
- **Data persists until an admin runs Reset Demo.** This was verified across a real application
  restart.
- **The file must be on persistent storage.** On a platform with an *ephemeral* filesystem,
  every redeploy or restart can lose the database. Mount a persistent volume (the Docker image
  uses `/data`).
- **Single instance only.** Never run two application instances against one SQLite file, and
  never scale the service horizontally. Moving to a server database such as PostgreSQL is only
  needed for multiple instances, which this demo does not require.
- To start completely fresh you can stop the app and delete the file. Prefer **Reset Demo**,
  which keeps the configured PIN.

### Admin PIN

- **Default and storage:** the default is **`1234`**, stored in `admin_settings` (`admin.pin`).
  It is never shown in the UI or written to logs. Change it with SQL, for example
  `UPDATE admin_settings SET setting_value='4321' WHERE setting_key='admin.pin';`, and Reset
  Demo keeps it.
- **Unlocking is per browser session and held on the server:**
  - **Lock & return to kiosk** locks it again.
  - Wrong attempts are counted, and too many lock the screen temporarily.
- **Demo-grade only.** This is demonstration authentication, not a production identity
  system: the PIN is stored in plain text and there are no user accounts.

---

## 6. Docker

```bash
docker build -t hilite-parking .
docker run -d --name hilite-parking -p 8080:8080 -v hilite-data:/data hilite-parking
# or
cp .env.example .env    # optional
docker compose up --build -d
```

- **Build stage:** `maven:3.9.16-eclipse-temurin-21-noble` runs `mvn -DskipTests package`. The
  tests are meant to run before the image build, with `mvn verify` locally or in CI. Vaadin
  fetches Node itself if it needs it.
- **Runtime stage:**
  - pinned `eclipse-temurin:21.0.12.1_1-jre-noble`
  - non-root user (UID 10001)
  - `HILITE_DB_PATH=/data/hilite-parking.db` on `VOLUME /data`
  - `EXPOSE 8080`
  - `HEALTHCHECK` with `curl` against `/actuator/health`
- **Compose:** one service with a named volume `hilite-data` and `restart: unless-stopped`.
- **Bind mount:** if you bind-mount a host folder instead of the named volume, it must be
  writable by UID 10001.
- **Not yet verified:** Docker was **not installed** on the build machine, so the image, the
  container health check and the volume permissions have **not been tested**. The same JAR the
  image packages was verified directly (§8).
- **Before relying on the container:**
  1. Run `docker compose up --build`.
  2. Open `/actuator/health`.
  3. Create a session.
  4. Restart the container.
  5. Confirm the session is still there.
- **Deploying:** any platform that runs a JAR or a container works, provided it gives the
  app **one instance** with a **persistent volume** at `/data`. No public deployment has been
  made; that step is yours.

---

## 7. Demonstration script

1. Start the app and open `http://localhost:8080`. The Entry kiosk opens.
2. **Entry:**
   1. Wait for "Number plate ready".
   2. Type `KA 05 ZX 7788`, choose **Car**, and leave EV at **No**.
   3. Press **Find My Parking**. You get bay **G-01** on an empty database.
   4. Open **Print Parking Ticket** → **Simulate Print**, then **Done**.
3. **Duplicate:** enter `ka05-zx7788` again. You get an inline "already has an active parking
   session" message, and no second session is created.
4. **EV:** enter another plate with **EV = Yes**. You get **G-03**, the first charging bay.
5. **Exit:**
   1. Press **Ctrl+Shift+A** → `1234` → **Exit Kiosk**. This saves the mode; then open `/`.
   2. Type the first plate and press **Calculate Parking Fee**. A fresh stay is ₹0, so
      **Complete Exit** closes it with no payment.
   3. For a paid exit, use a vehicle parked for more than 2 h. **Pay & Exit** → **Cancel**
      keeps the session; **Pay & Exit** → **Confirm Demo Payment** closes it with one payment.
6. **Dashboard:**
   - The KPIs, floor maps, cars/bikes counts, EV use and hourly chart reflect the actions above.
   - Click a bay tile to see its details.
   - Long-stay alerts list vehicles parked for more than 12 h.
7. **Reset Demo:** **Cancel** changes nothing; **Confirm Reset** clears everything and sets the
   kiosk back to Entry.
8. **Lock & return to kiosk.** Opening `/admin/dashboard` now asks for the PIN again.

---

## 8. Verified results (2026-10-28)

**Machine:**
- Windows 11 10.0.26300, Oracle JDK 26.0.2.1 (compiling with `--release 21`).
- Maven 3.9.11 from the Maven wrapper cache. `mvn` is not on `PATH` on that machine.
- Node 24.22.1. No Docker.

**Automated tests:** `mvn -B clean verify` → **BUILD SUCCESS, Tests run: 289, Failures: 0,
Errors: 0, Skipped: 0**.

| Area | Tests |
|---|---:|
| Model validation | 42 |
| Fee calculator | 26 |
| Repositories, schema, WAL / concurrency, hardening, reset | 54 |
| Allocation (incl. failure classification and concurrent entries) and allocation lookup | 34 |
| Exit, fee quote, payment, rollback | 27 |
| Admin access, statistics, long stay, reset service | 38 |
| Formatting and registration rules | 10 |
| Deployment: startup with a configured DB path, health endpoint and indicator, persistence across restart | 14 |
| Server-side admin authorization and PIN lock-out | 13 |
| Browserless Vaadin views (entry, allocation, exit, kiosk home, PIN, dashboard) | 31 |

**Packaged application:** `java -jar target/hilite-parking.jar` on a scratch database.
- The log showed "Vaadin is running in production mode" and Tomcat started.
- `GET /actuator/health` returned HTTP 200 with `"status":"UP"`.
- `/` returned 200.

**Browser walkthrough:** a throwaway script outside the repository drove headless Microsoft
Edge over the DevTools protocol. It clicked real buttons and typed into real fields, against
the production JAR on a seeded scratch database. **43/43 checks passed.**

1. **Entry and allocation:**
   - The plate was allocated **G-02**, because the seed occupied G-01 and G-03.
   - Revisiting the allocation URL showed the same bay.
   - A duplicate plate was rejected inline and stayed on `/entry`.
2. **Paid exit:**
   - The quote showed **₹10** for a 3 h 10 min car.
   - Payment → **Cancel** showed the notice → **Confirm Demo Payment** → "Payment successful"
     and "Parking Session Closed".
   - The same plate was then refused as already closed.
3. **Zero-fee exit:** the quote showed **₹0** → **Complete Exit** → "No payment required".
4. **Restart:** the application was **restarted on the same file**. The dashboard then showed
   4 entries and 2 exits, matching the run before the restart.
5. **Admin:**
   - A locked `/admin/dashboard` forwarded to `/admin`.
   - A wrong PIN was rejected, and the PIN was never rendered.
   - `1234` opened the live dashboard, including the 13-hour long-stay bike and no preview
     labels.
   - The **Exit Kiosk** mode was saved without leaving the page, and `/` then opened `/exit`.
6. **Reset:** **Cancel** kept the data. **Confirm Reset** cleared everything and `/` returned
   to `/entry`.
7. **Lock:** after **Lock**, the dashboard required the PIN again.
8. **Layout:** no horizontal scrolling at 1920×1080, 1366×768, or 1024×768 (dashboard). The
   screenshots were inspected visually.

The browser test exercised real DOM events but no physical mouse or keyboard. Mobile phones
were not tested.

---

## 9. Known limitations

- The Docker image, its health check and the volume permissions have **not been built or
  run** (§6).
- SQLite is **single-instance**. Ephemeral hosting loses data unless a volume is mounted.
- The demo PIN is plain text in the database. There are no accounts and no HTTPS inside the
  app; terminate TLS at the hosting platform or a reverse proxy.
- Durations are *displayed* in whole minutes while billing uses whole seconds. For example,
  5 h 00 min 40 s shows "5 h" but bills 4 chargeable hours. The amount is correct.
- The scan always pre-fills the demo plate `KL 11 AB 1234`. Users type over it.
- Unknown URLs such as `/actuator/env` return the Vaadin app shell with HTTP 200, not a 404.
  No actuator data is exposed.
- During `mvn verify`, Vaadin's development-mode thread logs harmless errors when short-lived
  test contexts shut down ("this web application instance has been stopped already"). Tests
  are unaffected.
- `mvn spring-boot:run` (dev mode) and real phones or tablets were not verified.

---

## 10. Troubleshooting

| Symptom | Fix |
|---|---|
| Startup fails with "missing required column(s)" or "layout" | The file is not a HiLITE database, or its 24-bay layout was edited. Point `HILITE_DB_PATH` at a new file, or restore the original. Startup never repairs data automatically. |
| `/actuator/health` is `DOWN` | The database file is unreadable or missing its tables. Check the path, the permissions and the free disk space. Details are written to the server log only. |
| Port already in use | Set `PORT` to a free port. |
| "Too many incorrect PINs" | Wait for the lock-out (30 s by default), or start a new browser session. |
| The first build is slow or fails offline | Vaadin and Maven download dependencies (and Node if needed) on the first build. Build once while online. |
| The container cannot write `/data` with a bind mount | Make the host folder writable by UID 10001, or use the named volume. |

---

## 11. Project structure

```text
Hilite-Parking-Kiosk/
├─ pom.xml                     Spring Boot 4.1.2 parent, Vaadin BOM 25.3.4, sqlite-jdbc
├─ Dockerfile, .dockerignore, docker-compose.yml, .env.example
├─ AUDIT_REPORT.md  PHASE_PLAN.md  PROJECT_RULES.md  UI_GUIDE.md  README.md
├─ Design References/          five mockups (reference only, not runtime assets)
└─ src/
   ├─ main/java/com/hilite/parking/
   │  ├─ HiliteParkingApplication.java    Spring Boot + Vaadin app shell
   │  ├─ config/                          ParkingConfiguration, HiliteProperties
   │  ├─ model/                           Floor, Zone, VehicleType, KioskMode, ParkingSlot,
   │  │                                   ParkingSession, Payment, PaymentStatus
   │  ├─ service/                         FeeCalculator, ParkingService, ExitService (+ quote,
   │  │                                   outcome, summary), AdminAccessService,
   │  │                                   StatisticsService (+ records), LongStayService,
   │  │                                   DemoResetService
   │  ├─ repository/ (+ jdbc/)            Database, DatabaseInitializer, JDBC repositories
   │  ├─ health/                          DatabaseHealthIndicator
   │  ├─ web/kiosk/  web/admin/  web/ui/  Vaadin views and shared components
   │  ├─ web/security/                    AdminAuthorization, AdminOperations
   │  └─ util/                            FormatUtil, RegistrationUtil
   ├─ main/resources/
   │  ├─ application.properties
   │  └─ META-INF/resources/styles.css    design system
   └─ test/java/com/hilite/parking/       model, repository, service, util, web, deploy, health
```

## 12. Documentation map

| File | Purpose |
|---|---|
| `AUDIT_REPORT.md` | Full repository audit, findings, and migration plan |
| `PHASE_PLAN.md` | Phase history (0–7) with real results |
| `PROJECT_RULES.md` | Stack, architecture rules, frozen business rules, quality bar |
| `UI_GUIDE.md` | Design system; §11 covers the web implementation |
