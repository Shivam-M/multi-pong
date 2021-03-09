using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace pong_network {
    class Server {

        public const int PORT = 5001;

        double[] BallPosition = { 0.4921, 0.4861 }, BallVelocity = { 0, 0 }, PaddlePositions = { 0.45, 0.45 };
        int[] PlayerScores = { 0, 0 };
        string MatchPhase = "waiting";

        List<string> GameCoordinators = new List<string> { "127.0.0.1" };
        List<string> ExpectedTokens = new List<string> {};
        List<IPEndPoint> ClientAddresses = new List<IPEndPoint>();

        IPEndPoint LocalEndPoint;
        UdpClient ServerSocket = new UdpClient();
        
        public Server(int port = PORT) {
            LocalEndPoint = new IPEndPoint(IPAddress.Any, port);
            ServerSocket = new UdpClient(LocalEndPoint);
            ListenClients();
        }

        bool PaddleColliding(double x1, double y1, double w1, double h1, double x2, double y2, double w2, double h2) {
            return !(x1 + w1 < x2 || x2 + w2 < x1 || y1 + h1 < y2 || y2 + h2 < y1);
        }

        void UpdatePosition() {
            Random random = new Random();
            if (random.NextDouble() <= 0.50) {
                BallVelocity[0] = -0.005;
            } else {
                BallVelocity[0] = 0.005;
            }
            if (random.NextDouble() <= 0.50) {
                BallVelocity[1] = -0.005;
            } else {
                BallVelocity[1] = 0.005;
            }
            long currentTime = (long)(DateTime.UtcNow.Subtract(new DateTime(1970, 1, 1))).TotalMilliseconds;
            int updateCycles = 0;
            while (!MatchPhase.Equals("waiting")) {
                BallPosition[0] += BallVelocity[0];
                BallPosition[1] += BallVelocity[1];
                if (!(0.0 < BallPosition[1] && BallPosition[1] < 1.0)) {
                    BallVelocity[1] *= -1;
                }
                if (!(0.0 < BallPosition[0] && BallPosition[0] < 1.0)) {
                    if (BallPosition[0] < 0.0) {
                        PlayerScores[0] += 1;
                    } else if (BallPosition[0] > 1.0) {
                        PlayerScores[1] += 1;
                    }
                    Console.WriteLine($"Score: {PlayerScores[0]} vs {PlayerScores[1]}");
                    JObject scoreInformation = new JObject { ["data-type"] = "score", ["0"] = PlayerScores[0], ["1"] = PlayerScores[1] };
                    byte[] scoreResponse = Encoding.ASCII.GetBytes(scoreInformation.ToString() + "\n");
                    ServerSocket.Send(scoreResponse, scoreResponse.Length, ClientAddresses[0]);
                    ServerSocket.Send(scoreResponse, scoreResponse.Length, ClientAddresses[1]);
                    BallPosition = new double[] { 0.4921, 0.4861 };
                }
                for (int x = 0; x < 2; x++) {
                    double paddlePosition = PaddlePositions[x];
                    double xPosition = x == 0 ? 0.075 : 0.9094;
                    if (PaddleColliding(xPosition, paddlePosition, 0.015625, 0.138888, BallPosition[0], BallPosition[1], 0.0152625, 0.0277778)) {  
                        BallVelocity[0] *= -1;
                        double relativePosition = Math.Abs((paddlePosition - BallPosition[1]) / 0.138888);
                        double updatedVelocity = 0.0025;
                        if (relativePosition <= 0.25) {
                            updatedVelocity = 0.01;
                        } else if (relativePosition <= 0.50) {
                            updatedVelocity = 0.0075;
                        } else if (relativePosition <= 0.75) {
                            updatedVelocity = 0.005;
                        }
                        if (BallVelocity[1] < 0) {
                            BallVelocity[1] = -updatedVelocity;
                        } else {
                            BallVelocity[1] = updatedVelocity;
                        } break;
                    }
                }
                JObject ballInformation = new JObject { ["data-type"] = "position", ["x"] = BallPosition[0], ["y"] = BallPosition[1] };
                byte[] ballResponse = Encoding.ASCII.GetBytes(ballInformation.ToString() + "\n");
                ServerSocket.Send(ballResponse, ballResponse.Length, ClientAddresses[0]);
                ServerSocket.Send(ballResponse, ballResponse.Length, ClientAddresses[1]);
                updateCycles++;
                if ((long)(DateTime.UtcNow.Subtract(new DateTime(1970, 1, 1))).TotalMilliseconds - currentTime >= 1000) {
                    Console.WriteLine($"Update rate: {updateCycles}");
                    updateCycles = 0;
                    currentTime = (long)(DateTime.UtcNow.Subtract(new DateTime(1970, 1, 1))).TotalMilliseconds;
                } Thread.Sleep(1000 / 128);
            }
        }

        private void ListenClients() {
            while (true) {
                IPEndPoint clientEndPoint = new IPEndPoint(IPAddress.Any, 0);
                byte[] dataBuffer = ServerSocket.Receive(ref clientEndPoint);
                string stringData = Encoding.ASCII.GetString(dataBuffer, 0, dataBuffer.Length);
                if (dataBuffer.Length == 0) {
                    ServerSocket.Close();
                } else {
                    JObject messageInformation = JObject.Parse(stringData);
                    switch (messageInformation["data-type"].ToString()) {
                        case "prepare":
                            if (GameCoordinators.Contains(clientEndPoint.Address.ToString())) {
                                ExpectedTokens = new List<string> { messageInformation["token_1"].ToString(), messageInformation["token_2"].ToString() };
                                MatchPhase = "preparing";
                            } break;
                        case "query":
                            byte[] responseBuffer = Encoding.ASCII.GetBytes(new JObject { ["data-type"] = "response", ["phase"] = MatchPhase }.ToString() + "\n");
                            ServerSocket.Send(responseBuffer, responseBuffer.Length, clientEndPoint);
                            break;
                        case "auth":
                            if (MatchPhase != "ongoing" && !ClientAddresses.Contains(clientEndPoint) && ExpectedTokens.Contains(messageInformation["token"].ToString())) {
                                ExpectedTokens.Remove(messageInformation["token"].ToString());
                                ClientAddresses.Add(clientEndPoint);
                                if (ClientAddresses.Count == 2) {
                                    MatchPhase = "ongoing";
                                    new Thread(new ThreadStart(UpdatePosition)).Start();
                                }
                            } break;
                        case "movement":
                            if (ClientAddresses.Contains(clientEndPoint)) {
                                int clientIndex = ClientAddresses.IndexOf(clientEndPoint);
                                PaddlePositions[clientIndex] += (double)messageInformation["direction"] / 75.0;
                                JObject movementInformation = new JObject { ["data-type"] = "placement", ["identifier"] = clientIndex, ["direction"] = messageInformation["direction"] };
                                byte[] movementResponse = Encoding.ASCII.GetBytes(movementInformation.ToString() + "\n");
                                ServerSocket.Send(movementResponse, movementResponse.Length, ClientAddresses[0]);
                                ServerSocket.Send(movementResponse, movementResponse.Length, ClientAddresses[1]);
                            } break;
                    }
                }
            }
        }
    }
}
