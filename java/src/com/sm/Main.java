package com.sm;

import java.io.IOException;

public class Main {

    public static void main(String[] args) {
        try {
            // Coordinator coordinator = new Coordinator();
            // Server server = new Server();
            Client client = new Client();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
