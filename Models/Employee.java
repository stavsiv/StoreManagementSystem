package Models;

public class Employee {
    private String fullName;
    private String employeeId;
    private String phoneNumber;
    private String accountNumber;
    private int employeeNumber;

    private String branchId;
    private Role role;

    public enum Role {
        SHIFT_MANAGER,
        CASHIER,
        SELLER,
        ADMIN
    }

    // authentication
    private String userName;
    private String password;

    // Constructor
    public Employee(String name, String employeeId, String phoneNumber, String accountNumber, int employeeNumber,
            String branchId, Role role) {
        this.fullName = name;
        this.employeeId = employeeId;
        this.phoneNumber = phoneNumber;
        this.accountNumber = accountNumber;
        this.employeeNumber = employeeNumber;
        this.branchId = branchId;
        this.role = role;
    }

    // Getters & Setters
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
        this.employeeId = employeeId;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phone) {
        this.phoneNumber = phone;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getEmployeeNumber() {
        return employeeNumber;
    }

    public void setEmployeeNumber(int employeeNumber) {
        this.employeeNumber = employeeNumber;
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

    // Authentication methods (if needed)
    public String getUserName() {
        return userName;
    }

    public void setUserName(String username) {
        this.userName = username;
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
                '}';
    }
}
