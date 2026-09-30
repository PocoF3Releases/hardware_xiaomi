# Alioth AW8697 vibrator

The AIDL V2 service uses Alioth's stock AW8697 kernel driver and waveform RAM.
It runs as `system` in `hal_vibrator_default`; production playback needs no root,
Magisk module, stock MIUI service or RichTap library.

## Active interfaces

| Interface | Consumer and purpose |
| --- | --- |
| Input device named `aw8697_haptic` | HAL uploads/erases effects with `EVIOCSFF`/`EVIOCRMFF`, then starts/stops them with `EV_FF`. |
| `custom_wave` (read only) | HAL checks RAM readiness and negotiates `ram_gain_abi=1`. It does not write or stream custom waves. |
| `gain` | HAL updates amplitude for timed `on()` vibrations. Predefined effects carry their own gain in the negotiated FF payload. |
| `f0_value` | HAL reads cached resonance. It never reads `f0`, which triggers calibration. |

The sysfs attributes are under
`/sys/devices/platform/soc/a8c000.i2c/i2c-2/2-005a/`.
The driver retains its legacy FF ABI; the HAL uses tagged direct gain only when
the driver advertises it.

## Shipped firmware and consumers

Only these two vibrator prebuilts are listed in Alioth's `proprietary-files.txt`
and copied by `vendor/xiaomi/alioth/alioth-vendor.mk`:

| File under `/vendor/firmware/` | Size | Consumer |
| --- | ---: | --- |
| `aw8697_haptic.bin` | 3,622 bytes | Kernel `aw8697_ram_update()` programs the RAM bank. Its ten effect slots and slot 11 constant loop serve the production HAL. Required for normal vibration. |
| `aw8697_rtp_1.bin` | 120,000 bytes | Kernel `aw8697_ram_loaded()` calls `aw8697_rtp_update()` to preload it. `aw8697_rtp_osc_calibration()` also requests this sample. Retained for that stock backend, not used as an Android predefined effect. |

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
The service reports completion callbacks and cached resonant frequency. It does
not advertise primitives, PWLE, external control, always-on or vendor effects.

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
