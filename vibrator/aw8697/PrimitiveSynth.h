// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <cstdint>
#include <vector>

namespace alioth::haptics {
// Envelope primitives without a stock RAM or prebaked waveform. They follow the
// stock vibratorfeature SineTrack model: a 24 kHz sine at the actuator's
// resonance, int8 full scale 127, amplitude shaped over time. Alioth's LRA is
// narrow band (stock compensation table: about 1/8 output at +-25 Hz), so
// shape comes from the envelope rather than from frequency changes.
enum class Shape { kQuickRise, kSlowRise, kQuickFall, kSpin };
// Sustained drive rings for 50-70 ms, so each envelope ends with active
// damping against a second-order actuator model (cached resonance, Q 18
// fitted to the stock vibratorfeature drive-compensation table).
std::vector<int8_t> synthesize(Shape shape, float resonanceHz);
}  // namespace alioth::haptics
