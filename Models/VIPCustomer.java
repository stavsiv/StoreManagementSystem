package Models;

public class VIPCustomer extends Customer {

    private static final double DISCOUNT_RATE = 0.30;

    // Constructor
    public VIPCustomer(String fullName, String customerId, String phoneNumber) {
        super(fullName, customerId, phoneNumber);
    }

    @Override
    public String getType() {
        return "VIP";
    }

    @Override
    public double calculateFinalPrice(double totalPrice) {
        return totalPrice * (1 - DISCOUNT_RATE);
    }

}
