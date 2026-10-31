// FoodBridge Client-side Application Logic
const API_BASE = window.FOODBRIDGE_API_URL || "https://foodbridge-backend-wnpw.onrender.com";
const API_URL = `${API_BASE}/api/donations`;

const donationList = document.getElementById("donations");
const message = document.getElementById("message");
const form = document.getElementById("donationForm");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const refreshButton = document.getElementById("refreshButton");
const submitButton = document.getElementById("submitButton");

let allDonations = [];
let isLoading = false;
let loggedInUser = null;

function showMessage(text, type = "") {
  message.textContent = text;
  message.className = "message";

  if (type) {
    message.classList.add(type);
  }
}

function normalizeStatus(status) {
  return String(status || "AVAILABLE")
    .trim()
    .toUpperCase();
}

function getStatusLabel(status) {
  const normalized = normalizeStatus(status);

  const labels = {
    AVAILABLE: "Available",
    CLAIMED: "Claimed",
    COMPLETED: "Completed",
    CANCELLED: "Cancelled",
  };

  return labels[normalized] || normalized;
}

function getFoodEmoji(food) {
  const text = String(food || "").toLowerCase();

  if (/fruit|apple|banana|mango|orange|grape/.test(text)) return "🍎";
  if (/vegetable|veggie|carrot|potato|spinach|salad/.test(text)) return "🥗";
  if (/bread|cake|biscuit|cookie|pastry|roti/.test(text)) return "🍞";
  if (/rice|biryani|pasta|noodle|pulao/.test(text)) return "🍚";
  if (/milk|juice|water|drink|tea|coffee/.test(text)) return "🥤";
  if (/meal|food|lunch|dinner|cooked|curry|thali/.test(text)) return "🍱";
  return "🍲";
}

function createDetail(icon, label, value) {
  const detail = document.createElement("div");
  detail.className = "card-detail";

  const detailIcon = document.createElement("span");
  detailIcon.className = "detail-icon";
  detailIcon.setAttribute("aria-hidden", "true");
  detailIcon.textContent = icon;

  const content = document.createElement("div");

  const detailLabel = document.createElement("span");
  detailLabel.className = "detail-label";
  detailLabel.textContent = label;

  const detailValue = document.createElement("span");
  detailValue.className = "detail-value";
  detailValue.textContent = value;

  content.append(detailLabel, detailValue);
  detail.append(detailIcon, content);

  return detail;
}

