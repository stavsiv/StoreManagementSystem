package Services;

import Models.Product;

import java.util.ArrayList;
import java.util.List;

public class ProductService {
    private List<Product> productList;

    public ProductService() {
        this.productList = new ArrayList<>();
    }

    public List<Product> getAllProducts() {
        return productList;
    }

    public void addProduct(Product p) {
        productList.add(p);
    }

    public Product getProductById(String productId) {
        for (Product p : productList) {
            if (p.getProductId().equals(productId)) {
                return p;
            }
        }
        return null;
    }

    public boolean updateProduct(Product updatedProduct) {
        for (int i = 0; i < productList.size(); i++) {
            if (productList.get(i).getProductId().equals(updatedProduct.getProductId())) {
                productList.set(i, updatedProduct);
                return true;
            }
        }
        return false;
    }

    public boolean removeProduct(String productId) {
        return productList.removeIf(p -> p.getProductId().equals(productId));
    }

    public List<Product> listAllProducts() {
        return new ArrayList<>(productList);
    }

    public List<Product> getProductsByBranch(String branchId) {
        List<Product> result = new ArrayList<>();
        for (Product p : productList) {
            if (p.getBranch().equals(branchId)) {
                result.add(p);
            }
        }
        return result;
    }

    public String getFormattedProductList(List<Product> products) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append(String.format("%-10s | %-20s | %-15s | %-10s | %-10s | %-10s\n",
                "Product ID", "Name", "Category", "Price", "Stock", "Branch"));
        sb.append("-----------------------------------------------------------------------------------\n");

        // Rows
        for (Product p : products) {
            sb.append(String.format("%-10s | %-20s | %-15s | %-10.2f | %-10d | %-10s\n",
                    p.getProductId(), p.getProductName(), p.getCategory(),
                    p.getPrice(), p.getQuantityInStock(), p.getBranch()));
        }

        return sb.toString();
    }

    public String getFormattedAllProducts() {
        return getFormattedProductList(productList);
    }
}
