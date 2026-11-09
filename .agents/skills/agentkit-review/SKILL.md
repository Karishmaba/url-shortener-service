---
name: agentkit-review
description: Review a combined Agent Kit candidate and request controlled integration into the target repository.
---

Start the graph's final review task after all prerequisites succeed. Compare specification, design, implementation, tests, documentation, and actual validation logs. Check scope and distinguish required behavior from optional design choices. A review task has an empty write scope: inspect the supplied combined workspace without changing it.

If defects remain, fail the review and explain the evidence; do not claim release readiness. If it is acceptable, complete the task with a substantive review note, then request `agentkit integrate <review-id> --feature <slug>`. This produces a diff and a version-bound human approval before modifying the target checkout. Wait for the user to approve; never approve yourself. Rerun integrate only after the recorded approval, then report the actual integration result, tests, residual risks, and evidence paths. No deployment, Git push, or external publishing is implied.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
