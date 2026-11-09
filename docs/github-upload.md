# GitHub upload handoff

The files are prepared locally. No commit, push, remote creation or deployment was
performed by the preparation step. The learning project now has an empty local Git
repository on main. The toolkit already had a Git repository; the original application
already has an origin remote. Existing changes were preserved.

## Recommended repositories

1. `agentic-orchestrator-kit`: reusable orchestration toolkit. Read
   docs/deliverables.md, docs/architecture.md and docs/scenarios.md.
2. `url-shortener-service`: integrated greenfield application and tests. Read
   README.md and docs/delivery.md.
3. Optional `agentic-url-shortener`: original application. It has existing uncommitted
   changes, including removal of the old Java workflow-runner. Inspect those deletions
   before committing; they were not made by the publication preparation step.

Each repository is self-contained. Agent Kit is not needed to run either application.
Do not copy directories from one application into the other.

## Review the upload contents

In each repository:

```sh
git status --short
git diff --check
git ls-files --cached --others --exclude-standard
```

The preparation scan found no uploadable runtime databases, targets, IDE files,
credentials directories, or common credential-token patterns. This is a bounded
pattern check, not a guarantee about arbitrary sensitive prose or historical commits.
Private Agent Kit runtime state, full snapshots, local database files and caches are
ignored. Specifications, policy, reusable skills and portable evidence are included.
Review project text before choosing a public audience.

## Commit the two primary repositories

Run separately in each repository directory, after reviewing the listed files:

```sh
git add .
git diff --cached --stat
git diff --cached --check
git commit -m "Prepare documented and verified project for GitHub"
```

If Git requests identity, configure your own name and GitHub email; none was invented
for you. Inspect `git branch --show-current` before pushing. The learning branch is
main; preserve the toolkit's existing branch name unless you intentionally rename it.

## Upload with GitHub CLI

Install/authenticate GitHub CLI if needed, then check `gh auth status`. From the
toolkit directory, after committing:

```sh
gh repo create agentic-orchestrator-kit --private --source=. --remote=origin --push
```

From the learning directory, after committing:

```sh
gh repo create url-shortener-service --private --source=. --remote=origin --push
```

These commands create private repositories. Choose `--public` instead only if you
want that audience. If a remote/repository already exists, inspect `git remote -v`
and use the existing destination rather than creating it again. For an existing
origin, use `git push -u origin HEAD` after checking the destination and branch.
No license was chosen on your behalf; add a license if you intend to grant reuse rights.

Without GitHub CLI, create an empty repository on github.com (no generated README,
license or gitignore), then follow GitHub's instructions to add its HTTPS remote and
push the committed branch. Never place a token in a remote URL or tracked file.

## After upload

Open Actions and confirm the Verify workflow succeeds. The toolkit runs its 22-test
suite, three fixture scenarios and wheel packaging. The learning app runs Maven
verification on JDK 21 and uploads Surefire/Failsafe reports even on failure.
Local success is not a claim that GitHub Actions has already passed.

## Evidence and limits

Local preparation checks on 2026-11-08: toolkit 22 tests passed; greenfield, brownfield
and ambiguous helper fixtures passed; learning app 91 unit plus 54 integration tests
passed; real packaged-app HTTP health/create/redirect/stats passed. The fixture
scenarios are scripted and do not claim three live LLM runs. Expiration and authenticated
management remain proposed extensions, not shipped functionality.

These files make the three small orchestration scenarios reproducible. If an evaluator
requires three *native-agent* runs or larger application changes, additional feature
executions are still required; do not describe fixtures as that evidence.
