#include "display.hpp"
#include "vfd.hpp"

#include <avr/io.h>
#include <avr/pgmspace.h>

using namespace octoglow::vfd_clock;
using namespace octoglow::vfd_clock::display;
using namespace octoglow::vfd_clock::vfd;

constexpr uint8_t RECEIVER_UPDATE_FLAG = 0b100;
constexpr uint8_t RECEIVER_VALID_UPDATE_CHARACTER_SHAPE = 0b1100010;
constexpr uint8_t RECEIVER_INVALID_UPDATE_CHARACTER_SHAPE = 0b0000010;

static_assert((RECEIVER_UPDATE_FLAG & protocol::LOWER_DOT) == 0);
static_assert((RECEIVER_UPDATE_FLAG & protocol::UPPER_DOT) == 0);
static_assert(protocol::UPPER_DOT == vfd::UPPER_DOT and protocol::LOWER_DOT == vfd::LOWER_DOT);

static_assert(sizeof(BRIGHTNESS_PWM_VALUES) == protocol::MAX_BRIGHTNESS + 1, "invalid number of brightness PWM values");

static uint8_t characterBuffer[NUMBER_OF_POSITIONS];
static auto receiverUpdateFlagEnabled = ReceiverUpdateFlag::DISABLED;
static uint8_t segmentBuffer[SEGMENT_BUFFER_SIZE];

static void reloadDisplay() {
    static_assert(ALWAYS_ON_SEGMENT / 8 == 4);
    segmentBuffer[0] = segmentBuffer[2] = segmentBuffer[3] = 0;
    segmentBuffer[4] = _BV(ALWAYS_ON_SEGMENT % 8);
    segmentBuffer[1] &= UPPER_DOT | LOWER_DOT;

    for (uint8_t numberPos = 0; numberPos != NUMBER_OF_POSITIONS; ++numberPos) {
        uint8_t characterShape = pgm_read_byte(CHARACTER_SHAPES + characterBuffer[numberPos]);

        if (numberPos == 0) {
            if (receiverUpdateFlagEnabled == ReceiverUpdateFlag::VALID) {
                characterShape = RECEIVER_VALID_UPDATE_CHARACTER_SHAPE;
            } else if (receiverUpdateFlagEnabled == ReceiverUpdateFlag::INVALID) {
                characterShape = RECEIVER_INVALID_UPDATE_CHARACTER_SHAPE;
            }
        }

        for (uint8_t s = 0; s != 7; ++s) {
            if (characterShape & 1 << s) {
                const uint8_t segment = pgm_read_byte(SEGMENT_ORDERING + 7 * numberPos + s) - 1;

                segmentBuffer[segment / 8] |= 1 << (segment % 8);
            }
        }
    }

    loadSegments(segmentBuffer);
}

void display::init() {
    vfd::initPins();
    initBrightnessPwm(pgm_read_byte(BRIGHTNESS_PWM_VALUES + DEFAULT_BRIGHTNESS));

    setDots(protocol::LOWER_DOT, false);
    setAllCharacters(const_cast<char *>("-_-_"));
}

void display::setBrightness(const uint8_t brightness) {
    setBrightnessPwm(pgm_read_byte(
        BRIGHTNESS_PWM_VALUES + (brightness > protocol::MAX_BRIGHTNESS ? protocol::MAX_BRIGHTNESS : brightness)));
}

void display::setCharacter(const uint8_t position, const char character,
                           const bool shouldReloadDisplay) {
    constexpr uint8_t numberOfCharacters = sizeof(CHARACTER_ORDER) / sizeof(CHARACTER_ORDER[0]);

    uint8_t c;
    for (c = numberOfCharacters - 1; c != 0; --c) {
        if (pgm_read_byte(CHARACTER_ORDER + c) == character) {
            break;
        }
    }

    characterBuffer[position % NUMBER_OF_POSITIONS] = c;

    if (shouldReloadDisplay) {
        reloadDisplay();
    }
}

void display::setAllCharacters(const volatile char *characters) {
    uint8_t p = 0;
    for (; p != NUMBER_OF_POSITIONS - 1; ++p) {
        setCharacter(p, characters[p], false);
    }
    setCharacter(p, characters[p], true);
}

void display::setDots(const uint8_t newDotState, const bool shouldReloadDisplay) {
    segmentBuffer[1] = newDotState;

    if (shouldReloadDisplay) {
        reloadDisplay();
    }
}

void display::setReceiverUpdateFlag(const ReceiverUpdateFlag flag) {
    receiverUpdateFlagEnabled = flag;
    reloadDisplay();
}