function createDonationCard(donation) {
  const card = document.createElement("article");
  card.className = "donation-card";

  const top = document.createElement("div");
  top.className = "card-top";

  const foodSymbol = document.createElement("span");
  foodSymbol.className = "food-symbol";
  foodSymbol.setAttribute("aria-hidden", "true");
  foodSymbol.textContent = getFoodEmoji(donation.food);

  const status = document.createElement("span");
  const normalizedStatus = normalizeStatus(donation.status);

  status.className = "status";

  const statusClasses = {
    AVAILABLE: "status-available",
    CLAIMED: "status-claimed",
    COMPLETED: "status-completed",
    CANCELLED: "status-cancelled",
  };

  status.classList.add(statusClasses[normalizedStatus] || "status-available");
  status.textContent = getStatusLabel(normalizedStatus);

  top.append(foodSymbol, status);

  const title = document.createElement("h3");
  title.textContent = donation.food || "Food donation";

  const donor = document.createElement("p");
  donor.className = "card-donor";
  donor.textContent = "Shared by " + (donation.donor || "Community donor");

  const divider = document.createElement("div");
  divider.className = "card-divider";

  const quantity = createDetail(
    "♧",
    "QUANTITY",
    String(donation.quantity ?? "Not specified") + " servings / units",
  );

  const location = createDetail(
    "⌖",
    "PICKUP LOCATION",
    String(donation.location || "Not specified"),
  );

  card.append(top, title, donor, divider, quantity, location);

  if (normalizeStatus(donation.status) === "AVAILABLE") {
    const claimButton = document.createElement("button");
    claimButton.type = "button";
    claimButton.className = "button button-primary claim-button";
    claimButton.style.width = "100%";
    claimButton.style.marginTop = "14px";
    claimButton.textContent = "Claim Donation →";

    claimButton.addEventListener("click", async () => {
      if (claimButton.disabled) return;

      if (!loggedInUser) {
        if (confirm("You must be logged in as an authorized Food Receiver to claim donations.\n\nGo to the Login / Register page now?")) {
          window.location.href = "auth.html";
        }
        return;
      }

      if (loggedInUser.role !== "RECEIVER") {
        alert("Only registered Food Receivers can claim donations. Your current account role is: " + loggedInUser.role);
        return;
      }

      const confirmed = window.confirm(
        `Confirm Donation Claim:\n\nFood: ${donation.food}\nQuantity: ${donation.quantity}\nPickup: ${donation.location}\n\nDo you want to reserve this donation?`
      );

      if (!confirmed) return;

      claimButton.disabled = true;
      claimButton.textContent = "Verifying claim...";

      try {
        const response = await fetch(`${API_URL}/${donation.id}/status`, {
          method: "PUT",
          headers: {
            "Content-Type": "application/json",
          },
          credentials: "include",
          body: JSON.stringify({ status: "CLAIMED" }),
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
          throw new Error(
            result.error || "Claim failed. Please refresh and try again.",
          );
        }

        await loadDonations();
        showMessage("Donation claimed successfully! Please coordinate pickup.", "success");
      } catch (error) {
        showMessage(
          error.message === "Failed to fetch"
            ? "Cannot connect to the Java server."
            : error.message,
          "error",
        );

        claimButton.disabled = false;
        claimButton.textContent = "Claim Donation →";
      }
    });

    card.appendChild(claimButton);
  }

  return card;
}

function showEmptyState(title, description) {
  const empty = document.createElement("div");
  empty.className = "empty-state";

  const icon = document.createElement("div");
  icon.className = "empty-icon";
  icon.setAttribute("aria-hidden", "true");
  icon.textContent = "🥗";

  const heading = document.createElement("h3");
  heading.textContent = title;

  const detail = document.createElement("p");
  detail.textContent = description;

  empty.append(icon, heading, detail);
  donationList.appendChild(empty);
}

function renderDonations() {
  const searchTerm = searchInput.value.trim().toLowerCase();
  const selectedStatus = statusFilter.value;

  const filteredDonations = allDonations.filter((donation) => {
    const searchableText = [
      donation.food,
      donation.donor,
      donation.quantity,
      donation.location,
      donation.status,
    ]
      .map((value) => String(value ?? ""))
      .join(" ")
      .toLowerCase();

    const matchesSearch = searchableText.includes(searchTerm);
    const matchesStatus =
      selectedStatus === "ALL" ||
      normalizeStatus(donation.status) === selectedStatus;

    return matchesSearch && matchesStatus;
  });

  donationList.replaceChildren();

  if (filteredDonations.length === 0) {
    if (allDonations.length === 0) {
      showEmptyState(
        "No donations yet",
        "Be the first to share food with your community.",
      );
    } else {
      showEmptyState(
        "No matching donations",
        "Try a different search term or status filter.",
      );
    }
  } else {
    filteredDonations.forEach((donation) => {
      donationList.appendChild(createDonationCard(donation));
    });
  }

  if (allDonations.length > 0) {
    showMessage(
      `Showing ${filteredDonations.length} of ${allDonations.length} donation(s).`,
    );
  } else {
    showMessage("No donations have been listed yet.");
  }
}

async function loadDonations() {
  if (isLoading) return;

  isLoading = true;
  refreshButton.disabled = true;
  refreshButton.innerHTML = '<span aria-hidden="true">↻</span> Loading...';

  showMessage("Loading donations...");
  donationList.replaceChildren();

  const loading = document.createElement("div");
  loading.className = "empty-state";

  const loadingText = document.createElement("p");
  loadingText.textContent = "Please wait while we load donations...";

  loading.appendChild(loadingText);
  donationList.appendChild(loading);

  try {
    const response = await fetch(API_URL, { credentials: "include" });

    if (!response.ok) {
      throw new Error("Could not load donations.");
    }

    const donations = await response.json();

    if (!Array.isArray(donations)) {
      throw new Error("The server returned an unexpected response.");
    }

    allDonations = donations;
    renderDonations();
  } catch (error) {
    donationList.replaceChildren();

    showEmptyState(
      "Unable to load donations",
      "Please check that the Java server is running and try again.",
    );

    showMessage(
      "Cannot connect to FoodBridge. Check the Java server and try refreshing.",
      "error",
    );
  } finally {
    isLoading = false;
    refreshButton.disabled = false;
    refreshButton.innerHTML = '<span aria-hidden="true">↻</span> Refresh';
  }
}

// Check logged in user to update navbar and prefill donor fields
async function checkUserNav() {
  try {
    const res = await fetch(`${API_BASE}/api/me`, { credentials: "include" });
    if (res.ok) {
      loggedInUser = await res.json();
      const navLinks = document.querySelector(".nav-links");
      if (navLinks && loggedInUser && loggedInUser.fullName) {
        const dashPage = loggedInUser.role === "DONOR" ? "donor.html" : "receiver.html";
        navLinks.innerHTML = `
          <a href="${dashPage}"><strong>${loggedInUser.fullName}</strong> (${loggedInUser.role})</a>
          <a href="#donations-section">Donations</a>
          <a class="nav-cta" href="${dashPage}">Go to Dashboard ↗</a>
          <button id="logoutNavBtn" class="button button-secondary" style="min-height:36px; padding:6px 14px; font-size:12px;">Logout</button>
        `;

        document.getElementById("logoutNavBtn")?.addEventListener("click", async () => {
          await fetch(`${API_BASE}/api/logout`, { method: "POST", credentials: "include" });
          window.location.reload();
        });

        // Prefill donor form if logged in as donor
        const donorInput = document.getElementById("donor");
        if (donorInput && loggedInUser.role === "DONOR") {
          donorInput.value = loggedInUser.fullName;
        }
      }
    }
  } catch (e) {
    // Guest visitor
  }
}

searchInput.addEventListener("input", renderDonations);
statusFilter.addEventListener("change", renderDonations);
refreshButton.addEventListener("click", loadDonations);

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (submitButton.disabled) return;

  if (!loggedInUser) {
    if (confirm("You must be logged in as a registered Food Donor to publish a food donation.\n\nGo to the Login / Register page now?")) {
      window.location.href = "auth.html";
    }
    return;
  }

  if (loggedInUser.role !== "DONOR") {
    alert("Only registered Food Donors can list donations. Your account role is: " + loggedInUser.role);
    return;
  }

  const data = {
    donor: form.elements.donor.value.trim() || loggedInUser.fullName,
    food: form.elements.food.value.trim(),
    quantity: Number(form.elements.quantity.value),
    location: form.elements.location.value.trim(),
  };

  if (
    !data.donor ||
    !data.food ||
    !data.location ||
    !Number.isInteger(data.quantity) ||
    data.quantity <= 0 ||
    data.quantity > 100000
  ) {
    showMessage("Please enter valid donation details.", "error");
    return;
  }

  submitButton.disabled = true;
  submitButton.textContent = "Submitting donation...";
  showMessage("Submitting your donation...");

  try {
    const response = await fetch(API_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      credentials: "include",
      body: JSON.stringify(data),
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(
        result.error || "Donation submission failed. Please try again.",
      );
    }

    form.reset();
    if (loggedInUser && loggedInUser.role === "DONOR") {
      document.getElementById("donor").value = loggedInUser.fullName;
    }

    await loadDonations();

    showMessage("Donation submitted successfully! Thank you for sharing.", "success");

    document.getElementById("donations-section").scrollIntoView({
      behavior: "smooth",
      block: "start",
    });
  } catch (error) {
    showMessage(
      error.message === "Failed to fetch"
        ? "Cannot connect to the Java server. Check that it is running."
        : error.message,
      "error",
    );
  } finally {
    submitButton.disabled = false;
    submitButton.innerHTML = "Submit donation <span>→</span>";
  }
});

// Initialize on page load
checkUserNav();
loadDonations();
