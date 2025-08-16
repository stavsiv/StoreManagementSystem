package Services;

import Models.Customer;
import java.util.ArrayList;
import java.util.List;

public class CustomerService {
    private List<Customer> customersList;

    public CustomerService() {
        this.customersList = new ArrayList<>();
    }

    public void addCustomer(Customer c) {
        customersList.add(c);
    }

    public Customer getCustomerById(String id) {
        for (Customer c : customersList) {
            if (c.getCustomerId().equals(id)) {
                return c;
            }
        }
        return null;
    }

    public boolean updateCustomer(Customer updatedCustomer) {
        for (int i = 0; i < customersList.size(); i++) {
            if (customersList.get(i).getCustomerId().equals(updatedCustomer.getCustomerId())) {
                customersList.set(i, updatedCustomer);
                return true;
            }
        }
        return false;
    }

    public boolean removeCustomer(String id) {
        return customersList.removeIf(c -> c.getCustomerId().equals(id));
    }

    public List<Customer> listAllCustomers() {
        // Return a copy
        return new ArrayList<>(customersList);
    }
}