
package com.foodbridge.server;

import java.util.ArrayList;
import java.util.List;

public class DonationStore {

    private static final List<Donation> donations = new ArrayList<>();
    private static int nextId = 1;

    static {
        donations.add(new Donation(
            nextId++, "Community Kitchen",
            "Cooked meals", 30, "Ludhiana", "Available"
        ));
    }

    public static synchronized List<Donation> getAll() {
        return new ArrayList<>(donations);
    }

    public static synchronized Donation add(
            String donor, String food, int quantity, String location) {

        Donation donation = new Donation(
            nextId++, donor, food, quantity, location, "Available"
        );

        donations.add(donation);
        return donation;
    }
}
