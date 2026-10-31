package com.foodbridge.server;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private static String configuredJdbcUrl = null;
    private static String dbUser = null;
    private static String dbPassword = null;
    private static boolean driverAvailable = false;
    private static boolean isInitialized = false;

    static {
        try {
            Class.forName("org.postgresql.Driver");
            driverAvailable = true;
        } catch (ClassNotFoundException e) {
            driverAvailable = false;
        }
    }

    public static void main(String[] args) {
        init();
    }

    public static synchronized void init() {
        if (isInitialized) return;

        String dbUrl = System.getenv("DATABASE_URL");
        if (dbUrl == null || dbUrl.isBlank()) {
            dbUrl = System.getProperty("DATABASE_URL");
        }

        if (dbUrl != null && !dbUrl.isBlank() && driverAvailable) {
            parseConnectionConfig(dbUrl);
            try (Connection conn = getConnection()) {
                if (conn != null && !conn.isClosed()) {
                    System.out.println("Connected to Neon PostgreSQL database ("
                            + conn.getMetaData().getDatabaseProductVersion() + ").");
                    initSchema(conn);
                    seedInitialData(conn);
                }
            } catch (SQLException e) {
                System.err.println("Could not connect to Neon PostgreSQL: " + e.getMessage());
                System.err.println("Defaulting to local file storage.");
                configuredJdbcUrl = null;
            }
        } else {
            System.out.println("No DATABASE_URL configured or JDBC driver absent. Using local file storage.");
        }

        isInitialized = true;
    }

    public static boolean isConfigured() {
        if (!isInitialized) {
            init();
        }
        return configuredJdbcUrl != null && driverAvailable;
    }

    public static Connection getConnection() throws SQLException {
        if (configuredJdbcUrl == null) {
            throw new SQLException("Database is not configured. DATABASE_URL environment variable is missing.");
        }
        if (dbUser != null && dbPassword != null) {
            return DriverManager.getConnection(configuredJdbcUrl, dbUser, dbPassword);
        }
        return DriverManager.getConnection(configuredJdbcUrl);
    }

    private static void parseConnectionConfig(String raw) {
        try {
            String clean = raw.trim();

            if (clean.startsWith("jdbc:postgresql://")) {
                clean = clean.substring("jdbc:".length());
            } else if (clean.startsWith("postgres://")) {
                clean = "postgresql://" + clean.substring("postgres://".length());
            }

            URI uri = new URI(clean);

            if (uri.getUserInfo() != null) {
                String[] parts = uri.getUserInfo().split(":", 2);
                dbUser = parts[0];
                if (parts.length > 1) {
                    dbPassword = parts[1];
                }
            }

            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath();

            if (path == null || path.isBlank()) {
                path = "/neondb";
            }

            configuredJdbcUrl = "jdbc:postgresql://" + host + ":" + port + path + "?sslmode=require";

        } catch (Exception e) {
            // Fallback to basic string prefixing if URI parsing fails
            String fallback = raw.trim();
            if (fallback.startsWith("postgres://")) {
                fallback = "jdbc:postgresql://" + fallback.substring("postgres://".length());
            } else if (fallback.startsWith("postgresql://")) {
                fallback = "jdbc:postgresql://" + fallback.substring("postgresql://".length());
            } else if (!fallback.startsWith("jdbc:postgresql://")) {
                fallback = "jdbc:postgresql://" + fallback;
            }
            if (!fallback.contains("sslmode=")) {
                fallback += (fallback.contains("?") ? "&" : "?") + "sslmode=require";
            }
            configuredJdbcUrl = fallback;
        }
    }

    private static void initSchema(Connection conn) {
        String createUsersSql = """
            CREATE TABLE IF NOT EXISTS users (
                id BIGSERIAL PRIMARY KEY,
                full_name VARCHAR(100) NOT NULL,
                email VARCHAR(255) UNIQUE NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                role VARCHAR(20) NOT NULL CHECK (role IN ('DONOR', 'RECEIVER')),
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);
        """;

        String createDonationsSql = """
            CREATE TABLE IF NOT EXISTS donations (
                id BIGSERIAL PRIMARY KEY,
                owner_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                donor VARCHAR(100) NOT NULL,
                food VARCHAR(200) NOT NULL,
                quantity INT NOT NULL CHECK (quantity > 0),
                location VARCHAR(300) NOT NULL,
                status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'CLAIMED', 'COMPLETED', 'CANCELLED')),
                claimed_by_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
            );
            CREATE INDEX IF NOT EXISTS idx_donations_status ON donations (status);
            CREATE INDEX IF NOT EXISTS idx_donations_owner ON donations (owner_id);
        """;

        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createUsersSql);
            stmt.execute(createDonationsSql);
        } catch (SQLException e) {
            System.err.println("Schema auto-initialization warning: " + e.getMessage());
        }
    }

    private static void seedInitialData(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            if (rs.next() && rs.getInt(1) == 0) {
                stmt.execute("""
                    INSERT INTO users (id, full_name, email, password_hash, role)
                    VALUES (
                        1,
                        'test',
                        'test1@gmail.com',
                        '210000:k84n1U4P6Z7b+VP98JOYLQ==:jugafYjv8NoIfrSss4zQXJ9k4Gyb+uoZ0x7P0U159JY=',
                        'DONOR'
                    ) ON CONFLICT (email) DO NOTHING;
                    SELECT setval('users_id_seq', (SELECT COALESCE(MAX(id), 1) FROM users));
                """);
            }

            ResultSet rsDonations = stmt.executeQuery("SELECT COUNT(*) FROM donations");
            if (rsDonations.next() && rsDonations.getInt(1) == 0) {
                stmt.execute("""
                    INSERT INTO donations (id, owner_id, donor, food, quantity, location, status)
                    VALUES (
                        1,
                        1,
                        'test',
                        'Totato',
                        200,
                        'Panchkula',
                        'AVAILABLE'
                    ) ON CONFLICT (id) DO NOTHING;
                    SELECT setval('donations_id_seq', (SELECT COALESCE(MAX(id), 1) FROM donations));
                """);
            }
        } catch (SQLException e) {
            // Seeding is optional and idempotent
        }
    }
}
