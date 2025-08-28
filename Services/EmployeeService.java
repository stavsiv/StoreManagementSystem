package Services;

import Exceptions.CustomExceptions;
import Models.Employee;
import java.util.ArrayList;
import java.util.List;

public class EmployeeService {

    private List<Employee> employees;

    public EmployeeService() {
        this.employees = new ArrayList<>();
    }

    public boolean addEmployee(Employee employee) throws CustomExceptions.EmployeeException {
        for (Employee emp : employees) {
            if (emp.getUserName().equals(employee.getUserName()))
                throw new CustomExceptions.InvalidUsernameException("Username already exists: " + employee.getUserName());
            if (emp.getEmployeeId().equals(employee.getEmployeeId()))
                throw new CustomExceptions.InvalidEmployeeIdException("Employee ID already exists: " + employee.getEmployeeId());
            if (emp.getEmployeeNumber() == employee.getEmployeeNumber())
                throw new CustomExceptions.InvalidEmployeeNumberException("Employee number already exists: " + employee.getEmployeeNumber());
        }
        employees.add(employee);
        return true;
    }

    public List<Employee> listAllEmployees() {
        return new ArrayList<>(employees);
    }
}
