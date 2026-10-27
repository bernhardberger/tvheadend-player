# Translation workflow

The [approved plan](../translations-plan.md) defines the release slate. English
resources in `app/src/main/res/values/strings.xml` are the source of truth. Keep
accepted German and human corrections; do not regenerate them for style.

Arabic and Hebrew are deferred to a later release and are not shipped in 0.3.0.
Their reviewed resources remain in `values-ar` and `values-iw`, but are excluded
from `appLanguages` and filtered out of the APK. The release has 28 language-picker
entries. Debug builds retain `en-XA` and `ar-XB` for long-text and RTL checks;
these pseudo-locales are not shipped in release builds.

## Preparing a translation round

1. Start from current local `main` after fetching remotes. Coordinate pending
   screen changes with their owners. Record the source commit and resource hash
   with the draft so later changes can be identified exactly.
2. Exclude every `translatable="false"` resource. GPL license bodies, attribution
   notices, names, identifiers and URLs remain unchanged. About navigation,
   headings and ordinary explanatory text are translated.
3. Supply the source XML, adjacent context comments, glossary and language notes
   to GPT-6.1 Sol at medium effort. Use the drafting instructions below.
4. Keep unreviewed output outside `app/src/main/res`, in ignored project-owned
   `artifacts/translations/`. Adding a locale folder to resources can make Android
   select it even before it appears in the app's picker.
5. Check XML, keys, placeholders, escaping, plural categories and required Unicode
   characters. Run Sonnet 5.5 at medium effort once quota is available. Review
   findings individually; a model suggestion is not permission to rewrite text.
6. Integrate accepted drafts, run Android lint and compilation, then verify
   long text, CJK and RTL captures. Complete physical-TV acceptance separately.
7. To ship a language, add its tag to `appLanguages` in `app/build.gradle.kts`.
   That list alone decides which translations the APK keeps, the locale config
   Android 13+ shows and the in-app picker. Hebrew resources go in `values-iw`,
   listed as `he`. Until a person has reviewed the language, set
   `<bool name="language_machine_translated">true</bool>` in its folder; Settings →
   About then shows the machine-translated notice. When the first language with a
   script, region or Hebrew tag ships, build the release APK and check that its
   locale config and packaged resources contain that language and nothing else
   new, for example with `aapt2 dump resources`.

## Drafting instructions

Translate every translatable source resource in the assigned language. Preserve
resource names, formatting arguments including indexes and types, markup, escapes
and meaningful non-breaking spaces. Never translate Tvheadend, HTSP, product
names or remote key names such as GUIDE and OK. Do not add bidi control characters
without an explained need. Translate sentences as units, not word by word.

Use Android target-language plural categories. Preserve arguments used by each
sentence; do not fabricate numeric information. A plural's quantity selector may
differ from its first format argument: `recording_folder_action_failed_delete`
follows the total `%2$d`, not the failed count `%1$d`. Preserve distinctions among
canceling a future recording, stopping an active recording and deleting a file.
Keep security negations and consequences exact. Tune means switching to a channel,
not scanning for channels. A mux is a broadcast multiplex, not a channel.

Use natural concise TV wording. Length notes are guidance, not proof of visual
fit; explain necessary overruns instead of dropping meaning. Avoid introducing
formal address where the agreed register is informal. Arabic and Hebrew should
avoid gendered address. Do not claim human review or native-speaker acceptance.
Record terminology and uncertain choices with resource keys for the reviewer.

## Reviewing instructions

Compare the draft with the same frozen source, comments, glossary and language
notes. Report language, key/quantity, current text, semantic reason, confidence
and proposed correction. Separate definite defects from preferences. Check
meaning, plural grammar, reader gender/register, placeholders, negations and
terminology; do not rewrite sound text. If invisible characters cannot be
verified, request a mechanical check. Flag uncertainty rather than inventing a
native-speaker verdict. Keep the source and draft unchanged during the review.

## Keeping up with main

Before integration, compare the recorded source to the current source by resource
name and content, not line numbers or timestamps. Rebase the isolated branch only
after preserving its work. Resolve conflicts by source meaning, never by accepting
an entire side's XML file.

- New keys need drafting and review.
- Meaning changes need a new source key and new translations.
- Wording-only changes retain the key; assess whether each translation is affected.
- Removed keys are removed from translations. Remove their obsolete per-key notes;
  keep shared glossary terms still used elsewhere.
- Keys newly marked non-translatable lose localized copies.
- Unchanged keys retain accepted translations and corrections.

Repeat the comparison after the final main update. Source drift invalidates review
only for affected resources, not the entire language. Each language record names
the source revision, drafting model, review status, accepted term decisions and any
human reviewer. Machine-drafted status is not removed merely because AI reviewed it.

Release notes stay English. Weblate and store metadata are later work.
