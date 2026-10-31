package com.foodbridge.app;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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

    private String serverBase = "http://localhost:8080";
    private String sessionCookie = null;
    private String currentUserName = null;
    private String currentUserRole = null;
    private String currentUserEmail = null;

    private static final Color GREEN = new Color(43, 119, 78);
    private static final Color DARK = new Color(22, 49, 37);
    private static final Color SIDEBAR_TEXT = new Color(222, 235, 225);
    private static final Color BACKGROUND = new Color(245, 248, 245);
    private static final Color MUTED = new Color(105, 120, 110);
    private static final Color BORDER = new Color(225, 233, 226);
    private static final Color WHITE = Color.WHITE;

    private final HttpClient client = HttpClient.newHttpClient();

    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pagePanel = new JPanel(pageLayout);

    private final DefaultTableModel tableModel =
            new DefaultTableModel(
                    new String[]{
                            "ID", "Donor", "Food",
                            "Quantity", "Location", "Status"
                    }, 0) {

                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };

    // Five dashboard statistics
    private final JLabel totalValue = new JLabel("—");
    private final JLabel availableValue = new JLabel("—");
    private final JLabel claimedValue = new JLabel("—");
    private final JLabel completedValue = new JLabel("—");
    private final JLabel quantityValue = new JLabel("—");

    private final JLabel statusLabel =
            new JLabel("Connecting to FoodBridge...");

    private final JLabel dashboardStatus =
            new JLabel("Loading donation data...");

    private final JLabel userStatusBadge = new JLabel("👤 Guest (Not signed in)");

    // Donation form fields
    private final JTextField donorField = new JTextField();
    private final JTextField foodField = new JTextField();
    private final JTextField quantityField = new JTextField();
    private final JTextField locationField = new JTextField();

    private final JButton submitButton =
            new JButton("Submit donation");

    // Donation status dropdown for editing a selected donation
    private final JComboBox<String> statusCombo =
            new JComboBox<>(new String[]{
                    "Available", "Claimed", "Completed", "Cancelled"
            });

    private final JButton updateStatusButton =
            new JButton("Update Status");

    private final JButton dashboardNav;
    private final JButton donationsNav;
    private final JButton accountNav;

    private final JTable donationsTable = new JTable(tableModel);

    // Search and status filter
    private final JTextField searchField = new JTextField(18);

    private final JComboBox<String> filterStatus =
            new JComboBox<>(new String[]{
                    "All Statuses",
                    "Available",
                    "Claimed",
                    "Completed",
                    "Cancelled"
            });

    private TableRowSorter<DefaultTableModel> donationSorter;

    private List<DonationRow> donations = new ArrayList<>();

    // Account view dynamic elements
    private final JPanel accountCardContainer = new JPanel(new CardLayout());
    private final JTextField loginEmailField = new JTextField();
    private final JPasswordField loginPasswordField = new JPasswordField();
    private final JTextField regNameField = new JTextField();
    private final JTextField regEmailField = new JTextField();
    private final JPasswordField regPasswordField = new JPasswordField();
    private final JComboBox<String> regRoleCombo = new JComboBox<>(new String[]{"DONOR", "RECEIVER"});
    private final JLabel accountStatusLabel = new JLabel(" ");

    public SwingApp() {
        setTitle("FoodBridge | Donation Management");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        setMinimumSize(new Dimension(1100, 700));
        setSize(1350, 820);
        setLocationRelativeTo(null);

        dashboardNav = createNavButton("▦   Dashboard", true);
        donationsNav = createNavButton("♡   Food Donations", false);
        accountNav = createNavButton("👤   Account / Login", false);

        buildInterface();
        loadDonations();
    }

    // Build the complete application interface
    private void buildInterface() {

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(BACKGROUND);

        // Sidebar
        JPanel sidebar = new JPanel();
        sidebar.setBackground(DARK);
        sidebar.setPreferredSize(new Dimension(245, 0));
        sidebar.setBorder(
                BorderFactory.createEmptyBorder(28, 17, 22, 17));
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));

        JLabel logo = new JLabel("FoodBridge");
        logo.setForeground(WHITE);
        logo.setFont(new Font("SansSerif", Font.BOLD, 27));
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel tagline = new JLabel("Food for everyone.");
        tagline.setForeground(new Color(177, 204, 185));
        tagline.setFont(new Font("SansSerif", Font.PLAIN, 13));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        userStatusBadge.setForeground(new Color(205, 230, 215));
        userStatusBadge.setFont(new Font("SansSerif", Font.BOLD, 12));
        userStatusBadge.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(logo);
        sidebar.add(Box.createVerticalStrut(5));
        sidebar.add(tagline);
        sidebar.add(Box.createVerticalStrut(15));
        sidebar.add(userStatusBadge);
        sidebar.add(Box.createVerticalStrut(28));

        dashboardNav.addActionListener(e -> showPage("dashboard"));
        donationsNav.addActionListener(e -> showPage("donations"));
        accountNav.addActionListener(e -> showPage("account"));

        sidebar.add(dashboardNav);
        sidebar.add(Box.createVerticalStrut(9));
        sidebar.add(donationsNav);
        sidebar.add(Box.createVerticalStrut(9));
        sidebar.add(accountNav);
        sidebar.add(Box.createVerticalGlue());

        JButton serverConfigBtn = new JButton("⚙ " + serverBase);
        serverConfigBtn.setFont(new Font("SansSerif", Font.PLAIN, 11));
        serverConfigBtn.setForeground(new Color(177, 204, 185));
        serverConfigBtn.setBackground(DARK);
        serverConfigBtn.setBorderPainted(false);
        serverConfigBtn.setFocusPainted(false);
        serverConfigBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        serverConfigBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        serverConfigBtn.addActionListener(e -> promptChangeServer());

        JLabel footer = new JLabel(
                "<html><b>Reducing waste,</b><br>sharing hope.</html>");
        footer.setForeground(new Color(177, 204, 185));
        footer.setFont(new Font("SansSerif", Font.PLAIN, 12));
        footer.setAlignmentX(Component.LEFT_ALIGNMENT);

        sidebar.add(serverConfigBtn);
        sidebar.add(Box.createVerticalStrut(10));
        sidebar.add(footer);

        // Main pages
        pagePanel.setOpaque(false);
        pagePanel.add(buildDashboardPage(), "dashboard");
        pagePanel.add(buildDonationsPage(), "donations");
        pagePanel.add(buildAccountPage(), "account");

        root.add(sidebar, BorderLayout.WEST);
        root.add(pagePanel, BorderLayout.CENTER);

        setContentPane(root);
    }

    private void promptChangeServer() {
        String input = JOptionPane.showInputDialog(
                this,
                "Enter FoodBridge Backend API URL:\n(e.g., http://localhost:8080 or your deployed HTTPS URL)",
                serverBase);

        if (input != null && !input.trim().isBlank()) {
            serverBase = input.trim().replaceAll("/+$", "");
            loadDonations();
        }
    }

    // Dashboard page
    private JPanel buildDashboardPage() {

        JPanel page = pageContainer();

        JLabel title = pageTitle("Dashboard");

        JLabel subtitle = new JLabel(
                "A quick overview of food donations in your community.");
        subtitle.setForeground(MUTED);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(7));
        heading.add(subtitle);

        // Five statistic cards
        JPanel stats = new JPanel(new GridLayout(1, 5, 10, 0));
        stats.setOpaque(false);
        stats.setPreferredSize(new Dimension(900, 115));
        stats.setMinimumSize(new Dimension(750, 115));

        stats.add(statCard(
                "TOTAL DONATIONS", totalValue, "All records"));

        stats.add(statCard(
                "AVAILABLE", availableValue, "Ready to collect"));

        stats.add(statCard(
                "CLAIMED", claimedValue, "Reserved donations"));

        stats.add(statCard(
                "COMPLETED", completedValue, "Collected donations"));

        stats.add(statCard(
                "TOTAL QUANTITY", quantityValue, "Combined quantity"));

        JPanel top = new JPanel(new BorderLayout(0, 16));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(stats, BorderLayout.CENTER);

        // Recent donations card
        JPanel recentCard = cardPanel();
        recentCard.setLayout(new BorderLayout(0, 12));

        JLabel recentTitle = new JLabel("Recent donations");
        recentTitle.setFont(new Font("SansSerif", Font.BOLD, 19));

        JButton refresh = primaryButton("Refresh data");
        refresh.addActionListener(e -> loadDonations());

        JPanel recentHeader = new JPanel(new BorderLayout());
        recentHeader.setOpaque(false);
        recentHeader.add(recentTitle, BorderLayout.WEST);
        recentHeader.add(refresh, BorderLayout.EAST);

        JTable recentTable = new JTable(tableModel);
        styleTable(recentTable);

        JScrollPane scroll = new JScrollPane(recentTable);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(WHITE);

        recentCard.add(recentHeader, BorderLayout.NORTH);
        recentCard.add(scroll, BorderLayout.CENTER);

        dashboardStatus.setForeground(MUTED);
        dashboardStatus.setFont(
                new Font("SansSerif", Font.PLAIN, 12));

        page.add(top, BorderLayout.NORTH);
        page.add(recentCard, BorderLayout.CENTER);
        page.add(dashboardStatus, BorderLayout.SOUTH);

        return page;
    }

    // Food donations page
    private JPanel buildDonationsPage() {

        JPanel page = pageContainer();

        JLabel title = pageTitle("Food Donations");

        JLabel subtitle = new JLabel(
                "Search, filter, manage, and add food donations.");
        subtitle.setForeground(MUTED);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(7));
        heading.add(subtitle);

        // Donations table card
        JPanel tableCard = cardPanel();
        tableCard.setLayout(new BorderLayout(0, 12));

        JLabel tableTitle = new JLabel("All donations");
        tableTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        JButton refresh = primaryButton("Refresh");
        refresh.addActionListener(e -> loadDonations());

        JPanel tableHeader = new JPanel(new BorderLayout());
        tableHeader.setOpaque(false);
        tableHeader.add(tableTitle, BorderLayout.WEST);
        tableHeader.add(refresh, BorderLayout.EAST);

        // Search and filter controls
        JPanel searchPanel = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 5));
        searchPanel.setOpaque(false);

        JLabel searchLabel = new JLabel("Search:");
        searchLabel.setForeground(DARK);

        searchField.setToolTipText(
                "Search donor, food, quantity, or location");
        styleInput(searchField);
        searchField.setPreferredSize(new Dimension(220, 36));

        JLabel filterLabel = new JLabel("Status:");
        filterLabel.setForeground(DARK);

        filterStatus.setPreferredSize(new Dimension(155, 36));

        searchPanel.add(searchLabel);
        searchPanel.add(searchField);
        searchPanel.add(filterLabel);
        searchPanel.add(filterStatus);

        JPanel tableTop = new JPanel(new BorderLayout(0, 8));
        tableTop.setOpaque(false);
        tableTop.add(tableHeader, BorderLayout.NORTH);
        tableTop.add(searchPanel, BorderLayout.SOUTH);

        styleTable(donationsTable);

        donationSorter = new TableRowSorter<>(tableModel);
        donationsTable.setRowSorter(donationSorter);

        searchField.getDocument().addDocumentListener(
                new DocumentListener() {

                    @Override
                    public void insertUpdate(DocumentEvent e) {
                        applyDonationFilter();
                    }

                    @Override
                    public void removeUpdate(DocumentEvent e) {
                        applyDonationFilter();
                    }

                    @Override
                    public void changedUpdate(DocumentEvent e) {
                        applyDonationFilter();
                    }
                });

        filterStatus.addActionListener(
                e -> applyDonationFilter());

        JScrollPane scroll = new JScrollPane(donationsTable);
        scroll.setBorder(BorderFactory.createLineBorder(BORDER));
        scroll.getViewport().setBackground(WHITE);

        tableCard.add(tableTop, BorderLayout.NORTH);
        tableCard.add(scroll, BorderLayout.CENTER);

        // Donation status controls
        JPanel statusControls = new JPanel(
                new FlowLayout(FlowLayout.LEFT, 10, 8));
        statusControls.setBackground(WHITE);

        JLabel statusTitle = new JLabel(
                "Change selected donation status:");
        statusTitle.setForeground(DARK);

        statusCombo.setPreferredSize(new Dimension(140, 34));
        stylePrimaryButton(updateStatusButton);

        statusControls.add(statusTitle);
        statusControls.add(statusCombo);
        statusControls.add(updateStatusButton);

        tableCard.add(statusControls, BorderLayout.SOUTH);

        updateStatusButton.addActionListener(
                e -> updateDonationStatus());

        // Donation form card
        JPanel formCard = cardPanel();
        formCard.setLayout(new BorderLayout(0, 13));

        JLabel formTitle = new JLabel("Create a food donation");
        formTitle.setFont(new Font("SansSerif", Font.BOLD, 18));

        JPanel fields = new JPanel(new GridLayout(1, 4, 12, 0));
        fields.setOpaque(false);

        addField(fields, "Donor name", donorField);
        addField(fields, "Food description", foodField);
        addField(fields, "Quantity (whole number)", quantityField);
        addField(fields, "Pickup location", locationField);

        styleInput(donorField);
        styleInput(foodField);
        styleInput(quantityField);
        styleInput(locationField);

        submitButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        stylePrimaryButton(submitButton);
        submitButton.addActionListener(e -> submitDonation());

        statusLabel.setForeground(MUTED);
        statusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        JPanel bottom = new JPanel(new BorderLayout(12, 0));
        bottom.setOpaque(false);
        bottom.add(statusLabel, BorderLayout.CENTER);
        bottom.add(submitButton, BorderLayout.EAST);

        formCard.add(formTitle, BorderLayout.NORTH);
        formCard.add(fields, BorderLayout.CENTER);
        formCard.add(bottom, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout(0, 16));
        content.setOpaque(false);
        content.add(tableCard, BorderLayout.CENTER);
        content.add(formCard, BorderLayout.SOUTH);

        page.add(heading, BorderLayout.NORTH);
        page.add(content, BorderLayout.CENTER);

        return page;
    }

    // Account Page (Login / Register / Profile)
    private JPanel buildAccountPage() {
        JPanel page = pageContainer();

        JLabel title = pageTitle("Account & Authentication");
        JLabel subtitle = new JLabel("Log in or register to publish and claim donations.");
        subtitle.setForeground(MUTED);

        JPanel heading = new JPanel();
        heading.setOpaque(false);
        heading.setLayout(new BoxLayout(heading, BoxLayout.Y_AXIS));
        heading.add(title);
        heading.add(Box.createVerticalStrut(7));
        heading.add(subtitle);

        // Sub-panel 1: Auth Forms (Not Logged In)
        JPanel authFormsPanel = new JPanel(new GridLayout(1, 2, 24, 0));
        authFormsPanel.setOpaque(false);

        // Sign In Card
        JPanel loginCard = cardPanel();
        loginCard.setLayout(new BoxLayout(loginCard, BoxLayout.Y_AXIS));
        JLabel loginTitle = new JLabel("Sign In");
        loginTitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        loginTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel loginSub = new JLabel("Access your FoodBridge account.");
        loginSub.setForeground(MUTED);
        loginSub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        loginSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleInput(loginEmailField);
        loginEmailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        loginPasswordField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        loginPasswordField.setPreferredSize(new Dimension(120, 38));
        loginPasswordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        loginPasswordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 219, 209)),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));

        JButton signInBtn = primaryButton("Sign In →");
        signInBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        signInBtn.addActionListener(e -> performLogin());

        loginCard.add(loginTitle);
        loginCard.add(Box.createVerticalStrut(5));
        loginCard.add(loginSub);
        loginCard.add(Box.createVerticalStrut(20));
        loginCard.add(createFieldWrapper("Email address", loginEmailField));
        loginCard.add(Box.createVerticalStrut(12));
        loginCard.add(createFieldWrapper("Password", loginPasswordField));
        loginCard.add(Box.createVerticalStrut(20));
        loginCard.add(signInBtn);
        loginCard.add(Box.createVerticalGlue());

        // Register Card
        JPanel regCard = cardPanel();
        regCard.setLayout(new BoxLayout(regCard, BoxLayout.Y_AXIS));
        JLabel regTitle = new JLabel("Create Account");
        regTitle.setFont(new Font("SansSerif", Font.BOLD, 20));
        regTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel regSub = new JLabel("Register as a Food Donor or Receiver.");
        regSub.setForeground(MUTED);
        regSub.setFont(new Font("SansSerif", Font.PLAIN, 12));
        regSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        styleInput(regNameField);
        regNameField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        styleInput(regEmailField);
        regEmailField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        regPasswordField.setFont(new Font("SansSerif", Font.PLAIN, 13));
        regPasswordField.setPreferredSize(new Dimension(120, 38));
        regPasswordField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        regPasswordField.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(207, 219, 209)),
                BorderFactory.createEmptyBorder(8, 9, 8, 9)));

        regRoleCombo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));

        JButton registerBtn = primaryButton("Register Account →");
        registerBtn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        registerBtn.addActionListener(e -> performRegister());

        regCard.add(regTitle);
        regCard.add(Box.createVerticalStrut(5));
        regCard.add(regSub);
        regCard.add(Box.createVerticalStrut(20));
        regCard.add(createFieldWrapper("Full name", regNameField));
        regCard.add(Box.createVerticalStrut(12));
        regCard.add(createFieldWrapper("Email address", regEmailField));
        regCard.add(Box.createVerticalStrut(12));
        regCard.add(createFieldWrapper("Password (min 8 chars)", regPasswordField));
        regCard.add(Box.createVerticalStrut(12));
        regCard.add(createFieldWrapper("Register as", regRoleCombo));
        regCard.add(Box.createVerticalStrut(20));
        regCard.add(registerBtn);
        regCard.add(Box.createVerticalGlue());

        authFormsPanel.add(loginCard);
        authFormsPanel.add(regCard);

        // Sub-panel 2: Profile (Logged In)
        JPanel profilePanel = cardPanel();
        profilePanel.setLayout(new BoxLayout(profilePanel, BoxLayout.Y_AXIS));

        JLabel profTitle = new JLabel("Your Account Profile");
        profTitle.setFont(new Font("SansSerif", Font.BOLD, 22));
        profTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel profileDetails = new JLabel("You are currently signed in.");
        profileDetails.setForeground(MUTED);
        profileDetails.setFont(new Font("SansSerif", Font.PLAIN, 14));
        profileDetails.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton logoutBtn = new JButton("Sign Out");
        logoutBtn.setBackground(new Color(180, 40, 40));
        logoutBtn.setForeground(WHITE);
        logoutBtn.setFont(new Font("SansSerif", Font.BOLD, 12));
        logoutBtn.setBorderPainted(false);
        logoutBtn.setFocusPainted(false);
        logoutBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        logoutBtn.addActionListener(e -> performLogout());

        profilePanel.add(profTitle);
        profilePanel.add(Box.createVerticalStrut(15));
        profilePanel.add(profileDetails);
        profilePanel.add(Box.createVerticalStrut(25));
        profilePanel.add(logoutBtn);
        profilePanel.add(Box.createVerticalGlue());

        accountCardContainer.setOpaque(false);
        accountCardContainer.add(authFormsPanel, "forms");
        accountCardContainer.add(profilePanel, "profile");

        accountStatusLabel.setForeground(MUTED);
        accountStatusLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));

        page.add(heading, BorderLayout.NORTH);
        page.add(accountCardContainer, BorderLayout.CENTER);
        page.add(accountStatusLabel, BorderLayout.SOUTH);

        return page;
    }

    private JPanel createFieldWrapper(String labelText, JComponent input) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 5));
        wrapper.setOpaque(false);
        JLabel label = new JLabel(labelText);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));
        label.setForeground(MUTED);
        wrapper.add(label, BorderLayout.NORTH);
        wrapper.add(input, BorderLayout.CENTER);
        return wrapper;
    }

    private void performLogin() {
        String email = loginEmailField.getText().trim();
        String pass = new String(loginPasswordField.getPassword());

        if (email.isEmpty() || pass.isEmpty()) {
            accountStatusLabel.setText("Please enter your email and password.");
            return;
        }

        accountStatusLabel.setText("Signing in...");

        new SwingWorker<HttpResponse<String>, Void>() {
            @Override
            protected HttpResponse<String> doInBackground() throws Exception {
                String json = "{\"email\":\"" + escape(email) + "\",\"password\":\"" + escape(pass) + "\"}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverBase + "/api/login"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            @Override
            protected void done() {
                try {
                    HttpResponse<String> response = get();
                    if (response.statusCode() == 200) {
                        String cookieHeader = response.headers().firstValue("Set-Cookie").orElse(null);
                        if (cookieHeader != null) {
                            sessionCookie = cookieHeader.split(";")[0];
                        }
                        String body = response.body();
                        currentUserName = getValue(body, "fullName");
                        currentUserEmail = getValue(body, "email");
                        currentUserRole = getValue(body, "role");

                        onAuthSuccess();
                    } else {
                        String err = getValue(response.body(), "error");
                        accountStatusLabel.setText(err.isEmpty() ? "Invalid email or password." : err);
                        JOptionPane.showMessageDialog(SwingApp.this, "Sign in failed: " + (err.isEmpty() ? "Invalid credentials" : err), "Login Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    accountStatusLabel.setText("Sign in failed. Check server connection.");
                }
            }
        }.execute();
    }

    private void performRegister() {
        String name = regNameField.getText().trim();
        String email = regEmailField.getText().trim();
        String pass = new String(regPasswordField.getPassword());
        String role = (String) regRoleCombo.getSelectedItem();

        if (name.isEmpty() || email.isEmpty() || pass.isEmpty()) {
            accountStatusLabel.setText("Please fill out all registration fields.");
            return;
        }

        accountStatusLabel.setText("Registering account...");

        new SwingWorker<HttpResponse<String>, Void>() {
            @Override
            protected HttpResponse<String> doInBackground() throws Exception {
                String json = "{\"fullName\":\"" + escape(name)
                        + "\",\"email\":\"" + escape(email)
                        + "\",\"password\":\"" + escape(pass)
                        + "\",\"role\":\"" + escape(role) + "\"}";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverBase + "/api/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                return client.send(request, HttpResponse.BodyHandlers.ofString());
            }

            @Override
            protected void done() {
                try {
                    HttpResponse<String> response = get();
                    if (response.statusCode() == 201) {
                        String cookieHeader = response.headers().firstValue("Set-Cookie").orElse(null);
                        if (cookieHeader != null) {
                            sessionCookie = cookieHeader.split(";")[0];
                        }
                        String body = response.body();
                        currentUserName = getValue(body, "fullName");
                        currentUserEmail = getValue(body, "email");
                        currentUserRole = getValue(body, "role");

                        onAuthSuccess();
                    } else {
                        String err = getValue(response.body(), "error");
                        accountStatusLabel.setText(err.isEmpty() ? "Registration rejected." : err);
                        JOptionPane.showMessageDialog(SwingApp.this, "Registration failed: " + err, "Registration Error", JOptionPane.ERROR_MESSAGE);
                    }
                } catch (Exception ex) {
                    accountStatusLabel.setText("Registration failed. Check server connection.");
                }
            }
        }.execute();
    }

    private void onAuthSuccess() {
        userStatusBadge.setText("👤 " + currentUserName + " (" + currentUserRole + ")");
        accountStatusLabel.setText("Signed in as " + currentUserName);
        donorField.setText(currentUserName);

        CardLayout cl = (CardLayout) accountCardContainer.getLayout();
        cl.show(accountCardContainer, "profile");

        JOptionPane.showMessageDialog(this, "Welcome to FoodBridge, " + currentUserName + "!\nYou are signed in as: " + currentUserRole, "Authenticated", JOptionPane.INFORMATION_MESSAGE);
        showPage("donations");
    }

    private void performLogout() {
        sessionCookie = null;
        currentUserName = null;
        currentUserRole = null;
        currentUserEmail = null;

        userStatusBadge.setText("👤 Guest (Not signed in)");
        donorField.setText("");
        loginPasswordField.setText("");
        regPasswordField.setText("");

        CardLayout cl = (CardLayout) accountCardContainer.getLayout();
        cl.show(accountCardContainer, "forms");
        accountStatusLabel.setText("Logged out successfully.");
        showPage("account");
    }

    // Search donations and filter by status
    private void applyDonationFilter() {

        if (donationSorter == null) {
            return;
        }

        String search = searchField.getText().trim();
        String selectedStatus =
                (String) filterStatus.getSelectedItem();

        List<RowFilter<Object, Object>> filters =
                new ArrayList<>();

        if (!search.isEmpty()) {
            filters.add(RowFilter.regexFilter(
                    "(?i)" + Pattern.quote(search),
                    1, 2, 3, 4));
        }

        if (selectedStatus != null
                && !"All Statuses".equals(selectedStatus)) {

            filters.add(RowFilter.regexFilter(
                    "(?i)^" + Pattern.quote(selectedStatus) + "$",
                    5));
        }

        if (filters.isEmpty()) {
            donationSorter.setRowFilter(null);
        } else {
            donationSorter.setRowFilter(
                    RowFilter.andFilter(filters));
        }
    }

    private JPanel pageContainer() {

        JPanel panel = new JPanel(new BorderLayout(0, 22));
        panel.setBackground(BACKGROUND);
        panel.setBorder(
                BorderFactory.createEmptyBorder(30, 30, 24, 30));
        return panel;
    }

    private JLabel pageTitle(String text) {

        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.BOLD, 30));
        label.setForeground(DARK);
        return label;
    }

    private JButton createNavButton(String text, boolean active) {

        JButton button = new JButton(text);
        button.setFont(new Font("SansSerif", Font.BOLD, 14));
        button.setForeground(active ? WHITE : SIDEBAR_TEXT);
        button.setBackground(active ? GREEN : DARK);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(true);
        button.setOpaque(true);
        button.setBorder(
                BorderFactory.createEmptyBorder(13, 12, 13, 10));
        button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        button.setPreferredSize(new Dimension(210, 48));
        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private void showPage(String page) {

        boolean dashboard = "dashboard".equals(page);
        boolean donationsPage = "donations".equals(page);
        boolean accountPage = "account".equals(page);

        dashboardNav.setBackground(dashboard ? GREEN : DARK);
        dashboardNav.setForeground(dashboard ? WHITE : SIDEBAR_TEXT);

        donationsNav.setBackground(donationsPage ? GREEN : DARK);
        donationsNav.setForeground(donationsPage ? WHITE : SIDEBAR_TEXT);

        accountNav.setBackground(accountPage ? GREEN : DARK);
        accountNav.setForeground(accountPage ? WHITE : SIDEBAR_TEXT);

        pageLayout.show(pagePanel, page);
        if (!accountPage) {
            loadDonations();
        }
    }

    private JPanel statCard(
            String title, JLabel value, String detail) {

        JPanel card = cardPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));

        JLabel label = new JLabel(title);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.BOLD, 10));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);

        value.setForeground(GREEN);
        value.setFont(new Font("SansSerif", Font.BOLD, 25));
        value.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel caption = new JLabel(detail);
        caption.setForeground(MUTED);
        caption.setFont(new Font("SansSerif", Font.PLAIN, 10));
        caption.setAlignmentX(Component.LEFT_ALIGNMENT);

        card.add(label);
        card.add(Box.createVerticalStrut(10));
        card.add(value);
        card.add(Box.createVerticalStrut(5));
        card.add(caption);

        return card;
    }

    private JPanel cardPanel() {

        JPanel panel = new JPanel();
        panel.setBackground(WHITE);
        panel.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(BORDER),
                        BorderFactory.createEmptyBorder(
                                12, 12, 12, 12)));
        return panel;
    }

    private void styleTable(JTable table) {

        table.setRowHeight(34);
        table.setFont(new Font("SansSerif", Font.PLAIN, 13));
        table.setForeground(DARK);
        table.setBackground(WHITE);
        table.setGridColor(new Color(235, 240, 235));
        table.setSelectionBackground(new Color(220, 239, 225));
        table.setSelectionForeground(DARK);
        table.setShowVerticalLines(false);
        table.setFillsViewportHeight(true);

        table.getTableHeader().setFont(
                new Font("SansSerif", Font.BOLD, 12));
        table.getTableHeader().setBackground(
                new Color(237, 243, 237));
        table.getTableHeader().setForeground(DARK);
        table.getTableHeader().setPreferredSize(
                new Dimension(0, 38));

        table.setAutoCreateRowSorter(true);
    }

    private void addField(
            JPanel panel, String labelText, JTextField field) {

        JPanel wrapper = new JPanel(new BorderLayout(0, 7));
        wrapper.setOpaque(false);

        JLabel label = new JLabel(labelText);
        label.setForeground(MUTED);
        label.setFont(new Font("SansSerif", Font.PLAIN, 12));

        wrapper.add(label, BorderLayout.NORTH);
        wrapper.add(field, BorderLayout.CENTER);
        panel.add(wrapper);
    }

    private void styleInput(JTextField field) {

        field.setFont(new Font("SansSerif", Font.PLAIN, 13));
        field.setPreferredSize(new Dimension(120, 38));
        field.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(
                                new Color(207, 219, 209)),
                        BorderFactory.createEmptyBorder(
                                8, 9, 8, 9)));
    }

    private JButton primaryButton(String text) {

        JButton button = new JButton(text);
        stylePrimaryButton(button);
        return button;
    }

    private void stylePrimaryButton(JButton button) {

        button.setBackground(GREEN);
        button.setForeground(WHITE);
        button.setFont(new Font("SansSerif", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setOpaque(true);
        button.setBorder(
                BorderFactory.createEmptyBorder(11, 16, 11, 16));
        button.setCursor(
                Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Load donations from the Java server
    private void loadDonations() {

        statusLabel.setText("Loading donations...");
        dashboardStatus.setText("Refreshing donation data...");

        new SwingWorker<List<DonationRow>, Void>() {

            @Override
            protected List<DonationRow> doInBackground()
                    throws Exception {

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(serverBase + "/api/donations"))
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new IllegalStateException(
                            "API returned HTTP " + response.statusCode());
                }

                return parseDonations(response.body());
            }

            @Override
            protected void done() {

                try {
                    donations = get();
                    updateTableAndStats();
                    applyDonationFilter();

                    String message = "Connected · "
                            + donations.size() + " donation(s)";

                    statusLabel.setText(message);
                    dashboardStatus.setText(message);

                } catch (Exception ex) {

                    statusLabel.setText("Could not load donations.");
                    dashboardStatus.setText(
                            "Could not load donation data. Is the Java server running?");
                }
            }

        }.execute();
    }

    private void updateTableAndStats() {

        tableModel.setRowCount(0);

        int available = 0;
        int claimed = 0;
        int completed = 0;
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
            } else if ("Claimed".equalsIgnoreCase(donation.status)) {
                claimed++;
            } else if ("Completed".equalsIgnoreCase(donation.status)) {
                completed++;
            }

            totalQuantity += donation.quantity;
        }

        totalValue.setText(String.valueOf(donations.size()));
        availableValue.setText(String.valueOf(available));
        claimedValue.setText(String.valueOf(claimed));
        completedValue.setText(String.valueOf(completed));
        quantityValue.setText(String.valueOf(totalQuantity));
    }

    private void submitDonation() {

        if (sessionCookie == null) {
            showError("You must be logged in as a DONOR to publish a donation.\nPlease go to the 'Account / Login' page first.");
            showPage("account");
            return;
        }

        if (!"DONOR".equalsIgnoreCase(currentUserRole)) {
            showError("Only accounts registered with the 'DONOR' role can create donations.\nYour current role is: " + currentUserRole);
            return;
        }

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
                + ",\"location\":\"" + escape(location)
                + "\"}";

        submitButton.setEnabled(false);
        statusLabel.setText("Submitting donation...");

        new SwingWorker<Integer, Void>() {

            @Override
            protected Integer doInBackground() throws Exception {

                var reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(serverBase + "/api/donations"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json));

                if (sessionCookie != null) {
                    reqBuilder.header("Cookie", sessionCookie);
                }

                HttpResponse<String> response = client.send(
                        reqBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                return response.statusCode();
            }

            @Override
            protected void done() {

                submitButton.setEnabled(true);

                try {
                    int status = get();

                    if (status != 201) {
                        showError(
                                "The server rejected the donation "
                                        + "(HTTP " + status + "). Ensure you are logged in as a Donor.");
                        return;
                    }

                    foodField.setText("");
                    quantityField.setText("");
                    locationField.setText("");

                    statusLabel.setText(
                            "Donation submitted successfully.");
                    loadDonations();

                } catch (Exception ex) {
                    showError(
                            "Unable to submit donation. Check server connectivity.");
                }
            }

        }.execute();
    }

    private void updateDonationStatus() {

        if (sessionCookie == null) {
            showError("Please log in via 'Account / Login' before modifying donation status.");
            showPage("account");
            return;
        }

        int selectedViewRow = donationsTable.getSelectedRow();

        if (selectedViewRow == -1) {
            showError("Please select a donation from the table first.");
            return;
        }

        int selectedModelRow =
                donationsTable.convertRowIndexToModel(selectedViewRow);

        String donationId = String.valueOf(
                tableModel.getValueAt(selectedModelRow, 0));

        String selectedStatus =
                (String) statusCombo.getSelectedItem();

        if (selectedStatus == null) {
            showError("Please select a status.");
            return;
        }

        String json = "{\"status\":\"" + escape(selectedStatus) + "\"}";

        updateStatusButton.setEnabled(false);
        statusLabel.setText("Updating donation status...");

        new SwingWorker<Integer, Void>() {

            @Override
            protected Integer doInBackground() throws Exception {

                var reqBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(serverBase + "/api/donations/" + donationId + "/status"))
                        .header("Content-Type", "application/json")
                        .PUT(HttpRequest.BodyPublishers.ofString(json));

                if (sessionCookie != null) {
                    reqBuilder.header("Cookie", sessionCookie);
                }

                HttpResponse<String> response = client.send(
                        reqBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                return response.statusCode();
            }

            @Override
            protected void done() {

                updateStatusButton.setEnabled(true);

                try {
                    int responseCode = get();

                    if (responseCode == 200) {
                        statusLabel.setText(
                                "Donation status updated successfully.");
                        loadDonations();
                    } else if (responseCode == 403) {
                        showError("Permission denied: Receivers can claim donations; Donors can complete or cancel their own.");
                    } else if (responseCode == 409) {
                        showError("Donation has already been claimed by another user.");
                        loadDonations();
                    } else {
                        showError("Status update failed (HTTP " + responseCode + ").");
                    }

                } catch (Exception ex) {
                    showError("Unable to update status. Check server connection.");
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
                        + "\"\\s*:\\s*"
                        + "(?:\"((?:\\\\.|[^\"\\\\])*)\"|(\\d+))");

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
                this,
                message,
                "FoodBridge",
                JOptionPane.ERROR_MESSAGE);
    }

    private static class DonationRow {

        final String id;
        final String donor;
        final String food;
        final int quantity;
        final String location;
        final String status;

        DonationRow(
                String id,
                String donor,
                String food,
                String quantity,
                String location,
                String status) {

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
                        UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }

            new SwingApp().setVisible(true);
        });
    }
}
