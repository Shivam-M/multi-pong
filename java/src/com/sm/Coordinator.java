package com.sm;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

public class Coordinator {

    int PORT = 4999;
    boolean listeningClients = true;
    boolean acceptingConnections = true;
    ServerSocket serverSocket;
    ArrayList<Socket> clientSockets;
    ArrayList<Socket> searchingClients;
    JSONArray cachedMatches2;
    ArrayList<JSONObject> serverList = new ArrayList<>(Arrays.asList(
            new JSONObject().put("address", "127.0.0.1").put("port", 5000),
            new JSONObject().put("address", "127.0.0.1").put("port", 5001),
            new JSONObject().put("address", "127.0.0.1").put("port", 5002),
            new JSONObject().put("address", "127.0.0.1").put("port", 5003),
            new JSONObject().put("address", "127.0.0.1").put("port", 5004)
    ));

    public Coordinator() throws IOException {
        serverSocket = new ServerSocket(PORT);
        clientSockets = new ArrayList<>();
        searchingClients = new ArrayList<>();
        cachedMatches2 = new JSONArray();
        checkStatus();
        handleConnections();
        listenConnections();
    }

    InetSocketAddress checkStatus() {
        ArrayList<JSONObject> serverInformation = new ArrayList<>();
        InetSocketAddress selectedServer = null;
        try {
            for (JSONObject server : serverList) {
                try {
                    byte[] receiveBuffer = new byte[150];
                    byte[] sendBuffer = "{\"data-type\": \"query\"}".getBytes();
                    int serverPort = server.getInt("port");
                    String serverAddress = server.getString("address");
                    DatagramSocket querySocket = new DatagramSocket();
                    DatagramPacket sendPacket = new DatagramPacket(sendBuffer, sendBuffer.length, InetAddress.getByName(serverAddress), serverPort);
                    DatagramPacket receivePacket = new DatagramPacket(receiveBuffer, receiveBuffer.length);
                    querySocket.setSoTimeout(1000);
                    querySocket.send(sendPacket);
                    querySocket.receive(receivePacket);
                    querySocket.setSoTimeout(0);
                    String receivedData = new String(receivePacket.getData(), 0, receivePacket.getLength());
                    JSONObject information = new JSONObject(receivedData);
                    if (information.get("data-type").equals("response")) {
                        information.put("address", serverAddress).put("port", serverPort);
                        serverInformation.add(information);
                        // updateStatus(information);
                        if (information.get("phase").equals("waiting")) {
                            System.out.printf("('%s', %d) is available\n", serverAddress, serverPort); // format same as other versions
                            selectedServer = new InetSocketAddress(InetAddress.getByName(serverAddress), serverPort);
                        } else {
                            System.out.printf("('%s', %d) is busy\n", serverAddress, serverPort);
                        }
                    }
                } catch (SocketTimeoutException ir) {
                    System.out.printf("('%s', %d) is unresponsive\n", server.getString("address"), server.getInt("port"));
                }
            }
        } catch (Exception e) { e.printStackTrace(); }
        for (JSONObject i: serverInformation) {
            updateStatus(i);
        }
        return selectedServer;
    }

    void updateStatus(JSONObject serverInformation) {
        int indexCounter = 0;
        JSONArray cachedMatches3 = cachedMatches2;
        for (Object i: cachedMatches2) {
            JSONObject information = (JSONObject) i;
            if (information.getString("address").equals(serverInformation.getString("address"))) {
                if (information.getInt("port") == serverInformation.getInt("port")) {
                    cachedMatches3.remove(indexCounter);
                }
            }
            indexCounter++;
        }
        cachedMatches3.put(serverInformation);
        cachedMatches2 = cachedMatches3;
    }

    public void sendMessage(String message, Socket client) {
        try {
            PrintWriter outputStream = new PrintWriter(client.getOutputStream(), true);
            outputStream.println(message);
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void prepareServer(Socket clientOne, Socket clientTwo, InetSocketAddress serverAddress) {
        try {
            String[] connectionTokens = {UUID.randomUUID().toString(), UUID.randomUUID().toString()};
            JSONObject prepareRequest = new JSONObject();
            JSONObject tokenRequest = new JSONObject();
            DatagramSocket serverSocket = new DatagramSocket();
            DatagramPacket sendPacket;
            prepareRequest.put("data-type", "prepare");
            prepareRequest.put("token_1", connectionTokens[0]);
            prepareRequest.put("token_2", connectionTokens[1]);
            tokenRequest.put("data-type", "match");
            tokenRequest.put("address", serverAddress.getAddress().getHostAddress());
            tokenRequest.put("port", serverAddress.getPort());
            tokenRequest.put("token", connectionTokens[0]);
            sendPacket = new DatagramPacket(prepareRequest.toString().getBytes(), 0, prepareRequest.toString().getBytes().length, serverAddress);
            serverSocket.send(sendPacket);
            sendMessage(tokenRequest.toString(), clientOne);
            tokenRequest.put("token", connectionTokens[1]);
            sendMessage(tokenRequest.toString(), clientTwo);

        } catch (IOException e) { e.printStackTrace(); }
    }

    void matchmakeClients() {
        if (searchingClients.size() >= 2) {
            ArrayList<Socket> copiedSearchers = new ArrayList<>(searchingClients);
            InetSocketAddress selectedServer = checkStatus();
            if (selectedServer != null) {
                prepareServer(copiedSearchers.get(0), copiedSearchers.get(1), selectedServer);
                searchingClients.remove(copiedSearchers.get(0));
                searchingClients.remove(copiedSearchers.get(1));
            }
        }
    }

    void _listenConnections() throws IOException {
        while (listeningClients) {
            ArrayList<Socket> copiedClients = new ArrayList<>(clientSockets.size());
            copiedClients.addAll(clientSockets);
            for (Socket clientSocket: copiedClients) {
                BufferedReader inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                String receivedData;
                try {
                    if (inputStream.ready()) {
                        if ((receivedData = inputStream.readLine()) != null) {
                            System.out.println(receivedData);
                            JSONObject receivedInformation = new JSONObject(receivedData);
                            if (receivedInformation.getString("data-type").equalsIgnoreCase("search")) {
                                searchingClients.add(clientSocket);
                                matchmakeClients();
                            }
                        }
                    }
                } catch (Exception e) {
                    clientSockets.remove(clientSocket);
                    System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " has disconnected from the server.");
                }
            }
        }
    }

    void listenConnections() {
        new Thread(() -> {
            try {
                _listenConnections();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

    void _handleConnections() throws IOException {
        while (acceptingConnections) {
            Socket clientSocket = serverSocket.accept();
            clientSockets.add(clientSocket);
            System.out.println("Client " + clientSocket.getRemoteSocketAddress() + " has connected to the server.");
        }
    }

    void handleConnections() {
        new Thread(() -> {
            try {
                _handleConnections();
            } catch (IOException e) { e.printStackTrace(); }
        }).start();
    }

}
