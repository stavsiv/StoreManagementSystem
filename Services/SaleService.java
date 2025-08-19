////package Services;
////
////import Models.Customer;
////import Models.Product;
////
////import java.time.LocalDateTime;
////import java.util.ArrayList;
////import java.util.List;
////
////public class SaleService {
////
////    private ProductService productService;
////
////    // We keep a static list of all sales
////    private static List<SaleRecord> allSales = new ArrayList<>();
////
////    public SaleService(ProductService productService) {
////        this.productService = productService;
////    }
////
////    /**
////     * Purchases a product for the given customer.
////     *
////     * @param customer  the customer making the purchase
////     * @param productId the ID of the product
////     * @param quantity  the quantity to purchase
////     * @return the final price after discounts, or -1 if an error occurred
////     */
////    public double purchaseProduct(Customer customer, String productId, int quantity) {
////        Product product = productService.getProductByIdAndBranch(productId);
////        if (product == null) {
////            System.out.println("Product not found!");
////            return -1;
////        }
////
////        // 2) Check stock
////        if (product.getQuantityInStock() < quantity) {
////            System.out.println("Not enough stock for product " + productId);
////            return -1;
////        }
////
////        // 3) Calculate total price before discount
////        double totalPrice = product.getPrice() * quantity;
////
////        // 4) Apply discount based on customer type (polymorphism)
////        double finalPrice = customer.calculateFinalPrice(totalPrice);
////
////        // 5) Deduct stock
////        product.setQuantityInStock(product.getQuantityInStock() - quantity);
////
////        // 6) Record the sale in our static list
////        SaleRecord record = new SaleRecord(
////                product.getProductId(),
////                product.getProductName(),
////                product.getCategory(),
////                product.getBranch(),
////                quantity,
////                finalPrice,
////                LocalDateTime.now());
////        allSales.add(record);
////
////        // 7) Return final price
////        return finalPrice;
////    }
////
////    /**
////     * Returns all recorded sales.
////     */
////    public static List<SaleRecord> getAllSales() {
////        return allSales;
////    }
////
////    public static class SaleRecord {
////        private String productId;
////        private String productName;
////        private String productType;
////        private String branch;
////        private int quantity;
////        private double finalPrice;
////        private LocalDateTime saleTime;
////
////        public SaleRecord(String productId, String productName, String productType, String branch,
////                int quantity, double finalPrice, LocalDateTime saleTime) {
////            this.productId = productId;
////            this.productName = productName;
////            this.productType = productType;
////            this.branch = branch;
////            this.quantity = quantity;
////            this.finalPrice = finalPrice;
////            this.saleTime = saleTime;
////        }
////
////        public String getProductId() {
////            return productId;
////        }
////
////        public String getProductName() {
////            return productName;
////        }
////
////        public String getProductType() {
////            return productType;
////        }
////
////        public String getBranch() {
////            return branch;
////        }
////
////        public int getQuantity() {
////            return quantity;
////        }
////
////        public double getFinalPrice() {
////            return finalPrice;
////        }
////
////        public LocalDateTime getSaleTime() {
////            return saleTime;
////        }
////    }
////}
//
//package Services;
//
//import Models.Customer;
//import Models.Product;
//
//import java.time.LocalDateTime;
//import java.util.ArrayList;
//import java.util.List;
//
//public class SaleService {
//
//    private ProductService productService;
//    private List<SaleRecord> allSales; // Instance variable now
//
//    public SaleService(ProductService productService) {
//        this.productService = productService;
//        this.allSales = new ArrayList<>();
//    }
//
//    /**
//     * Purchases a product for the given customer.
//     * Throws exceptions on invalid conditions.
//     */
//    public double purchaseProduct(Customer customer, String productId, int quantity) {
//        Product product = productService.getProductById(productId);
//        if (product == null) {
//            throw new IllegalArgumentException("Product not found: " + productId);
//        }
//
//        if (quantity <= 0) {
//            throw new IllegalArgumentException("Quantity must be positive.");
//        }
//
//        if (product.getQuantityInStock() < quantity) {
//            throw new IllegalArgumentException("Not enough stock for product " + productId);
//        }
//
//        // Calculate final price with discount
//        double totalPrice = product.getPrice() * quantity;
//        double finalPrice = customer.calculateFinalPrice(totalPrice);
//
//        // Deduct stock
//        product.setQuantityInStock(product.getQuantityInStock() - quantity);
//
//        // Record the sale
//        SaleRecord record = new SaleRecord(
//                product.getProductId(),
//                product.getProductName(),
//                product.getCategory(),
//                product.getBranch(),
//                quantity,
//                finalPrice,
//                LocalDateTime.now()
//        );
//        allSales.add(record);
//
//        return finalPrice;
//    }
//
//    /**
//     * Returns a copy of all sales.
//     */
//    public List<SaleRecord> getAllSales() {
//        return new ArrayList<>(allSales);
//    }
//
//    public static class SaleRecord {
//        private final String productId;
//        private final String productName;
//        private final String productType;
//        private final String branch;
//        private final int quantity;
//        private final double finalPrice;
//        private final LocalDateTime saleTime;
//
//        public SaleRecord(String productId, String productName, String productType, String branch,
//                          int quantity, double finalPrice, LocalDateTime saleTime) {
//            this.productId = productId;
//            this.productName = productName;
//            this.productType = productType;
//            this.branch = branch;
//            this.quantity = quantity;
//            this.finalPrice = finalPrice;
//            this.saleTime = saleTime;
//        }
//
//        public String getProductId() { return productId; }
//        public String getProductName() { return productName; }
//        public String getProductType() { return productType; }
//        public String getBranch() { return branch; }
//        public int getQuantity() { return quantity; }
//        public double getFinalPrice() { return finalPrice; }
//        public LocalDateTime getSaleTime() { return saleTime; }
//    }
//}
package Services;

import Models.Customer;
import Models.Product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class SaleService {

    private ProductService productService;
    private static List<SaleRecord> allSales;

    public SaleService(ProductService productService) {
        this.productService = productService;
        this.allSales = new ArrayList<>();
    }

    /**
     * Sell a product to a customer.
     * Deducts stock and records sale.
     */
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
