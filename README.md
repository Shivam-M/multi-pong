# multi-pong
Networked Pong game written in several languages to work with each other

## Showcase
### Game server
![GAME_SERVER](https://i.imgur.com/nUa5R3f.png)

## Python client
![PYTHON_CLIENT](https://i.imgur.com/Ha84yMP.gif)

### Python
- client created using tkinter with resizable/scalable window with a fullscreen option
- regular game server status requests

### C#
- run `pong_network.exe` to a run a coordinator on port `4999`
- use the command line argument `-server [port]` to run a game server (default port: `5000`)
- client written using WPF .NET Core

### C++
- client written in OpenGL with a fullscreen option
- the C++ client features a cheat in the form of an injectable DLL
- `pong_cheat.dll` calculates the ball trajectory and moves the paddle accordingly
- after injection, use left/right arrow to select which paddle belongs to the user

### Java
- command line arguments similar to C# version - `pong_network.jar [[-server] [port]]`
- client written using Swing
