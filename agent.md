<!--
SPDX-FileCopyrightText: 2026 The GKUICalculator Project
SPDX-License-Identifier: Apache-2.0
-->

# Repository access guidelines for agents

These rules are mandatory for automated agents working in this repository:

- Always use `git clone` or `git fetch` to read repository contents. Do not use `gh api` to retrieve repository files individually.
- When querying upstream repositories, prioritize shallow clones (`git clone --depth 1`) over API-based tree traversal or per-file requests.
- Never invoke `gh api` inside a loop. When multiple remote objects must be queried, use one batched GraphQL request instead.
- If GitHub returns HTTP `403` or `429`, read the `retry-after` response header and wait for the full specified duration before retrying. Immediate retries are strictly prohibited. If no retry duration is supplied, stop and report the rate-limit failure rather than guessing or retrying immediately.
