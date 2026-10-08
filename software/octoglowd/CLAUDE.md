# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`octoglowd` is the Kotlin/JVM daemon driving a Fallout-inspired VFD desktop device (see https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/). It reads sensors and remote data sources, renders them on a front VFD display, drives analog gauges / a "magic eye" tube / backlight, and integrates with Home Assistant over MQTT. It talks to the physical device over Linux I²C and runs on the device itself. Requires JVM 17.

Note: the README says "Compiles under Maven" — that is stale. The build is **Gradle** (Kotlin DSL).

## Commands

```bash
./gradlew build                        # compile + test
./gradlew test                         # run tests ("hardware" and "external" JUnit tags are excluded by default)
./gradlew test -PincludeTags=hardware,external   # also run device / third-party-service tests
./gradlew test --tests '*NetworkDataHarvesterTest'   # single test class
./gradlew test --tests '*NetworkDataHarvesterTest.methodName'  # single test method
./gradlew shadowJar                    # fat jar -> build/libs/octoglowd-all.jar
./gradlew proguard                     # minified jar -> build/libs/octoglowd-min.jar (depends on shadowJar)
./deploy.sh                            # proguard + scp jar to `octoglow` host + supervisorctl restart
```

The jar has a command line (Clikt, `OctoglowCommand` in `main.kt`):

```bash
java -jar octoglowd.jar                                      # run the daemon
java -jar octoglowd.jar --burn-firmware clock-display x.hex  # upload firmware over I2C instead, then exit
java -jar octoglowd.jar --burn-firmware front-display x.hex
java -jar octoglowd.jar --help
```

`firmware/<device>/burn-over-ssh.sh` (in the repository root, not here) builds the firmware, copies the hex to the `octoglow` host, stops octoglowd via supervisorctl, runs `--burn-firmware` and starts the daemon again. It refuses to run if the deployed jar doesn't support `--burn-firmware` yet, so run `./deploy.sh` first after changing this code.

Tests tagged `hardware` require the real device on the I²C bus; tests tagged `external` call third-party services using credentials from the gitignored `src/test/resources/test-config.json`. Both are skipped in the normal `test` task unless named in `-PincludeTags` (see `tasks.test` in `build.gradle.kts`). SQLDelight generates DB code from `src/main/sqldelight/**/*.sq` into package `eu.slomkowski.octoglow.octoglowd.db` at build time.

Runtime config is read from `config.json` in the working directory (`Config.parse`, `config.kt`) — a Kotlin-serialization-decoded JSON, not YAML (`test-config.yml` is unrelated test fixture data).

## Architecture

Everything is wired together manually in `main.kt` (`runDaemon()`). The core is an event-driven set of coroutine "demons" communicating over two shared-flow buses; there is no DI framework.

### Demons and buses

- **`Demon`** (`demon/pollingDemons.kt`) — the base unit of work. `createJobs(scope)` launches its coroutines; `close(scope)` for shutdown. **`PollingDemon`** subclass runs `poll()` on a fixed interval with automatic exception-catch-and-retry. `main.kt` builds a `demons` list and starts each with a random 1.5–4 s stagger.
- **`DataSnapshotBus`** and **`CommandBus`** (`eventBuses.kt`) — `MutableSharedFlow(replay = 100)` wrappers. Producers `publish(...)`, consumers `.snapshots` / `.commands`. This is the only coupling between most components.
- Two data directions:
  - **DataHarvesters** (`dataharvesters/`) extend `DataHarvester` (a `PollingDemon`) and `publish(Snapshot)` onto the `DataSnapshotBus`. Each harvester owns one data source (crypto, NBP forex, air quality, Geiger tube, local BME280/SCD40 sensors, radio weather sensor, SimpleMonitor, Todoist, network ping, Poznań garbage timetable).
  - **Commands** (`commands.kt`) flow on the `CommandBus` — dial/button events (`DialTurned`, `DialPressedLong/Short`) and device state toggles (`Backlight*`, `MagicEye*`). MQTT integration both publishes and consumes commands here.

### Data model (`dataSampleDefinitions.kt`)

`Snapshot` is the base type on the bus. `DataSnapshot` carries a list of `DataSample`s, each keyed by a `DataSampleType`. `DbDataSampleType` objects define a `databaseSymbol` (snake-cased class name by default) used as the storage key. `StateChanged` snapshots (backlight, magic eye) and `MqttConnectionChanged` also travel the bus.

### Persistence (`Database.kt`, SQLDelight)

`DatabaseDemon` subscribes to the bus and persists numeric `DataSample`s into `historical_values` (key + timestamp + REAL value). `changeable_settings` stores user-adjustable settings (e.g. brightness). `Database.kt` builds dynamic SQL for time-bucketed averaging of historical values (used by views to draw trend charts). Uses the JDBC SQLite driver.

### Front display (`demon/FrontDisplayDemon.kt` + `demon/frontdisplay/`)

