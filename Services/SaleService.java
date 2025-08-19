package Services;

import Models.Customer;
import Models.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SaleService {

    /**
     * Sell a product to a customer.
     * Deducts stock and records sale.
     */

    private ProductService productService;
    private static List<SaleRecord> allSales;

    // Constructor
    public SaleService(ProductService productService) {
        this.productService = productService;
        this.allSales = new ArrayList<>();
    }

    public double sellProduct(Customer customer, String productId, String branchId, int quantity) {
        Product product = productService.getProductByIdAndBranch(productId, branchId);
        if (product == null)
            throw new IllegalArgumentException("Product not found in branch " + branchId);

        if (quantity <= 0)
            throw new IllegalArgumentException("Quantity must be positive.");
        if (product.getQuantityInStock() < quantity)
            throw new IllegalArgumentException("Not enough stock for product " + productId);

        double totalPrice = product.getPrice() * quantity;
        double finalPrice = customer.calculateFinalPrice(totalPrice);

        product.setQuantityInStock(product.getQuantityInStock() - quantity);
        allSales.add(new SaleRecord(
                product.getProductId(),
                product.getProductName(),
                product.getCategory(),
                product.getBranch(),
                quantity,
                finalPrice,
                LocalDateTime.now()
        ));

        return finalPrice;
    }


    public static List<SaleRecord> getAllSales() {
        return new ArrayList<>(allSales);
    }

    public static class SaleRecord {
        private final String productId;
        private final String productName;
        private final String productType;
        private final String branch;
        private final int quantity;
        private final double finalPrice;
        private final LocalDateTime saleTime;

        public SaleRecord(String productId, String productName, String productType, String branch,
                          int quantity, double finalPrice, LocalDateTime saleTime) {
            this.productId = productId;
            this.productName = productName;
            this.productType = productType;
            this.branch = branch;
            this.quantity = quantity;
            this.finalPrice = finalPrice;
            this.saleTime = saleTime;
        }

        public String getProductId() { return productId; }
        public String getProductName() { return productName; }
        public String getProductType() { return productType; }
        public String getBranch() { return branch; }
        public int getQuantity() { return quantity; }
        public double getFinalPrice() { return finalPrice; }
        public LocalDateTime getSaleTime() { return saleTime; }
    }
}
