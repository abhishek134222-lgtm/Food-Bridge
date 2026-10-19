
package com.foodbridge.server;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;

    public record User(
            long id, String fullName, String email, String role) {
    }

    // Register a new user
    public static User register(
            String fullName, String email,
            String password, String role) {

        if (fullName == null || email == null
                || password == null || role == null) {
            throw new IllegalArgumentException(
                    "All fields are required.");
        }

        fullName = fullName.trim();
        email = email.trim().toLowerCase(Locale.ROOT);
        role = role.trim().toUpperCase(Locale.ROOT);

        if (fullName.isBlank() || fullName.length() > 100) {
            throw new IllegalArgumentException(
                    "Enter a valid name.");
        }

        if (email.length() > 255
                || !email.matches(
                    "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException(
                    "Enter a valid email.");
        }

        if (password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException(
                    "Password must contain 8 to 128 characters.");
        }

        if (!role.equals("DONOR") && !role.equals("RECEIVER")) {
            throw new IllegalArgumentException(
                    "Choose Donor or Receiver.");
        }

        String passwordHash = hashPassword(password);

        String sql = """
            INSERT INTO public.users
                (full_name, email, password_hash, role)
            VALUES (?, ?, ?, ?)
            RETURNING id, full_name, email, role
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement(sql)) {

            statement.setString(1, fullName);
            statement.setString(2, email);
            statement.setString(3, passwordHash);
            statement.setString(4, role);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new User(
                            result.getLong("id"),
                            result.getString("full_name"),
                            result.getString("email"),
                            result.getString("role"));
                }
            }

            throw new IllegalStateException(
                    "Registration failed.");

        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                throw new IllegalArgumentException(
                        "An account with this email already exists.");
            }

            throw new IllegalStateException(
                    "Could not register user in the database.", e);
        }
    }

    // Verify login credentials
    public static User login(String email, String password) {

        if (email == null || password == null
                || email.isBlank() || password.isEmpty()
                || email.length() > 255
                || password.length() > 128) {
            return null;
        }

        String sql = """
            SELECT id, full_name, email, role, password_hash
            FROM public.users
            WHERE email = ?
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                    connection.prepareStatement(sql)) {

            statement.setString(
                    1, email.trim().toLowerCase(Locale.ROOT));

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }

                String storedHash =
                        result.getString("password_hash");

                if (!verifyPassword(password, storedHash)) {
                    return null;
                }

                return new User(
                        result.getLong("id"),
                        result.getString("full_name"),
                        result.getString("email"),
                        result.getString("role"));
            }

        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Could not check login credentials.", e);
        }
    }

    // Create a salted password hash
    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        byte[] hash = derive(
                password.toCharArray(), salt, ITERATIONS);

        return ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

    // Compare entered password with stored hash
    private static boolean verifyPassword(
            String password, String storedHash) {

        try {
            String[] parts = storedHash.split(":");

            if (parts.length != 3) {
                return false;
            }

            int iterations = Integer.parseInt(parts[0]);

            if (iterations < 100000 || iterations > 1000000) {
                return false;
            }

            byte[] salt =
                    Base64.getDecoder().decode(parts[1]);
            byte[] expected =
                    Base64.getDecoder().decode(parts[2]);

            byte[] actual = derive(
                    password.toCharArray(), salt, iterations);

            return MessageDigest.isEqual(expected, actual);

        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // PBKDF2 password derivation
    private static byte[] derive(
            char[] password, byte[] salt, int iterations) {

        PBEKeySpec spec = new PBEKeySpec(
                password, salt, iterations, KEY_LENGTH);

        try {
            return SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();

        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(
                    "Password hashing is unavailable.", e);

        } finally {
            spec.clearPassword();
            Arrays.fill(password, '\0');
        }
    }
}
