---
name: agentkit-implement
description: Execute and adapt an Agent Kit task using native coding-agent tools in an isolated workspace.
---

Read status and choose a pending task whose prerequisites succeeded. Run `agentkit task start <id> --feature <slug>` and work only in the returned workspace. Inspect source, choose an implementation approach, edit using native tools, inspect feedback and revise your approach. The CLI does not call an LLM for you.

If the host supports subagents and delegation is authorized, independent ready tasks may run concurrently in their distinct workspaces. Give each worker its task contract, scope, input versions, and exit criteria. Otherwise execute ready tasks sequentially and say concurrency was unavailable. Do not invent subagents or results.

For an agent/review task, inspect the changed files and record evidence with `agentkit task complete <id> --feature <slug> --note "<result/review reasoning>"`. For a validation task, use `agentkit validate <id> --feature <slug>`. On validation failure, inspect the complete saved log; `task start` on the failed validation task provides the next repair workspace. Apply a scoped repair there and call `validate` again. Stop after the initial validation and two repairs. Do not weaken tests to make them pass.

If the task cannot proceed, record `task fail ... --note` or `task handoff ... --note` and explain what a human needs to resolve. When a structured scope/approval check fails twice, stop and hand off; do not loop indefinitely. Do not integrate work into the original checkout automatically.

Read `.agentkit/constitution.md` and the feature artifacts before acting. Use `agentkit context --feature <slug>` to discover current paths/state. If Agent Kit is not installed or a required tool is unavailable, stop and explain the missing prerequisite. Do not invent successful executions.

Task code changes belong in the isolated workspace returned by `agentkit task start`. Preserve the approved scope and inspect actual files/results while choosing your next action. Specifications/plans are edited in the original project's `specs/<slug>/`; synchronize changes before executing affected tasks.

Approvals must be made by the user. Never approve your own proposal, impersonate a reviewer, expand the validation allowlist, or bypass a failed gate. When approval is pending, show its exact request, candidate version, diff, and risk and wait for the user. The CLI's deterministic checks complement, rather than replace, the native agent's filesystem/process permissions.
