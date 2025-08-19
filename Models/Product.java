package Models;

public class Product {
    private String productId; // unique ID
    private String productName;
    private String category;
    private double price;
    private int quantityInStock;
    private String branchId;

    /**
     * This 'branch' field indicates which branch carries this product.
     * For centralized management, we store all products in one list,
     * but filter by 'branch' when needed.
     */

    public Product(String productId, String name, String category,
            double price, int quantityInStock, String branch) {
        setProductId(productId);
        setProductName(name);
        setCategory(category);
        setPrice(price);
        setQuantityInStock(quantityInStock);
        setBranch(branch);
    }

    // Getters & Setters
    public String getProductId() { return productId; }
    public void setProductId(String productId) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("Product Id cannot be null or empty.");
        }
        this.productId = productId;
    }

    public String getProductName() { return productName; }
    public void setProductName(String productName) {
        if (productName == null || productName.isEmpty()) {
            throw new IllegalArgumentException("Product name cannot be null or empty.");
        }
        this.productName = productName;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) {
        if (category == null || category.isEmpty()) {
            throw new IllegalArgumentException("Category cannot be null or empty.");
        }
        this.category = category;
    }


    public double getPrice() { return price; }
    public void setPrice(double price) {
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        this.price = price;
    }

    public int getQuantityInStock() { return quantityInStock; }
    public void setQuantityInStock(int quantityInStock) {
        if (quantityInStock < 0) {
            throw new IllegalArgumentException("Quantity in stock cannot be negative.");
        }
        this.quantityInStock = quantityInStock;
    }

    public String getBranch() { return branchId; }
    public void setBranch(String branchId) {
        if (branchId == null || branchId.isEmpty()) {
            throw new IllegalArgumentException("Branch must be specified.");
        }
        this.branchId = branchId;
    }

    @Override
    public String toString() {
        return String.format("Product{id='%s', name='%s', category='%s', price=%.2f, stock=%d, branch='%s'}",
                productId, productName, category, price, quantityInStock, branchId);
    }

}
