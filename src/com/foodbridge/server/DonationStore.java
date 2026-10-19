
package com.foodbridge.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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

                    // Old records have ownerId = 0.
                    // Do not guess their owners.
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
        List<StoredDonation> stored = readDonations();
        List<Donation> result = new ArrayList<>();

        for (StoredDonation item : stored) {
            result.add(item.toDonation());
        }

        result.sort((a, b) ->
                Integer.compare(b.getId(), a.getId()));

        return result;
    }

    // ownerId comes from the authenticated account, never from the browser.
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

    // Synchronized: only one receiver can claim an available donation.
    public static synchronized boolean claimDonation(int id) {
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

    // Donors can update only their own donations.
    // Allowed transitions: AVAILABLE -> CANCELLED,
    // CLAIMED -> COMPLETED. Repeating the current status is harmless.
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

        List<StoredDonation> donations = readDonations();

        for (StoredDonation donation : donations) {
            if (donation.id != id) {
                continue;
            }

            // Includes legacy records with ownerId == 0.
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
