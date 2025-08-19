package Services;

import Models.Customer;
import java.util.ArrayList;
import java.util.List;

public class CustomerService {

    /**
     * This Class manages the business logic for handling customers (create, update, retrieve, and delete).
     */

    private List<Customer> customersList;

    // Constructor
    public CustomerService() {
        this.customersList = new ArrayList<>();
    }

    public void addCustomer(Customer customer) {
        for (Customer existing : customersList) {
            if (existing.getCustomerId().equals(customer.getCustomerId())) {
                throw new IllegalArgumentException("Customer ID already exists: " + customer.getCustomerId());
            }
        }
        customersList.add(customer);
    }


    public Customer getCustomerById(String id) {
        for (Customer c : customersList) {
            if (c.getCustomerId().equals(id)) {
                return c;
            }
        }
        return null;
    }

//    public boolean updateCustomer(Customer updatedCustomer) {
//        for (int index = 0; index < customersList.size(); index++) {
//            if (customersList.get(index).getCustomerId().equals(updatedCustomer.getCustomerId())) {
//                customersList.set(index, updatedCustomer);
//                return true;
//            }
//        }
//        return false;
//    }

//    public boolean removeCustomer(String id) {
//        return customersList.removeIf(c -> c.getCustomerId().equals(id));
//    }

    public List<Customer> listAllCustomers() {
        return new ArrayList<>(customersList);
    }
}
