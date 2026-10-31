package com.foodbridge.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class AuthStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int ITERATIONS = 210000;
    private static final int KEY_LENGTH = 256;

    private static final Path DATA_DIR =
            Paths.get("data").toAbsolutePath();

    private static final Path USERS_FILE =
            DATA_DIR.resolve("users.dat");

    public record User(
            long id, String fullName, String email, String role) {
    }

    private static class StoredUser implements Serializable {
        private static final long serialVersionUID = 1L;

        long id;
        String fullName;
        String email;
        String role;
        String passwordHash;

        StoredUser(long id, String fullName, String email,
                   String role, String passwordHash) {
            this.id = id;
            this.fullName = fullName;
            this.email = email;
            this.role = role;
            this.passwordHash = passwordHash;
        }

        User toUser() {
            return new User(id, fullName, email, role);
        }
    }

    private static List<StoredUser> readUsers() {
        try {
            Files.createDirectories(DATA_DIR);

            if (!Files.exists(USERS_FILE)) {
                return new ArrayList<>();
            }

            try (ObjectInputStream input = new ObjectInputStream(
                    Files.newInputStream(USERS_FILE))) {

                Object object = input.readObject();

                if (!(object instanceof ArrayList<?> list)) {
                    throw new IllegalStateException(
                            "Invalid users data file.");
                }

                List<StoredUser> users = new ArrayList<>();

                for (Object item : list) {
                    if (!(item instanceof StoredUser user)) {
                        throw new IllegalStateException(
                                "Invalid user record in data file.");
                    }
                    users.add(user);
                }

                return users;
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Could not read user data. Keep a backup of data/users.dat.",
                    e);
        }
    }

    private static void saveUsers(List<StoredUser> users) {
        try {
            Files.createDirectories(DATA_DIR);

            Path temporaryFile =
                    DATA_DIR.resolve("users.tmp");

            try (ObjectOutputStream output =
                    new ObjectOutputStream(
                            Files.newOutputStream(temporaryFile))) {
                output.writeObject(new ArrayList<>(users));
            }

            try {
                Files.move(
                        temporaryFile,
                        USERS_FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(
                        temporaryFile,
                        USERS_FILE,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save user data.", e);
        }
    }

    public static synchronized User register(
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

        // 1. Database persistence (Neon PostgreSQL)
        if (Database.isConfigured()) {
            String checkSql = "SELECT id FROM users WHERE email = ?";
            try (Connection conn = Database.getConnection();
                 PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {

                checkStmt.setString(1, email);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next()) {
                        throw new IllegalArgumentException(
                                "An account with this email already exists.");
                    }
                }

                String insertSql = "INSERT INTO users (full_name, email, password_hash, role) VALUES (?, ?, ?, ?) RETURNING id";
                try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                    insertStmt.setString(1, fullName);
                    insertStmt.setString(2, email);
                    insertStmt.setString(3, passwordHash);
                    insertStmt.setString(4, role);
                    try (ResultSet rs = insertStmt.executeQuery()) {
                        if (rs.next()) {
                            long generatedId = rs.getLong(1);
                            return new User(generatedId, fullName, email, role);
                        }
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database registration error: " + e.getMessage(), e);
            }
        }

        // 2. File-based fallback persistence
        List<StoredUser> users = readUsers();

        for (StoredUser user : users) {
            if (user.email.equals(email)) {
                throw new IllegalArgumentException(
                        "An account with this email already exists.");
            }
        }

        long nextId = users.stream()
                .mapToLong(user -> user.id)
                .max()
                .orElse(0L) + 1L;

        StoredUser newUser = new StoredUser(
                nextId, fullName, email, role, passwordHash);

        users.add(newUser);
        saveUsers(users);

        return newUser.toUser();
    }

    public static synchronized User login(
            String email, String password) {

        if (email == null || password == null
                || email.isBlank() || password.isEmpty()
                || email.length() > 255
                || password.length() > 128) {
            return null;
        }

        email = email.trim().toLowerCase(Locale.ROOT);

        // 1. Database persistence (Neon PostgreSQL)
        if (Database.isConfigured()) {
            String querySql = "SELECT id, full_name, email, password_hash, role FROM users WHERE email = ?";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(querySql)) {

                stmt.setString(1, email);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String storedHash = rs.getString("password_hash");
                        if (verifyPassword(password, storedHash)) {
                            return new User(
                                    rs.getLong("id"),
                                    rs.getString("full_name"),
                                    rs.getString("email"),
                                    rs.getString("role")
                            );
                        }
                    }
                }
            } catch (SQLException e) {
                System.err.println("Database login error: " + e.getMessage());
            }
            return null;
        }

        // 2. File-based fallback persistence
        for (StoredUser user : readUsers()) {
            if (user.email.equals(email)
                    && verifyPassword(password, user.passwordHash)) {
                return user.toUser();
            }
        }

        return null;
    }

    private static String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);

        byte[] hash = derive(
                password.toCharArray(), salt, ITERATIONS);

        return ITERATIONS + ":"
                + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(hash);
    }

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
