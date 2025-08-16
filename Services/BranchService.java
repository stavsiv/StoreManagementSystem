package Services;

import Models.Branch;
import java.util.HashMap;
import java.util.Map;

public class BranchService {
    // Map branchId -> Branch object
    private Map<String, Branch> branches;

    public BranchService() {
        this.branches = new HashMap<>();
    }

    public void addBranch(Branch branch) {
        branches.put(branch.getBranchId(), branch);
    }

    public Branch getBranchById(String branchId) {
        return branches.get(branchId);
    }

    public void removeBranch(String branchId) {
        branches.remove(branchId);
    }

    public Map<String, Branch> getAllBranches() {
        return branches;
    }
}