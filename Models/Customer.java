package Models;

import java.util.HashSet;
import java.util.Set;

/**
 * Abstract base class representing a Customer.
 * Provides common fields, validation, and abstract methods for different
 * customer types.
 */
public abstract class Customer {
    protected String fullName;
    protected String customerId; // must be 9 digits, unique
    protected String phoneNumber; // must be 10 digits

    // Static sets to ensure uniqueness across all customers
    private static final Set<String> existingCustomerIds = new HashSet<>();

    // Constructor
    public Customer(String fullName, String customerId, String phoneNumber) {
        setCustomerName(fullName);
        setCustomerId(customerId);
        setPhoneNumber(phoneNumber);
    }

    // Getters and Setters with Validation
    public String getCustomerName() {
        return fullName;
    }

    public void setCustomerName(String name) {
        if (name == null || !name.matches("[A-Za-z ]{2,}")) {
            throw new IllegalArgumentException(
                    "Customer name must contain only letters and spaces, and be at least 2 characters long.");
        }
        this.fullName = name;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String id) {
        if (id == null || !id.matches("\\d{9}")) {
            throw new IllegalArgumentException("Customer ID must be exactly 9 digits.");
        }
        if (existingCustomerIds.contains(id)) {
            throw new IllegalArgumentException("Customer ID already exists: " + id);
        }
        this.customerId = id;
        existingCustomerIds.add(id);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phone) {
        if (phone == null || !phone.matches("\\d{10}")) {
            throw new IllegalArgumentException("Phone number must contain exactly 10 digits.");
        }

        this.phoneNumber = phone;
    }

    public abstract String getCustomerType();

    public abstract double calculateFinalPrice(double totalPrice);

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "fullName='" + fullName + '\'' +
                ", customerId='" + customerId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }
}
