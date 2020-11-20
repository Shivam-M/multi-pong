#include <iostream>
#include <stdio.h>
#include <chrono>
#include <ctime>
#include <string>
#include <algorithm>
#include <WS2tcpip.h>
#include <sstream>
#include <Windows.h>
#include <GLFW/glfw3.h>
#include <json.hpp>

#pragma comment (lib, "ws2_32.lib")


enum Direction { UP = -1, DOWN = 1 };

const std::string ADDRESS = "127.0.0.1";
const int PORT = 4999;

int frame_rate, update_rate, call_rate = 0;
int scores[2] = { 0, 0 };
double paddles[2] = { 0.6, 0.6 };
double ball_position[2] = { 0.4921, 0.4861 };
bool fullscreen = false;
GLFWwindow* window;

sockaddr_in game_server;
sockaddr_in coordinator_server;
SOCKET game_socket;
SOCKET coordinator_socket;

using namespace std;
using json = nlohmann::json;

void render_window(void);
void move_paddle(Direction);
void connect_server(PCSTR, int, string);
void send_server(string);


int* retrieve_dimensions() {
    int width, height;
    glfwGetWindowSize(window, &width, &height);
    glViewport(0, 0, width, height);
    std::stringstream temp_str;
    temp_str << "Pong - (" << scores[0] << " vs " << scores[1] << ") [" << width << 'x' << height << "] - UD: " << update_rate << " - FR: " << frame_rate << " - press F to toggle fullscreen";
    std::string str = temp_str.str();
    const char* cstr2 = str.c_str();
    call_rate++;
    if (call_rate > 60) {
        glfwSetWindowTitle(window, cstr2);
        call_rate = 0;
    } return (new int[] {width, height});
}

void toggle_fullscreen() {
    fullscreen = !fullscreen;
    glfwSetWindowMonitor(window, fullscreen ? glfwGetPrimaryMonitor() : NULL, 100, 100, retrieve_dimensions()[0], retrieve_dimensions()[1], GLFW_DONT_CARE);
    render_window();
}

void draw_quad(double x, double y, double width, double height) {
    glColor3dv(new double[] {1.0, 1.0, 1.0});
    glBegin(GL_QUADS);

    x = x < 0.5 ? -1 + (x * 2) : x > 0.5 ? ((x - 0.5) * 2) : 0;
    y = y < 0.5 ? (-1 + (y * 2)) * -1 : y > 0.5 ? ((y - 0.5) * 2) * -1 : 0;

    glVertex2d(x, y);
    glVertex2d(x, y + height);
    glVertex2d(x + width, y + height);
    glVertex2d(x + width, y);

    glEnd();
}

void render_window() {
    auto initial = std::chrono::system_clock::now();
    int* resolution = retrieve_dimensions();
    glClear(GL_COLOR_BUFFER_BIT);
    draw_quad(0.075, paddles[0], (20.0 / resolution[0]) * 2, (100.0 / resolution[1]) * 2);
    draw_quad(0.9094, paddles[1], (20.0 / resolution[0]) * 2, (100.0 / resolution[1]) * 2);
    draw_quad(ball_position[0], ball_position[1], (20.0 / resolution[0]) * 2, (20.0 / resolution[1]) * 2);
    glfwSwapBuffers(window);
    std::chrono::duration<double> elapsed_seconds = std::chrono::system_clock::now() - initial;
    frame_rate = (int)(1 / elapsed_seconds.count());
}

void key_callback(GLFWwindow* window, int key, int scancode, int action, int mods) {
    if (action == GLFW_RELEASE)
        if (key == 70) toggle_fullscreen(); 
    if (key == 265) move_paddle(UP);
    if (key == 264) move_paddle(DOWN);
    render_window();
}

void move_paddle(Direction direction) {
    json movement_request;
    movement_request["data-type"] = "movement";
    movement_request["direction"] = (int)direction;
    send_server(movement_request.dump());
}

