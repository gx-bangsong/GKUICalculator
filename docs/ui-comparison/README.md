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

Both steps are reproducible: `python3 docs/ui-comparison/render_ours.py` rewrites `ours/` from
this repository's resources, then `python3 docs/ui-comparison/build_sheets.py` recomposes the
three sheets.

---

## 1. Summary

The palette already reads as the same product: the warm cream surfaces and the olive "equals" key
match Google within a few percent. The mode control, the key-label size and the scientific pad were
the three structural differences. The tables below describe the *implemented* state, and §9
separates what this change fixed from what is still open (key shape and gutter, unit/category
pickers, precision, currency coverage):

| | Google v10 | GKUICalculator |
|---|---|---|
| Mode control | **Segmented pill** `123 │ ƒx │ ⇄`: a 135 × 44 dp track, dark selected segment, *below* the display and above the keypad. Only the calculator's own surfaces live here — Google's other tools are in the overflow. | **Same segmented control, extended to every tool**: 44 dp track with a dark selected segment for `123 / fx / ⇄`, followed in the **same pill** by **icon-only segments** — programmer, BMI, income tax, mortgage, date, relatives. The pill spans the strip and holds every tool; there is no tool menu. |
| Scientific pad | **3 flat pills** (95 × 47.6 dp, r ≈ 24 dp, 15–17 dp labels) inside one fixed 409 dp key block, so the display never moves | **3 flat pills** (10 dp radius, 0.165 label scale) inside the fixed `pad_stack` block; pressing `fx` re-divides the rows and **does not move the display** |
| Unit categories | Inline chips **below** the two rows, right above the keypad (`Currency Length Weight …`) **plus** a full-screen `Choose a category` picker with search | Inline chips **above** the from-row at the top of the control slot; the `Choose a category` screen does not exist, the *unit* picker is an anchored `PopupMenu` |
| Currency | **130+ currencies**, flag + name + ISO code, `Loading exchange rates…`, EUR/USD pill labels | **12 curated currencies**, `symbol ISO · name` text rows, `Loading rates…`, `Offline rates` fallback |
| Unit names | Descriptive: `Celsius (°C)`, `Kilometer per hour (km/h)`, `Horsepower (HP)` | Compact: `°C`, `km/h`, `hp` |
| Precision | 4 s.f. by default; in a conversion row it keeps the typed digits (`683` stays `683`) | 12 s.f. via `DISPLAY_MC`, then `stripTrailingZeros()` |
| Overflow menu | `Refresh exchange rate · Rates disclaimer · Clear history · Choose theme · Privacy Policy · Send feedback · Help` | `History · Leading zero · Fraction · Licenses · (tools) · (rates auto-update) · (kinship)`, history lives on a separate screen |
| Tools | Pixel Solver / "Ask math" (AI), saved formulas | 8 offline tools: Units, Currency, Mortgage, Income tax, BMI, Date, Programmer, Kinship |

## 2. Calculator surface

Measured values (Google side from the frame; our side from `ratios.xml` / `dimens.xml` /
`styles.xml`):

| Item | Google v10 (measured) | GKUI (now, from `res/`) | Δ |
|---|---|---|---|
| App width | 400 dp | 400 dp | — |
| Key block | **one 409 dp block**, 456 → 865 dp, 34 dp above the gesture bar | `@id/pad_stack`, **0.47 H**, bottom-anchored | block height matches once the 34 dp inset is counted |
| Standard keypad rows | 5 rows, **78.5 dp** tall, pitch 82.8 dp | 5 rows in the block ⇒ **≈ 79.8 dp** on a 899 dp window | ≈ 1 dp |
| Scientific rows | **47.6–47.9 dp** tall, pitch 51.7 dp | 3 rows, 3/8 of the block ⇒ **≈ 47.9 dp** | ≈ 0 dp |
| Keys | **76.4 × 78.8 dp** stadium (r ≈ h/2) | 88 × 75.8 dp, radius 32 dp | we are 12 dp wider, 3 dp shorter |
| Gaps between keys | 4.5 dp horizontal, 4.3 dp vertical | 8 dp horizontal, 4 dp vertical (`button_vertical_margin`) | horizontal double |
| Outer padding | 3.5 dp each side | 12 dp (8 dp pad + 4 dp margin) | 8.5 dp |
| Key label | **24.7 dp ink** on a digit key, **15.3 dp** on a scientific key | 0.21 × width clamped to 0.42 × height ⇒ **20.2 sp** digits, 0.165 × width ⇒ **15.9 sp** scientific | digits ~4 dp small |
| Mode strip | **135.1 × 44.4 dp**, r = 22 dp, ink ≈ 21 dp, 11.8 dp from the left edge | `mode_segments`, height **44 dp**, r 22 dp, 15 sp ink, 4 dp padding | matches |
| Display top | key block starts at 46 % of the window | 0.45 display + 0.08 strip ⇒ block starts at 53 % | we keep 7 % less display |
| Display on `ƒx` | **does not move at all** | **does not move** — the pad is GONE and the digit pad inherits its 3 rows (weight 3:5 → 8) | fixed |
| Scientific band | 0.00 H — rows live inside the key block | 0.00 H — `advanced_pad` lives inside `pad_stack` | fixed |

