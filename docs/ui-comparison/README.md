<!--
SPDX-FileCopyrightText: 2026 The GKUICalculator Project
SPDX-License-Identifier: Apache-2.0
-->

# Google Calculator v10 vs GKUICalculator — UI comparison

Source of the Google side: frame-by-frame extraction from *Google Calculator v10.0 – Preview of
Unit Conversion and Pixel Solver features* (1152×2560 video, ~400 dp wide → **1 dp ≈ 2.88 px**,
so every number below is comparable with our `res/` values). The GKUICalculator side is
reconstructed from this repository's layout resources (`res/layout`, `ratios.xml`, `dimens.xml`,
`styles.xml`, `color.xml`); it is **not** a device screenshot — see
[§8 Caveats](#8-caveats).

| Sheet | Contents |
|---|---|
| [`01-calculator.png`](01-calculator.png) | keypad + mode strip, scientific pad |
| [`02-unit-conversion.png`](02-unit-conversion.png) | unit conversion, unit selection |
| [`03-history-and-more.png`](03-history-and-more.png) | history entry point, overflow surface |

---

## 1. Summary

The palette already reads as the same product: the warm cream surfaces and the olive "equals" key
match Google within a few percent. The visible differences are structural, not colour-related:

| | Google v10 | GKUICalculator |
|---|---|---|
| Mode control | **Segmented pill** `123 │ ƒx │ ⇄` (icons only), *below* the display, above the keypad | **Chip row**: `Sci` + `Calc`/`Units`/`Currency`… + overflow, one 8 % band |
| Scientific pad | **3 flat, low pills** (20.6 dp tall, 47.4 dp pitch, ~10 dp radius) inside the same 6-row block, so the display never moves | **3 rounded rows** in a separate 0.17 H band with the same 32 dp key style, so the display shrinks when `Sci` is on |
| Unit categories | Inline chips **below** the two rows, right above the keypad (`Currency Length Weight …`) **plus** a full-screen `Choose a category` picker with search | Inline chips **above** the from-row at the top of the control slot; the `Choose a category` screen does not exist, the *unit* picker is an anchored `PopupMenu` |
| Currency | **130+ currencies**, flag + name + ISO code, `Loading exchange rates…`, EUR/USD pill labels | **12 curated currencies**, `symbol ISO · name` text rows, `Loading rates…`, `Offline rates` fallback |
| Unit names | Descriptive: `Celsius (°C)`, `Kilometer per hour (km/h)`, `Horsepower (HP)` | Compact: `°C`, `km/h`, `hp` |
| Precision | 4 s.f. by default; in a conversion row it keeps the typed digits (`683` stays `683`) | 12 s.f. via `DISPLAY_MC`, then `stripTrailingZeros()` |
| Overflow menu | `Refresh exchange rate · Rates disclaimer · Clear history · Choose theme · Privacy Policy · Send feedback · Help` | `History · Leading zero · Fraction · Licenses · (tools) · (rates auto-update) · (kinship)`, history lives on a separate screen |
| Tools | Pixel Solver / "Ask math" (AI), saved formulas | 8 offline tools: Units, Currency, Mortgage, Income tax, BMI, Date, Programmer, Kinship |

## 2. Calculator surface

Measured values (Google side from the frame; our side from `ratios.xml` / `dimens.xml` /
`styles.xml`):

| Item | Google | GKUI | Δ |
|---|---|---|---|
| App width | 400 dp | 400 dp | — |
| Key pitch | **96.5 × 99.1 dp** | **96 × 106.6 dp** ¹ | +7 dp per row |
| Key size | **76.4 × 78.8 dp** | 88 × 106.6 dp | +12 / +28 dp |
| Corner radius | **≈ 37.8 dp → stadium** (r ≈ h/2) | 32 dp (r ≈ 0.30 h) | flatter |
| Gaps between keys | **≈ 4 dp** horizontal, **≈ 4 dp** vertical (glyph-free runs 4.5/4.3 dp; component bounds merge through the gutter) | **8 dp** horizontal, **0 dp** vertical ² | — |
| Outer padding | **3.5 dp** each side (keys at x = 3.5 … 393.5 dp) | **12 dp** (8 dp pad + 4 dp margin) | — |
| Row structure | 6 equal rows: **4 digit rows + 1 bottom row + a 44 dp control row** (~110 dp total) | 5 rows in one 0.45 H band + a separate 0.17 H band | — |
| Key label size | 24.7 dp ink (AC ≈ 25.7) | `PadButtonStyle` 24 sp, but **overridden to 0.4 × key height** = 42.6 dp ³ | ~1.7× |
| Formula / result | 24.7 dp ink, result `#515E3C` | 32–48 sp auto / 32 sp, result `#566642` | larger |
| Display area (full chrome) | 54 % (keypad block starts at 46 %) | **75 %** (0.30 H display + 0.08 H chip band; keypad starts at 38 %) | — |
| Scientific band | 0.00 H — the 3 rows are *inside* the 46 % keypad block (6 rows total) | **0.17 H** extra band (`advanced_pad`) on top of the 0.45 H keypad | iPad-style growth |

¹ 62 % of the height for five rows on a 860 dp-tall working area.
² `PadButtonStyle` sets only `layout_marginHorizontal`; rows share a guideline with no vertical margin.
³ `HapticButton.onSizeChanged()` calls `setTextSize(h * 0.4f)` — it overrides the 24 sp in
`PadButtonStyle`, and because the 0.45 H band is shared by five rows the label scales with screen
height (≈42 dp ink on a 1080×2392 phone vs Google's ~25 dp).

**What this feels like:** our keys are taller than they are wide (aspect 0.83) with no vertical
air, so the pad reads as a dense wall of tall pills. Google's pad is a 4 × 5 grid of near-square
"stadium" keys (aspect 0.97, radius ≈ half the key height) with a tight ~4 dp gutter, and the whole
keypad — including the extra scientific rows — occupies a fixed 6-row block at the bottom of the
screen. The single most visible difference is not the gutter: it is that **Google's keypad block is
110 dp shorter and starts ~340 dp lower** (46 % vs 75 % for the display), and that enabling the
scientific pad *adds* a band in ours (the display shrinks from 75 % to 58 %) whereas Google simply
re-flows the same 6-row block into 9 rows. Our labels are also ~1.7× larger because
`HapticButton.onSizeChanged()` sizes text from the key height.

## 3. Mode strip and scientific pad

* Google's `123 │ ƒx │ ⇄` is a **segmented control**: one 36 dp container `#F4E5D9` with the active
  segment drawn as a dark `#715B3E` circle/rounded rect. It sits **below** the display and directly
  above the pad, and it is the same control in calculator and converter modes (the active converter
  category is appended as a 4th, dark segment, e.g. `⑄ Power`).
* Ours is a row of independent filter chips (`scientific_toggle_background` + `tool_chip.xml`) plus
  a `Calc` chip for the calculator itself, so the active mode is a *olive pill* rather than a segment,
  and the calculator is one of the modes instead of the "123" segment.
* Scientific keys: Google uses **flat, low** pills — 20.6 dp tall × 67.5 dp wide, 47.4 dp pitch,
  ~11–15 dp labels, radius ≈ 10 dp — so the block reads as a different "keyboard layer" while
  keeping the keypad's exact width and column guides. The whole keypad is then a fixed 6-row block
  (3 scientific + 3 rows + AC…), which is why enabling `ƒx` does not move the display at all.
  Ours mounts a separate 0.17 H `advanced_pad` with the same 32 dp-rounded `PadButtonStyle` and 16 sp
  text, so our scientific keys look identical to the digit keys and the display shrinks instead.
* Google's scientific rows are **contextual**: `Rad`/`Deg`, `Inv`, `sin/cos/tan` are toggles-stateful;
  GKUI matches the function set (Rad/Inv/sin/cos/tan/e/ln/log/π/√/^/!) — the set is right, the
  presentation differs.

## 4. Unit conversion

Both apps agree on the essential row anatomy — `[unit pill]` above `[value]`, a `⇅` swap affordance
and a divider, then destination pill + value — and both use the same colours for it
(`#EFE0D4` pill, `#515E3C` result). Differences:

1. **Entry point.** Google opens the converter from the `⇄` segment and shows `Choose a category` as
   a **edge-to-edge screen** (search field, `Currency / Length / Weight / …` rows with unit
   summaries, selected row tinted + ✓). GKUI's `UnitConverterMode` never leaves the calculator: it
   mounts `tool_unit_control.xml` into the display's `tool_control_slot` and switches category with
   the chip strip at the top of that slot. There is no `Choose a category` screen and no unit search
   in our tree (12 categories, `assets/tools/units.json`).
2. **Unit selection.** Google's `Choose a unit` is a full screen with search and (for currency) flag
   icons; ours is an anchored `PopupMenu` (`UnitConverterMode.showUnitDropdown`) with the unit names
   plus an `Add custom unit` row. Ours is a legitimate, more compact pattern — it fits 3–10 units per
   category, which is the trade-off for not having Google's searchable list.
3. **Naming.** `Celsius (°C)` vs `°C`, `Horsepower (HP)` vs `hp`, `Kilometer per hour` vs `km/h`.
   Ours is terse; Google is explicit. For the small pill this is a genuine trade-off, but the
   unit menu could show the long name while the pill keeps the symbol.
4. **Precision.** Google shows `464,646` for `464.646 MB` (mirrors the typed digits) and
   `3.33333333` (8 s.f.) for a non-exact value. `UnitConverterMode.format()` rounds with
   `MathContext(12)` and calls `stripTrailingZeros()`, so exact results such as `683` already stay
   short — the gap is the *significand count* (12 vs ~8) and the fact that our conversions of
   inexact factors print up to 12 digits where Google stops around 8.
5. **Chip placement.** Both apps have inline category chips, but in different places: Google puts
   them **between the two rows and the keypad**, so the chip you just tapped sits directly above the
   keys you are using. In `tool_unit_control.xml` the `ChipGroup` is the **first child of the control
   slot**, i.e. above the from-pill and above the value you are editing. Moving it below the
   secondary row (or into the tool-bar band) would match Google and keep it adjacent to the keypad.

## 5. Currency

Coverage is the headline gap: **130+ vs 12** (EUR, USD, CNY, JPY, GBP, HKD, KRW, CHF, SGD, AUD,
CAD, INR). Also:

* Google's pairs are selectable from a 130-entry list with **flag icons**, the name and the ISO code
  under it; ours shows `€ EUR` in the pill and `€ EUR · Euro` in the menu — no flags, no search.
* Status strings: `Loading exchange rates…` / `Can't update exchange rates. Try again later.`
  (Google) vs `Loading rates…` / `Can't update…`? — we ship `currency_loading`,
  `currency_updated`, `currency_offline` (`Offline rates`) but **no "try again later" error string**,
  so a failed refresh has no user-visible message. Worth adding.
* Ours is EUR-based ECB with a disk cache, TTL and offline fallback (Google also caches). The
  data-model difference is fine; the vocabulary difference is not.

## 6. History, overflow and tools

* **Entry point.** Google puts a `history` icon at the top-**left** and also opens history by pulling
  the display down. Ours matches on both counts: the same `ic_history` glyph is the toolbar
  navigation icon (`topToolbar.setNavigationIcon`), and the pull-down gesture is bound to `@id/display`
  in `activity_calculator_scene.xml` (`OnSwipe dragDown`). We additionally navigate to a **separate
  `HistoryFragment` screen** (`transitionToEnd()` → `R.id.history_frame`) with its own toolbar; Google
  never leaves the calculator and shows history as a sheet with a `History` title and a `Clear`
  action.
* **History grouping.** Google labels the first group `Today`; ours uses
  `DateUtils.getRelativeTimeSpanString` (`Today`, `Yesterday`, `4 days ago`), so same-day entries are
  grouped by relative day rather than by calendar date, and a new header appears whenever that
  relative string changes.
* **Overflow.** Google's overflow lists `Refresh exchange rate · Rates disclaimer · Clear history ·
  Choose theme · Privacy Policy · Send feedback · Help` in every mode. Ours is mode-dependent
  (`menu_history`, `menu_leading`, `menu_fraction`, `menu_licenses`, plus conditional tool items) and
  has **no theme switch** — we inherit `Theme.Material3Expressive.DayNight` only, so there is no
  light/dark/system override like Google's `Choose theme`.
* **Tools surface.** Google has no tool grid: its extras are the AI "Ask math" button in the top bar
  and the `fx` segment (saved formulas). We have a `tool_more_button` opening `tool_panel_overlay`
  (a card from 40 % height with an 8-item grid). This is our product differentiator, not a parity
  gap — but it means the top bar has *two* affordances (`»` and the overflow `⋮`) where Google has
  one. Also, `R.menu.activity_calculator` is always inflated and still contains `menu_history`
  (handled in `onItemSelected`), so **History appears twice**: as the toolbar navigation icon and
  as an overflow row, both calling `mMainCalculator.transitionToEnd()`.

## 7. What is already a match

* Palette, sampled from the video and compared with `res/values/color.xml`:

  | Surface | Google | GKUI |
  |---|---|---|
  | display background | `#FEF7F2` | `#FFFBF4` |
  | number key | `#EFE0D4` | `#F0E8DF` |
  | operator key | `#FDDEBC` | `#F8DEBF` |
  | AC key | `#FFDDB4` | `#FFE2C0` (action) / `#EEE8DF` (clear) |
  | equals key | `#515E3C` | `#506443` |
  | selected chip / segment | `#715B3E` | `#796849` |
  | unit pill | `#EFE0D4` | `#F1E8DC` |
  | result ink (green) | `#515E3C` | `#566642` |

  Every pair is within ~6 units per channel (ΔE ≲ 3), i.e. the palette is already the same idea.
* Right-aligned, auto-shrinking formula/result with tabular figures (`fontFeatureSettings="tnum, cv03,
  cv04"`), and the same one-line formula + result stack.
* `⇅` swap between source/target rows with a hairline divider and pill-shaped selectors in both
  converters (`tool_unit_control.xml`, `tool_currency_control.xml`).
* The dark, "active" treatment for the selected category (Google's dark segment and our
  `calculator_chip_selected_background` are the same colour family).
* A `drag_handle` (`24 × 4 dp`) and a pull-down gesture bound to the display for history, plus the
  same `ic_history` glyph in the same top-left slot.

## 8. Caveats

* No JDK/Android SDK is available in this environment, so these are **renders of the `res/`
  contract** (colours, dp values, ratios, strings), not screenshots of the running APK. Where the
  code computes geometry at runtime the report says so (e.g. the `HapticButton` 0.4 × height text
  size). The repository's CI job `Android UI on emulator` publishes real
  `calculator-default.png` / `calculator-units.png`; once a run is reachable, drop them into
  `ours/` and the sheets can be rebuilt 1:1.
* Google measurements assume 400 dp width (1152 px / 2.88). If the device used a different density
  the px→dp conversion scales, but all *ratios* (key gaps vs key width, pad start percentage) are
  unaffected.
* The Google frames come from a preview video that includes onboarding overlays (`Math made easier`),
  the AI sheet and a floating touch-indicator bubble; frames were chosen to avoid those where
  possible, and `google/g5-history.png` keeps the original frame with the two history affordances
  ringed for clarity.

## 9. Suggested next steps (ordered by visual impact)

1. **Keypad geometry and label size** — keep the ~4 dp gutter but make the keys near-square
   (Google: 96.5 × 99.1 dp pitch → 76.4 × 78.8 dp keys, radius ≈ h/2) by giving the pad a fixed
   5-row block instead of three percentage bands, and stop sizing labels from the key height:
   replace `h * 0.4f` in `HapticButton.onSizeChanged()` with ~0.32 × height (Google's ink is
   ~25 dp on a 78.8 dp key) or a max-capped `sp` value.
2. **Replace the chip row with a segmented control** for `123 / ƒx / ⇄`, keeping tool chips as a
   second, separate row only when a tool is active — this also fixes the `Sci`/`Calc` duplication.
3. **`Choose a category` parity** — an optional full-screen picker with search for the unit
   converter (the `Choose a unit` popup can stay for the fast path), so unit names can be shown in
   full (`Celsius (°C)` instead of `°C`) without widening the selector pill.
4. **Restore inline category chips below the rows** (per `tool_unit_control.xml`'s stated design) and
   keep the row stack above them.
5. **Number formatting** — keep the trailing-zero trimming we already do, but drop `DISPLAY_MC`
   from 12 to ~8 significant digits so converted values read like Google's (`424.39652429` →
   `424.3965`) and the result line stops overflowing with long decimals.
6. **Currency coverage + error copy** — grow past 12 codes if offline data allows, and add a
   "can't update rates, try again later" string next to `currency_offline`.
7. **Menu tidy-up** — remove `menu_history` from `res/menu/activity_calculator.xml` (it duplicates
   the toolbar navigation icon, which already opens history) and consider a `Choose theme`
   (light/dark/system) item to match Google.
