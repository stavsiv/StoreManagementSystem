package Services;

import Exceptions.CustomExceptions;
import Models.Product;
import java.util.ArrayList;
import java.util.List;

public class ProductService {

    private final List<Product> productList;

    public ProductService() {
        this.productList = new ArrayList<>();
    }

    public void addOrUpdateProduct(Product p, int additionalQuantity) throws CustomExceptions.ProductException {
        for (Product existing : productList) {
            if (existing.getProductId().equals(p.getProductId()) &&
                    existing.getBranch().equalsIgnoreCase(p.getBranch())) {
                existing.setQuantityInStock(existing.getQuantityInStock() + additionalQuantity);
                return;
            }
        }
        productList.add(p);
    }

    public Product getProductByIdAndBranch(String productId, String branchId) {
        for (Product p : productList) {
            if (p.getProductId().equals(productId) && p.getBranch().equalsIgnoreCase(branchId)) {
                return p;
            }
        }
        return null;
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

    public List<Product> getProductsByBranch(String branchId) {
       List<Product> result = new ArrayList<>();
     for (Product p : productList) {
           if (p.getBranch().equalsIgnoreCase(branchId)) {
              result.add(p);
            }
        }
        return result;
    }

    public List<Product> getAllProducts() {
        return new ArrayList<>(productList);
    }
}
