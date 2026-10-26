# Translation workflow plan

Status: approved scope; implementation in progress. Language drafts remain
unreviewed until the agreed Sonnet pass is available. No release acceptance is
implied by this plan.

Primary adjudication (2026-10-26): Turkish uses polite “siz”; Japanese and Korean
use neutral labels and conventional polite explanatory text. Sol medium remains
authorized for translation drafting only. Application code changes, including
Gradle and the picker, require a Claude implementer; OpenAI models are review-only
for that code. Wait for the primary's main-merge signal before implementation,
and for the overnight string changes before reconciling the existing drafts.

## Decisions so far

- **Where translations live:** in the repository, as ordinary
  `values-xx/strings.xml` files. No Weblate or Crowdin for now. Weblate becomes
  worth it once human volunteers show up; the glossary format below keeps that
  door open.
- **Who drafts:** GPT-6.1 Sol at medium effort, with string notes, glossary and
  language page as context. Automated checks precede Sonnet 5.5 at medium effort
  as the second-family reviewer. Findings are adjudicated, not applied blindly.
- **DeepL:** tested as a baseline; not selected for the workflow.
- **Labeling:** languages nobody has checked ship marked as machine-drafted,
  in Settings → About and on the language's page, not in the language picker.
- **Corrections:** people send them as GitHub issues or pull requests. The AI
  preserves existing translations unless the source changed or a specific
  correction was accepted; it never performs unsolicited style rewrites.
- **Changed meaning gets a new key.** When an English string changes meaning,
  it gets a new key, so lint reports it as missing in every language. A pure
  wording fix in English keeps the key.
- **Completeness:** every shipped language is complete, so lint's
  `MissingTranslation` and `MissingQuantity` stay errors.
- **Release notes:** English only.
- **About screen:** translate navigation, headings and ordinary UI text, including
  the machine-drafted explanation. Keep GPL license text and attribution notices
  in English, preserving names and legal wording. Exclude those bodies from AI
  translation inputs; if stored as Android string resources, mark them
  `translatable="false"`. Inspect the new About screen during source preparation.
- **Register:** informal in the European languages, starting with German "du"
  (already in use). Exceptions and the non-European languages are open; see
  below.

## First set of languages (ships with 0.3.0)

The agreed list is frozen for this release (codes identify intended locales;
Android resource qualifiers are chosen and verified during implementation):

| Group | Languages |
|---|---|
| Source | English (en-US, en-GB) |
| Western Europe | German (de-DE, de-AT, de-CH), French (fr), Spanish (es), Italian (it), Dutch (nl) |
| Portuguese | Brazilian (pt-BR) and European (pt-PT), with distinct wording |
| Nordic | Swedish (sv), Danish (da), Norwegian Bokmål (nb), Finnish (fi) |
| Central and Southeastern Europe | Czech (cs), Polish (pl), Hungarian (hu), Croatian (hr), Romanian (ro), Greek (el) |
| Cyrillic | Russian (ru) and Ukrainian (uk), always together |
| Other agreed languages | Turkish (tr), Simplified Chinese (zh-Hans), Traditional Chinese (zh-Hant), Japanese (ja), Korean (ko), Arabic (ar), Hebrew (he) |

Afrikaans and further additions wait for demand or a contributor. Traditional
Chinese gets its own terminology review, not just character conversion.

### Regional variants

English and German use en-US, en-GB, de-DE, de-AT and de-CH. The Swiss variant
uses Swiss Standard German spelling, not a dialect translation.

- **Dates, times and numbers come from the system's locale data, not from the
  translations.** With "Follow system" on an Austrian TV, the app should already
  show Austrian forms such as "Jänner" (not yet checked on a device). The app's
  own language picker currently stores
  only "de" or "en" and drops the region, so picking German there gives German
  (Germany) formats. The picker and the locale config get one entry per variant.
- **The chosen language drives dates and numbers too, in every language.** The
  picker stores the full tag (de-AT, not de). Picking a bare language uses that
  language's default region, except when it matches the system language: then
  the system's region is kept (French on a Canadian TV stays fr-CA). Formatters
  that fall back to `Locale.getDefault()` must be checked to follow the picker on
  Android 9–12 as well as 13+.
- **Strings stay shared.** Each variant uses its language's file and overrides
  only the strings that differ:
  - en-GB: British spellings (programme, cancelled, colour).
  - de-CH: "ss" instead of "ß" (6 strings today).
  - de-AT: only where Austrian wording differs; possibly none.
- Lint does not require a regional override to be complete; missing strings fall
  back to the language file.

