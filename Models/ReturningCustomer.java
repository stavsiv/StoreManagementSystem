package Models;

public class ReturningCustomer extends Customer {

    private static final double DISCOUNT_RATE = 0.10;

    // Constructor
    public ReturningCustomer(String fullName, String customerId, String phoneNumber) {
        super(fullName, customerId, phoneNumber);
    }

    @Override
    public String getType() {
        return "Returning";
    }

    @Override
    public double calculateFinalPrice(double totalPrice) {
        return totalPrice * (1 - DISCOUNT_RATE);
    }

}
