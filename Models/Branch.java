package Models;

public class Branch {
    private String branchId; // TV01
    private String branchName; // Tel Aviv

    // Constructor
    public Branch(String branchId, String branchName) {
        this.branchId = branchId;
        this.branchName = branchName;
    }

    // Getters & Setters
    public String getBranchId() {
        return branchId;
    }

    public void setBranchId(String branchId) {
        this.branchId = branchId;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    @Override
    public String toString() {
        return "Branch{" +
                "branchId='" + branchId + '\'' +
                ", name='" + branchName + '\'' +
                '}';
    }

}
