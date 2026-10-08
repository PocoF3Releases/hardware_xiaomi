# Alioth AW8697 vibrator

The AIDL V2 service uses Alioth's stock AW8697 kernel driver and waveform RAM.
It runs as `system` in `hal_vibrator_default`; production playback needs no root,
Magisk module, stock MIUI service or RichTap library.

## Active interfaces

| Interface | Consumer and purpose |
| --- | --- |
| Input device named `aw8697_haptic` | HAL uploads/erases effects with `EVIOCSFF`/`EVIOCRMFF`, then starts/stops them with `EV_FF`. |
| `custom_wave` | HAL checks RAM readiness, negotiates RAM gain, refills bounded waveform samples, and reads queued/hardware playback status. |
| `gain` | HAL updates amplitude for timed `on()` vibrations. Predefined effects carry their own gain in the negotiated FF payload. |
| `f0_value` | HAL reads cached resonance. It never reads `f0`, which triggers calibration. |

The sysfs attributes are under
`/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/`.
The driver retains its legacy FF ABI; the HAL uses tagged direct gain only when
the driver advertises it.

## Shipped firmware and consumers

These two original vibrator prebuilts are listed in Alioth's `proprietary-files.txt`
and copied by `vendor/xiaomi/alioth/alioth-vendor.mk`:

| File under `/vendor/firmware/` | Size | Consumer |
| --- | ---: | --- |
| `aw8697_haptic.bin` | 3,622 bytes | Kernel `aw8697_ram_update()` programs the RAM bank. Its ten effect slots and slot 11 constant loop serve the production HAL. Required for normal vibration. |
| `aw8697_rtp_1.bin` | 120,000 bytes | Kernel `aw8697_ram_loaded()` calls `aw8697_rtp_update()` to preload it. `aw8697_rtp_osc_calibration()` also requests this sample. Retained for that stock backend, not used as an Android predefined effect. |

Three additional files under `/vendor/etc/vibrator/` contain unchanged samples
extracted from that same RAM bank: `primitive_effect_1.bin` (RAM 0 CLICK),
`primitive_effect_2.bin` (RAM 3 THUD), and `primitive_effect_7.bin` (RAM 2 LIGHT_TICK).
The device extraction fixup verifies both the full bank and each slice by SHA-256.
They are not generated envelopes or firmware imported from another actuator.

Both files match the Alioth stock dump byte for byte. The rebuilt device's boot
log confirms both loads. RTP loading does not mean oscillator calibration ran
at boot; ordinary Android effects use the RAM bank.

Numbered MIUI ringtone, game and UI RTP files, including the duplicate
`vibrator_firmware_aidl` bank, are not shipped. The HAL does not expose MIUI
extended IDs or RichTap streaming, so those banks have no production caller.

## Android mappings

RAM selectors are zero based; hardware RAM slots are one based.

| Android effect | RAM selector | Behavior |
| --- | ---: | --- |
| `CLICK` | 0 | Stock click, 20 ms. |
| `DOUBLE_CLICK` | 0 twice | Two 20 ms clicks separated by 80 ms; 120 ms total. |
| `TICK` | 2 | Stock tick, 20 ms. |
| `THUD` | 3 | Stock waveform, 20 ms. |
| `POP` | 4 | Stock waveform, 28 ms. |
| `HEAVY_CLICK` | 8 | MIUI `sys.haptic.mesh.heavy` selector, 20 ms. |
| `TEXTURE_TICK` | 5 | MIUI `sys.haptic.mesh.light` waveform, 20 ms; no additional attenuation. |

Light/medium/strong use gains 48/80/128. Alioth stock enables
`sys.haptic.infinitelevel`: `miui.util.VibrateUtils` maps the three levels to
0.375/0.625/1.0 and `VibratorExt::configStrengthForEffect` stores each effect's
factor. `InputFFDevice::playEffect` uses it with the global intensity initialized
to 1.0 by `VibratorManagerServiceImpl`. The older 0/0.5/1.0 native strength table
is a fallback, not the active stock UI path. The AOSP HAL adopts the verified
levels without importing the MIUI extension service. Its medium `TEXTURE_TICK`
matches stock `mesh.light=5,1` (RAM 5, gain 80).
The kernel keeps Alioth's stock boost (`0x11`), continuous drive (73/73),
legacy strength floor and battery-compensation formula. The tagged gain ABI
preserves low amplitudes for timed waveforms; it does not impose a global
minimum that would flatten fades. Android's intensity setting selects the
predefined-effect strength independently.

Timed vibration uses the RAM loop and supports amplitude changes and cancellation.
The service reports completion callbacks and cached resonant frequency. It advertises
composition for CLICK, THUD, LIGHT_TICK and NOOP. It does not advertise PWLE,
external control, always-on or vendor effects.

