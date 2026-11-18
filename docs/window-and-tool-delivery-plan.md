# Window and tool delivery checklist

This extends the active branch. The accepted expression-position editing algorithms are
out of scope for rewriting. Compilation, emulator execution, evidence capture and user
physical-device acceptance are reported separately. A partial candidate is not final acceptance.

## Current sequence

1. **Tool state restoration and actual multi-window** (in progress)
   - Explicit model snapshots for all eight tools, not just the selected mode ID.
   - Nondefault inputs/options, real outputs, active fields and legal selections across actual
     orientation changes. Restore without calculator carry-over or default initialization.
   - Actual phone 50:50 split screen, divider resizing, returning fullscreen. Rotation,
     Activity recreation and simulated display-size changes are not split-screen evidence.
   - Programmer history-handle defect is a separate layout patch: measure display/handle/toolbar
     bounds first; anchor relative to display bottom, preserve the actual drag-to-history route.
     Cover ordinary, programmer (status shown/hidden/error), units and currency, portrait and
     landscape; narrow-window coverage remains pending until actually executed.

2. **Programmer selector and swap interaction** (required subsequent delivery, not implemented)
   - Two anchored pill menus, `DEC ▾` / `HEX ▾`, no repeated-click radix cycling or fullscreen list.
   - Resource-backed default and Chinese menu explanations for HEX (0–9, A–F), DEC (0–9),
     OCT (0–7), BIN (0, 1), actually visible in the menu; no Toast/code-comment substitute.
   - Unit-style swap button/divider between the two rows. DEC 10 / HEX A becomes HEX A /
     DEC 10 and back, preserving numeric meaning, automatic conversion, bit width, signedness,
     validity, pending operations and errors. Input editable, output selectable/copyable readonly.
   - Keep one main numeric panel only. Supply programmer/unit comparison screenshots and
     executed menu, swap-twice, operation, full-copy and readonly tests plus a same-SHA APK.

3. **Large-window left history + right calculator/tools** (required subsequent delivery,
   not implemented)
   - Choose using *current app window width and height*, not device type/orientation.
     Do not force a two-column layout into a short phone landscape or narrow split window.
   - Persistent independently scrolling history on the left; current display, toolbar and
     keyboard on the right. Reuse existing history storage/copy/refill/clear semantics; do not
     introduce a new history system for all tools.
   - No duplicate pull-down history/handle while history is persistent. Narrowing returns to
     single-column and restores the original history entry/drag affordance.
   - Preserve model, input, selection and history scroll position through width-class changes;
     do not duplicate history fragments or refill calculator expressions into BMI/tool fields.
   - Supply a wide two-column screenshot and an actual narrowed split-screen single-column
     screenshot with mode/data/scroll and history-operation tests. "No crash" is insufficient.

Other requested stages remain separate: history edge-to-edge polish; standard localized toolbar
long-press tooltips; limited feasibility investigation of Via/Android 11's existing Convert action.
Do not implement PROCESS_TEXT as an alleged replacement for that original action without approval.

## Scientific panel checkpoint

The partial candidate `0cb8e8e21d4e43b9a6deed44c12734d446303cae` passed the scientific
panel geometry/normal/inverse/rotation/ordinary-keyboard and basic landscape history transition
tests; its complete run still failed the eight tool-restoration tests. It is not a completed
first-stage or split-screen/tablet acceptance. Screenshot capture is independent of functional
assertions; unavailable evidence must not be reported as visually verified.
