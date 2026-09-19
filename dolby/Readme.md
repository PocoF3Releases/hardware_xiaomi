# Xiaomi Dolby Atmos integration

![Dolby Atmos](docs/dolby-atmos.png)

Android platform integration for Xiaomi Dolby effects, developed with POCO F3
(alioth) stock MIUI/HyperOS behavior as a reference. Includes the XiaomiDolby
settings app, effect control/recovery, endpoint selection and shared DMS SELinux
policy. The proprietary Dolby DSP/effect binaries remain external dependencies;
this project does not implement or replace their signal processing.

## Implementation status

“Implemented” describes source behavior, not proof that every vendor binary,
route or installed ROM has passed listening and lifecycle tests.

| Area | Implemented | Limits |
|---|---|---|
| Media controls | Enable/disable, built-in and named profiles, saved per-profile settings, profile reset and reset-all | Requires compatible native effect parameters |
| Equalization and enhancement | Graphic EQ/presets, intelligent EQ, bass enhancement, volume leveler, dialogue enhancement and speaker/headphone virtualization controls | Audible quality and leveler dynamics require route-specific listening tests |
| State recovery | Serialized reconciliation, bounded retries, audio-server recovery, profile acknowledgments and invalidation before native resets | Command acknowledgment is not DSP measurement |
| Communication handling | Playback/recording and audio-mode monitoring; bypass media processing during communication and restore saved media state afterward | Calls, game voice chat and recovery still need coverage on the fixed installed build |
| Output tuning | Speaker, wired, A2DP and USB endpoint selection; product-gated portrait/landscape speaker choices | Unknown/native routes are left to native policy; supported IDs must exist in vendor tuning |
| Spatializer integration | Capability-gated endpoint handling | Alioth does not enable Android spatializer support; Dolby virtualization is a different feature |
| Game audio | Capability-gated HyperOS game-effect controller, editable app selection and tuning in Dolby settings | Not proof of a working standalone Dolby VQE effect on alioth |
| UI | Main controls, equalizer, profile settings, runtime status and Quick Settings tile | Displayed status reflects observed/control state, not objective audio quality |
| SELinux | Shared DMS domain/service labels, audio and codec binder rules, capability-property readers and boot initializer | Must be integrated once; device trees must remove duplicate DMS declarations |

## Architecture and source map

| Source | Responsibility |
|---|---|
| [DolbyEngine.kt](src/co/aospa/dolby/xiaomi/DolbyEngine.kt) | Effect lifecycle, settings replay, profiles and processing gates |
| [DolbyController.kt](src/co/aospa/dolby/xiaomi/DolbyController.kt) | Serialized requests and runtime-state publication |
| [DolbyAudioEffect.kt](src/co/aospa/dolby/xiaomi/DolbyAudioEffect.kt) / [DolbyWireCodec.kt](src/co/aospa/dolby/xiaomi/DolbyWireCodec.kt) | Native effect commands and binary parameter encoding |
| [DolbyAudioState.kt](src/co/aospa/dolby/xiaomi/DolbyAudioState.kt) | Shared communication-state detection |
| [DolbyEndpointPolicy.kt](src/co/aospa/dolby/xiaomi/DolbyEndpointPolicy.kt) | Route-specific tuning selection and conservative fallback |
| [GameEffectController.kt](src/co/aospa/dolby/xiaomi/GameEffectController.kt) | Product-gated game-audio lifecycle and app settings |
| [profiles/](src/co/aospa/dolby/xiaomi/profiles/) / [geq/](src/co/aospa/dolby/xiaomi/geq/) | Profile storage/settings and equalizer |
| [sepolicy/](sepolicy/README.md) | DMS integration and Dolby property permissions |

Related code lives outside this repository:

- `frameworks/av/services/audioflinger/DolbyDapController.*`: selected-binary
  contract handling, DAP attachment, per-output pregain and compatible track
  metadata, bounded recovery and dumpsys bookkeeping. The QDSP path must not
  receive private commands intended for the legacy software implementation.
- `frameworks/av/media/libstagefright/ACodec.cpp`: Dolby AC-4 decoder setup and
  table-initialization error handling. AC-4 decoding is separate from DAP effects.
