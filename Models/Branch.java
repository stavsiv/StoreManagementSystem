package Models;

public class Branch {
    private String branchId; // Example: B001
    private String branchName; // Example: Tel Aviv Main

    // Constructor
    public Branch(String branchId, String branchName) {
        setBranchId(branchId);
        setBranchName(branchName);
    }

    // Getters & Setters
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        if (branchId == null || !branchId.matches("[A-Z]{1,3}\\d{2,3}")) {
            throw new IllegalArgumentException("Branch ID must be 1-3 uppercase letters followed by 2-3 digits. Example: B01, TV001.");
        }
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    @Override
    public String toString() {
        return "Branch{" +
                "branchId='" + branchId + '\'' +
                ", branchName='" + branchName + '\'' +
                '}';
    }
}
