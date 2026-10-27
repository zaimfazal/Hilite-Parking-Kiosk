# HiLITE Mall Smart Parking — UI Guide

Visual contract for the application. Derived from the five supplied design references, which
were inspected in Phase 0. References define the design language, not pixel-exact cloning.
This guide is authoritative for later phases; deviations require a documented reason.

> **Since Phase 7 (2026-10-28) the UI is a Java website (Vaadin Flow 25.3).** The design
> language, tokens and component specifications below still apply. §4 ("JavaFX-safe" glass)
> and §9 (JavaFX rules) are kept as history; the web implementation rules and the deliberate web
> differences are in **§11**.

---

## 1. Reference analysis

| Reference file | What to take |
|---|---|
| `HiLITE Mall Smart Parking Kiosk.png` | Cinematic garage backdrop, dark scrim, frosted right-hand panel, vehicle cards, EV segmented toggle, plate field, electric-blue CTA, footer disclaimer |
| `Smart Parking Bay Allocation.png` | Green success mark, oversized bay code, floor/zone direction block, compact detail grid, secondary + primary action pair |
| `Smart Parking Exit Kiosk Interface.png` | Sequential exit layout: image/scanner left, light result card right, fee hierarchy, prominent amount, single payment CTA |
| `HiLITE Mall Parking Admin Login.png` | Centered glass PIN card, shield emblem, spaced dots, large numeric keypad, gold primary action, inline error banner |
| `HiLITE Mall Parking Dashboard.png` | Dark navy sidebar, light workspace, KPI strip, floor bay grids, entry chart, long-stay table, kiosk mode switch, reset action |

**Do not** copy the reference dashboard's extra sidebar pages (Parking Layout, Vehicle
Records, Reports, Today filter) or extra KPI cards. Our dashboard is deliberately smaller.

**Runtime imagery:** the supplied files are mockups, not approved runtime assets. Do not
display a whole mockup as UI or crop its fake UI regions into assets. Phase 1 uses a crafted
JavaFX gradient/vector garage scene as the fallback. If a user-supplied, license-clear photo
is later placed in `src/main/resources/images/`, it may replace the fallback; FXML must still
contain real controls over it.

---

## 2. Design principles

1. **Cinematic calm** — dark, spacious, high-contrast kiosk; one obvious primary action.
2. **Information hierarchy first** — bay number, fee, and status are the largest elements;
   chrome stays quiet.
3. **Frosted glass, simulated honestly** — JavaFX has no backdrop blur. Use:
   a photographic/gradient background + dark scrim + translucent panel fill + hairline
   light border + soft drop shadow. Never use unsupported `backdrop-filter`.
4. **Operational clarity** — admin UI is practical, light, scannable; not a second kiosk.
5. **Consistency** — same tokens, spacing, radii, status language, and action hierarchy.
6. **No AI-slop excess** — no random gradients everywhere, fake logos, lorem ipsum,
   meaningless charts, or decorative clutter. Accents are deliberate.
7. **Accessible by default** — readable contrast, text + icon + color for status, keyboard
   focus, no color-only meaning.

---

## 3. Design tokens

These will be defined once in `src/main/resources/css/tokens.css` (or the top of `app.css`)
as JavaFX looked-up colors. Names below are the contract.

### 3.1 Kiosk palette (dark)

