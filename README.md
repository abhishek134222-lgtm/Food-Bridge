# FoodBridge

FoodBridge is a food donation management platform that connects food donors with NGOs and community receivers to minimize food waste. It includes a responsive website, a Java Swing desktop application, and a shared Java backend powered by Neon PostgreSQL.

---

## Architecture

```text
  FoodBridge Website (Vercel)          Java Swing Desktop App
              │                                   │
              └───────────────┬───────────────────┘
                              │ HTTPS / JSON
                              ▼
                      Java Backend (Render)
                              │
                              │ JDBC / SSL
                              ▼
                     Neon PostgreSQL (Cloud)
```

---

## Features

- **User Authentication**: Secure registration and login with PBKDF2 password hashing.
- **Role-Based Access**: Specialized portals for **Food Donors** and **Food Receivers**.
- **Donation Lifecycle**: Track donations through `AVAILABLE` → `CLAIMED` → `COMPLETED` statuses.
- **Race Condition Prevention**: Database-level atomic updates prevent duplicate claims.
- **Dual Client Support**: Both the website and the Swing desktop app synchronize through the same API.
- **Dual Storage Engine**: Connects to Neon PostgreSQL in production, with graceful local fallback.

---

## Tech Stack

| Layer | Technology |
| :--- | :--- |
| **Backend** | Java 21 (`com.sun.net.httpserver`), REST APIs |
| **Database** | Neon PostgreSQL (Serverless JDBC) |
| **Desktop App** | Java Swing |
| **Website** | HTML5, CSS3, Vanilla JavaScript |
| **Hosting** | Render (Backend), Vercel (Frontend), Neon (Database) |

---

## Project Structure

```text
Food-Bridge/
├── database/
│   └── schema.sql        # PostgreSQL schema & initial data
├── src/com/foodbridge/
│   ├── app/
│   │   ├── IntegrationTest.java  # End-to-end API test runner
│   │   └── SwingApp.java         # Desktop Swing application
│   └── server/
│       ├── AuthSessions.java     # Token & session management
│       ├── AuthStore.java        # User data access
│       ├── Database.java         # PostgreSQL connection manager
│       ├── DonationStore.java    # Donation data access
│       └── Server.java           # HTTP server & REST routes
├── web/                          # Responsive website files
├── Dockerfile                    # Container configuration for Render
├── pom.xml                       # Maven build configuration
└── vercel.json                   # Deployment routing for Vercel
```

---

## Running Locally

### 1. Compile

```powershell
javac -d out (Get-ChildItem -Path src -Filter *.java -Recurse | Select-Object -ExpandProperty FullName)
```

### 2. Start the Backend Server

```powershell
java -cp out com.foodbridge.server.Server
```
*Website runs locally at: `http://localhost:8080`*

### 3. Start the Desktop App

```powershell
java -cp out com.foodbridge.app.SwingApp
```

### 4. Run Integration Tests

```powershell
java -cp out com.foodbridge.app.IntegrationTest
```

---

## Neon PostgreSQL Setup

1. Create a free database at [neon.tech](https://neon.tech).
2. Copy your connection string from the Neon dashboard.
3. Set the `DATABASE_URL` environment variable:

```powershell
$env:DATABASE_URL = "postgresql://user:password@ep-sample.neon.tech/foodbridge?sslmode=require"
java -cp out com.foodbridge.server.Server
```
*The server automatically initializes the database schema and initial records on first startup.*

---
##Deployment
##Backend (Render)
```
Create a new Web Service on render.com connected to your GitHub repository.
Select Docker runtime.
Add the following Environment Variables:
PORT: 8080
DATABASE_URL: (Your Neon PostgreSQL connection string)
CORS_ALLOWED_ORIGINS: https://<your-frontend>.onrender.com,http://localhost:8080
Deploy the backend and copy your live Render backend URL.
Frontend (Render)
Create a new Static Site on render.com connected to the same GitHub repository.
Set the Root Directory to web (if your frontend files are inside the web folder).
Set the Build Command according to your frontend setup (leave empty if no build is required).
Set the Publish Directory to . (if the Root Directory is already set to web).
Update the backend API URL in web/app.js:
javascript
window.FOODBRIDGE_API_URL = "https://<your-backend>.onrender.com";

Deploy the frontend.
Final Configuration
Backend: Render Web Service
Frontend: Render Static Site
Database: Neon PostgreSQL
Hosting Platform: Render only
```
---

## License

MIT License. Open source and educational.
