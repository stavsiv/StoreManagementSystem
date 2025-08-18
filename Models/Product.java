package Models;

import java.util.HashSet;
import java.util.Set;

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
        setProductId(productId, branch);
        setProductName(name);
        setCategory(category);
        setPrice(price);
        setQuantityInStock(quantityInStock);
        setBranch(branch);
    }

    // Static sets for uniqueness checks
    private static final Set<String> existingProductIds = new HashSet<>();

    // Getters & Setters
    public String getProductId() {
        return productId;
    }

//    public void setProductId(String productId) {
//        if (productId == null || productId.isEmpty()) {
//            throw new IllegalArgumentException("Product ID cannot be null or empty.");
//        }
//        if (existingProductIds.contains(productId)) {
//            throw new IllegalArgumentException("Product ID already exists: " + productId);
//        }
//        this.productId = productId;
//        existingProductIds.add(productId);
//    }

    public void setProductId(String productId, String branchId) {
        if (productId == null || productId.isEmpty()) {
            throw new IllegalArgumentException("Product ID cannot be null or empty.");
        }
        // unique
        String key = productId + "-" + branchId;
        if (existingProductIds.contains(key)) {
            throw new IllegalArgumentException("Product ID already exists in this branch: " + key);
        }
        this.productId = productId;
        existingProductIds.add(key);
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
        if (price < 0) {
            throw new IllegalArgumentException("Price cannot be negative.");
        }
        this.price = price;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public void setQuantityInStock(int quantityInStock) {
        if (quantityInStock < 0) {
            throw new IllegalArgumentException("Quantity in stock cannot be negative.");
        }
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
