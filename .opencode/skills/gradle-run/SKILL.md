---
name: gradle-run
description: Use when planning to execute Gradle through `gradle`, `./gradlew`, or a custom `gradlew*` wrapper script, or diagnosing a Gradle build, check, test, lint, warning, or failure.
license: Apache-2.0
metadata:
  source: "chrisbanes/skills@ded78abbe5a170c9ca0497b614f63c1a872d9f8e"
  modifications: "TVHeadend Player live-test credential isolation and primary-owned diagnosis"
---

# Gradle run

## Provenance

This skill is based on `chrisbanes/skills` commit
`ded78abbe5a170c9ca0497b614f63c1a872d9f8e`. TVHeadend Player locally added
the live-test credential isolation described below and its focused tests,
and made diagnostic delegation optional while retaining exclusive run ownership;
those changes are not upstream. The bundled `LICENSE` contains the Apache
License 2.0 terms for the upstream work and local derivative.

## Core principle

Treat complete Gradle output as a temporary artifact, never conversation
context. Every agent-initiated Gradle command goes through the compact-output
wrapper; never stream, `tee`, paste, or reopen a complete build log.

## Procedure

1. Classify the request. A focused Gradle command that only validates another
   implementation change is incidental validation. A build, check,
   warning-cleanup, or failure-investigation loop is a Gradle-centered
   workflow.
2. Resolve this installed skill's directory and confirm `python3` and
   `<skill-dir>/scripts/gradle_run.py` are available. If either is
   unavailable, stop before running Gradle directly and report the failed
   prerequisite.
3. Create one wrapper workflow before the first command:

   ```sh
   python3 <skill-dir>/scripts/gradle_run.py create
   ```

   Retain the returned opaque workflow identifier. Use only this wrapper to
   run Gradle. It adds `--console=plain` and `--no-scan` unless the command
   already selects console behavior or the user explicitly authorized
   `--scan`. For warning discovery, include `--warning-mode all` in the Gradle
   command; otherwise include it only when the user asks for it. Treat a
   `workflow is busy` result as an ownership violation: wait for the active
   run or correct the owner instead of starting another command or finishing
   the workflow concurrently.
   The wrapper removes `TVHEADEND_SOAK_CREDENTIALS_FILE` and
   `TVHEADEND_SOAK_NODVR_CREDENTIALS_FILE` from Gradle's environment by
   default so ordinary tests cannot inherit live-server activation. Set
   `GRADLE_RUN_ALLOW_LIVE_TESTS=1` on the wrapper invocation only when the
   requested command intentionally owns those live tests.
4. For incidental validation, stay in the current agent and run the smallest
   owning task with a non-empty verification question:

   ```sh
   python3 <skill-dir>/scripts/gradle_run.py run \
     --workflow <id> --scope targeted \
     --question "Does :module:test pass after this change?" -- \
     ./gradlew :module:test
   ```

   Read only the bounded JSON summary and continue from its failed tasks,
   fingerprints, and excerpt. Do not inspect its log unless a user explicitly
   requests that artifact. The summary and ledger redact common credential
   patterns; the retained full log is intentionally raw and can contain
   secrets, so never paste or reopen it as a substitute for the summary.
5. Keep ordinary Gradle diagnosis with the primary. A substantial independent
   diagnostic question may justify one read-only adviser within project delegation
   limits, but creating a child is not a prerequisite. Maintain one wrapper-run
   owner; never run competing Gradle workflows. The primary owns repository edits.
6. Reuse prior actionable summaries, group warnings and
   failures by fingerprint, and return exact file or line evidence plus the
   narrowest next command. Prefer source or compiler failure fingerprints over
   a following generic Gradle failure block. Run an initial broad command only
   when existing targeted evidence cannot answer the recorded question. The
   current owner verifies relevant changes with the same wrapper and the narrowest
   applicable task; reuse unchanged passing evidence.
7. Record `broad` only for aggregate project checks. Give every broad run a
   distinct question that a narrower task cannot answer. The wrapper flags
   repeated commands and primary failure fingerprints; if the primary failure
   repeats, stop the run loop and revise the diagnosis before running another
   unchanged command. If the wrapper is interrupted, use its recorded signal
   and retained log; it stops the isolated Gradle process group or Windows
   process tree, extracts bounded diagnostics from the partial log, and makes
   the ledger durable before returning. Only logs still represented by the
   bounded recent-run ledger are retained.
8. Finish after the requested broad validation passes, or report unresolved
   warning fingerprints and the reason validation cannot continue. Summarize
   the compact ledger, then delete only the wrapper-owned logs:

   ```sh
   python3 <skill-dir>/scripts/gradle_run.py finish --workflow <id>
   ```

   Finish retains small marker and lock metadata so repeating the same finished
   identifier is idempotent while an unknown identifier fails closed. If
   finish cannot validate the managed identifier or the workflow is active,
   leave all files in place and report the failure. This skill does not
   constrain unrelated review, exploration, implementation, or other
   subagents.

## RED/GREEN agent scenarios

1. Direct: “Run `check` and fix every warning.” RED runs repeated full
   builds with their logs in context. GREEN keeps one run owner,
   records the broad question, groups compact diagnostics, validates each fix
   narrowly, and runs the requested broad check only as final validation.
2. Novel: a final broad check finds a downstream failure after targeted tasks
   pass. GREEN records the new question, targets the owning task, and only
   broad-reruns once that task passes.
3. Repetition: an unchanged failure fingerprint survives a claimed fix. GREEN
   stops rebuilding and asks for a revised diagnosis; it does not treat a new
   question string as permission for a blind repeat. A changed source failure
   remains primary even when the following generic Gradle block is unchanged.
4. Fail closed: the wrapper or Python runtime is
   unavailable. GREEN runs no direct Gradle fallback and reports the missing
   prerequisite. An unavailable optional adviser does not block the primary.
   A valid-looking but unknown finish identifier also fails; it
   is not treated as a previously completed workflow.
5. Counterexample: “After changing this Kotlin helper, run
   `:module:test`.” GREEN uses the wrapper but keeps this incidental focused
   validation with the current agent.
6. Boundary: while a Gradle workflow runs, a user starts an unrelated review
   subagent. GREEN permits it; this skill owns Gradle output handling and
   diagnostic delegation only.
7. Interruption: Ctrl-C arrives twice after Gradle emits a diagnostic and enters
   a signal-resistant worker process. GREEN tolerates the second signal, stops
   the isolated process group, extracts the partial diagnostic, retains the
   log, and records SIGINT in the compact ledger before returning. RED re-enters
   cleanup, leaves either process running, or loses the diagnostic or
   interruption record.
8. Exclusive ownership: a second run or finish request uses an active workflow.
   GREEN fails closed before launching or deleting anything. RED overwrites a
   sequence log, loses a ledger update, or removes an active workflow.
9. Sensitive output: a Gradle property and warning contain credentials. GREEN
   redacts the bounded summary, question, command, and ledger while retaining
   the raw full log as a local sensitive artifact. RED sends or persists the
   credential in model-visible metadata.
10. Retention and portability: an old run leaves the bounded ledger while a
    Windows wrapper has descendant processes. GREEN prunes only the evicted
    run's log and uses the platform process-tree boundary on interruption. RED
    leaks unbounded logs or terminates only the Windows launcher.
