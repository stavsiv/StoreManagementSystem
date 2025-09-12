package Services;

import Exceptions.CustomExceptions;
import Models.Employee;
import java.util.ArrayList;
import java.util.List;

public class EmployeeService {

    private final List<Employee> employees;

    public EmployeeService() {
        this.employees = new ArrayList<>();
    }

    public void addEmployee(Employee employee) throws CustomExceptions.EmployeeException {
        for (Employee emp : employees) {
            if (emp.getUserName().equals(employee.getUserName()))
                throw new CustomExceptions.InvalidUsernameException("Username already exists: " + employee.getUserName());
            if (emp.getEmployeeId().equals(employee.getEmployeeId()))
                throw new CustomExceptions.InvalidEmployeeIdException("Employee ID already exists: " + employee.getEmployeeId());
            if (emp.getEmployeeNumber() == employee.getEmployeeNumber())
                throw new CustomExceptions.InvalidEmployeeNumberException("Employee number already exists: " + employee.getEmployeeNumber());
        }
        employees.add(employee);
    }

    public List<Employee> listAllEmployees() {
        return new ArrayList<>(employees);
    }

    public String formatEmployeeList(List<Employee> employees) {
        StringBuilder employeeSB = new StringBuilder();
        employeeSB.append(String.format("%-15s | %-12s | %-12s | %-12s | %-8s | %-10s | %-15s | %-12s\n",
                "Name", "Id", "Phone", "BankAccount", "Branch", "EmpNum", "Role", "Username"));
        employeeSB.append("---------------------------------------------------------------------------------------------------------------------\n");

        for (Employee employee : employees) {
            employeeSB.append(String.format("%-15s | %-12s | %-12s | %-12s | %-8s | %-10d | %-15s | %-12s\n",
                    employee.getFullName(),
                    employee.getEmployeeId(),
                    employee.getPhoneNumber(),
                    employee.getAccountNumber(),
                    employee.getBranchId(),
                    employee.getEmployeeNumber(),
                    employee.getRole(),
                    employee.getUserName()
            ));
        }

        return employeeSB.toString();
    }
}