| Token | Value | Use |
|---|---|---|
| `-hk-ink` | `#080C12` | Deepest background |
| `-hk-navy` | `#0C1420` | Base navy/charcoal |
| `-hk-panel` | `rgba(16,24,34,0.72)` | Glass panel body |
| `-hk-panel-strong` | `rgba(10,17,26,0.88)` | Denser sub-panel |
| `-hk-glass-line` | `rgba(255,255,255,0.16)` | Panel hairline |
| `-hk-glass-line-soft` | `rgba(255,255,255,0.08)` | Internal dividers |
| `-hk-text` | `#F4F7FB` | Primary text |
| `-hk-text-2` | `#AAB6C6` | Secondary text |
| `-hk-text-3` | `#7E8B9C` | Tertiary/captions |
| `-hk-blue` | `#2F7BFF` | Primary action |
| `-hk-blue-hover` | `#4A8DFF` | Hover |
| `-hk-blue-deep` | `#1C5BD8` | Pressed/border |
| `-hk-cyan` | `#4DD6FF` | Scan beam / focus highlight |
| `-hk-success` | `#2ED47A` | Allocated/paid |
| `-hk-success-bg` | `rgba(46,212,122,0.14)` | Success tint |
| `-hk-gold` | `#D9BE86` | Restrained luxury accent (PIN CTA, brand line) |
| `-hk-danger` | `#FF6B6B` | Errors |
| `-hk-danger-bg` | `rgba(255,107,107,0.14)` | Error tint |
| `-hk-warning` | `#F2B544` | Long-stay / EV accents |

### 3.2 Admin palette (light)

| Token | Value | Use |
|---|---|---|
| `-ad-workspace` | `#F4F6FA` | Main background |
| `-ad-card` | `#FFFFFF` | Cards/surfaces |
| `-ad-sidebar` | `#0B1523` | Sidebar |
| `-ad-sidebar-2` | `#101D2E` | Sidebar hover/active |
| `-ad-text` | `#101828` | Primary text |
| `-ad-text-2` | `#667085` | Secondary text |
| `-ad-line` | `#E4E9F0` | Borders/dividers |
| `-ad-blue` | `#1F62D6` | Primary action/active nav |
| `-ad-blue-soft` | `#EAF1FF` | Selection tints |
| `-ad-available-bg` | `#EAF9F0` | Free bay tile |
| `-ad-available-line` | `#B9EACB` | Free bay border |
| `-ad-available-text` | `#137A3D` | Free label |
| `-ad-occupied-bg` | `#F4F6F9` | Occupied tile |
| `-ad-occupied-line` | `#D9E0EA` | Occupied border |
| `-ad-occupied-text` | `#475467` | Occupied label |
| `-ad-ev` | `#7A5AF8` | Charging capability (with bolt icon) |
| `-ad-danger` | `#D64545` | Reset/destructive/long-stay |
| `-ad-warning-bg` | `#FFF4E5` | Warning tint |

Charging capability uses the bolt icon **plus** a small "EV" label; color is never the only cue.

### 3.3 Spacing, radii, elevation

- Spacing scale: `4, 8, 12, 16, 24, 32, 48, 64` px. Use multiples only.
- Radii: cards `16`, panels `20–24`, kiosk main panel `28`, inputs/buttons `12–14`,
  pills `999` (use 999px background radius).
- Elevation: kiosk shadow `dropshadow(gaussian, rgba(0,0,0,0.45), 42, 0.18, 0, 18)`;
  admin cards `dropshadow(gaussian, rgba(16,24,40,0.06), 18, 0.10, 0, 4)`.
- Borders: 1 px hairlines; avoid heavy 2 px outlines except focus/selection.

### 3.4 Typography

Installed and verified on the target machine: **Segoe UI**, Segoe UI Semibold, Bahnschrift,
Arial. Use system fonts; bundle a font only if licensing is documented.

| Role | Family / weight | Size (1920 base) |
|---|---|---|
| Brand wordmark | Segoe UI Bold, uppercase | 30–34 |
| Kiosk display | Segoe UI Semibold | 40–44 |
| Screen title | Segoe UI Semibold | 30–34 |
| Section title | Segoe UI Semibold | 20–22 |
| Body | Segoe UI Regular | 15–16 |
| Secondary/caption | Segoe UI Regular | 13–14 |
| Uppercase label | Segoe UI Semibold, uppercase | 11–12 |
| Bay code (allocation) | Bahnschrift SemiBold (fallback Segoe UI) | 84–96 |
| Big money amount | Bahnschrift SemiBold (fallback Segoe UI) | 48–56 |
| Dashboard KPI number | Bahnschrift SemiBold | 34–40 |

