package Services;

import Models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductService {

    /**
     * Get, Add or Update a new product to a branch, or update stock if it already exists.
     */

    private List<Product> productList;

    // Constructor
    public ProductService() {
        this.productList = new ArrayList<>();
    }

    public void addOrUpdateProduct(Product p) {
        for (Product existing : productList) {
            if (existing.getProductId().equals(p.getProductId()) && existing.getBranch().equalsIgnoreCase(p.getBranch())) {
                existing.setQuantityInStock(existing.getQuantityInStock() + p.getQuantityInStock());
                return;
            }
        }
        productList.add(p);
    }

//    /**
//     * Retrieve product by ID (ignores branch) for sales operation.
//     * Use caution if same product exists in multiple branches.
//     */
//    public Product getProductById(String productId) {
//        for (Product p : productList) {
//            if (p.getProductId().equals(productId)) {
//                return p;
//            }
//        }
//        return null;
//    }

    public Product getProductByIdAndBranch(String productId, String branchId) {
        for (Product p : productList) {
            if (p.getProductId().equals(productId) &&
                    p.getBranch().equalsIgnoreCase(branchId)) {
                return p;
            }
        }
        return null;
    }

//    public boolean removeProduct(String productId, String branchId) {
//        return productList.removeIf(p -> p.getProductId().equals(productId)
//                && p.getBranch().equalsIgnoreCase(branchId));
//    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(productList);
    }

    public List<Product> getProductsByBranch(String branchId) {
        List<Product> result = new ArrayList<>();
        for (Product p : productList) {
            if (p.getBranch().equalsIgnoreCase(branchId)) {
                result.add(p);
            }
        }
        return result;
    }

    public String formatProductList(List<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-10s | %-20s | %-15s | %-10s | %-10s | %-10s\n",
                "Product Id", "Name", "Category", "Price", "Stock", "Branch"));
        sb.append("----------------------------------------------------------------------------------------\n");
        for (Product p : products) {
            sb.append(String.format("%-10s | %-20s | %-15s | %-10.2f | %-10d | %-10s\n",
                    p.getProductId(), p.getProductName(), p.getCategory(),
                    p.getPrice(), p.getQuantityInStock(), p.getBranch()));
        }
        return sb.toString();
    }

//    public String formatAllProducts() {
//        return formatProductList(productList);
//    }
}
