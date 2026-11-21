# Expression-position input acceptance

This extends digit insertion; it does not replace the existing 40-test result-role,
programmer, relationship, BMI, unit and currency acceptance suite.

## Intended rules

- Ordinary/scientific: digit and decimal edits use the existing numeric/token map.
  Four arithmetic operators and power use the insertion boundary, not the tail.
  Explicit minus corrects additive operators immediately before that boundary.
- Left parentheses and prefix functions (sin/cos/tan, inverse trig, ln/log/exp,
  square root) insert semantic tokens at that boundary. Function tokens include
  their implicit open parenthesis. Square root retains its existing prefix semantics.
- Right parenthesis requires an unmatched opening parenthesis and a preceding
  operand at the edit boundary. Smart parentheses use this same prefix context.
- Square, factorial and percentage are suffix tokens requiring a preceding operand;
  they attach there, not to the expression's last operand.
- Pi and e insert symbolic constant tokens; existing exponent-bearing constants,
  functions and pre-evaluated references stay atomic in display/model mapping.
- Replacing a selection removes only the mapped selected units. Rejected
  transactions must not mutate model text or selection and must not append instead.
- Equals, clear, inverse and angle switches remain control actions.
- Unit/currency support only their existing four arithmetic operators. Their
  decimal-expression transaction returns a new caret, or -1 without mutation.
  Adjacent-operator correction is local; a sole leading minus is not overwritten
  by another binary operator. No scientific functions are added to converters.

## Evidence tracking

- Previous scope: c97708122b134daeccdc67a843962e30bcdbd860 compiled both APKs;
  emulator run 44231671314 passed all 40 required tests (including programmer status).
- New symbol regressions first committed before production changes:
  ddcb5d4 (ordinary/currency), 6be885a (unit and required-method gates),
  d6947c5 (semantic token boundaries, rejection, selected operator replacement).
- Candidate accepted for physical-device testing:
  `29e990ce1c50823ffc4abc92e6bfde855e3c7b79`.
  UI #165 / run [44233795709](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44233795709):
  debug and test APK compilation passed; **46 executed, 0 skipped, 0 failed**.
  Build #369 / run [44233795708](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44233795708): success.
  [Candidate APK artifact 14395469677](https://github.com/gx-bangsong/GKUICalculator/actions/runs/44233795708/artifacts/14395469677).
  The artifact is a ZIP containing the APK; its reported archive size is 5,666,602 bytes.
  Download into the sandbox failed with network EOF; the original artifact link is supplied.
- User accepts the candidate automation result. Physical-device verification remains
  pending; no physical-device result is claimed by the agent. Final acceptance SHA
  awaits user device results and the complete supplemental regression.

## Short coverage inventory

Candidate **real UI tests** (ActivityScenario/Espresso, emulator, not a physical device):
- Ordinary/unit/currency four operators at `12|3`, each independently prepared;
  text, caret and evaluation/conversion; continuous `+` then `9` without repositioning.
- Selected `+` replaced by multiply in all three paths; real conversion results,
  with deterministic rates for currency.
- Scientific sin/cos/log/sqrt at a real touched insertion point, then evaluated.
- Existing result read-only/copy, relationship-input-role restoration, programmer
  single display/status/long binary, BMI and unit regressions retained.

Candidate **model-only tests running inside instrumentation**:
- `semanticSymbolBoundariesAndRejection`: function/prefix/constant insertion,
  suffix/power token placement and basic invalid right-paren/factorial rejection.
  This test checks model text/boundaries, not every scientific key's evaluated value.

Supplemental **test-only** changes (separate from candidate; not passed until CI confirms):
- UI `scientificSuffixTouchInsertionEvaluates`: square `3²+4=13`, factorial,
  percent and power; real touch/caret assertions; power then digit without repositioning.
- UI `smartParenthesesTouchInsertionEvaluates`: open/close at prefix boundaries,
  `1+(23)+4=28`.
- UI `invalidSymbolTouchPreservesSelection`: rejected factorial/multiply at the
  start preserves both displayed text and caret, then evaluates unchanged expression.
- Model `scientificModelFunctionsAndConstantsEvaluate`: sin/cos/tan, inverse trig,
  ln/log/exp, sqrt, pi/e; insertion text/caret and production evaluator numeric values.
- Model `modelParenthesesAndLocalOperatorContext`: explicit and smart parentheses,
  local adjacent-operator replacement/minus correction and evaluated values.
- Required-method lists include all five supplemental methods: **51 total**.
  No production code, focus handling, layout or workflow refactoring in this supplement.
