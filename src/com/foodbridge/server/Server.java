package com.foodbridge.server;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class Server {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
            new InetSocketAddress(8080), 0
        );

        server.createContext("/", Server::handleHome);

        server.start();

        System.out.println("FoodBridge server started!");
        System.out.println("Open: http://localhost:8080");
        System.out.println("Press Ctrl+C to stop the server.");
    }

    private static void handleHome(HttpExchange exchange)
            throws IOException {

        String response = """
            {
              "application": "FoodBridge",
              "description": "Food Donation Management System",
              "status": "running",
              "message": "Welcome to FoodBridge API"
            }
            """;

        byte[] data = response.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
            "Content-Type", "application/json; charset=UTF-8"
        );

        exchange.sendResponseHeaders(200, data.length);

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
