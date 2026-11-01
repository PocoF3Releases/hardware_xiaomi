// SPDX-License-Identifier: Apache-2.0
#include "PrimitiveSynth.h"
#include <algorithm>
#include <cmath>

namespace alioth::haptics {
namespace {
constexpr double kRate = 24000;
constexpr double kPi = 3.14159265358979323846;
constexpr double kQ = 18;
constexpr double kDampingGain = 30;

double resonance(float hz) {
    return hz >= 100 && hz <= 300 ? hz : 170;
}

struct Params {
    double ms;       // driven length
    double startHz;  // frequency glide, as multiples of resonance
    double endHz;
    double level;    // sustained drive at resonance builds about 5x a stock
                     // click, so envelopes stay near click-level output
};

Params params(Shape shape) {
    switch (shape) {
        case Shape::kQuickRise: return {70, 1, 1, 0.25};
        case Shape::kSlowRise: return {300, 1, 1, 0.2};
        case Shape::kQuickFall: return {70, 1, 1, 0.25};
        case Shape::kSpin: return {60, 0.94, 1.04, 0.2};
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

// Band-pass biquad: output follows the actuator's velocity.
class Resonator {
  public:
    explicit Resonator(double hz) {
        const double w = 2 * kPi * hz / kRate, alpha = std::sin(w) / (2 * kQ);
        mB0 = alpha / (1 + alpha);
        mA1 = -2 * std::cos(w) / (1 + alpha);
        mA2 = (1 - alpha) / (1 + alpha);
    }
    double step(double x) {
        const double y = mB0 * (x - mX2) - mA1 * mY1 - mA2 * mY2;
        mX2 = mX1;
        mX1 = x;
        mY2 = mY1;
        mY1 = y;
        return y;
    }
    double last() const { return mY1; }

  private:
    double mB0, mA1, mA2, mX1 = 0, mX2 = 0, mY1 = 0, mY2 = 0;
};
}  // namespace

void appendDamping(std::vector<int8_t>* samples, float resonanceHz) {
    const double f0 = resonance(resonanceHz);
    Resonator model(f0);
    double peak = 0;
    for (const int8_t s : *samples) peak = std::max(peak, std::abs(model.step(s)));
    const size_t cycle = static_cast<size_t>(kRate / f0);
    for (size_t n = 0; n < static_cast<size_t>(kRate * 0.04); ++n) {
        if (n > cycle && std::abs(model.last()) < 0.05 * peak) break;
        const double drive = std::clamp(-kDampingGain * model.last(), -127.0, 127.0);
        samples->push_back(static_cast<int8_t>(std::lround(drive)));
        model.step(samples->back());
    }
    while (samples->size() > 1 && samples->back() == 0) samples->pop_back();
}

std::vector<int8_t> synthesize(Shape shape, float resonanceHz) {
    const double f0 = resonance(resonanceHz);
    const Params p = params(shape);
    const size_t driven = static_cast<size_t>(p.ms * kRate / 1000);
    std::vector<int8_t> out;
    double phase = 0;
    for (size_t n = 0; n < driven; ++n) {
        const double x = static_cast<double>(n) / driven;
        phase += 2 * kPi * f0 * (p.startHz + (p.endHz - p.startHz) * x) / kRate;
        out.push_back(static_cast<int8_t>(
                std::lround(127 * p.level * envelope(shape, x) * std::sin(phase))));
    }
    appendDamping(&out, resonanceHz);
    return out;
}
}  // namespace alioth::haptics
