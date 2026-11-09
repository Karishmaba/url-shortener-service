---
name: agentkit-specify
description: Create an actionable feature specification in a project initialized with Agent Kit.
---

Use the user's requirement to choose a descriptive lowercase feature slug. Run `agentkit feature <slug> --requirement "<requirement>"` or use `--file` for an existing requirement. Refine `specs/<slug>/spec.md` with observable behavior, acceptance criteria, exclusions, assumptions, and open questions. Preserve the user's scope; ask only questions that block a useful specification. Run `agentkit sync --feature <slug>` after refining the spec. Tell the user the feature slug and remaining decisions. Do not implement application code yet.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