void connect_coordinator() {
    string search_request = "{\"data-type\": \"search\"}";
    char buffer[1024] = { 0 };

    coordinator_socket = socket(AF_INET, SOCK_STREAM, 0);
    coordinator_server.sin_family = AF_INET;
    coordinator_server.sin_port = htons(PORT);

    inet_pton(AF_INET, ADDRESS.c_str(), &coordinator_server.sin_addr);

    if (connect(coordinator_socket, (struct sockaddr*)&coordinator_server, sizeof(coordinator_server)) < 0) {
        printf("\nConnection Failed \n");
        return;
    }

    send(coordinator_socket, search_request.c_str(), strlen(search_request.c_str()), 0);
}

void send_coordinator(string message) {
    send(coordinator_socket, message.c_str(), message.size() + 1, 0);
}

void listen_coordinator() {
    cout << "Waiting for a match..." << endl;
    while (true) {
        char buffer[150];
        ZeroMemory(buffer, 150);
        int receive_status = recv(coordinator_socket, buffer, 150, 0);
        if (receive_status) {
            string formatted = buffer;
            replace(formatted.begin(), formatted.end(), '\'', '"');
            json information = json::parse(formatted);
            if (information["data-type"] == "match") {
                string server_token = information.at("token");
                string server_address = information.at("address");
                int server_port = information.at("port");
                connect_server(server_address.c_str(), server_port, server_token);
                break;
            }
        }
    }
}

void connect_server(PCSTR address, int port, string token) {
    json auth_request;
    auth_request["data-type"] = "auth";
    auth_request["token"] = token;
    game_server.sin_family = AF_INET;
    game_server.sin_port = htons(port);
    game_socket = socket(AF_INET, SOCK_DGRAM, 0);
    inet_pton(AF_INET, address, &game_server.sin_addr);
    send_server(auth_request.dump());
}

void send_server(string message) {
    message += "\n";
    sendto(game_socket, message.c_str(), message.size(), 0, (sockaddr*)&game_server, sizeof(game_server));
}

void listen_server() {
    char buffer[1024];
    ZeroMemory(buffer, 1024);
}

void retrieve() {
    char buffer[150];
    ZeroMemory(buffer, 150);
    int receive_status = recv(game_socket, buffer, 150, 0);
    if (receive_status) {
        string formatted = buffer;
        replace(formatted.begin(), formatted.end(), '\'', '"');
        json information = json::parse(formatted);
        if (information["data-type"] == "placement") {
            int identifier = information["identifier"];
            int direction = information["direction"];
            paddles[identifier] += direction / 75.0;
        } else if (information["data-type"] == "position") {
            double position_x = information["x"];
            double position_y = information["y"];
            ball_position[0] = position_x;
            ball_position[1] = position_y;
            render_window();
        } else if (information["data-type"] == "score") {
            scores[0] = information["0"];
            scores[1] = information["1"];
        }
    }
}

int old_main() {

    WSADATA data;
    WORD version = MAKEWORD(2, 2);

    int winsock_status = WSAStartup(version, &data);
    if (winsock_status != 0) {
        std::cout << "Winsock initialisation error!" << std::endl;
        return winsock_status;
    }

    connect_coordinator();
    listen_coordinator();

    if (!glfwInit()) return -1;

    if (!(window = glfwCreateWindow(1280, 720, "Pong", NULL, NULL))) {
        glfwTerminate();
        return -1;
    }

    glfwMakeContextCurrent(window);
    glClearColor(0.155, 0.155, 0.155, 0);
    glfwSetKeyCallback(window, key_callback);

    render_window();

    while (!glfwWindowShouldClose(window)) {
        auto initial = std::chrono::system_clock::now();
        retrieve();
        glfwPollEvents();
        std::chrono::duration<double> elapsed_seconds = std::chrono::system_clock::now() - initial;
        update_rate = (int)(1 / elapsed_seconds.count());
    }

    glfwTerminate();
    return 0;
}