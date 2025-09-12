package Services;

import Exceptions.CustomExceptions;
import Models.Customer;
import java.util.ArrayList;
import java.util.List;

public class CustomerService {

    private final List<Customer> customersList;

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
        for (Customer customer : customersList) {
            if (customer.getCustomerId().equals(id)) {
                return customer;
            }
        }
        return null;
    }

    public List<Customer> listAllCustomers() {
        return new ArrayList<>(customersList);
    }

    public String formatCustomerList(List<Customer> customers) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("%-20s | %-10s | %-12s | %-10s | %-8s\n",
                "Name", "Id", "Phone", "Type", "Discount"));
        sb.append("-------------------------------------------------------------------------\n");

        for (Customer c : customers) {
            String discount = switch (c.getCustomerType().toLowerCase()) {
                case "returning" -> "10%";
                case "vip" -> "30%";
                default -> "0%";
            };

            sb.append(String.format("%-20s | %-10s | %-12s | %-10s | %-8s\n",
                    c.getCustomerName(),
                    c.getCustomerId(),
                    c.getPhoneNumber(),
                    c.getCustomerType(),
                    discount
            ));
        }

        return sb.toString();
    }

}
