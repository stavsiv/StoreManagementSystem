package Models;

import java.util.HashSet;
import java.util.Set;

public class Employee {
    private String fullName;
    private String employeeId; // must be 9 digits
    private String phoneNumber; // must be 10 digits
    private String accountNumber;
    private int employeeNumber; // unique
    private String branchId;
    private Role role;

    public enum Role {
        SHIFT_MANAGER,
        CASHIER,
        SELLER,
        ADMIN
    }

    // authentication
    private String userName; // unique
    private String password;

    // Static Sets for Validation
    private static final Set<Integer> existingEmployeeNumbers = new HashSet<>();
    private static final Set<String> existingUserNames = new HashSet<>();
    private static final Set<String> existingEmployeeIds = new HashSet<>();

    // Constructor
    public Employee(String name, String employeeId, String phoneNumber, String accountNumber,
            int employeeNumber, String branchId, Role role,
            String userName, String password) {
        setEmployeeName(name);
        setEmployeeId(employeeId);
        setPhoneNumber(phoneNumber);
        setAccountNumber(accountNumber);
        setEmployeeNumber(employeeNumber);
        setBranchId(branchId);
        setRole(role);
        setUserName(userName);
        setPassword(password);
    }

    // Getters & Setters with validation
    public String getEmployeeName() {
        return fullName;
    }

    public void setEmployeeName(String name) {
        this.fullName = name;
    }

    public String getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(String employeeId) {
        if (employeeId == null || !employeeId.matches("\\d{9}")) {
            throw new IllegalArgumentException("Employee ID must be exactly 9 digits.");
        }
        if (existingEmployeeIds.contains(employeeId)) {
            throw new IllegalArgumentException("Employee ID already exists: " + employeeId);
        }
        this.employeeId = employeeId;
        existingEmployeeIds.add(employeeId);
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}")) {
            throw new IllegalArgumentException("Phone number must be exactly 10 digits.");
        }
        this.phoneNumber = phoneNumber;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public int getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(int employeeNumber) {
        if (existingEmployeeNumbers.contains(employeeNumber)) {
            throw new IllegalArgumentException("Employee number already exists: " + employeeNumber);
        }
        this.employeeNumber = employeeNumber;
        existingEmployeeNumbers.add(employeeNumber);
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        if (existingUserNames.contains(userName)) {
            throw new IllegalArgumentException("Username already exists: " + userName);
        }
        this.userName = userName;
        existingUserNames.add(userName);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    @Override
    public String toString() {
        return "Employee{" +
                "name='" + fullName + '\'' +
                ", employeeId='" + employeeId + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", accountNumber='" + accountNumber + '\'' +
                ", employeeNumber=" + employeeNumber +
                ", branchId='" + branchId + '\'' +
                ", role=" + role +
                ", userName='" + userName + '\'' +
                '}';
    }
}