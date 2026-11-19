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

## Physical acceptance update: 4ad3f9e1275adea2a84bd5686b9b1c04b88691ad

The user physically accepted both small fixes: salary/bonus switching no longer selects
text or stalls, and all mortgage loan-category options display and switch correctly in
small windows. Preserve both controls and the mortgage layout; normal regression only.
This is NOT acceptance of all tax split workflows. Remaining tax work is real result
reach/copy, bonus calculation/copy, return-and-edit and state retention. Native modal
scrolling remains physically confirmed; unfinished automation is not a product defect.
Unit and currency baseline/adaptation proceed independently of full system split chains.
