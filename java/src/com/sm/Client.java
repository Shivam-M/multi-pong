package com.sm;

import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;

public class Client implements KeyListener {

    final String COORDINATOR_ADDRESS = "127.0.0.1";
    final int COORDINATOR_PORT = 4999;

    boolean listeningCoordinator = true;
    double[] paddlePositions = {0.45, 0.45};
    double[] ballPosition = new double[2];

    JFrame windowFrame = new JFrame();
    JPanel contentPanel = new JPanel();
    JPanel ballObject;
    JPanel[] paddleObjects = new JPanel[2];

    Socket coordinatorSocket;
    PrintWriter outputStream;
    BufferedReader inputStream;

    DatagramSocket serverSocket;
    InetAddress serverAddress;
    int serverPort;

    public Client() {
        windowFrame.setSize(1280, 720);
        windowFrame.setBackground(new Color(40, 40, 40));
        windowFrame.setTitle("Pong - waiting for a match...");
        windowFrame.setLayout(null);

        contentPanel.setBackground(new Color(40, 40, 40));
        contentPanel.setSize(windowFrame.getSize());
        contentPanel.setLayout(null);

        ballObject = new JPanel(null);
        ballObject.setLayout(null);
        ballObject.setSize(20, 20);
        ballObject.setBackground(Color.WHITE);

        contentPanel.add(ballObject);
        windowFrame.add(contentPanel);
        windowFrame.setVisible(true);
        relativelyPlace(ballObject, 0.4921, 0.4861);

        for (int p = 0; p < 2; p++) {
            JPanel paddle = new JPanel(null);
            paddle.setSize(20, 100);
            paddle.setBackground(Color.WHITE);
            paddle.setLayout(null);
            relativelyPlace(paddle, (p == 0 ? 0.075 : 0.9094), 0.45);
            contentPanel.add(paddle);
            paddleObjects[p] = paddle;
        } connectCoordinator();
    }

    void relativelyPlace(JPanel object, double x, double y) {
        // System.out.println(x + " " + y);
        Point objectPosition = new Point((int)(x * windowFrame.getWidth()), (int)(y * windowFrame.getHeight()));
        object.setBounds(new Rectangle(objectPosition, object.getSize()));
    }

    void connectCoordinator() {
        try {
            coordinatorSocket = new Socket(COORDINATOR_ADDRESS, COORDINATOR_PORT);
            outputStream = new PrintWriter(coordinatorSocket.getOutputStream(), true);
            inputStream = new BufferedReader((new InputStreamReader(coordinatorSocket.getInputStream())));
            sendCoordinator(new JSONObject(), "search");
            listenCoordinator();
        } catch (IOException e) { e.printStackTrace(); }
    }

    void sendCoordinator(JSONObject message, String type) {
        message.put("data-type", type);
        outputStream.println(message.toString());
        outputStream.flush();
    }

    protected void _listenCoordinator() throws IOException {
        while (listeningCoordinator) {
            String receivedData;
            if (inputStream.ready()) {
                if ((receivedData = inputStream.readLine()) != null) {
                    try {
                        JSONObject receivedInformation = new JSONObject(receivedData);
                        // System.out.println(receivedInformation);
                        if (receivedInformation.getString("data-type").equalsIgnoreCase("match")) {
                            serverAddress = InetAddress.getByName(receivedInformation.getString("address"));
                            serverPort = receivedInformation.getInt("port");
                            listeningCoordinator = false;
                            connectServer(serverAddress, serverPort, receivedInformation.getString("token"));
                            listenServer();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void listenCoordinator() {
        new Thread(() -> {
            try {
                _listenCoordinator();
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    void connectServer(InetAddress address, Integer port, String token) {
        try {
            byte[] dataBuffer;
            serverSocket = new DatagramSocket();
            JSONObject connectionRequest = new JSONObject();
            connectionRequest.put("data-type", "auth");
            connectionRequest.put("token", token);
            dataBuffer = connectionRequest.toString().getBytes();
            serverSocket.send(new DatagramPacket(dataBuffer, dataBuffer.length, address, port));
            contentPanel.addKeyListener(this);
            windowFrame.addKeyListener(this);
        } catch (IOException e) { e.printStackTrace(); }
    }

    protected void _listenServer() throws IOException {
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
                switch (receivedInformation.getString("data-type")) {
                    case "placement" -> {
                        int paddleIndex = receivedInformation.getInt("identifier");
                        JPanel paddle = paddleObjects[paddleIndex];
                        paddlePositions[paddleIndex] += receivedInformation.getInt("direction") / 75.0;
                        relativelyPlace(paddle, (paddleIndex == 0 ? 0.075 : 0.9094), paddlePositions[paddleIndex]);
                    }
                    case "position" -> {
                        ballPosition[0] = receivedInformation.getDouble("x");
                        ballPosition[1] = receivedInformation.getDouble("y");
                        // System.out.println(ballPosition[0] + " " + ballPosition[1]);
                        relativelyPlace(ballObject, ballPosition[0], ballPosition[1]);
                    }
                    case "score" -> {
                        int[] playerScores = {receivedInformation.getInt("0"), receivedInformation.getInt("1")};
                        windowFrame.setTitle(String.format("Pong - %d vs %d", playerScores[0], playerScores[1]));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    void listenServer() {
        new Thread(() -> {
            try {
                _listenServer();
            } catch (IOException e) {e.printStackTrace();}
        }).start();
    }

    public void movePaddle(int direction) {
        try {
            byte[] dataBuffer;
            JSONObject moveRequest = new JSONObject();
            moveRequest.put("data-type", "movement");
            moveRequest.put("direction", direction);
            dataBuffer = moveRequest.toString().getBytes();
            serverSocket.send(new DatagramPacket(dataBuffer, dataBuffer.length, serverAddress, serverPort));
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void keyTyped(KeyEvent e) { }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == 40) {
            movePaddle(1);
        } else if (e.getKeyCode() == 38) {
            movePaddle(-1);
        }
    }

    @Override
    public void keyReleased(KeyEvent e) { }
}
