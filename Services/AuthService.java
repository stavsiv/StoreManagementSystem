package Services;

import Models.Employee;
import java.util.HashMap;
import java.util.Map;

public class AuthService {

    /**
     * This class handles authentication processes, including verifying user credentials and managing login operations.
     */

    private Map<String, Employee> users;
    private Map<String, Boolean> loggedInMap;

    // Constructor
    public AuthService() {
        this.users = new HashMap<>();
        this.loggedInMap = new HashMap<>();
    }

    public void register(Employee employee, String username, String password) {
        if (users.containsKey(username)) {
            return;
        }
        employee.setUserName(username);
        employee.setPassword(password);
        users.put(username, employee);
        loggedInMap.put(username, false);
    }

    public Employee login(String username, String password) {
        if (!users.containsKey(username)) {
            System.out.println("The username does not exist in the system!");
            return null;
        }

        Employee employee = users.get(username);
        if (!employee.getPassword().equals(password)) {
            System.out.println("Incorrect password!");
            return null;
        }

        if (loggedInMap.get(username)) {
            System.out.println("This user is already logged in!");
            return null;
        }

        loggedInMap.put(username, true);
        System.out.println("Login successful! Welcome, " + employee.getFullName());
        return employee;
    }

    public void logout(String username) {
        if (loggedInMap.containsKey(username) && loggedInMap.get(username)) {
            loggedInMap.put(username, false);
            System.out.println("User " + username + " logged out.");
        }
    }
}
