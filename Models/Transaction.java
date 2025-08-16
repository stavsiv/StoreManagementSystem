package Models;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class Transaction {
    /*
     * This class represents a transaction in the store.
     * It contains details about the transaction, including the customer, employee,
     * branch,
     * date, items in the transaction, and methods to calculate total prices.
     */
    private String transactionId;
    private Customer customer;
    private Employee employee;
    private Branch branch;
    private LocalDateTime date;
    private List<TransactionItem> items;
    private double totalPriceBeforeDiscount;
    private double finalPrice;

    // Constructor
    public Transaction(String transactionId, Customer customer, Employee employee, Branch branch) {
        this.transactionId = transactionId;
        this.customer = customer;
        this.employee = employee;
        this.branch = branch;
        this.date = LocalDateTime.now();
        this.items = new ArrayList<>();
        this.totalPriceBeforeDiscount = 0.0;
        this.finalPrice = 0.0;
    }

    // Getters & Setters
    public String getTransactionId() {
        return transactionId;
    }

    public Customer getCustomer() {
        return customer;
    }

    public Employee getEmployee() {
        return employee;
    }

    public Branch getBranch() {
        return branch;
    }

    public LocalDateTime getDate() {
        return date;
    }

    public List<TransactionItem> getItems() {
        return items;
    }

    public double getTotalPriceBeforeDiscount() {
        return totalPriceBeforeDiscount;
    }

    public double getFinalPrice() {
        return finalPrice;
    }

    // Methods
    public void addItem(Product product, int quantity) {
        if (product.getQuantityInStock() < quantity) {
            throw new IllegalArgumentException("Not enough stock for product: " + product.getProductName());
        }

        TransactionItem item = new TransactionItem(product, quantity);
        items.add(item);

        // update total price before discount
        totalPriceBeforeDiscount += item.getTotalPrice();

        // update product stock
        product.setQuantityInStock(product.getQuantityInStock() - quantity);

        // Recalculate final price based on customer discount
        recalculateFinalPrice();
    }

    private void recalculateFinalPrice() {
        if (customer != null) {
            this.finalPrice = customer.calculateFinalPrice(totalPriceBeforeDiscount);
        } else {
            this.finalPrice = totalPriceBeforeDiscount;
        }
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", customer=" + (customer != null ? customer.getCustomerName() : "null") +
                ", customerId=" + (customer != null ? customer.getCustomerId() : "null") +
                ", employee=" + (employee != null ? employee.getEmployeeName() : "null") +
                ", employeeId=" + (employee != null ? employee.getEmployeeId() : "null") +
                ", branch=" + (branch != null ? branch.getBranchName() : "null") +
                ", date=" + date +
                ", items=" + items +
                ", totalPriceBeforeDiscount=" + totalPriceBeforeDiscount +
                ", finalPrice=" + finalPrice +
                '}';
    }
}
