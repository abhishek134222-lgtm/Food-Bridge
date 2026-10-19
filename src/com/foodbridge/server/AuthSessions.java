
package com.foodbridge.server;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

public class AuthSessions {

    private static final SecureRandom RANDOM =
            new SecureRandom();

    private static final ConcurrentHashMap<String, AuthStore.User>
            SESSIONS = new ConcurrentHashMap<>();

    // Create a session after successful login or registration
    public static String create(AuthStore.User user) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        String token = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);

        SESSIONS.put(token, user);

        return token;
    }

    // Find the user associated with a session token
    public static AuthStore.User get(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }

        return SESSIONS.get(token);
    }

    // Remove the session during logout
    public static void remove(String token) {
        if (token != null) {
            SESSIONS.remove(token);
        }
    }
}
