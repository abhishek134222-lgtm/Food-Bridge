package com.foodbridge.app;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

public class IntegrationTest {

    private static final String BASE_URL = "http://localhost:8080";
    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition, String details) {
        if (condition) {
            System.out.println("  [PASS] " + name);
            passed++;
        } else {
            System.out.println("  [FAIL] " + name + " -> " + details);
            failed++;
        }
    }

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("   FOODBRIDGE AUTOMATED INTEGRATION TEST  ");
        System.out.println("==========================================");
        System.out.println("Target: " + BASE_URL + "\n");

        CookieManager donorCookies = new CookieManager();
        HttpClient donorClient = HttpClient.newBuilder()
                .cookieHandler(donorCookies)
                .build();

        CookieManager receiverCookies = new CookieManager();
        HttpClient receiverClient = HttpClient.newBuilder()
                .cookieHandler(receiverCookies)
                .build();

        HttpClient anonClient = HttpClient.newHttpClient();

        try {
            // 1. Health check
            HttpRequest healthReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/health"))
                    .GET()
                    .build();
            HttpResponse<String> healthRes = anonClient.send(healthReq, HttpResponse.BodyHandlers.ofString());
            check("GET /api/health responds with UP",
                    healthRes.statusCode() == 200 && healthRes.body().contains("\"status\":\"UP\""),
                    "HTTP " + healthRes.statusCode() + ": " + healthRes.body());

            // 2. Register Donor
            String donorEmail = "donor_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String regDonorJson = "{\"fullName\":\"Donor Test\",\"email\":\"" + donorEmail + "\",\"password\":\"secret123\",\"role\":\"DONOR\"}";
            HttpRequest regDonorReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(regDonorJson))
                    .build();
            HttpResponse<String> regDonorRes = donorClient.send(regDonorReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/register creates DONOR",
                    regDonorRes.statusCode() == 201 && regDonorRes.body().contains("\"role\":\"DONOR\""),
                    "HTTP " + regDonorRes.statusCode() + ": " + regDonorRes.body());

            // 3. Duplicate Email Rejection
            HttpResponse<String> dupRes = donorClient.send(regDonorReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/register rejects duplicate email (400)",
                    dupRes.statusCode() == 400 && dupRes.body().contains("already exists"),
                    "HTTP " + dupRes.statusCode() + ": " + dupRes.body());

            // 4. Login with wrong password
            String badLoginJson = "{\"email\":\"" + donorEmail + "\",\"password\":\"wrongPass\"}";
            HttpRequest badLoginReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(badLoginJson))
                    .build();
            HttpResponse<String> badLoginRes = anonClient.send(badLoginReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/login rejects invalid credentials (401)",
                    badLoginRes.statusCode() == 401,
                    "HTTP " + badLoginRes.statusCode() + ": " + badLoginRes.body());

            // 5. Current user check (/api/me)
            HttpRequest meReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/me"))
                    .GET()
                    .build();
            HttpResponse<String> meRes = donorClient.send(meReq, HttpResponse.BodyHandlers.ofString());
            check("GET /api/me returns authenticated user profile",
                    meRes.statusCode() == 200 && meRes.body().contains(donorEmail),
                    "HTTP " + meRes.statusCode() + ": " + meRes.body());

            // 6. Register Receiver
            String receiverEmail = "receiver_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
            String regReceiverJson = "{\"fullName\":\"Receiver Test\",\"email\":\"" + receiverEmail + "\",\"password\":\"secret123\",\"role\":\"RECEIVER\"}";
            HttpRequest regReceiverReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/register"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(regReceiverJson))
                    .build();
            HttpResponse<String> regReceiverRes = receiverClient.send(regReceiverReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/register creates RECEIVER",
                    regReceiverRes.statusCode() == 201 && regReceiverRes.body().contains("\"role\":\"RECEIVER\""),
                    "HTTP " + regReceiverRes.statusCode() + ": " + regReceiverRes.body());

            // 7. Role Authorization: Receiver cannot create donation
            String donationJson = "{\"food\":\"Fresh Rice & Curry\",\"quantity\":50,\"location\":\"Sector 5 Community Center\"}";
            HttpRequest receiverPostReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(donationJson))
                    .build();
            HttpResponse<String> receiverPostRes = receiverClient.send(receiverPostReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/donations rejects RECEIVER creation (403)",
                    receiverPostRes.statusCode() == 403,
                    "HTTP " + receiverPostRes.statusCode() + ": " + receiverPostRes.body());

            // 8. Donor creates donation
            HttpRequest donorPostReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(donationJson))
                    .build();
            HttpResponse<String> donorPostRes = donorClient.send(donorPostReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/donations creates donation for DONOR (201)",
                    donorPostRes.statusCode() == 201 && donorPostRes.body().contains("Fresh Rice & Curry"),
                    "HTTP " + donorPostRes.statusCode() + ": " + donorPostRes.body());

            // Extract created ID
            String respBody = donorPostRes.body();
            int idIndex = respBody.indexOf("\"id\":");
            String idStr = "1";
            if (idIndex != -1) {
                int start = idIndex + 5;
                int end = respBody.indexOf(",", start);
                if (end == -1) end = respBody.indexOf("}", start);
                idStr = respBody.substring(start, end).trim();
            }
            int createdId = Integer.parseInt(idStr);

            // 9. Public listing
            HttpRequest listReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations"))
                    .GET()
                    .build();
            HttpResponse<String> listRes = anonClient.send(listReq, HttpResponse.BodyHandlers.ofString());
            check("GET /api/donations lists donations publicly (200)",
                    listRes.statusCode() == 200 && listRes.body().contains("Fresh Rice & Curry"),
                    "HTTP " + listRes.statusCode() + ": " + listRes.body());

            // 10. Receiver claims donation
            String claimJson = "{\"status\":\"CLAIMED\"}";
            HttpRequest claimReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations/" + createdId + "/status"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(claimJson))
                    .build();
            HttpResponse<String> claimRes = receiverClient.send(claimReq, HttpResponse.BodyHandlers.ofString());
            check("PUT /api/donations/{id}/status allows RECEIVER to claim (200)",
                    claimRes.statusCode() == 200 && claimRes.body().contains("CLAIMED"),
                    "HTTP " + claimRes.statusCode() + ": " + claimRes.body());

            // 11. Duplicate claim prevention (Conflict 409)
            HttpResponse<String> dupClaimRes = receiverClient.send(claimReq, HttpResponse.BodyHandlers.ofString());
            check("Duplicate claim is prevented with HTTP 409 Conflict",
                    dupClaimRes.statusCode() == 409,
                    "HTTP " + dupClaimRes.statusCode() + ": " + dupClaimRes.body());

            // 12. Donor marks claimed donation as COMPLETED
            String completeJson = "{\"status\":\"COMPLETED\"}";
            HttpRequest completeReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/donations/" + createdId + "/status"))
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(completeJson))
                    .build();
            HttpResponse<String> completeRes = donorClient.send(completeReq, HttpResponse.BodyHandlers.ofString());
            check("Donor marks claimed donation as COMPLETED (200)",
                    completeRes.statusCode() == 200 && completeRes.body().contains("COMPLETED"),
                    "HTTP " + completeRes.statusCode() + ": " + completeRes.body());

            // 13. Logout
            HttpRequest logoutReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/api/logout"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> logoutRes = donorClient.send(logoutReq, HttpResponse.BodyHandlers.ofString());
            check("POST /api/logout logs out user (200)",
                    logoutRes.statusCode() == 200,
                    "HTTP " + logoutRes.statusCode() + ": " + logoutRes.body());

            // 14. Verify revoked session cannot access /api/me
            HttpResponse<String> afterLogoutMe = donorClient.send(meReq, HttpResponse.BodyHandlers.ofString());
            check("Logged out session is rejected with HTTP 401",
                    afterLogoutMe.statusCode() == 401,
                    "HTTP " + afterLogoutMe.statusCode() + ": " + afterLogoutMe.body());

        } catch (Exception e) {
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n==========================================");
        System.out.println("   RESULTS: " + passed + " PASSED, " + failed + " FAILED");
        System.out.println("==========================================");

        if (failed > 0) {
            System.exit(1);
        }
    }
}
