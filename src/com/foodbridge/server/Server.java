
package com.foodbridge.server;

import java.nio.file.Files;
import java.nio.file.Path;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.createContext("/api/donations", Server::handleDonations);
        server.createContext("/", Server::handleHome);

        server.start();
        System.out.println("FoodBridge server started at http://localhost:8080");
    }

    private static void handleHome(HttpExchange exchange)
            throws IOException {

        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        // Only serve files from the web directory.
        Path webRoot = Path.of("web").toAbsolutePath().normalize();
        Path file = webRoot.resolve(path.substring(1)).normalize();

        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404, "{\"error\":\"File not found\"}");
            return;
        }

        String contentType = switch (file.getFileName().toString()) {
            case "index.html" -> "text/html; charset=UTF-8";
            case "style.css" -> "text/css; charset=UTF-8";
            case "app.js" -> "text/javascript; charset=UTF-8";
            default -> "application/octet-stream";
        };

        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static void handleDonations(HttpExchange exchange)
            throws IOException {
        String method = exchange.getRequestMethod();

        if ("GET".equalsIgnoreCase(method)) {
            StringBuilder json = new StringBuilder("[");
            List<Donation> donations = DonationStore.getAll();

            for (int i = 0; i < donations.size(); i++) {
                if (i > 0)
                    json.append(",");
                json.append(toJson(donations.get(i)));
            }

            json.append("]");
            sendJson(exchange, 200, json.toString());
            return;
        }

        if ("POST".equalsIgnoreCase(method)) {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String donor = getString(body, "donor");
            String food = getString(body, "food");
            String location = getString(body, "location");
            int quantity = getInteger(body, "quantity");

            if (donor == null || food == null || location == null
                    || quantity <= 0) {
                sendJson(exchange, 400,
                        "{\"error\":\"Provide donor, food, positive quantity, and location.\"}");
                return;
            }

            Donation donation = DonationStore.add(
                    donor, food, quantity, location);

            sendJson(exchange, 201, toJson(donation));
            return;
        }

        exchange.getResponseHeaders().set("Allow", "GET, POST");
        sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private static String getString(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"\\\\]*)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static int getInteger(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(json);
        if (!matcher.find())
            return -1;

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static String toJson(Donation d) {
        return "{\"id\":" + d.getId()
                + ",\"donor\":\"" + escape(d.getDonor()) + "\""
                + ",\"food\":\"" + escape(d.getFood()) + "\""
                + ",\"quantity\":" + d.getQuantity()
                + ",\"location\":\"" + escape(d.getLocation()) + "\""
                + ",\"status\":\"" + escape(d.getStatus()) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r");
    }

    private static void sendJson(
            HttpExchange exchange, int status, String json)
            throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }
}