- `device/xiaomi/sm8250-common`: product properties, audio configuration and
  vendor prebuilt integration. MiSound/XiaomiParts is a separate effect stack.

Reference material used in development: alioth stock native sources under
`~/references_code/miui_proprietary_cpp` and decompiled HyperOS Java under
`~/miui/decompiled/jadx-frameworks`. These are local references, not build inputs
or portable dependencies supplied by this repository.

## Product integration

Ship `XiaomiDolby` and its required permissions through the product configuration.
The app is platform-signed, privileged and installed in system_ext; it is not a
standalone APK intended for arbitrary phones. Include `hardware/xiaomi/dolby/dolby.mk`
to add its vendor policy directory. Supply matching DMS/effect binaries, service
registration, audio-effects configuration and vendor tuning separately.

| Property | Purpose / alioth selection |
|---|---|
| `ro.vendor.audio.dolby.dax.support` | Enables the product Dolby integration |
| `ro.vendor.audio.dolby.dax.version` | Vendor version contract; alioth uses `DAX3_3.6` |
| `ro.vendor.audio.dolby.dap.control` | `none`, `qdsp` or `legacy`; alioth selects `qdsp` |
| `ro.vendor.audio.dolby.speaker_tuning.support` | Opt-in for verified speaker tuning IDs; alioth selects true |
| `ro.vendor.audio.dolby.spatializer.support` | Android spatializer integration; alioth selects false |
| `vendor.audio.dolby.control.tunning.by.volume.support` | Vendor spelling is intentional; alioth selects false |
| `ro.vendor.audio.game.effect` | Gates the separate HyperOS game-effect path; do not enable without HAL support |

Do not infer private-command compatibility from the DAX version string alone or
copy capability settings to another device without checking its binaries.

## Validation snapshot — 2026-09-20

The installed app reported **Enabled for media**, and AudioFlinger listed
DAP_offload and MiSound. However, boot SELinux denials prevented vendor_init from
initializing all four properties labeled vendor_dolby_config_prop. AudioFlinger
therefore reported **control=none**, with no captured DAP in its new controller.
Zero retry errors in that state do not validate the synchronization path.

Commit `3709043` adds the missing narrow initializer permission and consolidates
DMS policy here. Pair it with sm8250-common `69d847e`, which removes the old
copies. Relocation was checked for exact preservation of existing rules/labels,
and whitespace checks passed. The fixed policy has not been built and installed
as part of this validation. **Rebuild both repositories together and reboot before
claiming the new framework path works end to end.**

Captured ACDB lookup/inactive-stream errors remain under investigation; they do
not independently justify changing mixer routes or calibration IDs.

## Not implemented or not established

- A replacement for proprietary Dolby processing, arbitrary-device compatibility,
  or guaranteed support for every parameter exposed by newer HyperOS versions.
- Validated standalone Dolby VQE activation on alioth. A game-effect control path
  and communication bypass must not be described as proof of VQE processing.
- Android spatial audio/head tracking on alioth or reconstructed rotation-specific
  speaker spatializer behavior from incomplete stock code.
- Full AC-4 playback validation: plugin/library presence alone is insufficient.
- Exhaustive call, Bluetooth/USB, offload, audio-server restart and listening tests
  on the rebuilt property-policy fix; objective DSP readback is also not provided.

## Installed-build checklist

1. Confirm the four dedicated Dolby properties exist after boot and no vendor_init
   property-set denials recur.
2. During playback, inspect `adb shell dumpsys media.audio_flinger` for
   `control=qdsp`, DAP capture/attachment and retry state. These are bookkeeping.
3. Test profiles, EQ, leveler, enable/disable, speaker tuning and output changes
   at a comfortable volume; save and restore the original settings.
4. Exercise communication entry/exit and media recovery, then restart recovery
   in a controlled test. Confirm no stuck bypass or repeated native failures.
5. Test AC-4 with a verified stream containing an actual AC-4 audio track and
   inspect decoder selection/errors independently of Dolby enhancement settings.

No build is necessary for README or artwork changes. Functional/policy changes
require a matching installed build before release conclusions can be drawn.
