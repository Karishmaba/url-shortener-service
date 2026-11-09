---
name: agentkit-verify
description: Run configured validation and inspect evidence for an Agent Kit feature.
---

Use `agentkit status` to find ready validation tasks. Run them through `agentkit validate`, which selects an owner-configured command, executes it inside the combined isolated workspace, and records the real exit code. Read the saved log before concluding what failed. A test summary or model statement is not a substitute for the process result. Do not run ad hoc validation commands outside the configured policy.

On failure, show the symptom, relevant log, candidate version, and remaining repair budget. Repair work follows `$agentkit-implement`. Treat timeout/spawn failure as failure, never success. Check acceptance criteria beyond merely compiling: inspect whether tests exercise the required behavior and report unverified criteria explicitly.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