## Validation

The rebuilt HAL matched the build output and passed all seven effects at all
three strengths, callback, amplitude, cancellation, invalid-input and extended-ID
checks. All seven predefined effects and an amplitude waveform also completed
through Android's framework without root; cancellation left the vibrator idle.

The stock strength/mapping correction passed host regression tests and the same
on-device effect checks after a temporary service replacement. It ran under
the normal init service, UID and SELinux domain with enforcing enabled. The
user confirmed that previously weak feedback improved to an acceptable level;
Tremor's foreground feedback completed through Android. The live DTS drive
and boost settings matched stock. These checks do not establish measured
actuator acceleration or perceptual parity with HyperOS.

The release recheck exercised all seven effects at LOW/MEDIUM/HIGH through
Android. AW8697 DATDBG readback was 0x30/0x50/0x80 for all 21 combinations.
The existing HAL regression client passed effects, callbacks, amplitude,
cancellation, invalid input, long requests and extended-ID rejection. All 31
Android feedback commands returned without error; this does not mean every
constant necessarily produces a distinct waveform. Cancellation left GO and
GLB_STATE at zero, the service remained running, and the crash buffer was empty.
The installed HAL SHA-256 matched the build artifact.

Alioth's product haptics overlay makes keyboard feedback follow touch intensity;
the ROM's separate toggle otherwise holds it at MEDIUM. LOW/OFF/HIGH routing
was verified with SELinux enforcing. The overlay belongs in the device tree;
no framework changes or higher electrical limits are required. Perceptual
strength and stock parity still require human assessment.

## Combined waveform backend

`libqtivibratoreffect.xiaomi` supplies the same 24 kHz signed-byte `.bin` loader
used by the Qualcomm stack. The Alioth service uses its exact lookup API:
missing files are never relabelled CLICK during primitive capability discovery.
The legacy library entry point retains its existing fallback behavior for other
consumers, with bounds, read checks, synchronized caches and null-safe double click.

The HAL adapts samples to the stock driver's custom effect 193 and `custom_wave`
FIFO, rather than sending the incompatible Qualcomm `effect_stream` ioctl
payload. Each file must be nonempty and at most 240000 samples (ten seconds).
The worker reads available ring space before each write. Intermediate writes
contain whole hardware periods and stay below the sysfs write limit; only the
final write signals EOF, appending one zero for aligned payloads. The worker
releases its lock between refills, allowing cancellation without waiting for ring
space. Software amplitude scaling preserves the waveform. Completion follows
queued work and hardware state, bounded by nominal duration plus two seconds.
Cancellation uses the existing FF stop/erase path.

File playback requires kernel `playback_active` metadata plus narrowly scoped
HAL read/write access. Older kernels or missing files retain RAM CLICK, THUD
and LIGHT_TICK compositions. Existing predefined mappings and timed amplitude
control remain available. Valid `effect_<Android-ID>.bin` files can override
predefined effects; actual `primitive_effect_<Android-ID>.bin` files can extend
the supported primitive list. Firmware must be selected for this actuator and
its intended effect, not merely renamed to make capability queries succeed.

LOW_TICK uses the stock LIGHT_TICK samples (or RAM selector 2) as a compatibility
fallback, retaining Android scale/delay and completion behavior. An exact LOW_TICK
file takes precedence. This is a light-tick approximation, not a verified stock
low-frequency primitive.

Alioth has no verified stock Android SPIN or rise/fall mappings. They
remain unsupported unless corresponding real primitive files are supplied.
This is partial composition support, not full Android 17 haptic feature coverage.

The file backend requires the matching kernel completion-reporting ABI. The
installed matching build was checked through Android; see the AAC test below.

Targeted validation of this combined revision passed: HAL/shared-library build,
precompiled SELinux policy with neverallows, driver compilation with Alioth
Clang/CFI flags, ASan/UBSan loader tests, exact/idempotent stock extraction, and
FIFO length/EOF bounds. On-device short FIFO playback and cancellation also
passed through the normal init-managed HAL with SELinux enforcing.

### Alioth AAC rendering adapter (development only)

`xiaomi-alioth-richtap-render LIBRARY EVENTS.bin OUTPUT.bin` adapts Alioth's
`aac_haptics_init/process/get_frame` ABI. It is an explicit developer target,
not a product package or HAL dependency. No framework changes are required.
It produces signed 8-bit, 24 kHz PCM for the existing exact-file effect loader;
it does not assign Android primitive IDs or install the result automatically.

