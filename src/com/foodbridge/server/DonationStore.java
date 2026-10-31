package com.foodbridge.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DonationStore {

    private static final Path DATA_DIR =
            Paths.get("data").toAbsolutePath();

    private static final Path DONATIONS_FILE =
            DATA_DIR.resolve("donations.dat");

    private static class StoredDonation implements Serializable {
        private static final long serialVersionUID = 1L;

        int id;
        long ownerId;
        String donor;
        String food;
        int quantity;
        String location;
        String status;

        StoredDonation(int id, long ownerId, String donor,
                       String food, int quantity,
                       String location, String status) {
            this.id = id;
            this.ownerId = ownerId;
            this.donor = donor;
            this.food = food;
            this.quantity = quantity;
            this.location = location;
            this.status = status;
        }

        Donation toDonation() {
            return new Donation(
                    id, donor, food, quantity,
                    location, formatStatus(status));
        }
    }

    private static List<StoredDonation> readDonations() {
        try {
            Files.createDirectories(DATA_DIR);

            if (!Files.exists(DONATIONS_FILE)) {
                return new ArrayList<>();
            }

            try (ObjectInputStream input = new ObjectInputStream(
                    Files.newInputStream(DONATIONS_FILE))) {

                Object object = input.readObject();

                if (!(object instanceof ArrayList<?> list)) {
                    throw new IllegalStateException(
                            "Invalid donations data file.");
                }

                List<StoredDonation> donations = new ArrayList<>();

                for (Object item : list) {
                    if (!(item instanceof StoredDonation donation)) {
                        throw new IllegalStateException(
                                "Invalid donation record in data file.");
                    }
                    donations.add(donation);
                }

                return donations;
            }

        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(
                    "Could not read data/donations.dat.", e);
        }
    }

    private static void saveDonations(
            List<StoredDonation> donations) {
        try {
            Files.createDirectories(DATA_DIR);

            Path temporaryFile = DATA_DIR.resolve("donations.tmp");

            try (ObjectOutputStream output = new ObjectOutputStream(
                    Files.newOutputStream(temporaryFile))) {
                output.writeObject(new ArrayList<>(donations));
            }

            try {
                Files.move(
                        temporaryFile, DONATIONS_FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(
                        temporaryFile, DONATIONS_FILE,
                        StandardCopyOption.REPLACE_EXISTING);
            }

        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not save donations to file.", e);
        }
    }

    private static String normalizeStatus(String status) {
        return status == null
                ? "" : status.trim().toUpperCase(Locale.ROOT);
    }

    private static String formatStatus(String status) {
        return switch (normalizeStatus(status)) {
            case "AVAILABLE" -> "Available";
            case "CLAIMED" -> "Claimed";
            case "COMPLETED" -> "Completed";
            case "CANCELLED" -> "Cancelled";
            default -> normalizeStatus(status);
        };
    }

    public static synchronized List<Donation> getAll() {
        // 1. Neon PostgreSQL persistence
        if (Database.isConfigured()) {
            String sql = "SELECT id, donor, food, quantity, location, status FROM donations ORDER BY id DESC";
            List<Donation> list = new ArrayList<>();
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {

                while (rs.next()) {
                    list.add(new Donation(
                            (int) rs.getLong("id"),
                            rs.getString("donor"),
                            rs.getString("food"),
                            rs.getInt("quantity"),
                            rs.getString("location"),
                            formatStatus(rs.getString("status"))
                    ));
                }
                return list;
            } catch (SQLException e) {
                System.err.println("Database query error: " + e.getMessage());
            }
        }

        // 2. File-based fallback persistence
        List<StoredDonation> stored = readDonations();
        List<Donation> result = new ArrayList<>();

        for (StoredDonation item : stored) {
            result.add(item.toDonation());
        }

        result.sort((a, b) ->
                Integer.compare(b.getId(), a.getId()));

        return result;
    }

    public static synchronized Donation add(
            long ownerId, String donor, String food,
            int quantity, String location) {

        if (ownerId <= 0
                || donor == null || donor.isBlank()
                || food == null || food.isBlank()
                || location == null || location.isBlank()
                || quantity <= 0 || quantity > 100000) {
            throw new IllegalArgumentException(
                    "Enter valid donor, food, quantity and location.");
        }

        // 1. Neon PostgreSQL persistence
        if (Database.isConfigured()) {
            String sql = "INSERT INTO donations (owner_id, donor, food, quantity, location, status) VALUES (?, ?, ?, ?, ?, 'AVAILABLE') RETURNING id";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, ownerId);
                stmt.setString(2, donor.trim());
                stmt.setString(3, food.trim());
                stmt.setInt(4, quantity);
                stmt.setString(5, location.trim());

                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        int id = (int) rs.getLong(1);
                        return new Donation(id, donor.trim(), food.trim(), quantity, location.trim(), "Available");
                    }
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Database insertion error: " + e.getMessage(), e);
            }
        }

        // 2. File-based fallback persistence
        List<StoredDonation> donations = readDonations();

        int nextId = 1;
        for (StoredDonation donation : donations) {
            if (donation.id >= nextId) {
                nextId = donation.id + 1;
            }
        }

        StoredDonation created = new StoredDonation(
                nextId, ownerId, donor.trim(), food.trim(),
                quantity, location.trim(), "AVAILABLE");

        donations.add(created);
        saveDonations(donations);

        return created.toDonation();
    }

    public static synchronized boolean claimDonation(int id) {
        // 1. Neon PostgreSQL persistence: atomic conditional update
        if (Database.isConfigured()) {
            String sql = "UPDATE donations SET status = 'CLAIMED', updated_at = CURRENT_TIMESTAMP WHERE id = ? AND UPPER(status) = 'AVAILABLE'";
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setLong(1, id);
                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database claim error: " + e.getMessage());
                return false;
            }
        }

        // 2. File-based fallback persistence
        List<StoredDonation> donations = readDonations();

        for (StoredDonation donation : donations) {
            if (donation.id == id
                    && normalizeStatus(donation.status)
                            .equals("AVAILABLE")) {

                donation.status = "CLAIMED";
                saveDonations(donations);
                return true;
            }
        }

        return false;
    }

    public static synchronized boolean updateStatus(
            int id, String newStatus, long ownerId) {

        if (ownerId <= 0) {
            return false;
        }

        String requested = normalizeStatus(newStatus);

        if (!requested.equals("AVAILABLE")
                && !requested.equals("COMPLETED")
                && !requested.equals("CANCELLED")) {
            return false;
        }

        // 1. Neon PostgreSQL persistence
        if (Database.isConfigured()) {
            String sql = """
                UPDATE donations
                SET status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ? AND owner_id = ?
                  AND (
                    (UPPER(status) = 'AVAILABLE' AND ? = 'CANCELLED')
                    OR (UPPER(status) = 'CLAIMED' AND ? = 'COMPLETED')
                    OR UPPER(status) = ?
                  )
            """;
            try (Connection conn = Database.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {

                stmt.setString(1, requested);
                stmt.setLong(2, id);
                stmt.setLong(3, ownerId);
                stmt.setString(4, requested);
                stmt.setString(5, requested);
                stmt.setString(6, requested);

                return stmt.executeUpdate() > 0;
            } catch (SQLException e) {
                System.err.println("Database status update error: " + e.getMessage());
                return false;
            }
        }

        // 2. File-based fallback persistence
        List<StoredDonation> donations = readDonations();

        for (StoredDonation donation : donations) {
            if (donation.id != id) {
                continue;
            }

            if (donation.ownerId != ownerId) {
                return false;
            }

            String current = normalizeStatus(donation.status);

            if (current.equals(requested)) {
                return true;
            }

            boolean allowed =
                    (current.equals("AVAILABLE")
                            && requested.equals("CANCELLED"))
                    || (current.equals("CLAIMED")
                            && requested.equals("COMPLETED"));

            if (!allowed) {
                return false;
            }

            donation.status = requested;
            saveDonations(donations);
            return true;
        }

        return false;
    }
}