Rules:

- JavaFX CSS does **not** support `letter-spacing`. Do not fake tracking by inserting spaces
  into strings. Wide-label feel comes from uppercase, size, color, and spacing between lines.
- Never go below 11 px. Dashboard body minimum 13 px.
- Use `-fx-font-smoothing-type: lcd` on light backgrounds and `gray` on dark if it improves
  measured legibility (verify visually).
- Money uses `₹` + grouping (`₹15`, `₹1,20` if ever needed) via one shared formatter.
- Durations: `4 h 24 min`, `2 h`, `45 min`, `12 h 01 min` as appropriate; never raw minutes.

---

## 4. Glass panel recipe (JavaFX-safe)

Background stack per kiosk screen:

1. Base `Region` with layered gradients (navy → charcoal; diagonal light streaks as thin
   translucent rectangles or radial gradients). Optional bundled photo in `ImageView`
   with `preserveRatio` + `viewport` cover crop.
2. Scrim `Region`: `linear-gradient(to right, rgba(4,8,14,0.55), rgba(4,8,14,0.25))` plus
   a bottom vignette `linear-gradient(to top, rgba(4,8,14,0.75), transparent 45%)`.
3. Content layer.

Glass surface CSS:

```css
.glass-panel {
    -fx-background-color: linear-gradient(to bottom right,
        rgba(255,255,255,0.10), rgba(255,255,255,0.035));
    -fx-background-radius: 28;
    -fx-border-radius: 28;
    -fx-border-color: rgba(255,255,255,0.16);
    -fx-border-width: 1;
    -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.45), 42, 0.18, 0, 18);
}
.glass-panel-inner {
    -fx-background-color: rgba(8,14,22,0.42);
    -fx-background-radius: 18;
    -fx-border-color: rgba(255,255,255,0.08);
    -fx-border-radius: 18;
    -fx-border-width: 1;
}
```

Nested glass panels are allowed only one level deep (panel → inner card) to avoid muddiness.
For a top highlight, a 1 px inner top border can be simulated with a thin `Region` or
`-fx-background-insets`; verify it renders as intended before relying on it.

---

## 5. Layout and responsive behaviour

- Base design canvas: **1920 × 1080**. Minimum supported window: **1366 × 768**
  (content scrolls only if unavoidable; primary actions must stay reachable).
- Kiosk header 88–96 px: brand left, mode/date-time right.
- Entry/exit: two-column composition. Left ~45% is atmospheric scene/scan status; right
  ~55% is a max-width ~760 px glass panel. At compact width, hide decorative side copy and
  keep the panel centered; maintain control sizes.
- Allocation: left success/detail panel (~52%), right direction scene/signage (~48%).
- PIN: centered card max-width 520 px over dimmed backdrop.
- Footer: 44–56 px notice strip with demo disclaimer and subtle brand tagline.
- Admin: sidebar 248 px fixed; top bar 64 px; workspace padding 24–32; KPI row 4 equal cards;
  below: floor maps (left, ~62%) + analytics/alerts column (right, ~38%).
- Use `HBox`/`VBox`/`GridPane` with `HGrow`/`VGrow` and min/pref sizes. Avoid absolute
  positioning except decorative accents.
- Apply a `compact` style class on the root when scene width < 1500 px and override spacing/
  font sizes in CSS; verify at both sizes. (Technique to be validated in Phase 1.)

### Primary action sizing

- Kiosk primary CTA: 64 px high (range 56–72), full panel width, 16–18 px semibold text,
  12–14 px radius, arrow/ticket icon with 12 px gap.
- Touch targets: minimum 48×48 px; PIN keys 72 px high, key gaps 12 px.
- Admin buttons: 36–40 px high; destructive reset uses red text/border or red fill with
  confirmation dialog.

---

