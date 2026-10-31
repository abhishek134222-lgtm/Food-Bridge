# FoodBridge

> **Food Donation & Rescue Management System**  
> Connecting food donors with NGOs and community receivers to eliminate food waste.

---

## 📌 Architecture Overview

Both the responsive website and the Java Swing desktop client communicate with the **same Java Backend API** and store data permanently in **Neon PostgreSQL**.

```
                ┌─────────────────────────────────┐
                │       FoodBridge Website        │
                │        (Hosted on Vercel)       │
                └────────────────┬────────────────┘
                                 │
                            HTTPS / JSON
                                 ▼
┌─────────────────────────┐   ┌─────────────────────────┐
│     Java Swing App      ├──►│       Java Backend      │
│     (Desktop Client)    │   │    (Hosted on Render)   │
└─────────────────────────┘   └────────────┬────────────┘
                                           │
                             JDBC / SSL (sslmode=require)
                                           ▼
                              ┌─────────────────────────┐
                              │     Neon PostgreSQL     │
                              │    (Cloud Database)     │
                              └─────────────────────────┘
```

---

## 🛠️ Technology Stack

- **Backend**: Native Java 21 HTTP Server (`com.sun.net.httpserver.HttpServer`), REST APIs, PBKDF2 Password Hashing, Dual Cookie & Bearer Token Authentication.
- **Database**: [Neon PostgreSQL](https://neon.tech/) with JDBC connection management, automatic schema initialization, and local file fallback.
- **Desktop Application**: Java Swing with multi-view CardLayout, real-time filtering, background `SwingWorker` threads, and account management.
- **Website**: Modern responsive HTML5, CSS3 design system, and Vanilla JavaScript with CORS support.
- **Hosting**:
  - Website: [Vercel](https://vercel.com/)
  - Backend: [Render](https://render.com/) (Free Web Service tier)
  - Database: [Neon](https://neon.tech/) (Free Serverless PostgreSQL)

---

## 📂 Project Structure

```text
FoodBridge/
├── database/
│   └── schema.sql             # PostgreSQL schema with constraints, indexes & migration seeds
├── src/com/foodbridge/
│   ├── app/
│   │   ├── IntegrationTest.java # Automated end-to-end API test runner (14 passed)
│   │   ├── Main.java          # Console banner
│   │   └── SwingApp.java      # Java Swing desktop client
│   └── server/
│       ├── AuthSessions.java  # Dual session & token management with TTL
│       ├── AuthStore.java     # User authentication (PostgreSQL + file fallback)
│       ├── Database.java      # Neon JDBC connection manager & schema auto-init
│       ├── Donation.java      # Donation data model
│       ├── DonationStore.java # Donation lifecycle CRUD (PostgreSQL + file fallback)
│       └── Server.java        # REST API router, CORS filter & static file server
├── web/
│   ├── app.js                 # Frontend application logic with auth checks
│   ├── auth.html              # Unified Sign In & Register portal
│   ├── donor.html             # Donor dashboard with stats & donation management
│   ├── index.html             # Public landing page with community donations
│   ├── receiver.html          # Receiver portal with claim donation workflow
│   └── style.css              # Global design system & responsive styling
├── .env.example               # Template for environment variables
├── Dockerfile                 # Multi-stage production container for Render
├── pom.xml                    # Maven build configuration with PostgreSQL JDBC
├── vercel.json                # Vercel deployment routing configuration
└── README.md                  # Project documentation
```

---

## 🚀 Quick Start (Running Locally)

### 1. Compile the Project

Open PowerShell in the project directory:

```powershell
# Compile all Java files into the 'out' directory
javac -d out (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
```

### 2. Run the Java Server

```powershell
java -cp out com.foodbridge.server.Server
```

Open your browser at: **`http://localhost:8080`**

### 3. Run the Java Swing Desktop Application

In a separate PowerShell window:

```powershell
java -cp out com.foodbridge.app.SwingApp
```

### 4. Run the Automated Integration Tests

```powershell
java -cp out com.foodbridge.app.IntegrationTest
```

Expected output:
```text
==========================================
   FOODBRIDGE AUTOMATED INTEGRATION TEST  
==========================================
Target: http://localhost:8080

  [PASS] GET /api/health responds with UP
  [PASS] POST /api/register creates DONOR
  [PASS] POST /api/register rejects duplicate email (400)
  [PASS] POST /api/login rejects invalid credentials (401)
  [PASS] GET /api/me returns authenticated user profile
  [PASS] POST /api/register creates RECEIVER
  [PASS] POST /api/donations rejects RECEIVER creation (403)
  [PASS] POST /api/donations creates donation for DONOR (201)
  [PASS] GET /api/donations lists donations publicly (200)
  [PASS] PUT /api/donations/{id}/status allows RECEIVER to claim (200)
  [PASS] Duplicate claim is prevented with HTTP 409 Conflict
  [PASS] Donor marks claimed donation as COMPLETED (200)
  [PASS] POST /api/logout logs out user (200)
  [PASS] Logged out session is rejected with HTTP 401

==========================================
   RESULTS: 14 PASSED, 0 FAILED
==========================================
```

---

## 🗄️ Setting Up Neon PostgreSQL

1. Go to [https://neon.tech](https://neon.tech) and create a free account.
2. Click **Create Project**, name it **`foodbridge`**, and select the region closest to you.
3. In your Neon Project Dashboard, copy your **Connection String**. It looks like:
   ```text
   postgresql://foodbridge_owner:sample_password@ep-example.us-east-2.aws.neon.tech/foodbridge?sslmode=require
   ```
4. To test locally with your Neon database, set the environment variable in PowerShell before starting the server:
   ```powershell
   $env:DATABASE_URL = "postgresql://foodbridge_owner:sample_password@ep-example.us-east-2.aws.neon.tech/foodbridge?sslmode=require"
   java -cp out com.foodbridge.server.Server
   ```
   *The server will print:*
   ```text
   Connected to Neon PostgreSQL database.
   ```
   *(Note: The server automatically creates the tables and seeds initial data on startup, so no manual SQL is required!)*

---

## ☁️ Online Deployment Guide

### Step 1: Deploy Backend to Render (Free Web Service)

1. Sign in to [Render](https://render.com/).
2. Click **New +** → **Web Service**.
3. Connect your GitHub repository (`Food-Bridge`).
4. Select **Docker** as the Runtime (Render will automatically detect the provided `Dockerfile`).
5. Choose the **Free** instance type.
6. Add the following **Environment Variables** in the Render dashboard:
   - `PORT`: `8080`
   - `DATABASE_URL`: *(Your Neon PostgreSQL connection string)*
   - `CORS_ALLOWED_ORIGINS`: `https://<your-vercel-app-name>.vercel.app,http://localhost:8080`
7. Click **Deploy Web Service**.
8. Once deployed, copy your backend URL (e.g. `https://foodbridge-backend.onrender.com`).
9. Verify by opening `https://foodbridge-backend.onrender.com/api/health` in your browser. It should return `{"status":"UP", ...}`.

---

### Step 2: Deploy Website to Vercel (Free Hosting)

1. Sign in to [Vercel](https://vercel.com/).
2. Click **Add New...** → **Project** and select your `Food-Bridge` GitHub repository.
3. In project settings, Vercel will automatically read `vercel.json`.
4. Click **Deploy**.
5. Once your website is live (e.g., `https://foodbridge.vercel.app`), configure your frontend to use your Render backend:
   - Open your deployed website.
   - You can configure the backend URL in `web/app.js` or define `window.FOODBRIDGE_API_URL = "https://foodbridge-backend.onrender.com";`.

---

### Step 3: Connect Desktop Swing App to the Online Backend

1. Start your desktop application:
   ```powershell
   java -cp out com.foodbridge.app.SwingApp
   ```
2. In the bottom of the sidebar, click the **⚙ Server** button.
3. Enter your live Render URL (e.g., `https://foodbridge-backend.onrender.com`).
4. You can now register, sign in, list donations, and claim donations on your desktop app and see the changes reflected live on the website!

---

## 🔒 Security Summary

- **Passwords**: Never stored in plaintext. Hashed using PBKDF2WithHmacSHA256 with 210,000 iterations and cryptographic salts.
- **CORS Protection**: Origin-specific credentials handling preventing malicious cross-site scripting.
- **Race Condition Prevention**: Database-level conditional atomic updates prevent two receivers from claiming the same donation simultaneously.
- **Zero Committed Secrets**: `.gitignore` ensures real database credentials, `.env` files, and local logs are never committed to GitHub.

---

## 📄 License
This project is open-source and created for educational and community food-rescue purposes.
