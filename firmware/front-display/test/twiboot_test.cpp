#include "twiboot.hpp"
#include "main.hpp"
#include "stubs.hpp"

#include <gtest/gtest.h>

#include <cstring>
#include <string>
#include <vector>

using namespace octoglow::front_display;
using namespace octoglow::front_display::bootloader;

constexpr uint16_t BOOTLOADER_START = TWIBOOT_START;
constexpr int TIMEOUT_TICKS = TWIBOOT_TIMEOUT_MS / twiboot::TIMER_TICK_MS;

constexpr uint8_t CMD_WAIT = 0x00;
constexpr uint8_t CMD_READ_VERSION = 0x01;
constexpr uint8_t CMD_SWITCH_APPLICATION = 0x01;
constexpr uint8_t CMD_ACCESS_MEMORY = 0x02;
constexpr uint8_t BOOTTYPE_APPLICATION = 0x80;
constexpr uint8_t MEMTYPE_CHIPINFO = 0x00;
constexpr uint8_t MEMTYPE_FLASH = 0x01;

/**
 * Master write transmission as the TWI slave delivers it: after onDataWrite() returns false,
 * the following bytes are NAKed by the hardware and never reach the protocol.
 * @return number of bytes delivered to the protocol
 */
static size_t masterWrite(const std::vector<uint8_t> &bytes) {
    for (size_t i = 0; i < bytes.size(); ++i) {
        if (!twiboot::onDataWrite(i, bytes[i])) {
            return i + 1;
        }
    }
    return bytes.size();
}

static std::vector<uint8_t> masterRead(const size_t length) {
    std::vector<uint8_t> result;
    for (size_t i = 0; i < length; ++i) {
        result.push_back(twiboot::onDataRead(i));
    }
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
    std::vector<uint8_t> page(SPM_PAGESIZE);
    for (size_t i = 0; i < page.size(); ++i) {
        page[i] = static_cast<uint8_t>(seed + 7 * i);
    }
    return page;
}

static bool flashIsErased() {
    for (const auto b: test::flash) {
        if (b != 0xff) {
            return false;
        }
    }
    return true;
}

class Twiboot : public ::testing::Test {
protected:
    void SetUp() override {
        memset(test::flash, 0xff, sizeof(test::flash));
        test::flashPageWrites = 0;
        twiboot::init();
    }
};

TEST_F(Twiboot, StartsApplicationAfterTimeout) {
    for (int i = 0; i < TIMEOUT_TICKS - 1; ++i) {
        twiboot::onTimerTick();
        ASSERT_FALSE(twiboot::shouldStartApplication()) << "tick " << i;
    }

    twiboot::onTimerTick();
    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, WaitCommandAbortsTimeout) {
    ASSERT_EQ(1u, masterWrite({CMD_WAIT}));

    for (int i = 0; i < 10 * TIMEOUT_TICKS; ++i) {
        twiboot::onTimerTick();
    }

    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, ReadVersion) {
    masterWrite({CMD_READ_VERSION});

    const auto version = masterRead(16);

    ASSERT_EQ(std::string("TWIBOOT v3.2"), std::string(version.begin(), version.begin() + 12));
    for (size_t i = 12; i < version.size(); ++i) {
        ASSERT_EQ(0, version[i]);
    }
    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, ReadChipInfo) {
    ASSERT_EQ(4u, masterWrite({CMD_ACCESS_MEMORY, MEMTYPE_CHIPINFO, 0, 0}));

    ASSERT_EQ((std::vector<uint8_t>{
                  0x1e, 0x93, 0x0f, // ATmega88P
                  SPM_PAGESIZE,
                  BOOTLOADER_START >> 8, BOOTLOADER_START & 0xff,
                  0, 0,
              }), masterRead(8));
}

TEST_F(Twiboot, WriteAndReadFlashPage) {
    const auto page = testPage(3);

    ASSERT_EQ(4u + SPM_PAGESIZE, masterWrite(writePageCommand(0x0140, page)));

    ASSERT_EQ(1, test::flashPageWrites);
    ASSERT_EQ(0, memcmp(page.data(), test::flash + 0x0140, SPM_PAGESIZE));
    ASSERT_EQ(0xff, test::flash[0x0140 - 1]);
    ASSERT_EQ(0xff, test::flash[0x0140 + SPM_PAGESIZE]);

    masterWrite(flashCommand(0x0140));
    ASSERT_EQ(page, masterRead(SPM_PAGESIZE));
}

