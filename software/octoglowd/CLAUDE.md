# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Overview

`octoglowd` is the Kotlin/JVM daemon driving a Fallout-inspired VFD desktop device (see https://slomkowski.eu/octoglow-vfd-fallout-inspired-display/). It reads sensors and remote data sources, renders them on a front VFD display, drives analog gauges / a "magic eye" tube / backlight, and integrates with Home Assistant over MQTT. It talks to the physical device over Linux I²C and runs on the device itself. Requires JVM 17.

Note: the README says "Compiles under Maven" — that is stale. The build is **Gradle** (Kotlin DSL).

## Commands

```bash
./gradlew build                        # compile + test
./gradlew test                         # run tests (the "hardware" JUnit tag is excluded by default)
./gradlew test --tests '*NetworkDataHarvesterTest'   # single test class
./gradlew test --tests '*NetworkDataHarvesterTest.methodName'  # single test method
./gradlew shadowJar                    # fat jar -> build/libs/octoglowd-all.jar
./gradlew proguard                     # minified jar -> build/libs/octoglowd-min.jar (depends on shadowJar)
./deploy.sh                            # proguard + scp jar to `octoglow` host + supervisorctl restart
```

Tests tagged `hardware` require a real device and are skipped in the normal `test` task (see `tasks.test` in `build.gradle.kts`). SQLDelight generates DB code from `src/main/sqldelight/**/*.sq` into package `eu.slomkowski.octoglow.octoglowd.db` at build time.

Runtime config is read from `config.json` in the working directory (`Config.parse`, `config.kt`) — a Kotlin-serialization-decoded JSON, not YAML (`test-config.yml` is unrelated test fixture data).

## Architecture

Everything is wired together manually in `main.kt`. The core is an event-driven set of coroutine "demons" communicating over two shared-flow buses; there is no DI framework.

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

`Hardware` is an interface; `HardwareReal` wraps a Linux `I2CBus` behind a single `Mutex` (all bus access is serialized) with retry-on-`errno 6` logic. Individual I²C devices (`FrontDisplay`, `ClockDisplay`, `Geiger`, `Dac`, `Scd40`, `Bme280`) implement device-specific protocols; those with `HasBrightness` participate in global brightness. Tests use mock hardware under `test/.../hardware/mock`. Hardware-dependent tests carry the JUnit `hardware` tag.

### MQTT / Home Assistant (`mqtt/`)

`MqttDemon` bridges the `CommandBus`/`DataSnapshotBus` to an MQTT broker and publishes Home Assistant discovery messages (`homeassistantDiscovery.kt`). Enabled via `ConfMqttInfo` in config.

## Conventions

- Time uses `kotlin.time` (`Instant`, `Clock`, `Duration`) with `@OptIn(ExperimentalTime::class)` — many files opt in at the file level. `kotlinx.datetime` is used for calendar/`LocalTime` work.
- Logging via `io.github.oshai.kotlinlogging` (`KotlinLogging.logger {}`), backend is tinylog (`src/main/resources/tinylog.properties`).
- Some comments and TODOs are in Polish; the domain (garbage timetable, name days, NBP forex, holidays) is Poland-specific.
- Prefer injecting `Clock` (defaults to `Clock.System`) and `Hardware`/`Config` into demons and views to keep them testable, as existing code does.