`FrontDisplayDemon` is the most complex component. It:
- Polls the physical dial/button every 20 ms and feeds a small `DialState` state machine (short vs. long press by 450 ms threshold).
- Holds a list of **`FrontDisplayView`s** (`demon/frontdisplay/`), each generic over `<StatusType, InstantType>`. A view reacts to bus snapshots via `onNewDataSnapshot` (slow "status" data) and optionally polls fast-changing "instant" data via `pollForNewInstantData` (`pollInstantEvery`). It renders through three redraw flavors: static / status / instant.
- Runs a **`StateMachine`** (custom, `StateMachine.kt`) over `State` (`ViewCycle.Auto`, `ViewCycle.Manual`, `Menu.Overview`, `Menu.SettingOption`) reacting to `Event`s (button/encoder/timeout/status/instant updates), emitting `SideEffect`s that trigger redraws. Auto mode cycles views by a "most suitable view" score balancing data freshness and time-since-last-shown; the dial switches to Manual mode until `viewAutomaticCycleTimeout`.
- **`Menu`s** (e.g. `BrightnessMenu`, `MagicEyeMenu`, `BacklightMenu`) are settings entered by long-press.

`StateMachine.kt` is a self-contained Tinder-style finite state machine DSL (`create { state<...> { on<...> { transitionTo(...) } } }`) — used both for the front-display UI and the dial.

### Hardware (`hardware/`)

`Hardware` is an interface; `HardwareReal` wraps a Linux `I2CBus` behind a single `Mutex` (all bus access is serialized) with retry-on-`errno 6` logic. Individual I²C devices (`FrontDisplay`, `ClockDisplay`, `Geiger`, `Dac`, `Scd40`, `Bme280`) implement device-specific protocols; those with `HasBrightness` participate in global brightness. Tests use mock hardware under `test/.../hardware/mock`. Tests that take a real `Hardware` parameter (via `HardwareParameterResolver`) must carry the JUnit `hardware` tag.

`CustomI2cDevice` (the Octoglow boards: front display, clock display, Geiger) frames every command as `[CRC8, command, payload...]` and expects `[CRC8, command, ...]` back. Command numbers and payloads must match `firmware/<device>/.../protocol.hpp` in the repository root.

### Firmware upload (`firmware/`, `hardware/Twiboot.kt`)

Boards with the twiboot I²C bootloader can be updated by `--burn-firmware`:

- clock display: ATtiny461A, USI, bootloader in `firmware/clock-display/bootloader/`, application at 0x10, bootloader at 0x11;
- front display: ATmega88P, TWI, bootloader in `firmware/front-display/bootloader/`, application at 0x14, bootloader at 0x15. It can't show the progress of its own update, only the result.

- **`FirmwareTarget`** — one entry per board: command line name, bootloader address, AVR signature, how to enter the bootloader (`ClockDisplay.enterBootloader()`, `FrontDisplay.enterBootloader()`) and how to check the application afterwards. The command line name is the board's firmware directory name in the repository (`firmware/clock-display`), enforced by `FirmwareTargetTest`.
- **`FirmwareBurner`** — sends the enter bootloader command to the application (failure is only a warning: the board may already be in the bootloader), polls the bootloader until it answers (10 s, enough to power-cycle a board with broken firmware), checks signature and size, writes and verifies page by page with retries, starts the application and checks it responds.
- **`Twiboot`** — the protocol on top of `Hardware.doWrite`/`doTransaction`. The USI variant of the bootloader (clock display) NAKs the last byte of a page write and of the start application command, which Linux reports as an I/O error; these errors are ignored on purpose and every page is verified by reading it back. Reading flash returns the original vector table, although the bootloader patches it on the chip. The TWI variant (front display) uses the boot section, so nothing is patched, and it ACKs these bytes.
- **`TwibootDeviceEmulator`** (tests) emulates both boards on the bus level.
- **`IntelHex`** — parser for the `.hex` files from the firmware builds.
- Tests run against `TwibootDeviceEmulator` (test sources), a `Hardware` that emulates the application and the bootloader on the bus level, following `firmware/clock-display/bootloader/protocol.cpp`. Keep it in sync when the bootloader protocol changes.

In burn mode `HardwareReal` is not closed (closing the devices would overwrite the result shown on the front display) and the process ends via `ProgramResult`. `clikt-core` (used instead of full `clikt` to avoid Mordant) doesn't exit the process by default, so `OctoglowCommand` sets `exitProcess` in its context: without it, error exit codes would be 0 and the non-daemon I²C bus thread would keep the JVM running after burning.

### MQTT / Home Assistant (`mqtt/`)

`MqttDemon` bridges the `CommandBus`/`DataSnapshotBus` to an MQTT broker and publishes Home Assistant discovery messages (`homeassistantDiscovery.kt`). Enabled via `ConfMqttInfo` in config.

## Conventions

- Time uses `kotlin.time` (`Instant`, `Clock`, `Duration`) with `@OptIn(ExperimentalTime::class)` — many files opt in at the file level. `kotlinx.datetime` is used for calendar/`LocalTime` work.
- Logging via `io.github.oshai.kotlinlogging` (`KotlinLogging.logger {}`), backend is tinylog (`src/main/resources/tinylog.properties`).
- Some comments and TODOs are in Polish; the domain (garbage timetable, name days, NBP forex, holidays) is Poland-specific.
- Prefer injecting `Clock` (defaults to `Clock.System`) and `Hardware`/`Config` into demons and views to keep them testable, as existing code does.
- Check changes that may affect the deployed artifact with `./gradlew proguard` and `java -jar build/libs/octoglowd-min.jar --help`: the device runs the ProGuard-minified jar, not the test classpath.
