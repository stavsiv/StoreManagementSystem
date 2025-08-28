package Models;

import Exceptions.CustomExceptions;

public class NewCustomer extends Customer {

    private static final double DISCOUNT_RATE = 0.00; // No discount for new customers

    // Constructor
    public NewCustomer(String fullName, String customerId, String phoneNumber) throws CustomExceptions.CustomerException {
        super(fullName, customerId, phoneNumber);
    }

    @Override
    public String getCustomerType() {
        return "New";
    }

    @Override
    public double calculateFinalPrice(double totalPrice) {
        return totalPrice * (1 - DISCOUNT_RATE);
    }

}
