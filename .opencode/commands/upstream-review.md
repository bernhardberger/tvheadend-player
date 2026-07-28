---
description: Review the current branch for a clean generic upstream contribution boundary.
agent: android-reviewer
subtask: true
---

Apply the `tvhstream-upstream-contribution` skill. Inspect remote URLs, fetch all
configured remotes, compare the current branch with the `Preclikos/tvhstream`
default branch, and classify commits as generic, product-specific,
appliance-specific, or mixed. Do not edit, rebase, push, or open a pull request.
Return concrete blockers and a proposed clean commit range.
