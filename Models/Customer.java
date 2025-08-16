package Models;

//import java.util.Objects;

public abstract class Customer {
    protected String fullName;
    protected String customerId;
    protected String phoneNumber;

    // Constructor
    public Customer(String fullName, String customerId, String phoneNumber) {
        this.fullName = fullName;
        this.customerId = customerId;
        this.phoneNumber = phoneNumber;
    }

    // Getters & Setters
    public String getName() {
        return fullName;
    }

    public void setName(String name) {
        this.fullName = name;
    }

    public String getId() {
        return customerId;
    }

    public void setId(String id) {
        this.customerId = id;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phone) {
        this.phoneNumber = phone;
    }

    // Abstract methods for discount calculation

    public abstract String getType();

    public abstract double calculateFinalPrice(double totalPrice);

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{" +
                "fullName='" + fullName + '\'' +
                ", id='" + customerId + '\'' +
                ", phone='" + phoneNumber + '\'' +
                '}';
    }

    // @Override
    // public boolean equals(Object o) {
    // if (this == o) return true;
    // if (!(o instanceof Customer)) return false;
    // Customer customer = (Customer) o;
    // return Objects.equals(name, customer.name) && Objects.equals(email,
    // customer.email);
    // }

    // @Override
    // public int hashCode() {
    // return Objects.hash(name, email);
    // }

}
