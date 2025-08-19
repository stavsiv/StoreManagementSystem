////package Services;
////
////import Models.Product;
////
////import java.util.ArrayList;
////import java.util.List;
////
////public class ProductService {
////    private List<Product> productList;
////
////    public ProductService() {
////        this.productList = new ArrayList<>();
////    }
////
////    public List<Product> getAllProducts() {
////        return productList;
////    }
////
////    public void addProduct(Product p) {
////        productList.add(p);
////    }
////
////    public Product getProductById(String productId) {
////        for (Product p : productList) {
////            if (p.getProductId().equals(productId)) {
////                return p;
////            }
////        }
////        return null;
////    }
////
////    public boolean updateProduct(Product updatedProduct) {
////        for (int i = 0; i < productList.size(); i++) {
////            if (productList.get(i).getProductId().equals(updatedProduct.getProductId())) {
////                productList.set(i, updatedProduct);
////                return true;
////            }
////        }
////        return false;
////    }
////
////    public boolean removeProduct(String productId) {
////        return productList.removeIf(p -> p.getProductId().equals(productId));
////    }
////
////    public List<Product> listAllProducts() {
////        return new ArrayList<>(productList);
////    }
////
////    public List<Product> getProductsByBranch(String branchId) {
////        List<Product> result = new ArrayList<>();
////        for (Product p : productList) {
////            if (p.getBranch().equals(branchId)) {
////                result.add(p);
////            }package Services;
////
////import Models.Product;
////
////import java.util.ArrayList;
////import java.util.List;
////
////            public class ProductService {
////                private List<Product> productList;
////
////                public ProductService() {
////                    this.productList = new ArrayList<>();
////                }
////
////                public List<Product> getAllProducts() {
////                    return productList;
////                }
////
////                public void addProduct(Product p) {
////                    productList.add(p);
////                }
////
////                public Product getProductById(String productId) {
////                    for (Product p : productList) {
////                        if (p.getProductId().equals(productId)) {
////                            return p;
////                        }
////                    }
////                    return null;
////                }
////
////                public boolean updateProduct(Product updatedProduct) {
////                    for (int i = 0; i < productList.size(); i++) {
////                        if (productList.get(i).getProductId().equals(updatedProduct.getProductId())) {
////                            productList.set(i, updatedProduct);
////                            return true;
////                        }
////                    }
////                    return false;
////                }
////
////                public boolean removeProduct(String productId) {
////                    return productList.removeIf(p -> p.getProductId().equals(productId));
////                }
////
////                public List<Product> listAllProducts() {
////                    return new ArrayList<>(productList);
////                }
////
////                public List<Product> getProductsByBranch(String branchId) {
////                    List<Product> result = new ArrayList<>();
////                    for (Product p : productList) {
////                        if (p.getBranch().equals(branchId)) {
////                            result.add(p);
////                        }
////                    }
////                    return result;
////                }
////
////                public String getFormattedProductList(List<Product> products) {
////                    StringBuilder sb = new StringBuilder();
////
////                    // Header
////                    sb.append(String.format("%-10s | %-20s | %-15s | %-10s | %-10s | %-10s\n",
////                            "Product Id", "Name", "Category", "Price", "Stock", "Branch"));
////                    sb.append("----------------------------------------------------------------------------------------\n");
////
////                    // Rows
////                    for (Product p : products) {
////                        sb.append(String.format("%-10s | %-20s | %-15s | %-10.2f | %-10d | %-10s\n",
////                                p.getProductId(), p.getProductName(), p.getCategory(),
////                                p.getPrice(), p.getQuantityInStock(), p.getBranch()));
////                    }
////
////                    return sb.toString();
////                }
////
////                public String getFormattedAllProducts() {
////                    return getFormattedProductList(productList);
////                }
////            }
////
////        }
////        return result;
////    }
////
////    public String getFormattedProductList(List<Product> products) {
////        StringBuilder sb = new StringBuilder();
////
////        // Header
////        sb.append(String.format("%-10s | %-20s | %-15s | %-10s | %-10s | %-10s\n",
////                "Product Id", "Name", "Category", "Price", "Stock", "Branch"));
////        sb.append("----------------------------------------------------------------------------------------\n");
////
////        // Rows
////        for (Product p : products) {
////            sb.append(String.format("%-10s | %-20s | %-15s | %-10.2f | %-10d | %-10s\n",
////                    p.getProductId(), p.getProductName(), p.getCategory(),
////                    p.getPrice(), p.getQuantityInStock(), p.getBranch()));
////        }
////
////        return sb.toString();
////    }
////
////    public String getFormattedAllProducts() {
////        return getFormattedProductList(productList);
////    }
////}
//
//package Services;
//
//import Models.Product;
//import java.util.ArrayList;
//import java.util.List;
//
//public class ProductService {
//    private List<Product> productList;
//
//    public ProductService() {
//        this.productList = new ArrayList<>();
//    }
//
//                    public List<Product> getAllProducts() {
//                    return productList;
//                }
//
//    // Add a new product or update stock if it already exists in the branch
//    public void addOrUpdateProduct(Product p) {
//        for (Product existing : productList) {
//            if (existing.getProductId().equals(p.getProductId()) &&
//                    existing.getBranch().equalsIgnoreCase(p.getBranch())) {
//                // Update stock
//                existing.setQuantityInStock(existing.getQuantityInStock() + p.getQuantityInStock());
//                return;
//            }
//        }
//
//        // Validate price and stock
//        if (p.getPrice() < 0) throw new IllegalArgumentException("Price cannot be negative.");
//        if (p.getQuantityInStock() < 0) throw new IllegalArgumentException("Stock cannot be negative.");
//
//        productList.add(p);
//    }
//
//    public Product getProductByIdAndBranch(String productId, String branchId) {
//        for (Product p : productList) {
//            if (p.getProductId().equals(productId) && p.getBranch().equalsIgnoreCase(branchId)) {
//                return p;
//            }
//        }
//        return null;
//    }
//
//    public boolean removeProduct(String productId, String branchId) {
//        return productList.removeIf(p -> p.getProductId().equals(productId)
//                && p.getBranch().equalsIgnoreCase(branchId));
//    }
//
//    public List<Product> listAllProducts() {
//        return new ArrayList<>(productList);
//    }
//
//    public List<Product> getProductsByBranch(String branchId) {
//        List<Product> result = new ArrayList<>();
//        for (Product p : productList) {
//            if (p.getBranch().equalsIgnoreCase(branchId)) {
//                result.add(p);
//            }
//        }
//        return result;
//    }
//
//                    public String getFormattedProductList(List<Product> products) {
//                    StringBuilder sb = new StringBuilder();
//
//                    // Header
//                    sb.append(String.format("%-10s | %-20s | %-15s | %-10s | %-10s | %-10s\n",
//                            "Product Id", "Name", "Category", "Price", "Stock", "Branch"));
//                    sb.append("----------------------------------------------------------------------------------------\n");
//
//                    // Rows
//                    for (Product p : products) {
//                        sb.append(String.format("%-10s | %-20s | %-15s | %-10.2f | %-10d | %-10s\n",
//                                p.getProductId(), p.getProductName(), p.getCategory(),
//                                p.getPrice(), p.getQuantityInStock(), p.getBranch()));
//                    }
//
//                    return sb.toString();
//                }
//
//                public String getFormattedAllProducts() {
//                    return getFormattedProductList(productList);
//                }
//            }
//
//
package Services;

