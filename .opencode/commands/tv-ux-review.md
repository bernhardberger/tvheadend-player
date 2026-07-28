---
description: Run the read-only TV UX reviewer on supplied screenshots, handoff evidence, or a UI change.
agent: tv-ux-reviewer
subtask: true
---

Use `$ARGUMENTS` as the complete evidence allowlist and review scope. Treat a
screenshot, image, handoff, or inventory as current evidence only when its exact
path is supplied and `$ARGUMENTS` identifies it as current. Never glob, list, or
search the repository for additional visual evidence or handoffs.

Apply `android-tv-compose-ux`. Use the two-pass method only when current visual
evidence is supplied. For a source-only UI-change review, skip the visual pass,
inspect only the scoped source needed to evaluate interaction behavior, and do
not infer appearance from code. Ask for the smallest missing evidence only when
the requested conclusion requires visual or physical-TV proof. Lead with
user-impact findings, label each finding's evidence class, and separate claims
that still require the designated physical TV.
