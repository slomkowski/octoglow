#include "twiboot.hpp"
#include "flash.hpp"

#include <gtest/gtest.h>

#include <cstring>
#include <string>
#include <vector>

using namespace octoglow::geiger::bootloader;
using namespace octoglow::geiger::bootloader::twiboot;

constexpr int TIMEOUT_TICKS = TWIBOOT_TIMEOUT_MS / TIMER_TICK_MS;

constexpr uint8_t CMD_WAIT = 0x00;
constexpr uint8_t CMD_READ_VERSION = 0x01;
constexpr uint8_t CMD_SWITCH_APPLICATION = 0x01;
constexpr uint8_t CMD_ACCESS_MEMORY = 0x02;
constexpr uint8_t BOOTTYPE_APPLICATION = 0x80;
constexpr uint8_t MEMTYPE_CHIPINFO = 0x00;
constexpr uint8_t MEMTYPE_FLASH = 0x01;

constexpr uint32_t FLASH_SIZE = 0x10000 - FLASH_START;

/**
 * Main flash of MSP430G2553 as seen by the bootloader: erase sets the whole segment to 0xff,
 * write can only clear bits, like the real flash.
 */
static uint8_t flashMemory[FLASH_SIZE];
static std::vector<uint16_t> erasedSegments;
static int flashWrites;

static bool isInApplication(const uint32_t address) {
    return address >= FLASH_START and address < BOOTLOADER_START;
}

void flash::eraseSegment(const uint16_t address) {
    if (!isInApplication(address)) {
        ADD_FAILURE() << "erase outside the application: 0x" << std::hex << address;
        return;
    }
    const uint16_t segment = address - address % SEGMENT_SIZE;
    erasedSegments.push_back(segment);
    memset(flashMemory + segment - FLASH_START, 0xff, SEGMENT_SIZE);
}

void flash::write(const uint16_t address, const uint8_t *const data, const uint8_t length) {
    if (!isInApplication(address) or !isInApplication(address + length - 1) or address % 2 != 0 or length % 2 != 0) {
        ADD_FAILURE() << "invalid write: 0x" << std::hex << address << ", length " << std::dec << int(length);
        return;
    }
    ++flashWrites;
    for (uint8_t i = 0; i != length; ++i) {
        flashMemory[address - FLASH_START + i] &= data[i];
    }
}

uint8_t flash::readByte(const uint16_t address) {
    if (address < FLASH_START) {
        ADD_FAILURE() << "read outside the flash: 0x" << std::hex << address;
        return 0;
    }
    return flashMemory[address - FLASH_START];
}

/**
 * Master write transmission followed by STOP, as the USCI slave delivers it.
 */
static void masterWrite(const std::vector<uint8_t> &bytes, const bool stop = true) {
    for (size_t i = 0; i < bytes.size(); ++i) {
        twiboot::onDataWrite(i < 0xff ? i : 0xff, bytes[i]);
    }
    if (stop) {
        twiboot::onStop();
    }
}

static std::vector<uint8_t> masterRead(const size_t length) {
    std::vector<uint8_t> result;
    for (size_t i = 0; i < length; ++i) {
        result.push_back(twiboot::onDataRead(i));
    }
    twiboot::onStop();
    return result;
}

static std::vector<uint8_t> flashCommand(const uint16_t address) {
    return {CMD_ACCESS_MEMORY, MEMTYPE_FLASH, static_cast<uint8_t>(address >> 8), static_cast<uint8_t>(address & 0xff)};
}

static std::vector<uint8_t> writePageCommand(const uint16_t address, const std::vector<uint8_t> &page) {
    auto bytes = flashCommand(address);
    bytes.insert(bytes.end(), page.begin(), page.end());
    return bytes;
}

static std::vector<uint8_t> testPage(const uint8_t seed) {
    std::vector<uint8_t> page(PAGE_SIZE);
    for (size_t i = 0; i < page.size(); ++i) {
        page[i] = static_cast<uint8_t>(seed + 7 * i);
    }
    return page;
}

static std::vector<uint8_t> flashContent(const uint16_t address, const size_t length) {
    return {flashMemory + address - FLASH_START, flashMemory + address - FLASH_START + length};
}

static void installApplication() {
    flashMemory[APPLICATION_RESET_VECTOR - FLASH_START] = 0x00;
    flashMemory[APPLICATION_RESET_VECTOR - FLASH_START + 1] = 0xc0;
}

class Twiboot : public ::testing::Test {
protected:
    void SetUp() override {
        memset(flashMemory, 0xff, sizeof(flashMemory));
        erasedSegments.clear();
        flashWrites = 0;
        twiboot::init();
    }
};

TEST(TwibootLayout, ApplicationVectorsJustBelowBootloader) {
    ASSERT_EQ(0xfc00, BOOTLOADER_START) << "update the tests if the layout changes";
    ASSERT_EQ(0xfbe0, APPLICATION_VECTORS);
    ASSERT_EQ(0xfbfe, APPLICATION_RESET_VECTOR);
    ASSERT_EQ(0x3c00, APPLICATION_SIZE);
}

