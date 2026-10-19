
package com.foodbridge.server;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DonationStore {

    // Get all donations saved in PostgreSQL
    public static synchronized List<Donation> getAll() {

        List<Donation> donations = new ArrayList<>();

        String sql = """
            SELECT id, donor, food, quantity, location, status
            FROM public.donations
            ORDER BY id DESC
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                int quantity = Integer.parseInt(
                    result.getString("quantity")
                );

                String status = result.getString("status");
                status = status.substring(0, 1)
                    + status.substring(1).toLowerCase();

                Donation donation = new Donation(
                    result.getInt("id"),
                    result.getString("donor"),
                    result.getString("food"),
                    quantity,
                    result.getString("location"),
                    status
                );

                donations.add(donation);
            }

        } catch (SQLException | NumberFormatException e) {
            throw new IllegalStateException(
                "Could not load donations from PostgreSQL.", e
            );
        }

        return donations;
    }

    // Save a new donation in PostgreSQL
    public static synchronized Donation add(
            String donor, String food, int quantity, String location) {

        String sql = """
            INSERT INTO public.donations
                (donor, food, quantity, location, status)
            VALUES (?, ?, ?, ?, 'AVAILABLE')
            RETURNING id, status
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, donor);
            statement.setString(2, food);
            statement.setString(3, String.valueOf(quantity));
            statement.setString(4, location);

            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return new Donation(
                        result.getInt("id"),
                        donor,
                        food,
                        quantity,
                        location,
                        "Available"
                    );
                }
            }

            throw new SQLException("The donation was not returned.");

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Could not save donation to PostgreSQL.", e
            );
        }
    }

    // Securely claim a donation only if it is still AVAILABLE
    public static synchronized boolean claimDonation(int id) {

        String sql = """
            UPDATE public.donations
            SET status = 'CLAIMED'
            WHERE id = ?
              AND status = 'AVAILABLE'
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setInt(1, id);

            // true means the claim succeeded.
            // false means it does not exist or is no longer available.
            return statement.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Could not claim donation in PostgreSQL.", e
            );
        }
    }

    // Update the status of an existing donation
    public static synchronized boolean updateStatus(
            int id, String newStatus) {

        if (newStatus == null) {
            return false;
        }

        String status = newStatus.trim().toUpperCase();

        if (!status.equals("AVAILABLE")
                && !status.equals("CLAIMED")
                && !status.equals("COMPLETED")
                && !status.equals("CANCELLED")) {
            return false;
        }

        String sql = """
            UPDATE public.donations
            SET status = ?
            WHERE id = ?
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                     connection.prepareStatement(sql)) {

            statement.setString(1, status);
            statement.setInt(2, id);

            return statement.executeUpdate() == 1;

        } catch (SQLException e) {
            throw new IllegalStateException(
                "Could not update donation status in PostgreSQL.", e
            );
        }
    }
}
