// this code is a port of https://github.com/mike-matera/FastPID

#pragma once

#include <inttypes.h>

#define INTEG_MAX    (INT32_MAX)
#define INTEG_MIN    (INT32_MIN)
#define DERIV_MAX    (INT16_MAX)
#define DERIV_MIN    (INT16_MIN)

#define PARAM_SHIFT  8
#define PARAM_BITS   16
#define PARAM_MAX    (((0x1ULL << PARAM_BITS)-1) >> PARAM_SHIFT)
#define PARAM_MULT   (((0x1ULL << PARAM_BITS)) >> (PARAM_BITS - PARAM_SHIFT))


namespace fastpid {
    constexpr static inline uint32_t floatToParam(const float in) {
        if (in > PARAM_MAX || in < 0) {
            return 0;
        }

        const uint32_t param = in * PARAM_MULT;

        if (in != 0 && param == 0) {
            return 0;
        }

        return param;
    }

    class FastPID {
    public:
        constexpr FastPID(const float kp,
                          const float ki,
                          const float kd,
                          const float hz,
                          const int16_t outputMin,
                          const int16_t outputMax)
            : _p(floatToParam(kp))
              , _i(floatToParam(ki / hz))
              , _d(floatToParam(kd * hz))
              , _outmax(int64_t(outputMax) * PARAM_MULT)
              , _outmin(int64_t(outputMin) * PARAM_MULT)
              , _last_sp(0)
              , _last_out(0)
              , _sum(0)
              , _last_err(0) {
        }

        void clear();

        int16_t step(int16_t sp, int16_t fb);

    private:
        // Configuration
        const uint32_t _p, _i, _d;
        const int64_t _outmax, _outmin;

        // State
        int16_t _last_sp, _last_out;
        int64_t _sum;
        int32_t _last_err;
    };
}
