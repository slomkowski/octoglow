#include "animation.hpp"
#include "main.hpp"

#include <SDL.h>

#include <iostream>
#include <tuple>

using namespace std;

constexpr double STRIPE_WIDTH = 6;
constexpr double STRIPE_MAX_HEIGHT_BOTH = 35;
constexpr double STRIPE_MIN_HEIGHT = 5;

constexpr double WINDOW_WIDTH = 20;
constexpr double WINDOW_HEIGHT = 50;

constexpr tuple<uint8_t, uint8_t, uint8_t, uint8_t> BACKGROUND_COLOR(0x24, 0x24, 0x24, 0xff);
constexpr tuple<uint8_t, uint8_t, uint8_t, uint8_t> STRIPE_COLOR(0x42, 0xf4, 0xdc, 0xff);


int main(int, char *[]) {

    cout << "Magic eye animation test - Michał Słomkowski 2018.\n\nPress space to trigger the Geiger count.\n";

    if (SDL_Init(SDL_INIT_VIDEO) < 0) {
        cerr << "SDL could not initialize! SDL_Error: " << SDL_GetError() << endl;
        return 1;
    }

    float dpi;

    if (SDL_GetDisplayDPI(0, &dpi, nullptr, nullptr) != 0) {
        cerr << "Cannot get DPI: " << SDL_GetError() << endl;
        return 2;
    }

    auto inPx = [&dpi](const double inMm) -> int {
        return static_cast<int>(round(inMm * dpi / 25.4f));
    };

    SDL_Window *window = SDL_CreateWindow("MagicEye", SDL_WINDOWPOS_UNDEFINED, SDL_WINDOWPOS_UNDEFINED, inPx(WINDOW_WIDTH), inPx(WINDOW_HEIGHT), SDL_WINDOW_SHOWN);
    if (window == nullptr) {
        cerr << "Window could not be created! SDL_Error: " << SDL_GetError() << endl;
        return 3;
    }

    SDL_Renderer *const renderer = SDL_CreateRenderer(window, -1, SDL_RENDERER_ACCELERATED);

    bool quit = false;
    bool triggerGeigerCount = false;
    unsigned int lastTime = 0;

    while (!quit) {
        SDL_Event e;
        while (SDL_PollEvent(&e) != 0) {
            if (e.type == SDL_QUIT) {
                quit = true;
            } else if (e.type == SDL_KEYDOWN and e.key.keysym.sym == SDLK_SPACE) {
                triggerGeigerCount = true;
            }
        }

        const unsigned int currentTime = SDL_GetTicks();
        if (currentTime > lastTime + (1000.0 / octoglow::geiger::TICK_TIMER_FREQ)) {
            const uint8_t adcValue = octoglow::geiger::magiceye::_animate(triggerGeigerCount);
            triggerGeigerCount = false;

            SDL_Rect stripeLower;
            SDL_Rect stripeUpper;

            stripeUpper.x = stripeLower.x = inPx((WINDOW_WIDTH - STRIPE_WIDTH) / 2);
            stripeUpper.w = stripeLower.w = inPx(STRIPE_WIDTH);

            stripeUpper.y = inPx((WINDOW_HEIGHT - STRIPE_MAX_HEIGHT_BOTH) / 2.0);

            stripeUpper.h = stripeLower.h = inPx((static_cast<double>(adcValue) / 255.0) * (STRIPE_MAX_HEIGHT_BOTH - 2 * STRIPE_MIN_HEIGHT) / 2.0 + STRIPE_MIN_HEIGHT);

            stripeLower.y = inPx((WINDOW_HEIGHT + STRIPE_MAX_HEIGHT_BOTH) / 2.0) - stripeLower.h;

            SDL_SetRenderDrawColor(renderer, get<0>(BACKGROUND_COLOR), get<1>(BACKGROUND_COLOR), get<2>(BACKGROUND_COLOR), get<3>(BACKGROUND_COLOR));
            SDL_RenderClear(renderer);

            SDL_SetRenderDrawColor(renderer, get<0>(STRIPE_COLOR), get<1>(STRIPE_COLOR), get<2>(STRIPE_COLOR), get<3>(STRIPE_COLOR));
            SDL_RenderFillRect(renderer, &stripeUpper);
            SDL_RenderFillRect(renderer, &stripeLower);
            SDL_RenderPresent(renderer);

            lastTime = currentTime;
        }
    }

    SDL_DestroyWindow(window);
    SDL_Quit();

    return 0;
}