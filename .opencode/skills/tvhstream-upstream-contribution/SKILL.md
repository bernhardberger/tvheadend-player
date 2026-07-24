---
name: tvhstream-upstream-contribution
description: Use for TVHStream upstream syncs, generic feature extraction, GPLv3 attribution checks, commit-range cleanup, issue fixes, and pull-request preparation against Preclikos/tvhstream.
---

# TVHStream Upstream Contribution

TVHeadend Player is a standalone product descended from TVHStream. Remote names
can differ between existing and fresh clones, so inspect their URLs before every
upstream operation. The intended roles are:

- product repository: `bernhardberger/tvheadend-player`, normally `origin`
- predecessor repository: `Preclikos/tvhstream`, normally read-only `upstream`

Never push product or appliance commits to the predecessor repository.

## Classify first

Before coding or extracting commits, classify the work:

- **Generic:** no TVHeadend Player package, product branding, household device,
  server, HOME-default, or TCL-specific assumption. Candidate for predecessor.
- **Product-specific:** branding, public UX, repository metadata, and release
  policy. Product repository only.
- **Appliance-specific:** HOME behavior, TCL GUIDE interception, deployment,
  signing, or household policy. Product repository only.
- **Mixed:** split a generic primitive/policy from the appliance integration.

## Sync workflow

```bash
git remote -v
git fetch --all --prune
git status -sb
git log --oneline --decorate --graph -20
```

Do not rebase, merge, reset, cherry-pick, or force-push without first showing the
exact commit graph and proposed range. Preserve published appliance history.

## Upstream-ready gate

1. Compare the candidate range against the configured `Preclikos/tvhstream`
   remote's default branch.
2. Confirm it contains no TVHeadend Player application ID, local device/server address,
   credentials, signing assumptions, household copy, or appliance-only docs.
3. Keep the patch narrow and match upstream naming/style.
4. Add a regression test that proves the generic behavior.
5. Run upstream-relevant tests and `./tools/verify`.
6. Exclude fork-only native binary manifests, release policy, device roles, and
   product identity unless the upstream change specifically requires them.
7. Retain upstream copyright and GPLv3 licensing.
8. Summarize behavior, tests, and any Android-device evidence without exposing
   private runtime data.

Publishing a branch or opening a pull request requires explicit user approval.
