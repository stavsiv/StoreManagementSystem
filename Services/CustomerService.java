package Services;

import Exceptions.CustomExceptions;
import Models.Customer;
import java.util.ArrayList;
import java.util.List;

public class CustomerService {

    private List<Customer> customersList;

    public CustomerService() {
        this.customersList = new ArrayList<>();
    }

    public void addCustomer(Customer customer) throws CustomExceptions.CustomerException {
        for (Customer existing : customersList) {
            if (existing.getCustomerId().equals(customer.getCustomerId())) {
                throw new CustomExceptions.InvalidCustomerIdException("Customer ID already exists: " + customer.getCustomerId());
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

    public List<Customer> listAllCustomers() {
        return new ArrayList<>(customersList);
    }
}
