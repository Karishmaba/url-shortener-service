---
name: agentkit-plan
description: Design architecture and a dependency graph for an Agent Kit feature.
---

Inspect the specification and repository, then write `specs/<slug>/plan.md` grounded in existing interfaces, dependencies and risks. Edit `tasks.json` into a real task graph: choose task IDs, prerequisites, consumed `spec`/`plan` artifacts, file scopes, task kinds, and instructions. The supplied graph is an example, not a required technology or task list.

For coding work, identify an implementation contract that independent code/test/documentation workers can share. Use direct prerequisite edges to express entry gates. Validation tasks reference owner-configured validation IDs, not command strings. If no appropriate allowlist entry exists, present the proposed argv to the user; do not register it yourself. Exit gates must include actual validation and a final review. Use `agentkit plan --feature <slug>` to validate/register the graph only after the plan is ready. Later source changes use `sync`; graph changes require a new feature.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
