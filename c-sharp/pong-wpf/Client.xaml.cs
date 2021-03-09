using Newtonsoft.Json.Linq;
using System;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;

namespace pong_wpf {

    public partial class Client : Window {

        const string COORDINATOR_ADDRESS = "127.0.0.1";
        const int COORDINATOR_PORT = 4999;

        const int WINDOW_HEIGHT = 720;
        const int WINDOW_WIDTH = 1280;

        const Key KEY_UP = Key.Up;
        const Key KEY_DOWN = Key.Down;

        double[] BallPosition, PlayerScores, PaddlePositons = { 0.45, 0.45 };
        Button[] PaddleItems;

        IPEndPoint CoordinatorEndPoint = new IPEndPoint(IPAddress.Parse(COORDINATOR_ADDRESS), COORDINATOR_PORT);
        Socket CoordinatorSocket = new Socket(AddressFamily.InterNetwork, SocketType.Stream, ProtocolType.Tcp);

        IPEndPoint GameEndPoint;
        Socket GameServer = new Socket(AddressFamily.InterNetwork, SocketType.Dgram, ProtocolType.Udp);

        bool ActivelyListening = true;

        public Client() {
            InitializeComponent();
            ConnectCoordinator();
            PaddleItems = new Button[] { Paddle_1, Paddle_2 };
        }

        void ConnectCoordinator() {
            CoordinatorSocket.Connect(CoordinatorEndPoint);
            CoordinatorSocket.Send(Encoding.ASCII.GetBytes("{\"data-type\": \"search\"}\n"));
            new Thread(new ThreadStart(ListenCoordinator)).Start();
        }

        void ListenCoordinator() {
            while (ActivelyListening) {
                try {
                    var bufferedData = new byte[1024];
                    var receivedData = CoordinatorSocket.Receive(bufferedData);
                    string stringData = Encoding.ASCII.GetString(bufferedData, 0, receivedData);
                    if (receivedData == 0) {
                        CoordinatorSocket.Close();
                    } else {
                        try {
                            JObject matchInformation = JObject.Parse(stringData);
                            if (matchInformation["data-type"].ToString().Equals("match")) {
                                ActivelyListening = false;
                                string serverToken = matchInformation["token"].ToString();
                                string serverAddress = matchInformation["address"].ToString();
                                int serverPort = (int)matchInformation["port"];
                                ConnectServer(serverAddress, serverPort, serverToken);
                                new Thread(new ThreadStart(ListenServer)).Start();
                            }
                        } catch (Exception e) {
                            Console.WriteLine(e);
                        }
                    }
                } catch (Exception e) {
                    Console.WriteLine(e); break;
                }
            }
        }

        void ConnectServer(string address, int port, string token) {
            GameEndPoint = new IPEndPoint(IPAddress.Parse(address), port);
            GameServer.Connect(GameEndPoint);
            GameServer.Send(Encoding.ASCII.GetBytes((new JObject { ["data-type"] = "auth", ["token"] = token }).ToString()));
        }

        void PlaceRelatively(Button item, double x, double y) {
            Dispatcher.Invoke(() => {
                Thickness itemMargin = (Thickness)item.GetValue(MarginProperty);
                itemMargin.Left = (int)(WINDOW_WIDTH * x);
                itemMargin.Top = (int)(WINDOW_HEIGHT * y);
                item.Margin = itemMargin;
            });
        }

        void MovePaddle(int direction) {
            GameServer.Send(Encoding.ASCII.GetBytes((new JObject { ["data-type"] = "movement", ["direction"] = direction }).ToString()));
        }

        void ListenServer() {
            while (true) {
                var bufferedData = new byte[1024];
                var receivedData = GameServer.Receive(bufferedData);
                string receiveBytes = Encoding.ASCII.GetString(bufferedData, 0, receivedData);
                JObject gameInformation = JObject.Parse(receiveBytes);
                switch (gameInformation["data-type"].ToString()) {
                    case "placement":
                        int paddleIndex = (int)gameInformation["identifier"];
                        PaddlePositons[paddleIndex] += (int)gameInformation["direction"] / 75.0;
                        PlaceRelatively(PaddleItems[paddleIndex], paddleIndex == 0 ? 0.075 : 0.9094, PaddlePositons[paddleIndex]);
                        break;
                    case "position":
                        BallPosition = new double[] { (double)gameInformation["x"], (double)gameInformation["y"] };
                        PlaceRelatively(Ball, BallPosition[0], BallPosition[1]);
                        break;
                    case "score":
                        PlayerScores = new double[] { (int)gameInformation["0"], (int)gameInformation["1"] };
                        Dispatcher.Invoke(() => { Title = $"{PlayerScores[0]} vs {PlayerScores[1]}"; });
                        break;
                }
            }
        }

        void P_KeyDown(object sender, KeyEventArgs e) {
            if (e.Key == KEY_UP || e.Key == KEY_DOWN)
                MovePaddle(e.Key == KEY_UP ? -1 : 1);
        }

    }
}
