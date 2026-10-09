// this code is a port of https://github.com/mike-matera/FastPID

#include "FastPID.hpp"


void fastpid::FastPID::clear() {
    _last_sp = 0;
    _last_out = 0;
    _sum = 0;
    _last_err = 0;
}

int16_t fastpid::FastPID::step(int16_t sp, int16_t fb) {
    // int16 + int16 = int17
    int32_t err = int32_t(sp) - int32_t(fb);
    int32_t P = 0, I = 0;
    int32_t D = 0;

    if (_p) {
        // uint16 * int16 = int32
        P = int32_t(_p) * int32_t(err);
    }

    if (_i) {
        // int17 * int16 = int33
        _sum += int64_t(err) * int64_t(_i);

        // Anti-windup: the integral alone never exceeds the output range (it also fits 32 bits then),
        // otherwise a long saturation (e.g. the inverter starting from 0 V) would take as long to unwind.
        if (_sum > _outmax)
            _sum = _outmax;
        else if (_sum < _outmin)
            _sum = _outmin;

        // int32
        I = _sum;
    }

    if (_d) {
        // (int17 - int16) - (int16 - int16) = int19
        int32_t deriv = (err - _last_err) - int32_t(sp - _last_sp);
        _last_sp = sp;
        _last_err = err;

        // Limit the derivative to 16-bit signed value.
        if (deriv > DERIV_MAX)
            deriv = DERIV_MAX;
        else if (deriv < DERIV_MIN)
            deriv = DERIV_MIN;

        // int16 * int16 = int32
        D = int32_t(_d) * int32_t(deriv);
    }

    // int32 (P) + int32 (I) + int32 (D) = int34
    int64_t out = int64_t(P) + int64_t(I) + int64_t(D);

    // Make the output saturate
    if (out > _outmax)
        out = _outmax;
    else if (out < _outmin)
        out = _outmin;

    // Remove the integer scaling factor.
    int16_t rval = out >> PARAM_SHIFT;

    // Fair rounding.
    if (out & (0x1ULL << (PARAM_SHIFT - 1))) {
        rval++;
    }

    return rval;
}
