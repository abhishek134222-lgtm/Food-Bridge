
const API_URL = "http://localhost:8080/api/donations";

const donationList = document.getElementById("donations");
const message = document.getElementById("message");
const form = document.getElementById("donationForm");

function showMessage(text) {
    message.textContent = text;
}

function createDonationCard(donation) {
    const card = document.createElement("article");
    card.className = "donation-card";

    const title = document.createElement("h3");
    title.textContent = donation.food;

    const donor = document.createElement("p");
    donor.textContent = "Donor: " + donation.donor;

    const quantity = document.createElement("p");
    quantity.textContent = "Quantity: " + donation.quantity;

    const location = document.createElement("p");
    location.textContent = "Pickup: " + donation.location;

    const status = document.createElement("span");
    status.className = "status";
    status.textContent = donation.status;

    card.append(title, donor, quantity, location, status);
    return card;
}

async function loadDonations() {
    showMessage("Loading donations...");
    donationList.replaceChildren();

    try {
        const response = await fetch(API_URL);

        if (!response.ok) {
            throw new Error("Could not load donations.");
        }

        const donations = await response.json();

        donations.forEach(donation => {
            donationList.appendChild(createDonationCard(donation));
        });

        showMessage(
            donations.length
                ? `Showing ${donations.length} donation(s).`
                : "No donations available yet."
        );
    } catch (error) {
        showMessage(
            "Cannot connect to FoodBridge. Make sure the Java server is running."
        );
    }
}

form.addEventListener("submit", async event => {
    event.preventDefault();

    const data = {
        donor: form.elements.donor.value.trim(),
        food: form.elements.food.value.trim(),
        quantity: Number(form.elements.quantity.value),
        location: form.elements.location.value.trim()
    };

    if (!data.donor || !data.food || !data.location ||
        !Number.isInteger(data.quantity) || data.quantity <= 0) {
        showMessage("Please enter valid donation details.");
        return;
    }

    const submitButton = form.querySelector("button[type=submit]");
    submitButton.disabled = true;
    showMessage("Submitting donation...");

    try {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.error || "Donation submission failed.");
        }

        form.reset();
        await loadDonations();
        showMessage("Donation submitted successfully!");
    } catch (error) {
        showMessage(
            error.message === "Failed to fetch"
                ? "Cannot connect to the Java server. Check that it is running."
                : error.message
        );
    } finally {
        submitButton.disabled = false;
    }
});

loadDonations();
