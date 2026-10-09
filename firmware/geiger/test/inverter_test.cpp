#include "inverter.hpp"
#include "FastPID.hpp"
#include "protocol.hpp"

#include <gtest/gtest.h>

bool eyeInverterEnabled;

using namespace std;
using namespace octoglow::geiger::inverter;
using namespace octoglow::geiger::inverter::_private;

void octoglow::geiger::inverter::setEyeEnabled(const bool enabled) {
    eyeInverterEnabled = enabled;
}

TEST(Inverter, DesiredAdcValues) {
    // 400 V through the divider is below the 2.5 V reference
    ASSERT_GT(GEIGER_DESIRED_ADC_READOUT, 0);
    ASSERT_LT(GEIGER_DESIRED_ADC_READOUT, 0x3ff);
    ASSERT_NEAR(400.0, GEIGER_DESIRED_ADC_READOUT * REFERENCE_VOLTAGE / 0x3ff
                       * (GEIGER_DIVIDER_UPPER_RESISTOR + GEIGER_DIVIDER_LOWER_RESISTOR) / GEIGER_DIVIDER_LOWER_RESISTOR, 1.0);

    // the eye voltage grows with the brightness, values above the maximum are limited
    int16_t previous = -1;
    for (uint8_t brightness = 0; brightness <= octoglow::geiger::protocol::MAX_BRIGHTNESS; ++brightness) {
        setBrightness(brightness);
        ASSERT_GT(desiredEyeAdcValue, previous) << "brightness " << int(brightness);
        ASSERT_LT(desiredEyeAdcValue, 0x3ff);
        previous = desiredEyeAdcValue;
    }

    setBrightness(200);
    ASSERT_EQ(previous, desiredEyeAdcValue);

    setBrightness(3);
}

TEST(Inverter, PwmPeriodsFitTheProtocol) {
    // DeviceState reports the PWM values as one byte
    ASSERT_LE(GEIGER_PWM_PERIOD, 0xff);
    ASSERT_LE(EYE_PWM_PERIOD, 0xff);
    ASSERT_GE(GEIGER_MIDDLE_PWM_DUTY_CYCLES, geigerCycles(GEIGER_PWM_MIN_DUTY));
    ASSERT_LE(GEIGER_MIDDLE_PWM_DUTY_CYCLES, geigerCycles(GEIGER_PWM_MAX_DUTY));
    ASSERT_GE(EYE_MIDDLE_PWM_DUTY_CYCLES, eyeCycles(EYE_PWM_MIN_DUTY));
    ASSERT_LE(EYE_MIDDLE_PWM_DUTY_CYCLES, eyeCycles(EYE_PWM_MAX_DUTY));
}

TEST(Inverter, ReadAdcValueAveragesChannel) {
    for (uint8_t i = 0; i < ADC_TOTAL_SAMPLES_SIZE; ++i) {
        adcBuffer[i] = (i % 2 == EYE_ADC_CHANNEL) ? 1000 + i : 100;
    }

    ASSERT_EQ(100, readAdcValue(GEIGER_ADC_CHANNEL));
    ASSERT_EQ(1000 + 9, readAdcValue(EYE_ADC_CHANNEL)); // 0, 2, ... 18

    // no overflow with the maximum 10-bit readouts
    for (uint8_t i = 0; i < ADC_TOTAL_SAMPLES_SIZE; ++i) {
        adcBuffer[i] = 0x3ff;
    }
    ASSERT_EQ(0x3ff, readAdcValue(GEIGER_ADC_CHANNEL));
    ASSERT_EQ(0x3ff, readAdcValue(EYE_ADC_CHANNEL));
}

