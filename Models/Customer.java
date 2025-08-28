package Models;

import Exceptions.CustomExceptions;

/**
 * Abstract base class representing a Customer.
 * Provides common fields, validation, and abstract methods for different
 * customer types.
 */
public abstract class Customer {
    protected String fullName;
    protected String customerId; // must be 9 digits, unique
    protected String phoneNumber; // must be 10 digits

    // Constructor
    public Customer(String fullName, String customerId, String phoneNumber) throws CustomExceptions.CustomerException {
        setCustomerName(fullName);
        setCustomerId(customerId);
        setPhoneNumber(phoneNumber);
    }

    // Getters & Setters
    public String getCustomerName() {
        return fullName;
    }

    public void setCustomerName(String fullName) throws CustomExceptions.CustomerException {
        if (fullName == null || fullName.isBlank())
            throw new CustomExceptions.EmptyCustomerNameException("Full name cannot be empty");
        this.fullName = fullName;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) throws CustomExceptions.CustomerException {
        if (customerId == null || !customerId.matches("\\d{9}"))
            throw new CustomExceptions.InvalidCustomerIdException("Customer ID must be exactly 9 digits.");
        this.customerId = customerId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) throws CustomExceptions.CustomerException {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}"))
            throw new CustomExceptions.InvalidCustomerPhoneException("Phone number must be exactly 10 digits.");
        this.phoneNumber = phoneNumber;
    }

    // Abstract Methods
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