import Models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductService {
    private List<Product> productList;

    public ProductService() {
        this.productList = new ArrayList<>();
    }

    /**
     * Add a new product to a branch, or update stock if it already exists.
     */
    public void addOrUpdateProduct(Product p) {
        for (Product existing : productList) {
            if (existing.getProductId().equals(p.getProductId()) &&
                    existing.getBranch().equalsIgnoreCase(p.getBranch())) {
                // Update stock
                existing.setQuantityInStock(existing.getQuantityInStock() + p.getQuantityInStock());
                return;
            }
        }

        productList.add(p);
    }

    /**
     * Retrieve product by ID (ignores branch) for sales operation.
     * Use caution if same product exists in multiple branches.
     */
    public Product getProductById(String productId) {
        for (Product p : productList) {
            if (p.getProductId().equals(productId)) {
                return p;
            }
        }
        return null;
    }

    public Product getProductByIdAndBranch(String productId, String branchId) {
        for (Product p : productList) {
            if (p.getProductId().equals(productId) &&
                    p.getBranch().equalsIgnoreCase(branchId)) {
                return p;
            }
        }
        return null;
    }

    public boolean removeProduct(String productId, String branchId) {
        return productList.removeIf(p -> p.getProductId().equals(productId)
                && p.getBranch().equalsIgnoreCase(branchId));
    }

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

    public String formatAllProducts() {
        return formatProductList(productList);
    }
}
