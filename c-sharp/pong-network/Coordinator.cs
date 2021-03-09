using Newtonsoft.Json.Linq;
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace pong_network {
    class Coordinator {

        const int PORT = 4999;

        IPEndPoint GameEndPoint = new IPEndPoint(IPAddress.Any, 0);
        UdpClient GameSocket = new UdpClient();
        TcpListener ServerSocket = new TcpListener(IPAddress.Any, PORT);

        List<TcpClient> SearchingClients = new List<TcpClient>();
        List<TcpClient> ClientAddresses = new List<TcpClient>();
        List<IPEndPoint> GameServers = new List<IPEndPoint> {
            new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5000),
            new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5001),
            new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5002),
            new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5003),
            new IPEndPoint(IPAddress.Parse("127.0.0.1"), 5004)
        };


        public Coordinator() {
            CheckStatus();
            ListenClients();
        }

        IPEndPoint CheckStatus(bool instantly = false) {
            IPEndPoint selectedServer = null;
            foreach (IPEndPoint server in GameServers) {
                try {
                    byte[] queryInformation = Encoding.ASCII.GetBytes(new JObject { ["data-type"] = "query" }.ToString() + "\n");
                    GameSocket.Client.ReceiveTimeout = 1000;
                    GameSocket.Send(queryInformation, queryInformation.Length, server);
                    byte[] dataBuffer = GameSocket.Receive(ref GameEndPoint);
                    string stringData = Encoding.ASCII.GetString(dataBuffer, 0, dataBuffer.Length);
                    if (dataBuffer.Length == 0) {
                        Console.WriteLine($"{server.Address}:{server.Port} is unresponsive.");
                    } else {
                        JObject messageInformation = JObject.Parse(stringData);
                        if (messageInformation["data-type"].ToString().Equals("response")) {
                            if (messageInformation["phase"].ToString().Equals("waiting")) {
                                Console.WriteLine($"{server.Address}:{server.Port} is available.");
                                selectedServer = server;
                                if (instantly) return selectedServer;
                            } else {
                                Console.WriteLine($"{server.Address}:{server.Port} is busy.");
                            }
                        }
                    }
                } catch (SocketException) {
                    Console.WriteLine($"{server.Address}:{server.Port} is unavailable.");
                }
            } return selectedServer;
        }

        void ListenClients() {
            ServerSocket.Start();
            while (true) {
                TcpClient client = ServerSocket.AcceptTcpClient();
                new Thread(HandleClient).Start(client);
                Console.WriteLine($"Client {(IPEndPoint)client.Client.RemoteEndPoint} has connected.");
            }
        }

        void HandleClient(object o) {
            TcpClient client = (TcpClient)o;
            ClientAddresses.Add(client);
            while (true) {
                try {
                    NetworkStream stream = client.GetStream();
                    byte[] buffer = new byte[1024];
                    int bytes = stream.Read(buffer, 0, buffer.Length);
                    if (bytes == 0) {
                        ClientAddresses.Remove(client);
                        if (SearchingClients.Contains(client)) SearchingClients.Remove(client);
                        Console.WriteLine($"Client {(IPEndPoint)client.Client.RemoteEndPoint} has disconnected.");
                        break;
                    }
                    string stringData = Encoding.ASCII.GetString(buffer, 0, bytes);
                    JObject receivedInformation = JObject.Parse(stringData);
                    if (receivedInformation["data-type"].ToString().Equals("search")) {
                        if (!SearchingClients.Contains(client)) {
                            SearchingClients.Add(client);
                            if (SearchingClients.Count >= 2) {
                                IPEndPoint selectedServer = CheckStatus(true);
                                if (selectedServer == null) continue;
                                JObject serverInformation = new JObject { ["data-type"] = "match", ["address"] = selectedServer.Address.ToString(), ["port"] = selectedServer.Port };
                                long[] serverTokens = { new Random().Next((int)Math.Pow(9, 9)) * (long)Math.Pow(9, 9), new Random().Next((int)Math.Pow(9, 9)) * (long)Math.Pow(9, 9) };
                                JObject prepareInformation = new JObject { ["data-type"] = "prepare", ["token_1"] = serverTokens[0], ["token_2"] = serverTokens[1]};
                                byte[] prepareMessage = Encoding.ASCII.GetBytes(prepareInformation.ToString() + "\n");
                                GameSocket.Send(prepareMessage, prepareMessage.Length, selectedServer);
                                for (int x = 0; x < 2; x++) {
                                    serverInformation["token"] = serverTokens[x];
                                    byte[] serverMessage = Encoding.ASCII.GetBytes(serverInformation.ToString() + "\n");
                                    NetworkStream c_stream = SearchingClients[0].GetStream();
                                    c_stream.Write(serverMessage, 0, serverMessage.Length);
                                    SearchingClients.Remove(SearchingClients[0]);
                                }
                            }
                        }
                    }
                } catch (Newtonsoft.Json.JsonReaderException) { }
                catch (System.IO.IOException) {
                    ClientAddresses.Remove(client);
                    if (SearchingClients.Contains(client)) SearchingClients.Remove(client);
                    Console.WriteLine($"Client {(IPEndPoint)client.Client.RemoteEndPoint} has disconnected.");
                    break;
                }

            }
        }
    }
}
