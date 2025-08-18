package Services;

import Models.Employee;
import java.util.ArrayList;
import java.util.List;

public class EmployeeService {
    private List<Employee> employees;

    public EmployeeService() {
        this.employees = new ArrayList<>();
    }

    public boolean addEmployee(Employee e) {
        // Simple validation
        if (e.getUserName() == null || e.getUserName().isEmpty()) return false;
        if (e.getFullName() == null || e.getFullName().isEmpty()) return false;
        for (Employee emp : employees) {
            if (emp.getUserName().equals(e.getUserName())) return false;
            if (emp.getEmployeeId().equals(e.getEmployeeId())) return false;
        }
        employees.add(e);
        return true;
    }

    public Employee getEmployeeById(String id) {
        for (Employee e : employees) {
            if (e.getEmployeeId().equals(id)) return e;
        }
        return null;
    }

    public boolean updateEmployee(Employee updated) {
        for (int i = 0; i < employees.size(); i++) {
            if (employees.get(i).getEmployeeId().equals(updated.getEmployeeId())) {
                employees.set(i, updated);
                return true;
            }
        }
        return false;
    }

    public boolean removeEmployee(String id) {
        return employees.removeIf(e -> e.getEmployeeId().equals(id));
    }

    public List<Employee> listAllEmployees() {
        return new ArrayList<>(employees);
    }
}
