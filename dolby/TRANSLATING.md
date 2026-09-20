# Xiaomi Dolby Atmos: translation handoff for ChatGPT online

Updated: 2026-09-20. Scope: translations only.

## Copy this task into ChatGPT

> Use the connected GitHub tools to read PocoF3Releases/hardware_xiaomi on
> aosp-17. Follow this guide and complete missing Dolby UI translations for
> all listed locales, preserving correct existing translations. Finish the
> entire language set and validate it before publishing. You are authorized
> to commit the translation changes and push directly to aosp-17 once complete;
> no additional confirmation is needed. Do not force-push. Report the resulting
> commit links, coverage and checks performed. Do not change English resources
> or application code, and do not use paid external translation APIs.

## Repository and connected GitHub workflow

Target: `https://github.com/PocoF3Releases/hardware_xiaomi`, branch `aosp-17`,
resource path `dolby/res`. Use the connected GitHub capabilities to read the
current branch, existing locale files and applicable repository instructions.
Verify that write/push tools are actually available; account connection alone
is not proof that this session exposes write operations.

The live branch is authoritative. An attached resource ZIP is an optional
snapshot; do not overwrite newer work with it. Recalculate the source inventory
below from current English XML. Keep the requested branch; never target aosp-16.

Work in manageable language batches, but complete all listed locales before
publishing. Validate the assembled change set, refresh the remote branch head,
and preserve any concurrent changes. If English keys changed, update translations
and repeat affected checks. Use a normal commit/push or the connector's equivalent
atomic commit against the checked branch head. Never force-push or rewrite history.
Preserve unrelated files. Include accurate translation and validation details in
commit descriptions and `Co-authored-by: codex <noreply@openai.com>`. State the
actual model only if known; do not copy another model's provenance mechanically.

If the connector cannot write, say exactly which capability is unavailable and
return a downloadable patch/ZIP plus coverage results instead. Never claim a push
that did not occur. After publishing, verify the remote commit and changed files.

## Source inventory

English defaults: `dolby/res/values/*.xml`. Scan every XML file, especially
`strings.xml` and `runtime_strings.xml`; do not translate only the first file.
Translatable top-level string/plural/string-array counts at this snapshot:

| File | Translatable resources |
|---|---:|
| `arrays.xml` | 0 |
| `config.xml` | 0 |
| `runtime_strings.xml` | 30 |
| `strings.xml` | 101 |
| `ui_strings.xml` | 6 |
| **Total** | **137** |

Translate user-facing strings and any translatable plurals/arrays. Do not
translate booleans, integer arrays, IDs, colors, dimensions, configuration values,
resource references, or anything marked `translatable="false"`. Profile-entry
arrays reference string resources: translate those strings, not array references.

## Current coverage snapshot

After the 2026-09-20 translation sync for the six compact UI strings introduced
with the settings-hierarchy/equalizer UI refresh, all 86 target qualifiers have
complete effective coverage of the 137 translatable resources:

- 78 locale qualifiers contain explicit translations.
- 8 regional qualifiers intentionally inherit: `values-en-rAU`, `values-en-rCA`,
  `values-en-rGB`, `values-en-rIN`, `values-az-rAZ`, `values-es-rMX`,
  `values-es-rUS` and `values-kn-rIN`.
- Effective missing resources: 0.

The six resources in `ui_strings.xml` are deliberately compact UI copy. Keep
their translations concise rather than copying the older long-form summaries:
game-audio summary, custom-profile help, bass summary, volume-leveler summary,
reset options, and the horizontal 20-band equalizer scroll hint. Their meaning
must remain consistent with the corresponding longer resources.

## Output layout

Use `dolby/res/values-<locale>/additional_strings.xml` for missing keys. Merge if
it already exists. Read every XML in each locale and never define a resource
name/type twice. Preserve existing files and accurate translations. Unlike
XiaomiParts, Dolby currently builds only `res`; do not create an unconfigured
`Translations` directory or change Android.bp in this task.

Russian already has resources in this local snapshot: preserve its correct
translations and add genuinely missing keys. Do not assume Russian or Ukrainian
is complete merely because XiaomiParts was complete; these are different apps.

## Target locales — same retained set as XiaomiParts

86 Android locale qualifiers, copied from the actual XiaomiParts `Translations` directories:

```text
values-af
values-ar
values-as
values-ast-rES
values-az
values-az-rAZ
values-be
values-bg
values-bn
values-bs
values-ca
values-ckb
values-cs
values-cy
values-da
values-de
values-el
values-en-rAU
values-en-rCA
values-en-rGB
values-en-rIN
values-eo
values-es
values-es-rMX
values-es-rUS
values-et
values-eu
values-fa
values-fi
values-fr
values-fy-rNL
values-ga-rIE
values-gd
values-gl
values-gu
values-hr
values-hu
values-hy-rAM
values-in
values-is
values-it
values-iw
values-ja
values-ka
values-kk-rKZ
values-km-rKH
values-kn
values-kn-rIN
values-ko
values-lb
values-lo-rLA
values-lt
values-lv
values-mk-rMK
values-ml
values-mr
values-ms-rMY
values-my-rMM
values-nb
values-ne-rNP
values-nl
values-nn-rNO
values-or
values-pa-rIN
values-pl
values-pt-rBR
values-pt-rPT
values-ro
values-ru
values-sk
values-sl
values-sq
values-sr
values-sv
values-ta
values-te
values-th
values-tr
values-ug
values-uk
values-ur-rPK
values-uz-rUZ
values-vi
values-zh-rCN
values-zh-rHK
values-zh-rTW
```

