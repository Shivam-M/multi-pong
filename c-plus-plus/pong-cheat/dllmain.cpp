#include "pch.h"
#include "Memory.h"
#include <stdio.h>
#include <string>
#include <iostream>

#define PADDLE_MOVEMENT_OFFSET  0x04057D
#define PADDLE_POSITION_OFFSET  0x109000
#define BALL_POSITION_OFFSET    0x109010

typedef __int64(__cdecl * _move_paddle)(int direction);
_move_paddle move_paddle;

DWORD WINAPI MainThread(LPVOID param) {
    std::cout << "Cheat activated, use LEFT/RIGHT arrow keys to toggle the player paddle." << std::endl;
    std::uintptr_t base_address = reinterpret_cast<uintptr_t>(GetModuleHandle(NULL));
    move_paddle = (_move_paddle)(base_address + PADDLE_MOVEMENT_OFFSET);
    DWORD* paddle_address = (DWORD*)(base_address + PADDLE_POSITION_OFFSET);
    DWORD* ball_address = (DWORD*)(base_address + BALL_POSITION_OFFSET);
    double last_ball_position[2] = { *(double*)ball_address, *(double*)(ball_address + 0x2) };
    double ball_velocity[2] = { 0, 0 };
    int selected_paddle = 1;

    while (true) {
        double paddles[2] = { *(double*)paddle_address, *(double*)(paddle_address + 0x2) };
        double ball_position[2] = { *(double*)ball_address, *(double*)(ball_address + 0x2) };

        ball_velocity[0] = ball_position[0] - last_ball_position[0];
        ball_velocity[1] = ball_position[1] - last_ball_position[1];

        last_ball_position[0] = ball_position[0];
        last_ball_position[1] = ball_position[1];

        if ((ball_velocity[0] > 0.0 && selected_paddle == 1) || (ball_velocity[0] < 0.0 && selected_paddle == 0)) {
            double x_loc = selected_paddle == 1 ? 0.9 : 0.09;
            double distance = ball_position[0] - x_loc;
            double steps = abs(distance / ball_velocity[0]);
            double y_loc = ball_position[1] + (ball_velocity[1] * steps);
            if (paddles[selected_paddle] - 0.025 > y_loc) {
                if (paddles[selected_paddle] > 0.0)
                    move_paddle(-1);
            } else if (paddles[selected_paddle] - 0.025 < y_loc) {
                if (paddles[selected_paddle] < 1.0)
                    move_paddle(1);
            }
        } selected_paddle = GetAsyncKeyState(VK_LEFT) ? 0 : (GetAsyncKeyState(VK_RIGHT) ? 1 : selected_paddle);
    } return 0;
}

BOOL APIENTRY DllMain(HMODULE hModule, DWORD  ul_reason_for_call, LPVOID lpReserved) {
    switch (ul_reason_for_call) {
        case DLL_PROCESS_ATTACH:
            CreateThread(NULL, NULL, MainThread, hModule, NULL, NULL);
            break;
        case DLL_PROCESS_DETACH:
            break;
    } return TRUE;
}
