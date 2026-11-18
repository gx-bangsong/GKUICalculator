# Expression-position input acceptance (work in progress)

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
- New acceptance is NOT yet declared passed. Runtime results and same-SHA APK
  must be recorded after the full expanded suite. No new physical-device
  verification has been performed by the agent.