TEST(Inverter, GeigerRegulationStaysInRange) {
    const int16_t min = geigerCycles(GEIGER_PWM_MIN_DUTY);
    const int16_t max = geigerCycles(GEIGER_PWM_MAX_DUTY);

    // no voltage: full duty
    int16_t pwm = 0;
    for (int i = 0; i < 1000; ++i) {
        pwm = regulateGeigerInverter(0);
        ASSERT_GE(pwm, min);
        ASSERT_LE(pwm, max);
    }
    ASSERT_EQ(max, pwm);

    // overvoltage: minimum duty
    for (int i = 0; i < 1000; ++i) {
        pwm = regulateGeigerInverter(0x3ff);
        ASSERT_GE(pwm, min);
        ASSERT_LE(pwm, max);
    }
    ASSERT_EQ(min, pwm);
}

TEST(Inverter, EyeRegulationStaysInRange) {
    const int16_t min = eyeCycles(EYE_PWM_MIN_DUTY);
    const int16_t max = eyeCycles(EYE_PWM_MAX_DUTY);

    setBrightness(3);

    int16_t pwm = 0;
    for (int i = 0; i < 1000; ++i) {
        pwm = regulateEyeInverter(0);
        ASSERT_GE(pwm, min);
        ASSERT_LE(pwm, max);
    }
    ASSERT_EQ(max, pwm);

    for (int i = 0; i < 1000; ++i) {
        pwm = regulateEyeInverter(0x3ff);
    }
    ASSERT_EQ(min, pwm);
}

/**
 * Inverter model: the output voltage follows the duty cycle with a first-order lag (time constant 1 s),
 * the maximum duty gives 450 V.
 */
TEST(Inverter, GeigerRegulationReachesVoltage) {
    // the state of the regulator from power-up
    for (int i = 0; i < 1000; ++i) {
        regulateGeigerInverter(0x3ff);
    }

    const double voltsPerCycle = 450.0 / geigerCycles(GEIGER_PWM_MAX_DUTY);
    double voltage = 0;
    double maxVoltage = 0;

    for (int i = 0; i < 3000; ++i) {
        const int16_t adc = desiredAdcReadout(GEIGER_DIVIDER_UPPER_RESISTOR, GEIGER_DIVIDER_LOWER_RESISTOR, voltage);
        const int16_t pwm = regulateGeigerInverter(adc);
        voltage += 0.01 * (pwm * voltsPerCycle - voltage);
        maxVoltage = std::max(maxVoltage, voltage);
    }

    ASSERT_NEAR(400.0, voltage, 10.0);
    ASSERT_LT(maxVoltage, 410.0) << "the integral winds up while the voltage rises";
}

TEST(FastPID, Saturates) {
    fastpid::FastPID pid(1.0, 0.0, 0.0, 100, -10, 20);

    ASSERT_EQ(20, pid.step(1000, 0));
    ASSERT_EQ(-10, pid.step(-1000, 0));
    ASSERT_EQ(5, pid.step(5, 0));
}

TEST(FastPID, IntegratesAndClears) {
    fastpid::FastPID pid(0.0, 1.0, 0.0, 1, 0, 1000);

    ASSERT_EQ(10, pid.step(10, 0));
    ASSERT_EQ(20, pid.step(10, 0));
    ASSERT_EQ(30, pid.step(10, 0));

    pid.clear();
    ASSERT_EQ(10, pid.step(10, 0));
}

TEST(FastPID, RecoversFromSaturationImmediately) {
    fastpid::FastPID pid(0.0, 1.0, 0.0, 1, 0, 100);

    for (int i = 0; i < 10000; ++i) {
        pid.step(50, 0);
    }
    ASSERT_EQ(100, pid.step(50, 0));

    // the integral doesn't wind up above the output range
    ASSERT_EQ(50, pid.step(0, 50));
}

TEST(FastPID, InvalidParameterIsDisabled) {
    // parameters above 255 don't fit the fixed-point format
    ASSERT_EQ(0u, fastpid::floatToParam(300.0));
    ASSERT_EQ(0u, fastpid::floatToParam(-1.0));
    ASSERT_EQ(256u, fastpid::floatToParam(1.0));
}
