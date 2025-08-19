package Models;

public class Employee {
    private String fullName;
    private String employeeId; // 9 digits
    private String phoneNumber; // 10 digits
    private String accountNumber;
    private int employeeNumber;
    private String branchId;
    private Role role;
    private String userName;
    private String password;

    // Empty Constructor
    public Employee() { }

    // Constructor
    public Employee(String fullName, String employeeId, String phoneNumber, String accountNumber,
                    int employeeNumber, String branchId, Role role, String userName, String password) {
        setFullName(fullName);
        setEmployeeId(employeeId);
        setPhoneNumber(phoneNumber);
        setAccountNumber(accountNumber);
        setEmployeeNumber(employeeNumber);
        setBranchId(branchId);
        setRole(role);
        setUserName(userName);
        setPassword(password);
    }

    // Getters & Setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) {
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full name cannot be empty");
        this.fullName = fullName;
    }

    public String getEmployeeId() { return employeeId; }
    public void setEmployeeId(String employeeId) {
        if (employeeId == null || !employeeId.matches("\\d{9}"))
            throw new IllegalArgumentException("Employee ID must be exactly 9 digits.");
        this.employeeId = employeeId;
    }

    public String getPhoneNumber() { return phoneNumber; }
    public void setPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || !phoneNumber.matches("\\d{10}"))
            throw new IllegalArgumentException("Phone number must be exactly 10 digits.");
        this.phoneNumber = phoneNumber;
    }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public int getEmployeeNumber() { return employeeNumber; }
    public void setEmployeeNumber(int employeeNumber) {
        if (employeeNumber <= 0) throw new IllegalArgumentException("Employee number must be positive");
        this.employeeNumber = employeeNumber;
    }

    public String getBranchId() { return branchId; }
    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) {
        if (role == null) throw new IllegalArgumentException("Role cannot be null");
        this.role = role;
    }

    public String getUserName() { return userName; }
    public void setUserName(String userName) {
        if (userName == null || userName.isBlank())
            throw new IllegalArgumentException("Username cannot be empty");
        this.userName = userName;
    }

    public String getPassword() { return password; }
    public void setPassword(String password) {
        if (password == null || password.length() < 4)
            throw new IllegalArgumentException("Password must be at least 4 characters");
        this.password = password;
    }
}