**What this feels like:** the keypad block now occupies the same share of the screen as Google's
(a 409 dp block ending 34 dp above the gesture bar), the rows inside it are re-divided instead of
adding a band, and the `123 / fx / ⇄` strip is a 44 dp stadium group that never moves. What is
still visibly different is the key *shape* — our keys are 88 × 75.8 dp with a 32 dp radius (a
squircle), Google's are 76.4 × 78.8 dp with a radius of half the height (a stadium) — and the
horizontal gutter, which is 8 dp against Google's 4.5 dp. Both are cosmetic follow-ups: see §9.

## 3. Mode strip and scientific pad

* Google's `123 │ ƒx │ ⇄` is a **segmented control**: a **135.1 × 44.4 dp** track `#F4E5D9`, radius
  22 dp, starting **11.8 dp** from the left edge. The active segment is a dark `#715B3E` stadium
  covering roughly a third of the track (`123` ≈ 47 dp, `ƒx` ≈ 52 dp, `⇄` ≈ 36 dp) with ~21 dp
  cream ink. It sits below the display and directly above the pad, and in a converter the third
  segment *becomes* the active category (`⑄ Temperature`).
* Ours (implemented, `res/layout/tool_bar.xml` + `res/values/styles.xml`): one 44 dp pill — a
  `HorizontalScrollView` carrying `mode_segment_track_background` (radius 22 dp, matching the
  selected segment so `clipToOutline` never flattens its stadium) around a single `LinearLayout`.
  The pill fills the strip and its nine segments are weighted equally (36 dp minimum), so all of
  them are visible at once. The first three are `TextView`s selected with `setSelected()`:
  `Calculator.onModeSegmentClick()` routes `123` → calculator, `ƒx` → toggle the scientific pad,
  `⇄` → Units (and from Units → Currency). The six tool icons follow them inside the same pill, so
  the control is one track and not two. `fx` is disabled only for tools that cannot use it, and
  `ToolBarView.setModeSelection(basic, scientific, convert)` is the single place that paints the
  selection.
* Scientific keys: Google draws **flat stadium pills, 95.1 × 47.6 dp**, 4.2 dp gutter, radius ≈
  23.8 dp (half the height), labels **14.6–17.7 dp** — all four columns are as wide as a digit
  key, and the third and fourth rows of the block are still the digit rows. Ours now does the same:
  `PadButtonStyle.Scientific` gives the 18 scientific keys a **10 dp** radius, 2 dp gutters and a
  0.165 label scale (~15.9 sp), the band is 3/8 of the fixed pad block, and the block keeps its
  height, so enabling `ƒx` no longer moves the display.
* Google's scientific rows are **contextual**: `Rad`/`Deg`, `Inv`, `sin/cos/tan` are toggles-stateful;
  GKUI matches the function set (Rad/Inv/sin/cos/tan/e/ln/log/π/√/^/!) — the set is right, the
  presentation differs.

## 3b. Every tool is a mode segment (implemented after the first comparison)

The v10 video only exercises the calculator's own surfaces, so the first pass above stopped at
`123 / ƒx / ⇄` and left the eight life-calculation tools in a scrolling text-chip row. That row is
gone. The mode strip is now one continuous control in which **every tool is a segment**:

| Segment | Opens | Icon |
|---|---|---|
| `123` | standard keypad / calculator | text |
| `fx` | scientific pad | text |
| `⇄` | Units, and Currency on a second press | text |
| next icon | Programmer (base + bitwise) | `ic_tool_programmer` (Material *code*) |
| next icon | BMI | `ic_tool_bmi` (Material *person*) |
| next icon | Income tax | `ic_tool_tax` (Material *percent*) |
| next icon | Mortgage | `ic_tool_mortgage` (Material *home*) |
| next icon | Date interval | `ic_tool_date` (Material *date_range*) |
| next icon | Relatives (亲戚称呼) | `ic_tool_relationship` (Material *people*) |

Design notes:

* **Icon only**, per the v10 language: the `123 / ƒx / ⇄` segments are symbols, so the tools are
  symbols too. Each carries its tool name as a content description for TalkBack.
