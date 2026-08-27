---
description: Run a bounded Android TV device check through the repository wrapper.
agent: build
---

Use `$ARGUMENTS` as the `./tools/device` subcommand and arguments. If no argument
was supplied, run `./tools/device doctor`. Follow the `android-tv-device-testing`
skill, respect the configured production/test role without bypassing it, avoid
broad device dumps, and report which checks still require a physical remote
action or human-visible judgment.
