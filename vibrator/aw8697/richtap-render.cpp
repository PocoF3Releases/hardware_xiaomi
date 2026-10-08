// SPDX-License-Identifier: Apache-2.0
// Offline adapter for Alioth libaachaptics.so v1.0.7. Never load it in the HAL:
// its global worker has no deinit/stop API and get_frame can block indefinitely.
#include "RichTapPattern.h"
#include <array>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <fcntl.h>
#include <signal.h>
#include <unistd.h>
#include <vector>

namespace {
using alioth::richtap::Event;
constexpr size_t kMaxBytes = 240000;

int fail(const char* message) {
    fprintf(stderr, "%s\n", message);
    return 1;
}
} // namespace

int main(int argc, char** argv) {
    if (argc != 4 && argc != 5)
        return fail("Usage: richtap-render LIBRARY EVENTS.bin OUTPUT.bin [--he1]");
    const bool he1 = argc == 5;
    if (he1 && strcmp(argv[4], "--he1")) return fail("Unknown input format");
    // Bound both native records and HE1 packets; the extra byte detects excess.
    FILE* input = fopen(argv[2], "rb");
    if (!input) return fail("Cannot open events");
    std::array<unsigned char, (1 + 16 * 55) * sizeof(int32_t) + 1> raw{};
    const size_t bytes = fread(raw.data(), 1, raw.size(), input);
    const bool badRead = ferror(input);
    fclose(input);
    if (badRead || bytes == 0 || bytes == raw.size() || bytes % sizeof(int32_t))
        return fail("Invalid input size");
    const uint16_t endian = 1;
    if (*reinterpret_cast<const uint8_t*>(&endian) != 1)
        return fail("Only little-endian hosts supported");
    std::vector<Event> events;
    if (he1) {
        std::vector<int32_t> packet(bytes / sizeof(int32_t));
        memcpy(packet.data(), raw.data(), bytes);
        if (!alioth::richtap::decodeHe1(packet, &events))
            return fail("Malformed or unsupported HE1 packet");
    } else {
        if (bytes > 16 * sizeof(Event) || bytes % sizeof(Event))
            return fail("Expected 1..16 complete 68-byte events");
        events.resize(bytes / sizeof(Event));
        memcpy(events.data(), raw.data(), bytes);
    }
    int previous = -1;
    for (const auto& event : events) {
        if (!alioth::richtap::validEvent(event) || event.time < previous) return fail("Invalid or unordered event");
        previous = event.time;
    }
    // Bound the lifetime of both dlopen constructors and the native worker. No
    // output file exists until rendering has completed successfully.
    signal(SIGALRM, SIG_DFL);
    alarm(5);
    void* library = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!library) return fail(dlerror());
    auto init = reinterpret_cast<int (*)(int32_t*)>(dlsym(library, "aac_haptics_init"));
    auto process = reinterpret_cast<int (*)(void*, int32_t)>(dlsym(library, "aac_haptics_process"));
    auto getFrame = reinterpret_cast<int (*)(void*, int32_t*)>(dlsym(library, "aac_haptics_get_frame"));
    if (!init || !process || !getFrame) return fail("Not the Alioth AAC native API");
    std::array<int32_t, 4> config{512, 24000, 1, 170};
    if (init(config.data()) != 0 || process(events.data(), static_cast<int32_t>(events.size() * sizeof(Event))) != 0)
        return fail("AAC initialization or event submission failed");
    std::vector<int8_t> pcm;
    for (;;) {
        std::array<int8_t, 512> frame{};
        int32_t count = -1;
        if (getFrame(frame.data(), &count) != 0 || count < 0 || count > 512)
            return fail("Invalid AAC frame");
        if (!count) break;
        if (pcm.size() + static_cast<size_t>(count) > kMaxBytes)
            return fail("Waveform exceeds HAL playback limit; output not written");
        pcm.insert(pcm.end(), frame.begin(), frame.begin() + count);
    }
    if (pcm.empty()) return fail("AAC returned no waveform");
    alarm(0);
    // O_EXCL prevents overwriting an existing verified effect or following a symlink.
    const int output = open(argv[3], O_WRONLY | O_CREAT | O_EXCL | O_CLOEXEC, 0644);
    if (output < 0) return fail("Cannot create new output");
    size_t offset = 0;
    while (offset < pcm.size()) {
        const ssize_t n = write(output, pcm.data() + offset, pcm.size() - offset);
        if (n <= 0) {
            close(output);
            unlink(argv[3]);
            return fail("Output write failed");
        }
        offset += static_cast<size_t>(n);
    }
    if (close(output) != 0) {
        unlink(argv[3]);
        return fail("Output close failed");
    }
    fprintf(stderr, "Rendered %zu bytes at 24000 Hz, signed 8-bit PCM\n", pcm.size());
    // Do not dlclose: AAC's detached worker still executes library code. Process
    // exit is the only supported disposal boundary for this version of AAC.
    return 0;
}
