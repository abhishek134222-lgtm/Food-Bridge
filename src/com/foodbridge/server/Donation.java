
package com.foodbridge.server;

public class Donation {
    private final int id;
    private final String donor;
    private final String food;
    private final int quantity;
    private final String location;
    private final String status;

    public Donation(int id, String donor, String food,
                    int quantity, String location, String status) {
        this.id = id;
        this.donor = donor;
        this.food = food;
        this.quantity = quantity;
        this.location = location;
        this.status = status;
    }

    public int getId() {
        return id;
    }

    public String getDonor() {
        return donor;
    }

    public String getFood() {
        return food;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getLocation() {
        return location;
    }

    public String getStatus() {
        return status;
    }
}
