---
description: Show or switch all managed orchestrator, implementation, research, and review agents between standard and OpenAI fast service-tier models
agent: repo-maintainer
---

Accept exactly one argument from `$ARGUMENTS`: `status`, `fast`, or `standard`.
Run `./tools/ai-model-tier <argument>` exactly once and report its result. Do not
edit the model assignments manually. Remind the user that a changed tier takes
effect only after quitting and restarting OpenCode.
