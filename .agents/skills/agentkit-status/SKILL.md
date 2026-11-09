---
name: agentkit-status
description: Inspect Agent Kit task readiness, approvals, audit events, and operational metrics.
---

Use `agentkit status --feature <slug>`, `agentkit audit --feature <slug>`, and `agentkit report` as appropriate. Explain which prerequisites block each task and distinguish FAILED, PAUSED, STOPPED, SUCCEEDED, and INTEGRATED. Show log/diff paths for failures rather than inferring a cause from the last status line. MTTR must be unavailable when no failure has recovered. Do not start or retry tasks when the user only requests status.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
