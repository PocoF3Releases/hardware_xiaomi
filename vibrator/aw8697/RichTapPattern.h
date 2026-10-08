// SPDX-License-Identifier: Apache-2.0
#pragma once
#include <array>
#include <cstdint>
#include <vector>
#include <utility>
#include <cstddef>

namespace alioth::richtap {
struct Point { int32_t time, intensity, frequency; };
struct Event {
    int32_t type, time, intensity, frequency, duration;
    std::array<Point, 4> curve;
};
static_assert(sizeof(Event) == 68);
static_assert(sizeof(Point) == 12);

inline bool validEvent(const Event& e) {
    if (e.time < 0 || e.time > 10000 || e.intensity < 0 || e.intensity > 100 ||
        e.frequency < 0 || e.frequency > 100) return false;
    if (e.type == 0x1001) {
        if (e.duration != 0) return false;
        for (const auto& p : e.curve)
            if (p.time || p.intensity || p.frequency) return false;
        return true;
    }
    if (e.type != 0x1000 || e.duration <= 0 || e.duration > 10000 ||
        e.time + e.duration > 10000 || e.curve.front().time != 0 ||
        e.curve.back().time != e.duration || e.curve.front().intensity != 0 ||
        e.curve.back().intensity != 0) return false;
    int previous = -1;
    for (const auto& p : e.curve) {
        if (p.time <= previous || p.time > e.duration || p.intensity < 0 ||
            p.intensity > 100 || p.frequency < -50 || p.frequency > 50) return false;
        previous = p.time;
    }
    return true;
}

// Reference: RichTapCoreForAndroidT HapticPlayer HE1 serialization, format 3.
// Each event is 55 integers: five header fields, actuator index, point count,
// and sixteen point slots. Alioth has one actuator and exactly four native points.
// Reject unsupported curves instead of truncating or interpolating them.
inline bool decodeHe1(const std::vector<int32_t>& input, std::vector<Event>* output) {
    if (!output) return false;
    output->clear();
    if (input.size() < 56 || input[0] != 3 || (input.size() - 1) % 55 ||
        (input.size() - 1) / 55 > 16) return false;
    std::vector<Event> events;
    int previous = -1;
    for (size_t offset = 1; offset < input.size(); offset += 55) {
        const int32_t* p = input.data() + offset;
        Event event{p[0], p[1], p[2], p[3], p[4], {}};
        if (p[5] != 0 || p[6] != (event.type == 0x1000 ? 4 : 0)) return false;
        const size_t used = event.type == 0x1000 ? 19 : 7;
        for (size_t i = used; i < 55; ++i) if (p[i] != 0) return false;
        if (event.type == 0x1000)
            for (size_t i = 0; i < 4; ++i)
                event.curve[i] = {p[7 + 3*i], p[8 + 3*i], p[9 + 3*i]};
        if (!validEvent(event) || event.time < previous) return false;
        previous = event.time;
        events.push_back(event);
    }
    *output = std::move(events);
    return true;
}
}  // namespace alioth::richtap
