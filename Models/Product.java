package Models;

public class Product {
    private String productId;
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
    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String name) {
        this.productName = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public void setQuantityInStock(int quantityInStock) {
        this.quantityInStock = quantityInStock;
    }

    public String getBranch() {
        return branchId;
    }

    public void setBranch(String branch) {
        if (branch == null || branch.isEmpty()) {
            throw new IllegalArgumentException("Branch must be specified for a product.");
        }
        this.branchId = branch;
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId='" + productId + '\'' +
                ", name='" + productName + '\'' +
                ", category='" + category + '\'' +
                ", price=" + price +
                ", quantityInStock=" + quantityInStock +
                ", branch='" + branchId + '\'' +
                '}';
    }
}
