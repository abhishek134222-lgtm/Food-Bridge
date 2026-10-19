
package com.foodbridge.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080), 0);

        server.createContext("/api/donations", Server::handleDonations);
        server.createContext("/api/register", Server::handleRegister);
        server.createContext("/api/login", Server::handleLogin);
        server.createContext("/api/logout", Server::handleLogout);
        server.createContext("/api/me", Server::handleMe);
        server.createContext("/", Server::handleHome);

        server.start();

        System.out.println(
                "FoodBridge server started at http://localhost:8080");
    }

    // ---------------- WEBSITE ----------------

    private static void handleHome(HttpExchange exchange)
            throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        String path = exchange.getRequestURI().getPath();

        if (path.equals("/")) {
            path = "/index.html";
        }

        Path webRoot = Path.of("web").toAbsolutePath().normalize();
        Path file = webRoot.resolve(path.substring(1)).normalize();

        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404,
                    "{\"error\":\"File not found\"}");
            return;
        }

        String contentType = switch (file.getFileName().toString()) {
            case "html" -> "text/html; charset=UTF-8";
            default -> switch (file.getFileName().toString()) {
                case "index.html", "auth.html" ->
                        "text/html; charset=UTF-8";
                case "style.css" -> "text/css; charset=UTF-8";
                case "app.js" -> "text/javascript; charset=UTF-8";
                default -> "application/octet-stream";
            };
        };

        byte[] data = readFile(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    private static byte[] readFile(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    // ---------------- DONATIONS API ----------------

    private static void handleDonations(HttpExchange exchange)
            throws IOException {

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // Update donation status
        if ("PUT".equalsIgnoreCase(method)) {

            Pattern route = Pattern.compile(
                    "^/api/donations/(\\d+)/status/?$");

            Matcher matcher = route.matcher(path);

            if (!matcher.matches()) {
                sendJson(exchange, 404,
                        "{\"error\":\"Invalid donation status URL\"}");
                return;
            }

            int id;

            try {
                id = Integer.parseInt(matcher.group(1));
                if (id <= 0) {
                    throw new NumberFormatException();
                }
            } catch (NumberFormatException e) {
                sendJson(exchange, 400,
                        "{\"error\":\"Invalid donation ID\"}");
                return;
            }

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String status = getString(body, "status");

            if (status == null) {
                sendJson(exchange, 400,
                        "{\"error\":\"Provide a valid status\"}");
                return;
            }

            String requestedStatus = status.toUpperCase();

            try {
                if ("CLAIMED".equals(requestedStatus)) {
                    boolean claimed = DonationStore.claimDonation(id);

                    if (!claimed) {
                        sendJson(exchange, 409,
                                "{\"error\":\"Donation is no longer available. Refresh and try again.\"}");
                        return;
                    }

                    sendJson(exchange, 200,
                            "{\"message\":\"Donation claimed successfully\",\"id\":"
                                    + id + ",\"status\":\"CLAIMED\"}");
                    return;
                }

                if (!requestedStatus.equals("AVAILABLE")
                        && !requestedStatus.equals("COMPLETED")
                        && !requestedStatus.equals("CANCELLED")) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Invalid donation status\"}");
                    return;
                }

                boolean updated =
                        DonationStore.updateStatus(id, requestedStatus);

                if (!updated) {
                    sendJson(exchange, 404,
                            "{\"error\":\"Donation not found or not updated\"}");
                    return;
                }

                sendJson(exchange, 200,
                        "{\"message\":\"Donation status updated\",\"id\":"
                                + id + ",\"status\":\""
                                + escape(requestedStatus) + "\"}");

            } catch (IllegalStateException e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"error\":\"Could not update donation status\"}");
            }

            return;
        }

        // List donations
        if ("GET".equalsIgnoreCase(method)) {
            try {
                List<Donation> donations = DonationStore.getAll();
                StringBuilder json = new StringBuilder("[");

                for (int i = 0; i < donations.size(); i++) {
                    if (i > 0) {
                        json.append(",");
                    }
                    json.append(toJson(donations.get(i)));
                }

                json.append("]");
                sendJson(exchange, 200, json.toString());

            } catch (IllegalStateException e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"error\":\"Could not load donations\"}");
            }
            return;
        }

        // Create donation
        if ("POST".equalsIgnoreCase(method)) {
            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String donor = getString(body, "donor");
            String food = getString(body, "food");
            String location = getString(body, "location");
            int quantity = getInteger(body, "quantity");

            if (donor == null || food == null || location == null
                    || donor.isBlank() || food.isBlank()
                    || location.isBlank()
                    || quantity <= 0 || quantity > 100000) {
                sendJson(exchange, 400,
                        "{\"error\":\"Provide valid donor, food, quantity, and location\"}");
                return;
            }

            try {
                Donation donation = DonationStore.add(
                        donor, food, quantity, location);
                sendJson(exchange, 201, toJson(donation));

            } catch (IllegalStateException e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"error\":\"Could not save donation\"}");
            }
            return;
        }

        exchange.getResponseHeaders().set("Allow", "GET, POST, PUT");
        sendJson(exchange, 405,
                "{\"error\":\"Method not allowed\"}");
    }

    // ---------------- REGISTRATION ----------------

    private static void handleRegister(HttpExchange exchange)
            throws IOException {

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String name = getString(body, "fullName");
        String email = getString(body, "email");
        String password = getString(body, "password");
        String role = getString(body, "role");

        try {
            AuthStore.User user =
                    AuthStore.register(name, email, password, role);

            String token = AuthSessions.create(user);
            setSessionCookie(exchange, token);
            sendJson(exchange, 201, userJson(user));

        } catch (IllegalArgumentException e) {
            sendJson(exchange, 400,
                    "{\"error\":\"" + escape(e.getMessage()) + "\"}");
        } catch (IllegalStateException e) {
            e.printStackTrace();
            sendJson(exchange, 500,
                    "{\"error\":\"Registration failed\"}");
        }
    }

    // ---------------- LOGIN ----------------

    private static void handleLogin(HttpExchange exchange)
            throws IOException {

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(),
                StandardCharsets.UTF_8);

        String email = getString(body, "email");
        String password = getString(body, "password");

        try {
            AuthStore.User user = AuthStore.login(email, password);

            if (user == null) {
                sendJson(exchange, 401,
                        "{\"error\":\"Invalid email or password\"}");
                return;
            }

            String token = AuthSessions.create(user);
            setSessionCookie(exchange, token);
            sendJson(exchange, 200, userJson(user));

        } catch (IllegalStateException e) {
            e.printStackTrace();
            sendJson(exchange, 500,
                    "{\"error\":\"Login failed\"}");
        }
    }

    // ---------------- CURRENT USER ----------------

    private static void handleMe(HttpExchange exchange)
            throws IOException {

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"GET required\"}");
            return;
        }

        AuthStore.User user = currentUser(exchange);

        if (user == null) {
            sendJson(exchange, 401,
                    "{\"error\":\"Please log in\"}");
            return;
        }

        sendJson(exchange, 200, userJson(user));
    }

    // ---------------- LOGOUT ----------------

    private static void handleLogout(HttpExchange exchange)
            throws IOException {

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        AuthSessions.remove(sessionToken(exchange));

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                "FBSESSION=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");

        sendJson(exchange, 200, "{\"message\":\"Logged out\"}");
    }

    // ---------------- SESSION HELPERS ----------------

    private static void setSessionCookie(
            HttpExchange exchange, String token) {

        exchange.getResponseHeaders().add(
                "Set-Cookie",
                "FBSESSION=" + token
                        + "; Path=/; HttpOnly; SameSite=Strict");
    }

    private static String sessionToken(HttpExchange exchange) {
        List<String> cookies =
                exchange.getRequestHeaders().get("Cookie");

        if (cookies == null) {
            return null;
        }

        for (String header : cookies) {
            for (String cookie : header.split(";")) {
                String item = cookie.trim();

                if (item.startsWith("FBSESSION=")) {
                    return item.substring("FBSESSION=".length());
                }
            }
        }

        return null;
    }

    private static AuthStore.User currentUser(HttpExchange exchange) {
        return AuthSessions.get(sessionToken(exchange));
    }

    private static String userJson(AuthStore.User user) {
        return "{\"id\":" + user.id()
                + ",\"fullName\":\"" + escape(user.fullName())
                + "\",\"email\":\"" + escape(user.email())
                + "\",\"role\":\"" + escape(user.role()) + "\"}";
    }

    // ---------------- JSON HELPERS ----------------

    private static String getString(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"([^\"\\\\]*)\"");

        Matcher matcher = pattern.matcher(json);

        return matcher.find() ? matcher.group(1).trim() : null;
    }

    private static int getInteger(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*(\\d+)");

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return -1;
        }

        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
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
        if (value == null) {
            return "";
        }

        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
