package Services;

import Exceptions.CustomExceptions;
import Models.Employee;
import java.util.HashMap;
import java.util.Map;

public class AuthService {

    private final Map<String, Employee> users;
    private final Map<String, Boolean> loggedInMap;

    public AuthService() {
        this.users = new HashMap<>();
        this.loggedInMap = new HashMap<>();
    }

    public void register(Employee employee, String username, String password) throws CustomExceptions.EmployeeException {
        if (users.containsKey(username)) {
            throw new CustomExceptions.InvalidUsernameException("Username already exists: " + username);
        }
        employee.setUserName(username);
        employee.setPassword(password);
        users.put(username, employee);
        loggedInMap.put(username, false);
    }

    public Employee login(String username, String password) throws CustomExceptions.InvalidUsernameException, CustomExceptions.InvalidPasswordException {
        if (!users.containsKey(username)) {
            throw new CustomExceptions.InvalidUsernameException("The username does not exist!");
        }

        Employee employee = users.get(username);
        if (!employee.getPassword().equals(password)) {
            throw new CustomExceptions.InvalidPasswordException("Incorrect password!");
        }

        if (loggedInMap.get(username)) {
            throw new CustomExceptions.InvalidUsernameException("This user is already logged in!");
        }

        loggedInMap.put(username, true);
        return employee;
    }

    public void logout(String username) {
        if (loggedInMap.containsKey(username) && loggedInMap.get(username)) {
            loggedInMap.put(username, false);
        }
    }
}
