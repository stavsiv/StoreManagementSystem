package Services;

import Models.Employee;
import java.util.HashMap;
import java.util.Map;

/**
 * Basic authentication service (in-memory) that associates a username with an
 * employee and verifies passwords.
 */
public class AuthService {

    // Mapping username -> Employee (allows searching for a user by username)
    private Map<String, Employee> users;
    // Mapping username -> is currently logged in (true/false)
    private Map<String, Boolean> loggedInMap;

    public AuthService() {
        this.users = new HashMap<>();
        this.loggedInMap = new HashMap<>();
    }

    /**
     * Registers a new employee in the authentication system (assign username &
     * password).
     */
    public void register(Employee emp, String username, String password) {
        emp.setUserName(username);
        emp.setPassword(password);
        users.put(username, emp);
        loggedInMap.put(username, false);
    }

    /**
     * Attempts to log in with a given username/password.
     * 
     * @return Employee if successful, null if failed.
     */
    public Employee login(String username, String password) {
        // Check if the user exists in the map
        if (!users.containsKey(username)) {
            System.out.println("The username does not exist in the system!");
            return null;
        }
        Employee emp = users.get(username);

        // Verify password
        if (!emp.getPassword().equals(password)) {
            System.out.println("Incorrect password!");
            return null;
        }

        // Prevent multiple simultaneous logins
        if (loggedInMap.get(username)) {
            System.out.println("This user is already logged in from another session!");
            return null;
        }

        // Successful login
        loggedInMap.put(username, true);
        System.out.println("Login successful! Welcome, " + emp.getEmployeeName());
        return emp;
    }

    /**
     * Logs out the currently logged-in user.
     */
    public void logout(String username) {
        if (loggedInMap.containsKey(username) && loggedInMap.get(username)) {
            loggedInMap.put(username, false);
            System.out.println("The user " + username + " has logged out.");
        } else {
            System.out.println("The user " + username + " is not logged in.");
        }
    }
}
