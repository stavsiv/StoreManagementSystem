package Services;

import Exceptions.CustomExceptions;
import Models.Branch;
import java.util.HashMap;
import java.util.Map;

public class BranchService {

    private Map<String, Branch> branches;

    public BranchService() {
        this.branches = new HashMap<>();
    }

    public void addBranch(Branch branch) throws CustomExceptions.BranchException {
        if (branches.containsKey(branch.getBranchId()))
            throw new CustomExceptions.InvalidBranchIdException("Branch ID already exists: " + branch.getBranchId());
        branches.put(branch.getBranchId(), branch);
    }
}
