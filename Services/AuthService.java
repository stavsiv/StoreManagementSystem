package Services;

import Models.Employee;
import java.util.HashMap;
import java.util.Map;

public class AuthService {

    private Map<String, Employee> users;
    private Map<String, Boolean> loggedInMap;

    public AuthService() {
        this.users = new HashMap<>();
        this.loggedInMap = new HashMap<>();
    }

    public void register(Employee emp, String username, String password) {
        if (users.containsKey(username)) {
            //System.out.println("Skipping duplicate username: " + username);
            return;
        }
        emp.setUserName(username);
        emp.setPassword(password);
        users.put(username, emp);
        loggedInMap.put(username, false);
    }

    public Employee login(String username, String password) {
        if (!users.containsKey(username)) {
            System.out.println("The username does not exist in the system!");
            return null;
        }

        Employee emp = users.get(username);
        if (!emp.getPassword().equals(password)) {
            System.out.println("Incorrect password!");
            return null;
        }

        if (loggedInMap.get(username)) {
            System.out.println("This user is already logged in!");
            return null;
        }

        loggedInMap.put(username, true);
        System.out.println("Login successful! Welcome, " + emp.getFullName());
        return emp;
    }

    public void logout(String username) {
        if (loggedInMap.containsKey(username) && loggedInMap.get(username)) {
            loggedInMap.put(username, false);
            System.out.println("User " + username + " logged out.");
        }
    }
}
