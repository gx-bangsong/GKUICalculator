# Small-window acceptance and remaining verification

- User physically accepted mortgage lower-quarter scrolling at 24b58aa. Preserve its
  shared coordinate fix, keyboard proportions, field editing and state restoration.
- User accepts the tax first-group layout improvements. Both the social-insurance and
  special-deduction dialogs **scroll normally on the physical device**. The automated
  inability to reach ded_medical/ded_elderly_shared is an unfinished test flow, not a
  finding that either product dialog cannot scroll. Do not redesign those dialogs.
- Current small fixes are separate: only tax_mode_toggle loses text-selection/editor
  semantics while retaining click/ripple/accessibility; only mortgage_type_group gets
  full-button, equal-slot width measurement and responsive orientation. mortgage_method
  is a different view and is not the reported clipped category group.
- Tax salary/bonus calculation, actual result copies, return-to-input and small-window
  state checks continue independently of modal automation. Original modal assertions,
  full resize/swap/exit checks and history_toolbar failures are retained, not waived.
- Follow-up order remains tax, unit, currency. Preserve converter two-row panels, pills,
  swap, middle insertion, read-only copyable results and currency asynchronous state.
  Programmer menus/swap and large-screen left history remain separate later tasks.
