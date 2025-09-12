package Models;

import Exceptions.CustomExceptions;

public class Product {
    private String productId;
    private String productName;
    private String category;
    private double price;
    private int quantityInStock;
    private String branchId;

    // Constructor
    public Product(String productId, String name, String category, double price, int quantityInStock, String branch)
            throws CustomExceptions.ProductException {
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

    public void setProductId(String productId) throws CustomExceptions.ProductException {
        if (productId == null || productId.isEmpty())
            throw new CustomExceptions.InvalidProductIdException("Product ID cannot be null or empty.");
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) throws CustomExceptions.ProductException {
        if (productName == null || productName.isEmpty())
            throw new CustomExceptions.EmptyProductNameException("Product name cannot be null or empty.");
        this.productName = productName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) throws CustomExceptions.ProductException {
        if (category == null || category.isEmpty())
            throw new CustomExceptions.EmptyProductCategoryException("Category cannot be null or empty.");
        this.category = category;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) throws CustomExceptions.ProductException {
        if (price < 0) throw new CustomExceptions.NegativeProductPriceException("Price cannot be negative.");
        this.price = price;
    }

    public int getQuantityInStock() {
        return quantityInStock;
    }

    public void setQuantityInStock(int quantityInStock) throws CustomExceptions.ProductException {
        if (quantityInStock < 0)
            throw new CustomExceptions.NegativeProductQuantityException("Quantity in stock cannot be negative.");
        this.quantityInStock = quantityInStock;
    }

    public String getBranch() {
        return branchId;
    }

    public void setBranch(String branchId) throws CustomExceptions.ProductException {
        if (branchId == null || branchId.isEmpty())
            throw new CustomExceptions.EmptyProductBranchException("Branch must be specified.");
        this.branchId = branchId;
    }

    @Override
    public String toString() {
        return String.format("Product{id='%s', name='%s', category='%s', price=%.2f, stock=%d, branch='%s'}",
                productId, productName, category, price, quantityInStock, branchId);
    }
}
