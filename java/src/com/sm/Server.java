package com.sm;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

public class Server {

    ArrayList<String> gameCoordinators = new ArrayList<>();
    ArrayList<JSONObject> clientAddresses;
    ArrayList<String> expectedTokens;
    // Map<JSONObject, JSO>
    double[] ballPosition;
    double[] ballVelocity;
    String matchPhase;
    DatagramSocket serverSocket;
    final int PORT = 5001;

    public Server() {
        initialise();
        listen();
        gameCoordinators.add("127.0.0.1");
    }

    void initialise() {
        try {
            clientAddresses = new ArrayList<>();
            expectedTokens = new ArrayList<>();
            ballPosition = new double[]{0.4921, 0.4861};
            ballVelocity = new double[]{0.00, 0.00};
            matchPhase = "waiting";
            serverSocket = new DatagramSocket(PORT);
            System.out.println(serverSocket.getPort());
        } catch (IOException ignored) {}
    }

    void start() {
        matchPhase = "ongoing";
        updatePosition();
    }

    void _updatePosition() {
        if (Math.random() <= 0.50) {
            ballVelocity[0] = -0.005;
        } else {
            ballVelocity[0] = 0.005;
        }
        if (Math.random() <= 0.50) {
            ballVelocity[1] = -0.005;
        } else {
            ballVelocity[1] = 0.005;
        }
        long currentTime = System.currentTimeMillis();
        int runs = 0;
        while (!matchPhase.equalsIgnoreCase("waiting")) {
            ballPosition[0] += ballVelocity[0];
            ballPosition[1] += ballVelocity[1];
            if (!(0.0 < ballPosition[1] && ballPosition[1] < 1.0)) {
                ballVelocity[1] *= -1;
            }
            if (!(0.0 < ballPosition[0] && ballPosition[0] < 1.0)) {
                if (ballPosition[0] < 0.0) {
                    clientAddresses.get(0).put("score", clientAddresses.get(0).getInt("score") + 1);
                } else if (ballPosition[0] > 1.0) {
                    clientAddresses.get(1).put("score", clientAddresses.get(1).getInt("score") + 1);
                }
                JSONObject scoreMessage = new JSONObject();
                scoreMessage.put("data-type", "score");
                scoreMessage.put("0", clientAddresses.get(0).getInt("score"));
                scoreMessage.put("1", clientAddresses.get(1).getInt("score"));
                send(scoreMessage.toString(), (InetAddress) clientAddresses.get(0).get("address"), clientAddresses.get(0).getInt("port"));
                send(scoreMessage.toString(), (InetAddress) clientAddresses.get(1).get("address"), clientAddresses.get(1).getInt("port"));
                ballPosition = new double[]{0.4921, 0.4861};
            }
            JSONObject ballMessage = new JSONObject();
            ballMessage.put("data-type", "position");
            ballMessage.put("x", ballPosition[0]);
            ballMessage.put("y", ballPosition[1]);
            send(ballMessage.toString(), (InetAddress) clientAddresses.get(0).get("address"), clientAddresses.get(0).getInt("port"));
            send(ballMessage.toString(), (InetAddress) clientAddresses.get(1).get("address"), clientAddresses.get(1).getInt("port"));
            runs++;
            if (System.currentTimeMillis() - currentTime >= 1000) {
                System.out.println("runs  " + runs);
                runs = 0;
                currentTime = System.currentTimeMillis();
            }
            try {
                TimeUnit.MILLISECONDS.sleep(1000/64);
                //Thread.sleep(1000/64);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    void updatePosition() {
        new Thread(this::_updatePosition).start();
    }

    private void _listen() throws IOException {
        while (true) {
            byte[] dataBuffer = new byte[150];
            DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length);
            serverSocket.receive(packet);
            InetAddress address = packet.getAddress();
            int port = packet.getPort();
            packet = new DatagramPacket(dataBuffer, dataBuffer.length, address, port);
            String receivedData = new String(packet.getData(), 0, packet.getLength());
            try {
                JSONObject receivedInformation = new JSONObject(receivedData);
                handle(receivedInformation, address, port);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void listen() {
        new Thread(() -> {
            try {
                _listen();
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    public void handle(JSONObject information, InetAddress address, int port) {
        JSONObject client = new JSONObject().put("address", address).put("port", port);
        System.out.println(information.toString());
        switch (information.getString("data-type")) {
            case "auth":
                if (!clientAddresses.contains(client)) {
                    if (expectedTokens.contains(information.getString("token"))) {
                        expectedTokens.remove(information.getString("token"));
                        client.put("identifier", clientAddresses.size());
                        client.put("score", 0);
                        if (clientAddresses.size() == 0) {
                            client.put("x", 0.075);
                        } else {
                            client.put("x", 0.9094);
                        }
                        client.put("y", 0.45);
                        clientAddresses.add(client);
                        System.out.println("Size:" + clientAddresses.size());
                        if (clientAddresses.size() == 2) {
                            start();
                        }
                    }
                } return;
            case "prepare":
                if (!gameCoordinators.contains(address.toString())) { // todo
                    matchPhase = "preparing";
                    expectedTokens.add(information.getString("token_1"));
                    expectedTokens.add(information.getString("token_2"));
                } return;
            case "query":
                byte[] dataBuffer;
                JSONObject responseObject = new JSONObject();
                responseObject.put("data-type", "response");
                responseObject.put("phase", matchPhase);
                dataBuffer = responseObject.toString().getBytes();
                DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length, address, port);
                try {
                    serverSocket.send(packet);
                } catch (IOException e) {
                    e.printStackTrace();
                } return;
            case "movement":
                JSONObject clientInformation = clientFromAddress(address, port);
                int clientIndex = clientAddresses.indexOf(clientInformation);
                double clientPositionY = clientInformation.getDouble("y");
                clientPositionY += information.getInt("direction") / 75.0;
                clientInformation.put("y", clientPositionY);
                clientAddresses.set(clientIndex, clientInformation);
                information.put("data-type", "placement");
                information.put("identifier", clientIndex);
                send(information.toString(), (InetAddress) clientAddresses.get(0).get("address"), clientAddresses.get(0).getInt("port"));
                send(information.toString(), (InetAddress) clientAddresses.get(1).get("address"), clientAddresses.get(1).getInt("port"));
        }
    }

    JSONObject clientFromAddress(InetAddress address, int port) {
        for (JSONObject client: clientAddresses) {
            System.out.println(client.get("address"));
            System.out.println(address);
            System.out.println(client.get("port"));
            System.out.println(port);
            if ((client.get("address").equals(address)) && client.get("port").equals(port)) {
                return client;
            }
        } return null;
    }

    void send(String message, InetAddress address, int port) {
        byte[] dataBuffer;
        dataBuffer = message.getBytes();
        DatagramPacket packet = new DatagramPacket(dataBuffer, dataBuffer.length, address, port);
        try {
            serverSocket.send(packet);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }




}
