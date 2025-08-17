package Models;

import java.util.HashSet;
import java.util.Set;

public class Branch {
    private String branchId; // Example: B001
    private String branchName; // Example: Tel Aviv Main

    // Static set to track unique branch IDs across all Branch instances
    private static final Set<String> existingBranchIds = new HashSet<>();

    // Constructor
    public Branch(String branchId, String branchName) {
        setBranchId(branchId);
        setBranchName(branchName);
    }

    // Getters & Setters
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) { // Validate branch ID format and uniqueness
        if (branchId == null || !branchId.matches("[A-Z]{1,3}\\d{2,3}")) {
            throw new IllegalArgumentException(
                    "Branch ID must be 1-3 uppercase letters followed by 2-3 digits. Example: B01, TV001.");
        }
        if (existingBranchIds.contains(branchId)) {
            throw new IllegalArgumentException("Branch ID already exists: " + branchId);
        }
        this.branchId = branchId;
        existingBranchIds.add(branchId);
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
