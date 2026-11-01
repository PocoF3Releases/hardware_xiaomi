// SPDX-License-Identifier: Apache-2.0
#include "PrimitiveSynth.h"
#include <algorithm>
#include <cmath>

namespace alioth::haptics {
namespace {
constexpr double kRate = 24000;
constexpr double kPi = 3.14159265358979323846;

struct Params {
    double ms;       // driven length
    double startHz;  // frequency glide, as multiples of resonance
    double endHz;
    bool brake;      // end at full drive: add an anti-phase damping cycle
};

Params params(Shape shape) {
    switch (shape) {
        case Shape::kQuickRise: return {70, 1, 1, true};
        case Shape::kSlowRise: return {300, 1, 1, true};
        case Shape::kQuickFall: return {70, 1, 1, false};
        case Shape::kSpin: return {60, 0.94, 1.04, false};
    }
    return {};
}

double envelope(Shape shape, double x) {
    switch (shape) {
        case Shape::kQuickRise: return x * x;
        case Shape::kSlowRise: return std::pow(x, 1.5);
        case Shape::kQuickFall: return (1 - x) * (1 - x);
        case Shape::kSpin: return std::pow(std::sin(kPi * x), 2);
    }
    return 0;
}
}  // namespace

std::vector<int8_t> synthesize(Shape shape, float resonanceHz) {
    const double f0 = resonanceHz >= 100 && resonanceHz <= 300 ? resonanceHz : 170;
    const Params p = params(shape);
    const size_t driven = static_cast<size_t>(p.ms * kRate / 1000);
    std::vector<int8_t> out;
    out.reserve(driven + static_cast<size_t>(kRate / f0) + 1);
    double phase = 0;
    for (size_t n = 0; n < driven; ++n) {
        const double x = static_cast<double>(n) / driven;
        phase += 2 * kPi * f0 * (p.startHz + (p.endHz - p.startHz) * x) / kRate;
        out.push_back(static_cast<int8_t>(std::lround(127 * envelope(shape, x) * std::sin(phase))));
    }
    if (p.brake) {
        // One cycle in anti-phase, fading from half drive, shortens ring-down
        // after an envelope that ends at full amplitude.
        const size_t cycle = static_cast<size_t>(kRate / f0);
        for (size_t n = 0; n < cycle; ++n) {
            phase += 2 * kPi * f0 / kRate;
            const double a = 0.5 * (1 - static_cast<double>(n) / cycle);
            out.push_back(static_cast<int8_t>(std::lround(-127 * a * std::sin(phase))));
        }
    }
    // The PCM path needs a nonempty stream; trailing zeros only waste time.
    while (out.size() > 1 && out.back() == 0) out.pop_back();
    return out;
}
}  // namespace alioth::haptics
