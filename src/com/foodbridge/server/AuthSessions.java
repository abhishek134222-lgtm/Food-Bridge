package com.foodbridge.server;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSessions {

    private static final SecureRandom RANDOM = new SecureRandom();
    // Default session validity: 7 days
    private static final long SESSION_TTL_MS = 7L * 24 * 60 * 60 * 1000L;

    private record SessionEntry(AuthStore.User user, long expiresAt) {}

    private static final ConcurrentHashMap<String, SessionEntry>
            SESSIONS = new ConcurrentHashMap<>();

    // Create a session after successful login or registration
    public static String create(AuthStore.User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        long expiresAt = System.currentTimeMillis() + SESSION_TTL_MS;
        SESSIONS.put(token, new SessionEntry(user, expiresAt));

        return token;
    }

    // Find the user associated with a session token if not expired
    public static AuthStore.User get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        SessionEntry entry = SESSIONS.get(token);
        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() > entry.expiresAt()) {
            SESSIONS.remove(token);
            return null;
        }

        return entry.user();
    }

    // Remove the session during logout
    public static void remove(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }
}