## 6. Component specifications

### 6.1 Brand header

- `HiLITE` in Heavy/Bold white + `MALL` in Regular/Light, then a 1 px vertical divider,
  then `SMART PARKING` in uppercase small caps-like style (size/color, no letter-spacing).
- Admin variant: `HiLITE MALL` stacked over `PARKING MANAGEMENT` in the dark sidebar.
- Right side (kiosk): current date + time, two lines, 13–14 px, right aligned.
- Admin access: discreet circular 36 px lock/gear ghost button in the kiosk header/footer,
  tooltip "Administrator access (Ctrl+Shift+A)"; plus global `Ctrl+Shift+A` shortcut.

### 6.2 Vehicle type cards

- Two equal cards, ~250×170 px at base, 16 px radius, 1 px border.
- Unselected: translucent fill, white 8–10% border, icon + label in secondary text.
- Selected: blue `#2F7BFF` 2 px border, blue-tinted fill, soft outer glow, white bold label,
  and a top-right blue circle with white check mark (shape).
- Hover: slight lift via brighter border/shadow; pressed: 0.98 scale (optional).
- Use vector car/scooter shapes or bundled SVG paths; no emoji. If custom art is not ready,
  use clean JavaFX `SVGPath` silhouettes — acceptable and consistent.
- Whole card is clickable and keyboard-activatable; selected state also indicated by text
  ("Selected") for accessibility, not only border color.

### 6.3 EV charging toggle

- Segmented control: Off / On with a bolt icon; selected segment has darker navy fill and a
  thin light border; unselected is translucent. 52–56 px high.
- Helper text: "EV charging available for cars and bikes (subject to availability)."
- Must be operable for either vehicle type.

### 6.4 Plate scanner + field

- Simulated scan: corner brackets over a stylized plate illustration, a thin cyan horizontal
  sweep line animation (1.8–2.4 s loop) and progress bar; caption "Scanning number plate…".
- After scan completes, plate field becomes editable; caption "The number plate has been
  scanned. You can edit if needed."
- Plate field: 56–60 px high, `IND` prefix badge, uppercase display, clear button, 1 px light
  border, strong focus ring (`-hk-cyan`). Accept uppercase/lowercase input; normalize on submit.
- Field validation errors inline below the field, small error icon + message, never a dialog.
- Scan is explicitly simulated: footer disclaimer + small helper text. No camera permission
  or hardware access.

### 6.5 Inputs and focus

- Dark input: `rgba(255,255,255,0.06)` fill, 1 px `rgba(255,255,255,0.18)` border,
  white text, placeholder `-hk-text-3`; focus border cyan 1.5–2 px + subtle glow.
- Light admin input: white fill, `-ad-line` border, focus `-ad-blue`.
- Every interactive control needs a visible keyboard focus state; never remove focus rings.
- Disable controls while a workflow action is in flight; show inline spinner/progress and
  change button label (e.g. "Allocating…"), preventing double submission.

### 6.6 Error / success / info banners

- Inline, full-width of the panel/state, 12 px radius, 12–14 px padding.
- Error: danger tint, 1 px danger border at ~35% alpha, icon + concise sentence; field-level
  message stays near the field.
- Success: success tint + check icon; used on allocation/exit success blocks.
- Info: neutral glass tint + info icon.
- No routine errors as separate screens or blocking alerts. Dialogs only for destructive
  confirmation (reset) and ticket preview.

### 6.7 Allocation view

- Green circular check (48–56 px) + "Parking Slot Allocated" (30–34 px) + one-line subtitle.
- Large bay code 84–96 px, floor name and zone directions adjacent, "Floor / Bay" labels.
- Direction line: "Proceed to First Floor, follow the B-zone signs, locate bay F1-08."
- Detail card: 2×2 grid with icons — vehicle number, type, entry time, EV charging status.
- Actions: secondary "Print Parking Ticket" (glass/outline) + primary "Done" (blue), equal
  height 64 px. Ticket preview is a modal with mono-styled receipt and a simulated print
  action; no printer integration.