TEST_F(Twiboot, StartsApplicationAfterTimeout) {
    installApplication();

    for (int i = 0; i < TIMEOUT_TICKS - 1; ++i) {
        twiboot::onTimerTick();
        ASSERT_FALSE(twiboot::shouldStartApplication()) << "tick " << i;
    }

    twiboot::onTimerTick();
    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, WaitsForeverWithoutApplication) {
    ASSERT_FALSE(twiboot::isApplicationPresent());

    for (int i = 0; i < 10 * TIMEOUT_TICKS; ++i) {
        twiboot::onTimerTick();
    }
    ASSERT_FALSE(twiboot::shouldStartApplication());

    masterWrite({CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION});
    ASSERT_FALSE(twiboot::shouldStartApplication());

    // the host can still talk to it
    masterWrite({CMD_ACCESS_MEMORY, MEMTYPE_CHIPINFO, 0, 0}, false);
    ASSERT_EQ(PAGE_SIZE, masterRead(8)[3]);
}

TEST_F(Twiboot, WaitCommandAbortsTimeout) {
    installApplication();

    masterWrite({CMD_WAIT});

    for (int i = 0; i < 10 * TIMEOUT_TICKS; ++i) {
        twiboot::onTimerTick();
    }

    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, ReadVersion) {
    masterWrite({CMD_READ_VERSION}, false);

    const auto version = masterRead(16);

    ASSERT_EQ(std::string("TWIBOOT v3.2"), std::string(version.begin(), version.begin() + 12));
    for (size_t i = 12; i < version.size(); ++i) {
        ASSERT_EQ(0, version[i]);
    }
}

TEST_F(Twiboot, ReadChipInfo) {
    masterWrite({CMD_ACCESS_MEMORY, MEMTYPE_CHIPINFO, 0, 0});

    ASSERT_EQ((std::vector<uint8_t>{
                  0x00, 0x25, 0x53, // MSP430G2553
                  PAGE_SIZE,
                  0x3c, 0x00, // 15 kB for the application
                  0, 0,
              }), masterRead(8));
}

TEST_F(Twiboot, WriteAndReadFlashPage) {
    const auto page = testPage(3);

    masterWrite(writePageCommand(0x0240, page));

    ASSERT_EQ(1, flashWrites);
    ASSERT_TRUE(erasedSegments.empty()) << "not the start of the segment";
    ASSERT_EQ(page, flashContent(0xc240, PAGE_SIZE));
    ASSERT_EQ(0xff, flashMemory[0x0240 - 1]);
    ASSERT_EQ(0xff, flashMemory[0x0240 + PAGE_SIZE]);

    masterWrite(flashCommand(0x0240));
    ASSERT_EQ(page, masterRead(PAGE_SIZE));
}

TEST_F(Twiboot, PageIsWrittenOnlyAfterStop) {
    masterWrite(writePageCommand(0x0240, testPage(3)), false);

    ASSERT_EQ(0, flashWrites);

    twiboot::onStop();
    ASSERT_EQ(1, flashWrites);

    // repeated STOP doesn't write it again
    twiboot::onStop();
    ASSERT_EQ(1, flashWrites);
}

TEST_F(Twiboot, FirstPageOfSegmentErasesIt) {
    memset(flashMemory + 0x0400, 0x00, SEGMENT_SIZE);
    const auto page = testPage(9);

    masterWrite(writePageCommand(0x0400, page));

    ASSERT_EQ(std::vector<uint16_t>{0xc400}, erasedSegments);
    ASSERT_EQ(page, flashContent(0xc400, PAGE_SIZE));
    ASSERT_EQ(std::vector<uint8_t>(SEGMENT_SIZE - PAGE_SIZE, 0xff), flashContent(0xc400 + PAGE_SIZE, SEGMENT_SIZE - PAGE_SIZE));
}

TEST_F(Twiboot, PageZeroInvalidatesApplication) {
    installApplication();
    ASSERT_TRUE(twiboot::isApplicationPresent());

    const auto page = testPage(11);
    masterWrite(writePageCommand(0, page));

    ASSERT_EQ((std::vector<uint16_t>{0xfa00, 0xc000}), erasedSegments);
    ASSERT_FALSE(twiboot::isApplicationPresent());
    ASSERT_EQ(page, flashContent(0xc000, PAGE_SIZE));
}

TEST_F(Twiboot, ErasedPageIsNotWritten) {
    masterWrite(writePageCommand(0x0200, std::vector<uint8_t>(PAGE_SIZE, 0xff)));

    ASSERT_EQ(std::vector<uint16_t>{0xc200}, erasedSegments);
    ASSERT_EQ(0, flashWrites);
}

TEST_F(Twiboot, BytesAfterPageAreIgnored) {
    const auto page = testPage(5);
    auto bytes = writePageCommand(0x0240, page);
    bytes.push_back(0xaa);
    bytes.push_back(0xbb);

    masterWrite(bytes);

    ASSERT_EQ(1, flashWrites);
    ASSERT_EQ(page, flashContent(0xc240, PAGE_SIZE));
}

