package Models;

public class TransactionItem {
    private Product product;
    private int quantity;

    public TransactionItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
    }

    public double getTotalPrice() {
        return quantity * product.getPrice();
    }

    // Getters
    public Product getProduct() {
        return product;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getPricePerUnit() {
        return product.getPrice();
    }

    @Override
    public String toString() {
        return "TransactionItem{" +
                "product=" + product.getProductName() +
                ", quantity=" + quantity +
                ", pricePerUnit=" + getPricePerUnit() +
                ", total=" + getTotalPrice() +
                '}';
    }
}