- Data is read from the saved session — no new allocation on view open.

### 6.8 Exit states

Only one state visible at a time inside the right-hand card; animate a 150–200 ms fade/slide
on transition, no cross-fading of different logical states.

- **A Scan:** scanner panel + plate field + "Calculate Parking Fee" primary.
- **B Session & fee:** session header (plate, type), entry/quote times, duration, free
  allowance (120 min), chargeable duration, hourly rate, and a prominent "Amount Due".
  Actions: "Pay & Exit" (if fee > 0) or "Complete Exit" (zero fee); secondary "Back".
- **C Payment:** amount due at 48–56 px, "Demo payment only — no real charge" note,
  "Confirm Demo Payment" primary + "Cancel" secondary.
- **D Success:** green check, "Payment successful" or "No payment required",
  amount paid (or ₹0), entry/exit timestamps, "Parking Session Closed" status, Done.
- Cancel in C returns to B with an inline info "Payment cancelled — parking session remains
  active." Never closes or frees.
- Errors within the current state; unknown plate stays in A with message.

### 6.9 Admin PIN

- Centered 500–540 px glass card over strongly dimmed/blurred-look backdrop.
- Shield emblem, "Administrator Access", "Enter your demo PIN to continue."
- Four+ masked dots (show one dot per entered digit; supports 4 digits; never show digits).
- 3×4 keypad: 1–9, Clear, 0, ⌫. 64–72 px keys, 12 px gaps, glass fill, hover lighten,
  pressed darken, visible focus. Clear and backspace are labelled; backspace also has icon.
- Gold "Unlock Dashboard" CTA, 56–60 px.
- Invalid PIN: inline danger banner "Invalid PIN. Please try again." (keep demo wording
  human) and shake? Avoid excessive motion; a 200 ms subtle shake is acceptable.
- Correct PIN → dashboard; admin session lasts until leaving admin; Esc/back returns.
- Do not display or hint the actual PIN. Document 1234 only in README.

### 6.10 Admin dashboard

- Sidebar: brand, one active item "Dashboard" (blue-tinted), user "Admin" and a small
  "Lock" action, footer tagline; no fake navigation items.
- Top bar: "Dashboard" + subtitle "Live parking status and operations overview", kiosk mode
  segmented control (Entry Kiosk / Exit Kiosk), Reset Demo button.
- KPI row: exactly 4 cards — Current Occupancy, Available Bays, Total Vehicle Entries,
  Total Vehicle Exits. Each: small icon tile, uppercase label, 34–40 px value, one-line
  context (e.g. "of 24 bays", cars/bikes split for entries/exits).
- Secondary strip: Cars entered / Bikes entered / Cars exited / Bikes exited compact counts;
  EV charging occupancy e.g. "EV bays in use 2 / 4" with bolt icon + mini capacity bar.
- Floor maps: both floors visible simultaneously (Ground Floor G-01…G-12, First Floor
  F1-01…F1-12), 6-column grid of ~150×96 px tiles.
  - Free tile: soft green fill/border, "Available", floor/bay code, bolt + EV if charging.
  - Occupied tile: neutral fill, plate (truncate carefully with tooltip/full detail), type
    icon + label, bolt + EV if charging, and an "Occupied" accessible label.
  - Charging-capable tile always shows bolt + "EV" (free or occupied).
  - Selected tile: 2 px blue border + detail panel (bay, floor, zone, status, charging
    capability, session plate/type/entry time if occupied).
  - Do not rely on color alone; include status text.
- Analytics: peak entry hour card ("4 PM", "6 entries") + compact 24-hour bar chart built
  from JavaFX shapes/nodes (no chart library). Zero hours stay visible but subdued; empty
  dataset shows "No entries recorded yet" instead of an empty chart. No fake data.
