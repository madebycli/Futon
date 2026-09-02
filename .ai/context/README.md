# Futon / Mihon AI Context System

This directory is the persistent handoff memory for AI agents working on Mihon/Keiyoushi extension compatibility in Futon.

## Mandatory startup procedure

Before changing code, every new AI agent MUST:

1. Read `STATE.md`, `graph.yaml`, and `HANDOFF_PROMPT.md` completely.
2. Fetch the current head of `fix/mihon-uncaught-exception-interceptor`, PR #1, and `.ci/mihon-fix-latest.json` from GitHub. Never assume the SHA in these files is still current.
3. Fetch the current `devel` head of `Kototoro-app/Kototoro` and inspect its Mihon/Tachiyomi implementation before inventing another compatibility layer.
4. Treat the newest user-provided on-device log/test result as the highest-value runtime evidence. Collapse repeated log lines into unique root causes.
5. Update `STATE.md` and `graph.yaml` after every meaningful discovery, code change, test result, upstream-reference change, or resolved/introduced regression.

## Evidence priority

Use this order when evidence conflicts:

1. Reproducible on-device behavior and the newest user log.
2. Current Keiyoushi/Mihon/Kototoro source code at exact commit SHAs.
3. Focused regression tests plus full APK builds and binary inspection.
4. Documentation.
5. Assumptions/inference.

A green unit test is never allowed to overrule a real device failure.

## Kototoro reference policy

`https://github.com/Kototoro-app/Kototoro` is the preferred reference implementation because it shares the same Futon/Kotatsu lineage and has a broad Mihon/Tachiyomi compatibility layer.

If Futon's implementation still fails for a Mihon/Keiyoushi behavior and Kototoro has a working equivalent:

- Prefer porting Kototoro's proven implementation instead of creating another speculative workaround.
- Port the relevant Kototoro code as literally / word-for-word as practical.
- Preserve logic, interceptor order, class-loader policy, retry behavior, API signatures, compatibility bridges, and tests.
- Change only what is required for Futon's package names, imports, dependency-injection boundaries, model names, UI integration, and existing architecture.
- Do not rewrite a proven Kototoro algorithm merely to make it look different.
- Port the associated tests when applicable.
- Record the Kototoro source path and exact commit SHA in `graph.yaml` and `STATE.md`.
- Kototoro is Apache-2.0; Futon is GPL-3.0. Preserve required copyright/attribution/NOTICE information and mark modified copied files as required by Apache-2.0. Do not strip upstream notices.
- Copy only the relevant implementation, not unrelated modules.

## Branch and PR safety rules

- Work only on `fix/mihon-uncaught-exception-interceptor` unless the user explicitly changes this instruction.
- Base remains `devel`.
- Keep PR #1 as draft until the user explicitly asks otherwise.
- Never merge PR #1 and never directly modify `devel` without explicit user approval.
- Do not publish a release unless explicitly requested.
- Never expose signing secrets, keys, passwords, tokens, or masked CI values.

## Required validation loop

For each real compatibility bug:

1. Extract the unique root cause from the device log.
2. Find the corresponding Futon host path.
3. Find the equivalent Kototoro path and current Keiyoushi/Mihon contract.
4. Decide whether Futon already matches Kototoro or should port the relevant implementation.
5. Add a regression test that would have caught the device failure when technically possible.
6. Run focused Mihon tests.
7. Build the full debug APK.
8. For runtime/class-loading issues, inspect the built APK/DEX to prove required classes/methods are packaged.
9. When available, build and verify the optimized signed test APK.
10. Give the user a direct APK file for device testing, not only an Actions artifact ZIP.
11. Update this context system with the result and next unresolved node.

## Context files

- `STATE.md`: human-readable current status and handoff summary.
- `graph.yaml`: machine-readable knowledge graph of causes, fixes, evidence, references, and dependencies.
- `HANDOFF_PROMPT.md`: ready-to-paste prompt for the next AI agent.

The context system itself is part of the fix. Keeping it current is mandatory, not optional.