Arabic and Hebrew are the right-to-left test. Arabic also has six plural forms,
Croatian, Czech, Polish, Russian and Ukrainian three or four.

## Register per language (proposal)

| Language | Proposal | Why |
|---|---|---|
| de, nl, sv, da, nb, fi, es, it, fr, hu, pl, hr, ro, el, pt-BR, pt-PT | informal where direct address is natural | owner's European-language preference; record regional conventions rather than mechanically copying pronouns |
| cs | informal unless the Czech contact objects | |
| ru, uk | polite "вы" / "ви" | the usual form in Russian and Ukrainian software; informal reads as overly familiar |
| tr | polite “siz” | approved by the primary; appropriate for settings and instructional text |
| zh-Hans, zh-Hant | avoid pronouns; polite address where needed | review terminology separately for each script |
| ja, ko | neutral labels and conventional polite explanatory text | approved by the primary; record exact register in each language page |
| ar, he | gender-neutral phrasing | both languages mark the reader's gender in "you"; buttons as infinitives or nouns, sentences without direct address where possible |

Each language page records the final choice.

## Knowledge that stays in the repository

- **String notes** in `app/src/main/res/values/strings.xml`: meaning, where the
  string appears, maximum length. Only for tight or ambiguous strings.
- **Glossary** as CSV (Weblate can import it): one row per term, such as
  Recording, Timeshift, Guide, Channel tag and Go live, with the chosen word per
  language. Seeded from Tvheadend's own web-interface translations (GPL), so the
  app matches the server's words.
- **One page per language:** register, punctuation and quotation marks,
  decisions with reasons, and who checked it (or "machine-drafted").
- **How-to page:** the exact AI instructions, so any model or person can repeat a
  round the same way.

## Steps

### Before 0.3.0

1. **Model selection complete.** Owner selected Sol medium → Sonnet medium.
   Trials used 71 resources in German, Czech, Polish, Arabic and Hebrew,
   including larger-model low/medium comparisons and a DeepL baseline.
   Sonnet's blinded checker trial identified six of seven seeded defects and
   requested a byte check for the remaining non-breaking-space defect. Additional
   suggestions need adjudication; no false-positive rate or native-language
   acceptance is established. Reports: `build/handoff/translation-trial/report/`.
2. **Shipped-language gate.** One list of shipped languages in Gradle drives
   `androidResources.localeFilters`, the generated locale config and the
   language picker. The picker shows each language in its own name from
   `Locale` and no longer needs a string per language. This replaces the
   hand-written `AppLanguage` enum entries and `locales_config.xml`. To verify
   while doing it: that the generated locale config respects `localeFilters`,
   and which Hebrew folder (`he` or `iw`) Android 9 and later resolve.
3. **Test locales.** Turn on the pseudo-locales en-XA (long text) and ar-XB
   (right-to-left) for debug builds. Each release candidate gets one long-text
   and one right-to-left screenshot set; before 0.3.0, one right-to-left check
   on the TV as well.
4. **String notes and the last British spelling** ("Tunnelled") in the English
   strings.
5. **Glossary, language pages and how-to page.**
6. **Wait for the screen strings to settle.** The onboarding and connection
   editor redo and the Channels, Guide and Recordings work all change strings.
   Coordinate with their sessions before drafting.
7. **Draft the agreed slate and regional overrides.** Preserve reviewed German
   and other accepted text. Run XML, key-completeness, placeholder, plural-category,
   escaping and Unicode checks, then Sonnet review and adjudication. Run Android
   lint and compilation; capture representative long-text, CJK and RTL surfaces.
   Character limits guide review but do not prove rendered fit. Complete the
   physical-TV RTL check before release.
8. **Credits and labels** in Settings → About (already in the 0.3.0 scope).

### After 0.3.0

- Each release: translate new or meaning-changed keys and apply accepted
  corrections using the same how-to. Review wording-only source changes for
  relevance without automatically rewriting translations.
- Human corrections through issues and pull requests; the language page then
  names the reviewer.
- Weblate once volunteers appear.
- Store listing text later, through the same workflow.

## Needs the owner

- The plan is approved for implementation; public announcements still need approval.
- The full language slate, including de-CH, and the model choice are agreed.
- Asking the Czech and Croatian contacts to look over their languages.
- Any public beta-tester announcement (Tvheadend forum, LibreELEC forum,
  TVHClient and TVHStream trackers, #hts). Each one is public and waits for an
  explicit OK.
- Human observation of the physical-TV acceptance check when scheduled.