TEST_F(Twiboot, VeryLongTransmissionDoesNotOverflow) {
    auto bytes = writePageCommand(0x0240, testPage(5));
    bytes.resize(1000, 0x12);

    masterWrite(bytes);

    ASSERT_EQ(1, flashWrites);
    ASSERT_EQ(testPage(5), flashContent(0xc240, PAGE_SIZE));
}

TEST_F(Twiboot, IncompletePageIsNotWritten) {
    auto bytes = writePageCommand(0x0200, testPage(5));
    bytes.pop_back();

    masterWrite(bytes);

    ASSERT_EQ(0, flashWrites);
    ASSERT_TRUE(erasedSegments.empty());
}

TEST_F(Twiboot, LastApplicationPageCanBeWritten) {
    const auto page = testPage(1);

    masterWrite(writePageCommand(APPLICATION_SIZE - PAGE_SIZE, page));

    ASSERT_EQ(1, flashWrites);
    ASSERT_EQ(page, flashContent(BOOTLOADER_START - PAGE_SIZE, PAGE_SIZE));
    ASSERT_TRUE(twiboot::isApplicationPresent());
}

TEST_F(Twiboot, BootloaderIsNeverOverwritten) {
    masterWrite(writePageCommand(APPLICATION_SIZE, testPage(1)));
    masterWrite(writePageCommand(FLASH_SIZE - PAGE_SIZE, testPage(2)));
    masterWrite(writePageCommand(0xffc0, testPage(3)));

    ASSERT_EQ(0, flashWrites);
    ASSERT_TRUE(erasedSegments.empty());
}

TEST_F(Twiboot, UnalignedPageIsNotWritten) {
    masterWrite(writePageCommand(0x0101, testPage(1)));
    masterWrite(writePageCommand(0x0220, testPage(1)));

    ASSERT_EQ(0, flashWrites);
    ASSERT_TRUE(erasedSegments.empty());
}

TEST_F(Twiboot, ReadFlashContinuesAcrossReads) {
    for (int i = 0; i < 4; ++i) {
        flashMemory[0x0300 + i] = 0x10 + i;
    }

    masterWrite(flashCommand(0x0300));

    ASSERT_EQ((std::vector<uint8_t>{0x10, 0x11}), masterRead(2));
    ASSERT_EQ((std::vector<uint8_t>{0x12, 0x13}), masterRead(2));
}

TEST_F(Twiboot, BootloaderCanBeRead) {
    flashMemory[FLASH_SIZE - 2] = 0x00;
    flashMemory[FLASH_SIZE - 1] = 0xfc;

    masterWrite(flashCommand(FLASH_SIZE - 2));

    ASSERT_EQ((std::vector<uint8_t>{0x00, 0xfc, 0xff, 0xff}), masterRead(4));
}

TEST_F(Twiboot, ReadWithoutCommand) {
    masterWrite({CMD_WAIT});

    ASSERT_EQ((std::vector<uint8_t>{0xff, 0xff}), masterRead(2));
}

TEST_F(Twiboot, InvalidMemoryType) {
    installApplication();

    masterWrite({CMD_ACCESS_MEMORY, 0x05, 0, 0});

    ASSERT_EQ((std::vector<uint8_t>{0xff, 0xff}), masterRead(2));
    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, StartApplicationCommand) {
    installApplication();

    masterWrite({CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION});

    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, SwitchApplicationWithInvalidBootType) {
    installApplication();

    masterWrite({CMD_SWITCH_APPLICATION, 0x00});

    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, UnknownCommandStartsApplication) {
    // the same as in twiboot: the master talking anything else isn't the bootloader host
    installApplication();

    masterWrite({0x55, 1, 2});

    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, WholeApplicationRoundTrip) {
    // the previous application, not erased
    memset(flashMemory, 0x5a, APPLICATION_SIZE);
    installApplication();

    std::vector<uint8_t> image(APPLICATION_SIZE, 0xff);
    for (size_t i = 0; i < 0x2600; ++i) {
        image[i] = static_cast<uint8_t>(i * 13 + (i >> 8));
    }
    image[APPLICATION_SIZE - 2] = 0x00; // reset vector
    image[APPLICATION_SIZE - 1] = 0xc0;

    masterWrite({CMD_WAIT});
    for (uint16_t address = 0; address < APPLICATION_SIZE; address += PAGE_SIZE) {
        masterWrite(writePageCommand(address, std::vector<uint8_t>(image.begin() + address,
                                                                   image.begin() + address + PAGE_SIZE)));
        if (address + PAGE_SIZE < APPLICATION_SIZE) {
            ASSERT_FALSE(twiboot::isApplicationPresent()) << "the reset vector is written by the last page";
        }

        masterWrite(flashCommand(address), false);
        ASSERT_EQ(std::vector<uint8_t>(image.begin() + address, image.begin() + address + PAGE_SIZE),
                  masterRead(PAGE_SIZE)) << "page 0x" << std::hex << address;
    }

    ASSERT_EQ(0, memcmp(image.data(), flashMemory, image.size()));
    ASSERT_EQ(APPLICATION_SIZE / SEGMENT_SIZE + 1, erasedSegments.size());

    masterWrite({CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION});
    ASSERT_TRUE(twiboot::shouldStartApplication());
}
