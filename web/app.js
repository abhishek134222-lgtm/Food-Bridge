const API_URL = "http://localhost:8080/api/donations";

const donationList = document.getElementById("donations");
const message = document.getElementById("message");
const form = document.getElementById("donationForm");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const refreshButton = document.getElementById("refreshButton");
const submitButton = document.getElementById("submitButton");

let allDonations = [];
let isLoading = false;

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

  if (/fruit|apple|banana|mango|orange|grape/.test(text)) {
    return "🍎";
  }

  if (/vegetable|veggie|carrot|potato|spinach|salad/.test(text)) {
    return "🥗";
  }

  if (/bread|cake|biscuit|cookie|pastry/.test(text)) {
    return "🍞";
  }

  if (/rice|biryani|pasta|noodle/.test(text)) {
    return "🍚";
  }

  if (/milk|juice|water|drink/.test(text)) {
    return "🥤";
  }

  if (/meal|food|lunch|dinner|cooked|curry/.test(text)) {
    return "🍱";
  }

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
    String(donation.quantity ?? "Not specified"),
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
    claimButton.textContent = "Claim Donation →";

    claimButton.addEventListener("click", async () => {
      if (claimButton.disabled) return;

      const confirmed = window.confirm(
        "Confirm Donation Claim\n\n" +
          "Food: " +
          donation.food +
          "\n" +
          "Quantity: " +
          donation.quantity +
          "\n" +
          "Pickup location: " +
          donation.location +
          "\n\n" +
          "Are you sure you want to claim this donation?",
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
          body: JSON.stringify({ status: "CLAIMED" }),
        });

        const result = await response.json().catch(() => ({}));

        if (!response.ok) {
          throw new Error(
            result.error || "Claim failed. Please refresh and try again.",
          );
        }

        await loadDonations();
        showMessage("Donation claimed successfully!", "success");
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
  if (isLoading) {
    return;
  }

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
    const response = await fetch(API_URL);

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

searchInput.addEventListener("input", renderDonations);
statusFilter.addEventListener("change", renderDonations);
refreshButton.addEventListener("click", loadDonations);

form.addEventListener("submit", async (event) => {
  event.preventDefault();

  if (submitButton.disabled) {
    return;
  }

  const data = {
    donor: form.elements.donor.value.trim(),
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
      body: JSON.stringify(data),
    });

    const result = await response.json().catch(() => ({}));

    if (!response.ok) {
      throw new Error(
        result.error || "Donation submission failed. Please try again.",
      );
    }

    form.reset();

    await loadDonations();

    showMessage("Donation submitted successfully!", "success");

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

loadDonations();