TEST_F(Twiboot, WritePageZero) {
    // no vector table patching, unlike the clock display: BOOTRST fuse starts the bootloader
    const auto page = testPage(11);

    masterWrite(writePageCommand(0, page));

    ASSERT_EQ(0, memcmp(page.data(), test::flash, SPM_PAGESIZE));
}

TEST_F(Twiboot, BytesAfterPageAreNaked) {
    auto bytes = writePageCommand(0x0200, testPage(5));
    bytes.push_back(0xaa);
    bytes.push_back(0xbb);

    ASSERT_EQ(4u + SPM_PAGESIZE, masterWrite(bytes));
    ASSERT_EQ(1, test::flashPageWrites);
}

TEST_F(Twiboot, IncompletePageIsNotWritten) {
    auto bytes = writePageCommand(0x0200, testPage(5));
    bytes.pop_back();

    masterWrite(bytes);

    ASSERT_EQ(0, test::flashPageWrites);
    ASSERT_TRUE(flashIsErased());
}

TEST_F(Twiboot, LastApplicationPageCanBeWritten) {
    const auto page = testPage(1);

    masterWrite(writePageCommand(BOOTLOADER_START - SPM_PAGESIZE, page));

    ASSERT_EQ(1, test::flashPageWrites);
    ASSERT_EQ(0, memcmp(page.data(), test::flash + BOOTLOADER_START - SPM_PAGESIZE, SPM_PAGESIZE));
}

TEST_F(Twiboot, BootloaderIsNeverOverwritten) {
    masterWrite(writePageCommand(BOOTLOADER_START, testPage(1)));
    masterWrite(writePageCommand(test::FLASH_SIZE - SPM_PAGESIZE, testPage(2)));

    ASSERT_EQ(0, test::flashPageWrites);
    ASSERT_TRUE(flashIsErased());
}

TEST_F(Twiboot, UnalignedPageIsNotWritten) {
    masterWrite(writePageCommand(0x0101, testPage(1)));

    ASSERT_EQ(0, test::flashPageWrites);
    ASSERT_TRUE(flashIsErased());
}

TEST_F(Twiboot, ReadFlashContinuesAcrossReads) {
    for (int i = 0; i < 4; ++i) {
        test::flash[0x0300 + i] = 0x10 + i;
    }

    masterWrite(flashCommand(0x0300));

    ASSERT_EQ((std::vector<uint8_t>{0x10, 0x11}), masterRead(2));
    ASSERT_EQ((std::vector<uint8_t>{0x12, 0x13}), masterRead(2));
}

TEST_F(Twiboot, ReadWithoutCommand) {
    masterWrite({CMD_WAIT});

    ASSERT_EQ((std::vector<uint8_t>{0xff, 0xff}), masterRead(2));
}

TEST_F(Twiboot, InvalidMemoryType) {
    ASSERT_EQ(2u, masterWrite({CMD_ACCESS_MEMORY, 0x05, 0, 0}));

    ASSERT_EQ((std::vector<uint8_t>{0xff, 0xff}), masterRead(2));
    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, StartApplicationCommand) {
    ASSERT_EQ(2u, masterWrite({CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION}));

    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, SwitchApplicationWithInvalidBootType) {
    ASSERT_EQ(2u, masterWrite({CMD_SWITCH_APPLICATION, 0x00}));

    ASSERT_FALSE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, UnknownCommandStartsApplication) {
    // the same as in twiboot: the master talking anything else isn't the bootloader host
    ASSERT_EQ(1u, masterWrite({0x55, 1, 2}));

    ASSERT_TRUE(twiboot::shouldStartApplication());
}

TEST_F(Twiboot, WholeApplicationRoundTrip) {
    std::vector<uint8_t> image(BOOTLOADER_START);
    for (size_t i = 0; i < image.size(); ++i) {
        image[i] = static_cast<uint8_t>(i * 13 + (i >> 8));
    }

    masterWrite({CMD_WAIT});
    for (uint16_t address = 0; address < BOOTLOADER_START; address += SPM_PAGESIZE) {
        masterWrite(writePageCommand(address, std::vector<uint8_t>(image.begin() + address,
                                                                   image.begin() + address + SPM_PAGESIZE)));
    }

    ASSERT_EQ(BOOTLOADER_START / SPM_PAGESIZE, test::flashPageWrites);
    ASSERT_EQ(0, memcmp(image.data(), test::flash, image.size()));

    masterWrite({CMD_SWITCH_APPLICATION, BOOTTYPE_APPLICATION});
    ASSERT_TRUE(twiboot::shouldStartApplication());
}
