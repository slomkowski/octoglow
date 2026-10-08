#include "screen.hpp"
#include "twiboot.hpp"
#include "Font5x7.hpp"
#include "vfd.hpp"

using namespace octoglow::front_display;
using namespace octoglow::front_display::bootloader;
using display::COLUMNS_IN_CHARACTER;
using display::NUM_OF_CHARACTERS;

constexpr uint8_t BRIGHTNESS = 3;

constexpr uint32_t UPPER_BAR_ALL_SEGMENTS = 0xfffff;

// the upper bar toggles every BLINK_TICKS and keeps blinking ACTIVITY_TICKS after the last transfer
constexpr uint8_t BLINK_TICKS = 8;
constexpr uint8_t ACTIVITY_TICKS = 25;

static_assert((BLINK_TICKS & (BLINK_TICKS - 1)) == 0, "has to be a power of two");
static_assert(ACTIVITY_TICKS * twiboot::TIMER_TICK_MS == 250);

struct FrameBuffer {
    uint8_t columns[NUM_OF_CHARACTERS * COLUMNS_IN_CHARACTER];
};

/**
 * Evaluated at compile time, the font isn't linked into the bootloader.
 */
static constexpr FrameBuffer renderText(const uint8_t position, const char *const text) {
    FrameBuffer frameBuffer{};
    for (uint8_t i = 0; text[i] != 0; ++i) {
        for (uint8_t c = 0; c != COLUMNS_IN_CHARACTER; ++c) {
            frameBuffer.columns[COLUMNS_IN_CHARACTER * (position + i) + c]
                    = display::Font5x7[COLUMNS_IN_CHARACTER * (text[i] - ' ') + c];
        }
    }
    return frameBuffer;
}

// in the middle of the upper line
static constinit FrameBuffer frameBuffer = renderText(5, "BOOTLOADER");

static uint8_t currentPosition;
static uint32_t upperBar;
static uint8_t ticks;
static uint8_t activityTicksLeft;

void screen::init() {
    vfd::initPins();
}

void screen::poll() {
    vfd::showCharacter(currentPosition, frameBuffer.columns, BRIGHTNESS, upperBar);

    if (currentPosition == NUM_OF_CHARACTERS - 1) {
        currentPosition = 0;
    } else {
        ++currentPosition;
    }
}

void screen::onTimerTick(const bool i2cActivity) {
    ++ticks;

    if (i2cActivity) {
        activityTicksLeft = ACTIVITY_TICKS;
    } else if (activityTicksLeft != 0) {
        --activityTicksLeft;
    }

    const bool upperBarVisible = activityTicksLeft != 0 and (ticks & BLINK_TICKS);
    upperBar = upperBarVisible ? UPPER_BAR_ALL_SEGMENTS : 0;
}
