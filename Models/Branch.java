package Models;

import Exceptions.CustomExceptions;

public class Branch {
    private String branchId; // Example: B001
    private String branchName; // Example: Tel Aviv Main

    // Constructor
    public Branch(String branchId, String branchName) throws CustomExceptions.BranchException {
        setBranchId(branchId);
        setBranchName(branchName);
    }

    // Getters & Setters
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) throws CustomExceptions.BranchException {
        if (branchId == null || !branchId.matches("[A-Z]{1,3}\\d{2,3}"))
            throw new CustomExceptions.InvalidBranchIdException("Branch ID must be 1-3 uppercase letters followed by 2-3 digits.");
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) throws CustomExceptions.BranchException {
        if (branchName == null || branchName.isBlank())
            throw new CustomExceptions.EmptyBranchNameException("Branch name cannot be empty");
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
