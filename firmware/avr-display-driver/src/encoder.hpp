#pragma once

#include <stdint.h>


namespace vfd {
    namespace encoder {
        void init();

        void pool(); // todo zdecydować, czy pooling lub przerwanie

        /**
         * 
         */
        int8_t getValueAndClear();
    }
}
