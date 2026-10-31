-- ============================================================
-- FoodBridge — Neon PostgreSQL Database Schema
-- ============================================================

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    full_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('DONOR', 'RECEIVER')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users (email);

-- 2. Donations Table
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

-- ============================================================
-- Initial Data Migration (Preserving Existing Baseline Records)
-- ============================================================

-- Baseline User (from data/users.dat)
INSERT INTO users (id, full_name, email, password_hash, role)
VALUES (
    1,
    'test',
    'test1@gmail.com',
    '210000:k84n1U4P6Z7b+VP98JOYLQ==:jugafYjv8NoIfrSss4zQXJ9k4Gyb+uoZ0x7P0U159JY=',
    'DONOR'
)
ON CONFLICT (email) DO NOTHING;

-- Reset sequence to ensure subsequent registrations get next available ID
SELECT setval('users_id_seq', COALESCE((SELECT MAX(id) FROM users), 1));

-- Baseline Donation (from data/donations.dat)
INSERT INTO donations (id, owner_id, donor, food, quantity, location, status)
VALUES (
    1,
    1,
    'test',
    'Totato',
    200,
    'Panchkula',
    'AVAILABLE'
)
ON CONFLICT (id) DO NOTHING;

-- Reset sequence to ensure subsequent donations get next available ID
SELECT setval('donations_id_seq', COALESCE((SELECT MAX(id) FROM donations), 1));