The decoded library is RichTap v1.0.7, SHA-256 `9d70e1d5337f3494ea359eb7532112a0af48fc563c352d5ff816ef7d8f0dd54a`.
Native initialization is four int32 values `{512, 24000, 1, 170}`. Input is
1..16 little-endian records of 17 int32 values: type (0x1000 continuous,
0x1001 transient), start time in ms, intensity 0..100, relative frequency
0..100, duration in ms, then exactly four triples of time/intensity/frequency.
Continuous point intensity is 0..100; frequency offset is -50..50. The adapter
requires strictly increasing curve times, zero endpoint intensity and the last
point at duration. Transient duration and unused curve fields should be zero.
These are Alioth native records, not a newer HE1/HE2 serialized payload.

Evidence: stock library parser at 0xb2a4 uses 0x44-byte strides, header offsets
0/4/8/12/16 and four 12-byte points starting at offset 20; stock service calls
process with event_count*68. The renderer performs AAC's own amplitude and
frequency conversion; it does not reproduce GKME's estimated compensation.

Run one pattern per process: the library owns a detached global worker, exports
no shutdown API and can block in get_frame. The adapter imposes a five-second
process alarm, checks frame lengths, and writes a new output only after EOF.
Output is capped at 240000 bytes to match the HAL transport. Longer patterns
are rejected, not truncated. Existing files are never overwritten. Use only the
verified Alioth library; newer libaacvibrator APIs are not ABI-compatible.

Validation: standalone host compilation and mocked API tests passed for normal
output, malformed input, invalid frame count, output limits and blocked calls.
The adapter was also compiled for Android arm64 and run against the installed
stock library: a transient rendered 408 samples; a 60 ms continuous event rendered
1441 samples. Repeated continuous rendering was byte-identical. A temporary
exact-file override played that PCM through the normal HAL; cancellation passed
and the stock file and service were restored. This verifies the short transport,
not perceptual calibration or mappings for additional Android primitives.

The production HAL does not call AAC directly. Rendering remains an explicit
developer step; the RichTap Binder extension and HE1/HE2 framework API are not
implemented. Native rendering remains isolated because the stock library has
no shutdown API. Full live RichTap integration must not be advertised as supported.

The bounded-refill revision was compiled and linked using the Android 17 vendor
flags in separate validation outputs. On Alioth with SELinux enforcing, the
init-managed HAL ran as system in hal_vibrator_default and played an AAC-generated
28801-byte continuous waveform through Android. An active stream cancelled
correctly and left playback_active=0; all seven predefined effects completed.
The original service binary and stock waveform were restored and their hashes
verified. Sixty modeled FIFO boundary cases checked aligned writes, ring-space
bounds and exactly one final EOF. These checks do not establish perceptual
calibration, maximum-duration device coverage or additional primitive mappings.

### HE1 compatibility work

The developer renderer accepts `--he1` after the output filename for reference
format-3 HE1 packets (one format integer followed by 55 integers per event).
Only single-actuator, exactly four-point continuous curves and transients map
without alteration to the decoded Alioth ABI. Malformed input, nonzero unused
fields, additional actuators and unsupported curves are rejected before AAC is
loaded. No support/version advertisement or app-facing service is enabled by
this adapter.

Validation: ASan/UBSan decoder checks covered complete/truncated records,
invalid fields, unsupported point counts and nonzero padding. On-device stock
AAC rendering of the translated 60 ms fixture produced 1441 bytes identical to
its native-record equivalent (SHA-256
`fb1ca160dbbd41cf6905bbd46a1dd9f7b66bb8216b43ab4a37dff46aab592dbe`).
The reference is RichTapCoreForAndroidT commit
`abec840f74d62306564c6ffcd96cf71ca7dd34b6`, `HapticPlayer` HE1 serializer.
This is a tested decoder foundation, not complete RichTap integration.

### Production recheck, 2026-10-04

The exact HAL candidate compiled from `741154f` ran under the normal init
service as UID system in `hal_vibrator_default`, with SELinux enforcing.
All seven predefined effects completed at LOW, MEDIUM and HIGH. Hardware
DATDBG readback matched 48/80/128 for all 21 combinations. CLICK/THUD/TICK
compositions at varying scales, timed amplitude changes and cancellation also
completed through Android. After cancellation `playback_active=0` and the ring
was empty. The original installed binary, stock primitive file and saved user
intensity setting were restored; file hashes were checked. No additional ROM
build was needed. These tests establish control behavior, not measured stock
acceleration or perceptual calibration.

The final Review standards axis found no actionable bounds, lifecycle or
locking defect in the waveform/decoder changes. The spec axis confirmed a
remaining gap: this service exposes standard Android haptics and file-backed
PCM, but does not implement app-facing RichTap routing, looping or live
parameter updates. The HE1 adapter is a developer renderer, not that missing
service. Full RichTap support must not be advertised by this release.
