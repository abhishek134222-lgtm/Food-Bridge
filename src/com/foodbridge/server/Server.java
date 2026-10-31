package com.foodbridge.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    public static void main(String[] args) throws IOException {
        int port = 8080;
        String configuredPort = System.getenv("PORT");

        if (configuredPort != null && !configuredPort.isBlank()) {
            try {
                port = Integer.parseInt(configuredPort);
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(
                        "The PORT environment variable must be a number.");
            }
        }

        HttpServer server = HttpServer.create(
                new InetSocketAddress("0.0.0.0", port), 0);

        server.createContext("/api/health", Server::handleHealth);
        server.createContext("/api/donations", Server::handleDonations);
        server.createContext("/api/register", Server::handleRegister);
        server.createContext("/api/login", Server::handleLogin);
        server.createContext("/api/logout", Server::handleLogout);
        server.createContext("/api/me", Server::handleMe);
        server.createContext("/", Server::handleHome);

        server.start();

        System.out.println(
                "FoodBridge server started on port " + port);
        System.out.println(
                "Open http://localhost:" + port + " in your browser.");
    }

    // ---------------- CORS AND SECURITY ----------------

    private static boolean applyCors(HttpExchange exchange) throws IOException {
        String origin = exchange.getRequestHeaders().getFirst("Origin");

        if (origin != null && !origin.isBlank()) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            exchange.getResponseHeaders().set("Access-Control-Allow-Credentials", "true");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type, Authorization, Cookie, X-Requested-With, Accept");
            exchange.getResponseHeaders().set("Access-Control-Max-Age", "86400");
        }

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }

        return false;
    }

    // ---------------- HEALTH CHECK ----------------

    private static void handleHealth(HttpExchange exchange) throws IOException {
        if (applyCors(exchange)) return;

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String json = "{\"status\":\"UP\","
                + "\"service\":\"FoodBridge\","
                + "\"timestamp\":\"" + Instant.now().toString() + "\"}";

        sendJson(exchange, 200, json);
    }

    // ---------------- WEBSITE STATIC FILES ----------------

    private static void handleHome(HttpExchange exchange)
            throws IOException {

        if (applyCors(exchange)) return;

        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405,
                    "{\"error\":\"Method not allowed\"}");
            return;
        }

        String requestPath = exchange.getRequestURI().getPath();

        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }

        Path webRoot = Path.of("web").toAbsolutePath().normalize();
        Path file = webRoot.resolve(requestPath.substring(1)).normalize();

        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            sendJson(exchange, 404,
                    "{\"error\":\"File not found\"}");
            return;
        }

        String filename = file.getFileName().toString();

        String contentType;
        if (filename.endsWith(".html")) {
            contentType = "text/html; charset=UTF-8";
        } else if (filename.endsWith(".css")) {
            contentType = "text/css; charset=UTF-8";
        } else if (filename.endsWith(".js")) {
            contentType = "text/javascript; charset=UTF-8";
        } else if (filename.endsWith(".json")) {
            contentType = "application/json; charset=UTF-8";
        } else if (filename.endsWith(".png")) {
            contentType = "image/png";
        } else if (filename.endsWith(".jpg")
                || filename.endsWith(".jpeg")) {
            contentType = "image/jpeg";
        } else if (filename.endsWith(".svg")) {
            contentType = "image/svg+xml";
        } else {
            contentType = "application/octet-stream";
        }

        byte[] data = Files.readAllBytes(file);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(200, data.length);

        try (var output = exchange.getResponseBody()) {
            output.write(data);
        }
    }

    // ---------------- DONATIONS API ----------------

    private static void handleDonations(HttpExchange exchange)
            throws IOException {

        if (applyCors(exchange)) return;

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // List donations
        if ("GET".equalsIgnoreCase(method) && path.equals("/api/donations")) {
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

        // Create a donation: logged-in donors only
        if ("POST".equalsIgnoreCase(method)
                && path.equals("/api/donations")) {

            AuthStore.User user = currentUser(exchange);

            if (user == null) {
                sendJson(exchange, 401,
                        "{\"error\":\"Please log in first\"}");
                return;
            }

            if (!"DONOR".equals(user.role())) {
                sendJson(exchange, 403,
                        "{\"error\":\"Only donors can add donations\"}");
                return;
            }

            String body = new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8);

            String food = getString(body, "food");
            String location = getString(body, "location");
            int quantity = getInteger(body, "quantity");

            if (food == null || location == null
                    || food.isBlank() || location.isBlank()
                    || quantity <= 0 || quantity > 100000) {
                sendJson(exchange, 400,
                        "{\"error\":\"Provide valid food, quantity, and location\"}");
                return;
            }

            try {
                Donation donation = DonationStore.add(
                        user.id(), user.fullName(), food, quantity, location);

                sendJson(exchange, 201, toJson(donation));

            } catch (IllegalArgumentException e) {
                sendJson(exchange, 400,
                        "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            } catch (IllegalStateException e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"error\":\"Could not save donation\"}");
            }
            return;
        }

        // Update a donation's status
        if ("PUT".equalsIgnoreCase(method)) {
            Pattern route = Pattern.compile(
                    "^/api/donations/(\\d+)/status/?$");

            Matcher matcher = route.matcher(path);

            if (!matcher.matches()) {
                sendJson(exchange, 404,
                        "{\"error\":\"Invalid donation status URL\"}");
                return;
            }

            AuthStore.User user = currentUser(exchange);

            if (user == null) {
                sendJson(exchange, 401,
                        "{\"error\":\"Please log in first\"}");
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

            String requested = getString(body, "status");

            if (requested == null) {
                sendJson(exchange, 400,
                        "{\"error\":\"Provide a valid status\"}");
                return;
            }

            String status = requested.toUpperCase(Locale.ROOT);

            try {
                if ("CLAIMED".equals(status)) {
                    if (!"RECEIVER".equals(user.role())) {
                        sendJson(exchange, 403,
                                "{\"error\":\"Only receivers can claim donations\"}");
                        return;
                    }

                    boolean claimed = DonationStore.claimDonation(id);

                    if (!claimed) {
                        sendJson(exchange, 409,
                                "{\"error\":\"Donation is no longer available. Refresh and try again.\"}");
                        return;
                    }

                    sendJson(exchange, 200,
                            "{\"message\":\"Donation claimed successfully\","
                                    + "\"id\":" + id
                                    + ",\"status\":\"CLAIMED\"}");
                    return;
                }

                if (!"DONOR".equals(user.role())) {
                    sendJson(exchange, 403,
                            "{\"error\":\"Only donors can update these statuses\"}");
                    return;
                }

                if (!status.equals("AVAILABLE")
                        && !status.equals("COMPLETED")
                        && !status.equals("CANCELLED")) {
                    sendJson(exchange, 400,
                            "{\"error\":\"Invalid donation status\"}");
                    return;
                }

                boolean updated =
                        DonationStore.updateStatus(id, status, user.id());

                if (!updated) {
                    sendJson(exchange, 404,
                            "{\"error\":\"Donation not found or not updated\"}");
                    return;
                }

                sendJson(exchange, 200,
                        "{\"message\":\"Donation status updated\","
                                + "\"id\":" + id
                                + ",\"status\":\"" + escape(status) + "\"}");

            } catch (IllegalStateException e) {
                e.printStackTrace();
                sendJson(exchange, 500,
                        "{\"error\":\"Could not update donation status\"}");
            }
            return;
        }

        exchange.getResponseHeaders().set("Allow", "GET, POST, PUT, OPTIONS");
        sendJson(exchange, 405,
                "{\"error\":\"Method not allowed\"}");
    }

    // ---------------- REGISTRATION ----------------

    private static void handleRegister(HttpExchange exchange)
            throws IOException {

        if (applyCors(exchange)) return;

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
            sendJson(exchange, 201, userJson(user, token));

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

        if (applyCors(exchange)) return;

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
            sendJson(exchange, 200, userJson(user, token));

        } catch (IllegalStateException e) {
            e.printStackTrace();
            sendJson(exchange, 500,
                    "{\"error\":\"Login failed\"}");
        }
    }

    // ---------------- CURRENT USER ----------------

    private static void handleMe(HttpExchange exchange)
            throws IOException {

        if (applyCors(exchange)) return;

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

        sendJson(exchange, 200, userJson(user, null));
    }

    // ---------------- LOGOUT ----------------

    private static void handleLogout(HttpExchange exchange)
            throws IOException {

        if (applyCors(exchange)) return;

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendJson(exchange, 405, "{\"error\":\"POST required\"}");
            return;
        }

        AuthSessions.remove(sessionToken(exchange));

        String cookie = "FBSESSION=; Path=/; HttpOnly; Max-Age=0";
        if (isHttps(exchange)) {
            cookie += "; SameSite=None; Secure";
        } else {
            cookie += "; SameSite=Lax";
        }

        exchange.getResponseHeaders().add("Set-Cookie", cookie);
        sendJson(exchange, 200, "{\"message\":\"Logged out\"}");
    }

    // ---------------- SESSION HELPERS ----------------

    private static void setSessionCookie(
            HttpExchange exchange, String token) {

        String cookie = "FBSESSION=" + token + "; Path=/; HttpOnly";

        if (isHttps(exchange)) {
            cookie += "; SameSite=None; Secure";
        } else {
            cookie += "; SameSite=Lax";
        }

        exchange.getResponseHeaders().add("Set-Cookie", cookie);
    }

    private static boolean isHttps(HttpExchange exchange) {
        String forwarded = exchange.getRequestHeaders()
                .getFirst("X-Forwarded-Proto");

        return forwarded != null && forwarded.equalsIgnoreCase("https");
    }

    private static String sessionToken(HttpExchange exchange) {
        // 1. Check Authorization Bearer header
        String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authHeader.substring(7).trim();
            if (!token.isEmpty()) {
                return token;
            }
        }

        // 2. Check Cookie header
        List<String> cookies = exchange.getRequestHeaders().get("Cookie");
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

    private static String userJson(AuthStore.User user, String token) {
        String json = "{\"id\":" + user.id()
                + ",\"fullName\":\"" + escape(user.fullName())
                + "\",\"email\":\"" + escape(user.email())
                + "\",\"role\":\"" + escape(user.role()) + "\"";

        if (token != null && !token.isBlank()) {
            json += ",\"token\":\"" + escape(token) + "\"";
        }

        json += "}";
        return json;
    }

    // ---------------- JSON HELPERS ----------------

    private static String getString(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return null;
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .trim();
    }

    private static int getInteger(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*(-?\\d+)");

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