- Long-stay alerts: compact table/list with plate, bay, floor, entry time, elapsed; red
  duration text + "Long stay" tag; empty state "No long-stay vehicles." If many, scroll
  within a capped panel; no "View All" page.
- Reset Demo: red-tinted button; confirmation dialog with explicit consequence text and
  Cancel as default focus; after success, refresh dashboard/kiosk and show confirmation.
- Kiosk mode switch reflects and persists mode; no data changes.

### 6.11 Dialogs and overlays

- Use a dimmed overlay (`rgba(4,8,14,0.72)`) with a glass/light card depending on context.
- Destructive confirm: title, consequence list, Cancel (secondary) + Reset (danger).
- Ticket preview: receipt-style card, close and simulated print buttons.
- Dialogs trap focus, Esc cancels, and never obscure the only way to proceed.

---

## 7. Motion

- Purposeful only: screen/state fade 150–220 ms, button hover 120 ms, scan sweep 1.8–2.4 s,
  success check scale-in 180 ms.
- Do not animate layout continuously, loop decorative particles, or animate text.
- Respect readability: no flashing; no animation that delays the primary action more than
  ~0.3 s after a click.
- JavaFX `FadeTransition`/`ScaleTransition`/`Timeline`; keep durations in one constants file.

---

## 8. Content and tone

- Concise, human, premium: "Your parking space is ready. Drive safely."
- Vehicle: "Bike / Scooter" consistently (not "two-wheeler" in UI).
- Status vocabulary: "Available", "Occupied", "Charging bay", "Long stay",
  "Parking Session Closed", "Payment successful", "No payment required".
- Use "EV" and "EV charging" consistently.
- Dates: `24 Jun 2025, 10:24 AM`; timezone implicit (local). Dashboard duration `14 h 32 min`.
- Disclaimers: "Demonstration interface — simulated number-plate scanning."
  Exit adds "and payment." Keep them subtle (11–12 px, 55–65% opacity).
- No lorem ipsum, no fabricated bay numbers, no fake "today" metrics outside preview mode
  (preview data must be visibly isolated during Phase 1 and never shipped in Phase 7).

---

## 9. JavaFX implementation rules (Phases 1–5, historical)

- FXML for structure; CSS for styling; controllers only for behaviour/state.
- Every displayed value that represents a real session comes from a model/service — never
  hardcoded in FXML.
- No inline `setStyle` scattered through controllers. Reusable style classes only; dynamic
  states via pseudo-classes or a small set of added/removed style classes.
- Status classes: `.is-selected`, `.is-available`, `.is-occupied`, `.is-charging`,
  `.is-error`, `.is-success`, `.is-loading`.
- Use `PseudoClass` for selection/validation/focus-driven states where practical.
- Icons: `SVGPath`/`Region` shapes or a bundled permissive icon set; consistent stroke
  weight. No remote images, no emoji as UI icons.
- All user-visible strings in FXML/controllers (no string concatenation for sentences with
  grammar quirks; compose carefully).
- Window: resizable, min 1280×720, default 1600×900 (or maximized on 1920×1080 for demo);
  kiosk should still look intentional at 1366×768.
- No use of `WebView` for UI. No CSS features JavaFX does not support.
- Verify in an actual launch; do not judge JavaFX styling from CSS alone.

---

## 10. Visual acceptance checklist (used from Phase 1 onward)

- [ ] All five screens share tokens, typography, spacing, and status language.
- [ ] Kiosk feels premium and cinematic; admin feels clean and operational.
- [ ] Frosted panels read as glass; no muddy contrast or invisible borders.
- [ ] Bay numbers and fee amounts are the visual anchors.
- [ ] Every state (selected/disabled/loading/success/error/focus) is visibly distinct.
- [ ] Status never conveyed by color alone; labels/icons present.
- [ ] No clipped or wrapped-badly labels at 1920×1080 and 1366×768.
- [ ] No full-screen mockup, remote image, or fake metric in production.
- [ ] Primary actions 56–72 px at kiosk scale and reachable without scrolling.
- [ ] Disclaimer visible; PIN never shown; demo payment clearly labelled.