* **One pill, not two.** All eight segments sit in a single track: `123 / fx / ⇄` are declared in
  `tool_bar.xml` and `ToolBarView.render()` *appends* the tool icons to that same LinearLayout, so
  the pill's background and rounded outline wrap the whole control. Nothing sits next to the
  pill: the former "more" button is gone, and `ToolManager.getToolbarItems()` returns every
  registered tool in a fixed order instead of only the three most-used ones.
* Adding a tool now means adding one drawable and returning it from `getIconRes()` — `render()`
  picks it up automatically.
* Icons are `24 dp` vectors tinted from `@color/mode_segment_text_states`, so the selected tool's
  glyph turns cream exactly like the selected text segments.
* **Width budget.** Nine equal segments need 9 × 36 dp = 324 dp at minimum, which fits a 360 dp
  phone; wider screens give each segment more room. Horizontal scrolling remains only as a
  fallback for narrower windows (split-screen), where `ToolBarView` scrolls the selected
  segment into view.

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
  and the `fx` segment (saved formulas). Ours puts every tool in the mode pill as an icon
  segment, so there is no separate tool menu. Separately, `R.menu.activity_calculator` is always inflated and still contains `menu_history`
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

## 9. Status and next steps

### Implemented

1. ~~**Replace the chip row with a segmented control**~~ — done. `123 / fx / ⇄` is a 44 dp
   stadium track with a dark selected segment; the calculator is never a chip. The old text-chip
   row was then deleted entirely — see item 3.
2. ~~**Stop the display from shrinking when the scientific pad opens**~~ — done. The pads are one
   fixed `@id/pad_stack` block (0.47 H portrait) weighted 3:5; hiding `advanced_pad` hands its
   three rows to `input_pad` (weight 8) instead of changing the block's height, so the strip and
   the display stay put. `scientificPadDoesNotMoveDisplay` in
   `androidTest/java/com/android/calculator2/CalculatorToolUiTest.java` locks this in.
3. ~~**Every tool as a mode segment**~~ — done, see [§3b](#3b-every-tool-is-a-mode-segment-implemented-after-the-first-comparison).
   The old text-chip row is deleted; programmer and the five life-calculation tools are now
   icon-only segments that scroll inside the strip, and each `ToolMode` supplies its own drawable.
4. ~~**Key label size**~~ — done. `HapticButton.onSizeChanged()` sizes labels from the key
   *width* (0.21 × w, clamped to 0.42 × h and 26 sp); `PadButtonStyle.Scientific` carries its own
   0.165 scale so the flat pills get ~15.9 sp instead of the digits' 20.2 sp. The pad bands were
   re-derived from the measured frame (display 0.45 H, strip 0.08 H, pads 0.47 H) and
   `button_vertical_margin` gives the rows their missing 4 dp gutter.

### Still open, ordered by visual impact

5. **Key shape** — move from `cornerRadius 32dp` to a stadium (radius = h/2, ~38 dp on a 76 dp
   key) and narrow the keys by halving `button_horizontal_margin` to 2 dp, which also brings the
   outer padding down from 12 dp to ~6 dp (Google: 3.5 dp).
6. **`Choose a category` parity** — an optional full-screen picker with search for the unit
   converter (the `Choose a unit` popup can stay for the fast path), so unit names can be shown in
   full (`Celsius (°C)` instead of `°C`) without widening the selector pill.
7. **Restore inline category chips below the rows** (per `tool_unit_control.xml`'s stated design)
   and keep the row stack above them.
8. **Number formatting** — keep the trailing-zero trimming we already do, but drop `DISPLAY_MC`
   from 12 to ~8 significant digits so converted values read like Google's (`424.39652429` →
   `424.3965`) and the result line stops overflowing with long decimals.
9. **Currency coverage + error copy** — grow past 12 codes if offline data allows, and add a
   "can't update rates, try again later" string next to `currency_offline`.
10. **Menu tidy-up** — remove `menu_history` from `res/menu/activity_calculator.xml` (it duplicates
   the toolbar navigation icon, which already opens history) and consider a `Choose theme`
   (light/dark/system) item to match Google.

## Emulator captures

`emulator/` holds unretouched frames from the `Android UI on emulator` job (Pixel 2 profile,
API 34; the same PNGs ship in the job's `calculator-ui-screenshots` artifact):

| Capture | State |
|---|---|
| [`emulator/pixel2-api34-calculator.png`](emulator/pixel2-api34-calculator.png) | standard keypad; one mode pill holding every tool segment |
| [`emulator/pixel2-api34-units.png`](emulator/pixel2-api34-units.png) | unit converter active; the ⇄ segment selected inside the same pill |

These exist so claims about the pill are checked against pixels: the track is a single stadium,
every segment sits inside it, and nothing trails the last (relationship) segment — the old
"more"/expand-menu button is gone from both the app and the pictures.