Keep Android qualifiers exactly, including `in` for Indonesian and `iw` for
Hebrew. English regional variants may inherit the default English resources;
do not fill them with duplicate English solely to create folders. Regional
Spanish, Azerbaijani and Kannada may inherit their translated base language.
Report inherited coverage separately from explicitly translated entries. Keep
regional Portuguese and Simplified/Traditional Chinese distinctions. Do not
assume a base Chinese resource exists when only region folders are provided.

Do not recreate Kabyle (`values-kab-rDZ`), Friulian (`values-fur-rIT`) or Sardinian
(`values-sc-rIT`), removed from the XiaomiParts target set. Do not silently drop
other locales. Flag low confidence for review; English fallback is not a finished
translation except for intentionally inherited English variants.

## XML and translation rules

- Preserve resource names, formatting attributes, tags and xliff placeholders.
- Preserve each printf placeholder's index, type and multiplicity (`%1$s`,
  `%2$d`, etc.). Positional arguments may move grammatically. Never translate
  or renumber them. Preserve literal percent signs and `formatted="false"`.
- Preserve Android escapes, including literal `\n`; do not double-escape them.
  Escape XML ampersands/angle brackets and Android apostrophes/quotes correctly.
  XML parsing alone does not validate Android string escaping.
- For plurals, supply the target language's necessary quantity categories,
  retain `other`, and keep placeholders consistent. For arrays, preserve item
  ordering and count. Never translate numeric EQ arrays or profile IDs.
- Keep Dolby Atmos, Dolby, Xiaomi, POCO F3, MiSound, DAP, VQE, AC-4, USB,
  Bluetooth, CPU/DSP abbreviations, package names and property names intact.
- Keep labels short, explanations clear, and terminology consistent across files.
  Translate meaning directly; do not add marketing claims or unsupported features.

## Audio terminology

| English concept | Meaning to preserve |
|---|---|
| Volume leveler | Reduces loudness differences; not simply the volume slider |
| Dialogue enhancement | Emphasizes spoken dialogue, not a chat UI feature |
| Bass enhancement | Low-frequency enhancement, not base/default configuration |
| Graphic / intelligent equalizer | Distinct EQ controls; do not collapse them |
| Speaker / headphone virtualization | Audio effect; not proof of head tracking |
| Profile | Saved sound settings, not an account or user profile |
| Bypass during communication | Temporary media-effect bypass, not muting calls |
| Restore / reset | Restore saved state versus reset settings to defaults |
| Runtime / recovery status | Control-state information, not DSP measurements |
| Portrait / landscape tuning | Device orientation, not a photo or music genre |
| Unsupported / unavailable / disabled | Different states; preserve the distinction |

The wording of English source resources is authoritative for translation. This
file does not authorize enabling VQE, spatializer or any product capability.

## Checks and deliverables

1. Inventory English keys and locale resources; calculate effective missing keys
   using applicable Android fallback, not simply identical filenames.
2. Translate missing entries by language. Keep unrelated translations unchanged.
3. Parse XML; check duplicates, resource names, placeholder multisets, escapes,
   arrays/plurals and accidental untranslated English. Brand names may match.
4. Require complete effective coverage of all target locales before pushing.
   Intentional regional inheritance is allowed as described above. Missing keys,
   fabricated translations or unresolved low-confidence wording are not completion;
   report genuine blockers rather than deleting locales or publishing partial work.
5. Run XML, duplicate, placeholder and resource-structure checks for the full set.
   If AAPT2 is available, compile resources once at the final validation stage.
   If unavailable in ChatGPT online, explicitly report that limitation; it does
   not prohibit the authorized push after the available checks pass. Do not
   run a full ROM build or claim compilation/device tests that did not happen.
6. Once all languages are complete and available checks pass, commit and push
   the translation-only changes to aosp-17 using the connected GitHub tools.
   No additional approval is required. Keep clear language-batch commit boundaries
   if useful, but publish only after the full set is complete.
7. Verify the remote result and return commit links, changed-file counts and a
   coverage table distinguishing explicit translations, inherited and missing
   entries. Report linguistic-review limits honestly. A ZIP is optional after a
   verified push and is the fallback if write tools are unavailable.

Do not modify README artwork, SELinux, framework code, effect behavior, drawables
or capability configuration while translating. This handoff itself performs no
translations and makes no claim that all target languages are already covered.