---

## 11. Web implementation (Vaadin, Phase 7)

**Where it lives.**

| Area | Location |
|---|---|
| Views (Java only) | `com.hilite.parking.web.kiosk` (`KioskHomeView`, `EntryView`, `AllocationView`, `ExitView`) and `web.admin` (`AdminPinView`, `DashboardView`) |
| Shared pieces | `web.ui` (`KioskPage`, `KioskHeader`, `Backdrop`, `Icons`, `InlineMessage`, `ActionButton`, `Ticker`) |
| Design system | `src/main/resources/META-INF/resources/styles.css`, loaded with `@StyleSheet` after Lumo |

`@Theme` is not used (deprecated in Vaadin 25).

**Rules.**

- **Tokens.** The §3 palette, radii and shadows are CSS custom properties (`--hk-*` for the
  kiosk, `--ad-*` for admin). Compact rules apply at `max-width: 1499px` or
  `max-height: 819px`, the same breakpoint the JavaFX build used.
- **Glass.** Real `backdrop-filter: blur()` with a translucent-fill fallback.
- **Typography.** Tracked capitals (`letter-spacing`) are now possible and are used for the
  eyebrow and signage labels from the references. System font stacks only; no web fonts.
- **Icons.** Inline SVG built from the original 24×24 path data. Icons are
  `aria-hidden="true"` and always sit next to text or an `aria-label`.
- **Backdrop.** A constant inline SVG. The markup is a compile-time constant, never user input.
- **Timers.** The scan simulation and the header clock use Vaadin UI polling (500 ms on the
  kiosk, 10 s for dashboard refresh). There is no WebSocket push and no custom JavaScript.
- **Safety.** User input is only ever set with `setText` or as a field value, never as HTML.
- **Accessibility.**
  - Icon-only buttons have `aria-label`s ("Administrator access", "Clear number plate",
    "Backspace").
  - Errors use `role="alert"`.
  - Keyboard focus is visible (`:focus-visible`).
  - Focus moves to the new state on each Exit step.
  - Dialogs trap focus and close on Esc.
  - The PIN keypad does not steal focus.

**Deliberate web differences from the JavaFX screens.** Each one fixes an item in
`AUDIT_REPORT.md` §4.1.

- The dashboard is **live**: KPIs, floor maps, cars/bikes, EV use, peak hour and long-stay
  alerts all come from the database. Nothing comes from `PreviewData`, and there is no
  "PREVIEW DATA" pill.
- A compact 24-hour CSS bar chart sits under the peak-hour figure, with the peak bar
  highlighted. When there is no history it shows "No entries recorded yet." and the peak hour
  shows "—". No chart library is used.
- The kiosk-mode selector **saves the mode and stays on the dashboard**, then shows a
  confirmation. The public kiosk home (`/`) follows the saved mode.
- The Reset dialog copy states what really happens. A success notice appears only after the
  transaction commits.
- The bay-details default reads "Select a bay" with no misleading "Available" pill.
- Allocation signage reads "GROUND FLOOR · G" / "FIRST FLOOR · F1" instead of the invented
  "LEVEL 01/02".
- Minimum text size is 11 px on the kiosk and 12 px on the admin screens. Muted admin text is
  darkened for contrast.
- The vehicle caption reads "Bikes & scooters", never "two-wheelers".
- A locked `/admin/dashboard` **forwards** to `/admin`, so the address bar matches the screen.

**Verified layouts** (Phase 7 browser walkthrough, headless Edge):

- Entry, Allocation, Exit (scan, quote, payment, success), Admin PIN, Dashboard and the Reset
  dialog at 1920×1080.
- Entry and the Exit quote at 1366×768.
- The dashboard at 1366×768 and 1024×768.

No horizontal scrolling, clipping or overlap was found.
