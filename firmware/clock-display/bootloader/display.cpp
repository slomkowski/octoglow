#include "display.hpp"
#include "protocol.hpp"
#include "vfd.hpp"

using namespace octoglow::vfd_clock;
using namespace octoglow::vfd_clock::bootloader;

// T is shown as a mirrored 7
constexpr vfd::SegmentBuffer BOOT_SEGMENTS = vfd::segmentsForShapes({
    vfd::shapeOf('8'), vfd::shapeOf('0'), vfd::shapeOf('0'), vfd::shapeOf('T')
});

// constexpr forces compile-time evaluation, the table is in PROGMEM and can't be read directly at runtime
constexpr uint8_t BRIGHTNESS_PWM = vfd::BRIGHTNESS_PWM_VALUES[vfd::DEFAULT_BRIGHTNESS];

constexpr uint8_t DOTS = vfd::UPPER_DOT | vfd::LOWER_DOT;
constexpr uint8_t DOTS_BYTE = vfd::UPPER_DOT_SEGMENT / 8;

static_assert((BOOT_SEGMENTS.bytes[DOTS_BYTE] & DOTS) == 0, "dots are not part of BOOT");

// the dots toggle every BLINK_TICKS and keep blinking ACTIVITY_TICKS after the last transfer
constexpr uint8_t BLINK_TICKS = 4;
constexpr uint8_t ACTIVITY_TICKS = 10;

static_assert((BLINK_TICKS & (BLINK_TICKS - 1)) == 0, "has to be a power of two");
static_assert(BLINK_TICKS * protocol::TIMER_TICK_MS == 100);

static uint8_t segments[vfd::SEGMENT_BUFFER_SIZE] = {
    BOOT_SEGMENTS.bytes[0], BOOT_SEGMENTS.bytes[1], BOOT_SEGMENTS.bytes[2],
    BOOT_SEGMENTS.bytes[3], BOOT_SEGMENTS.bytes[4],
};

static uint8_t ticks;
static uint8_t activityTicksLeft;

void display::init() {
    vfd::initPins();
    vfd::initBrightnessPwm(BRIGHTNESS_PWM);
    vfd::loadSegments(segments);
}

void display::onTimerTick(const bool i2cActivity) {
    ++ticks;

    if (i2cActivity) {
        activityTicksLeft = ACTIVITY_TICKS;
    } else if (activityTicksLeft != 0) {
        --activityTicksLeft;
    }

    const bool dotsVisible = activityTicksLeft != 0 and (ticks & BLINK_TICKS);
    const uint8_t dotsByte = BOOT_SEGMENTS.bytes[DOTS_BYTE] | (dotsVisible ? DOTS : 0);

    if (segments[DOTS_BYTE] != dotsByte) {
        segments[DOTS_BYTE] = dotsByte;
        vfd::loadSegments(segments);
    }
}
