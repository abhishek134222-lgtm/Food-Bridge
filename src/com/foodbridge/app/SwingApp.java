
package com.foodbridge.app;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwingApp extends JFrame {

    private static final String API =
            "http://localhost:8080/api/donations";

    private static final Color GREEN = new Color(31, 105, 72);
    private static final Color DARK = new Color(24, 57, 43);
    private static final Color BACKGROUND = new Color(246, 248, 244);
    private static final Color MUTED = new Color(105, 119, 109);

    private final HttpClient client = HttpClient.newHttpClient();

    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"ID", "Donor", "Food", "Quantity", "Location", "Status"},
            0
    ) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };

    private final JLabel totalValue = new JLabel("—");
    private final JLabel availableValue = new JLabel("—");
    private final JLabel quantityValue = new JLabel("—");
    private final JLabel statusLabel = new JLabel("Connecting to FoodBridge...");

    private final JTextField donorField = new JTextField();
    private final JTextField foodField = new JTextField();
    private final JTextField quantityField = new JTextField();
    private final JTextField locationField = new JTextField();

    private final JButton submitButton = new JButton("Submit Donation");

    private List<DonationRow> donations = new ArrayList<>();

    public SwingApp() {
        setTitle("FoodBridge | Donation Management");
        setSize(1100, 720);
        setMinimumSize(new Dimension(850, 600));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        buildInterface();
        loadDonations();
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);

        JPanel sidebar = new JPanel();
        sidebar.setBackground(DARK);
        sidebar.setPreferredSize(new Dimension(210, 0));
        sidebar.setBorder(BorderFactory.createEmptyBorder(28, 16, 20, 16));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("FoodBridge");
        logo.setForeground(Color.WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 25));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("Food for everyone.");
        tagline.setForeground(new Color(190, 215, 197));
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 12));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(6));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(40));
        sidebar.add(sidebarItem("Dashboard", true));
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(sidebarItem("Food Donations", false));
        sidebar.add(Box.createVerticalGlue());

        JLabel footer = new JLabel("<html>Reducing waste,<br>sharing hope.</html>");
        footer.setForeground(new Color(190, 215, 197));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);
        sidebar.add(footer);

        JPanel content = new JPanel(new BorderLayout(0, 20));
        content.setBackground(BACKGROUND);
        content.setBorder(BorderFactory.createEmptyBorder(28, 28, 20, 28));

        JLabel title = new JLabel("Donation Dashboard");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(DARK);

        JLabel subtitle = new JLabel(
                "Manage donations and help surplus food reach communities."
        );
        subtitle.setForeground(MUTED);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(7));
        heading.add(subtitle);

        JPanel stats = new JPanel(new GridLayout(1, 3, 14, 0));
        stats.setOpaque(false);
        stats.add(statCard("Total donations", totalValue));
        stats.add(statCard("Available donations", availableValue));
        stats.add(statCard("Total quantity", quantityValue));

        JPanel overview = new JPanel(new BorderLayout(0, 18));
        overview.setOpaque(false);
        overview.add(heading, BorderLayout.NORTH);
        overview.add(stats, BorderLayout.CENTER);

        JTable table = new JTable(tableModel);
        table.setRowHeight(32);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setForeground(DARK);
        table.setBackground(Color.WHITE);
        table.setGridColor(new Color(232, 237, 231));
        table.setSelectionBackground(new Color(222, 240, 226));
        table.setSelectionForeground(DARK);
        table.getTableHeader().setFont(
                new Font("SansSerif", Font.BOLD, 12)
        );
        table.getTableHeader().setBackground(new Color(235, 241, 234));
        table.getTableHeader().setForeground(DARK);
        table.setFillsViewportHeight(true);

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createEmptyBorder());
        tableScroll.getViewport().setBackground(Color.WHITE);

        JPanel tableCard = cardPanel();
        tableCard.setLayout(new BorderLayout(0, 12));

        JLabel tableTitle = new JLabel("Recent Donations");
        tableTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton refreshButton = new JButton("Refresh");
        styleButton(refreshButton);
        refreshButton.addActionListener(e -> loadDonations());

        JPanel tableHeading = new JPanel(new BorderLayout());
        tableHeading.setOpaque(false);
        tableHeading.add(tableTitle, BorderLayout.WEST);
        tableHeading.add(refreshButton, BorderLayout.EAST);

        tableCard.add(tableHeading, BorderLayout.NORTH);
        tableCard.add(tableScroll, BorderLayout.CENTER);

        JPanel formCard = cardPanel();
        formCard.setLayout(new BorderLayout(0, 12));

        JLabel formTitle = new JLabel("Create a Donation");
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel fields = new JPanel(new GridLayout(2, 4, 12, 8));
        fields.setOpaque(false);

        addField(fields, "Donor name", donorField);
        addField(fields, "Food description", foodField);
        addField(fields, "Quantity", quantityField);
        addField(fields, "Pickup location", locationField);

        styleButton(submitButton);
        submitButton.addActionListener(e -> submitDonation());

        JPanel formBottom = new JPanel(new BorderLayout());
        formBottom.setOpaque(false);
        formBottom.add(statusLabel, BorderLayout.CENTER);
        formBottom.add(submitButton, BorderLayout.EAST);

        formCard.add(formTitle, BorderLayout.NORTH);
        formCard.add(fields, BorderLayout.CENTER);
        formCard.add(formBottom, BorderLayout.SOUTH);

        JPanel center = new JPanel(new BorderLayout(0, 18));
        center.setOpaque(false);
        center.add(tableCard, BorderLayout.CENTER);
        center.add(formCard, BorderLayout.SOUTH);

        content.add(overview, BorderLayout.NORTH);
        content.add(center, BorderLayout.CENTER);

        root.add(sidebar, BorderLayout.WEST);
        root.add(content, BorderLayout.CENTER);
        setContentPane(root);
    }

    private JLabel sidebarItem(String text, boolean active) {
        JLabel label = new JLabel("  " + text + "  ");
        label.setFont(new Font("SansSerif", Font.BOLD, 14));
        label.setForeground(Color.WHITE);
        label.setOpaque(true);
        label.setBackground(active ? GREEN : DARK);
        label.setBorder(BorderFactory.createEmptyBorder(12, 8, 12, 8));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        return label;
    }

    private JPanel statCard(String title, JLabel value) {
        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));

        value.setForeground(GREEN);
        value.setFont(new Font("SansSerif", Font.BOLD, 27));

        card.add(label);
        card.add(Box.createVerticalStrut(12));
        card.add(value);
        return card;
    }

    private JPanel cardPanel() {
        JPanel panel = new JPanel();
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(226, 233, 225)
                ),
                BorderFactory.createEmptyBorder(18, 18, 18, 18)
        ));
        return panel;
    }

    private void addField(
            JPanel panel, String labelText, JTextField field) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 6));
        wrapper.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        new Color(210, 220, 210)
                ),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        wrapper.add(label, BorderLayout.NORTH);
        wrapper.add(field, BorderLayout.CENTER);
        panel.add(wrapper);
    }

    private void styleButton(JButton button) {
        button.setBackground(GREEN);
        button.setForeground(Color.WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    private void loadDonations() {
        statusLabel.setText("Loading donations...");

        new SwingWorker<List<DonationRow>, Void>() {
            @Override
            protected List<DonationRow> doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() != 200) {
                    throw new IllegalStateException(
                            "API returned HTTP " + response.statusCode()
                    );
                }

                return parseDonations(response.body());
            }

            @Override
            protected void done() {
                try {
                    donations = get();
                    updateTableAndStats();
                    statusLabel.setText(
                            "Connected · " + donations.size() + " donation(s)"
                    );
                } catch (Exception ex) {
                    statusLabel.setText("Could not load donations.");
                    JOptionPane.showMessageDialog(
                            SwingApp.this,
                            "Cannot load donations. Ensure the Java server is running.\n"
                                    + ex.getMessage(),
                            "Connection Error",
                            JOptionPane.ERROR_MESSAGE
                    );
                }
            }
        }.execute();
    }

    private void updateTableAndStats() {
        tableModel.setRowCount(0);

        int available = 0;
        int totalQuantity = 0;

        for (DonationRow donation : donations) {
            tableModel.addRow(new Object[]{
                    donation.id,
                    donation.donor,
                    donation.food,
                    donation.quantity,
                    donation.location,
                    donation.status
            });

            if ("Available".equalsIgnoreCase(donation.status)) {
                available++;
            }

            totalQuantity += donation.quantity;
        }

        totalValue.setText(String.valueOf(donations.size()));
        availableValue.setText(String.valueOf(available));
        quantityValue.setText(String.valueOf(totalQuantity));
    }

    private void submitDonation() {
        String donor = donorField.getText().trim();
        String food = foodField.getText().trim();
        String location = locationField.getText().trim();

        int quantity;
        try {
            quantity = Integer.parseInt(quantityField.getText().trim());
            if (quantity <= 0) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException ex) {
            showError("Quantity must be a positive whole number.");
            return;
        }

        if (donor.isEmpty() || food.isEmpty() || location.isEmpty()) {
            showError("Please fill in all donation fields.");
            return;
        }

        String json = "{\"donor\":\"" + escape(donor)
                + "\",\"food\":\"" + escape(food)
                + "\",\"quantity\":" + quantity
                + ",\"location\":\"" + escape(location) + "\"}";

        submitButton.setEnabled(false);
        statusLabel.setText("Submitting donation...");

        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

                return response.statusCode();
            }

            @Override
            protected void done() {
                submitButton.setEnabled(true);
                try {
                    int status = get();

                    if (status != 201) {
                        showError("The server rejected the donation (HTTP "
                                + status + ").");
                        return;
                    }

                    donorField.setText("");
                    foodField.setText("");
                    quantityField.setText("");
                    locationField.setText("");
                    statusLabel.setText("Donation submitted successfully.");

                    loadDonations();
                } catch (Exception ex) {
                    showError("Unable to submit donation. Check the server.");
                }
            }
        }.execute();
    }

    private List<DonationRow> parseDonations(String json) {
        List<DonationRow> result = new ArrayList<>();

        Pattern objectPattern = Pattern.compile("\\{([^{}]*)\\}");
        Matcher objectMatcher = objectPattern.matcher(json);

        while (objectMatcher.find()) {
            String object = objectMatcher.group();
            result.add(new DonationRow(
                    getValue(object, "id"),
                    getValue(object, "donor"),
                    getValue(object, "food"),
                    getValue(object, "quantity"),
                    getValue(object, "location"),
                    getValue(object, "status")
            ));
        }

        return result;
    }

    private String getValue(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key)
                        + "\"\\s*:\\s*(?:\"((?:\\\\.|[^\"\\\\])*)\"|(\\d+))"
        );

        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            return "";
        }

        if (matcher.group(1) == null) {
            return matcher.group(2);
        }

        return matcher.group(1)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }

    private void showError(String message) {
        statusLabel.setText(message);
        JOptionPane.showMessageDialog(
                this, message, "FoodBridge", JOptionPane.ERROR_MESSAGE
        );
    }

    private static class DonationRow {
        final String id;
        final String donor;
        final String food;
        final int quantity;
        final String location;
        final String status;

        DonationRow(String id, String donor, String food,
                    String quantity, String location, String status) {
            this.id = id;
            this.donor = donor;
            this.food = food;
            this.quantity = Integer.parseInt(quantity);
            this.location = location;
            this.status = status;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName()
                );
            } catch (Exception ignored) {
                // Keep the default Swing look and feel.
            }

            new SwingApp().setVisible(true);
        });
    }
}
