package Exceptions;

public class CustomExceptions {

    // Customer Exceptions
    public static class CustomerException extends Exception {
        public CustomerException(String message) { super(message); }
    }

    public static class InvalidCustomerIdException extends CustomerException {
        public InvalidCustomerIdException(String message) { super(message); }
    }

    public static class InvalidCustomerPhoneException extends CustomerException {
        public InvalidCustomerPhoneException(String message) { super(message); }
    }

    public static class EmptyCustomerNameException extends CustomerException {
        public EmptyCustomerNameException(String message) { super(message); }
    }

    // Employee Exceptions
    public static class EmployeeException extends Exception {
        public EmployeeException(String message) { super(message); }
    }

    public static class InvalidEmployeeIdException extends EmployeeException {
        public InvalidEmployeeIdException(String message) { super(message); }
    }

    public static class InvalidEmployeePhoneException extends EmployeeException {
        public InvalidEmployeePhoneException(String message) { super(message); }
    }

    public static class EmptyEmployeeNameException extends EmployeeException {
        public EmptyEmployeeNameException(String message) { super(message); }
    }

    public static class InvalidEmployeeNumberException extends EmployeeException {
        public InvalidEmployeeNumberException(String message) { super(message); }
    }

    public static class NullEmployeeRoleException extends EmployeeException {
        public NullEmployeeRoleException(String message) { super(message); }
    }

    public static class InvalidUsernameException extends EmployeeException {
        public InvalidUsernameException(String message) { super(message); }
    }

    public static class InvalidPasswordException extends EmployeeException {
        public InvalidPasswordException(String message) { super(message); }
    }

    // Branch Exceptions
    public static class BranchException extends Exception {
        public BranchException(String message) { super(message); }
    }

    public static class InvalidBranchIdException extends BranchException {
        public InvalidBranchIdException(String message) { super(message); }
    }

    public static class EmptyBranchNameException extends BranchException {
        public EmptyBranchNameException(String message) { super(message); }
    }


    // Product Exceptions
    public static class ProductException extends Exception {
        public ProductException(String message) { super(message); }
    }

    public static class InvalidProductIdException extends ProductException {
        public InvalidProductIdException(String message) { super(message); }
    }

    public static class EmptyProductNameException extends ProductException {
        public EmptyProductNameException(String message) { super(message); }
    }

    public static class EmptyProductCategoryException extends ProductException {
        public EmptyProductCategoryException(String message) { super(message); }
    }

    public static class NegativeProductPriceException extends ProductException {
        public NegativeProductPriceException(String message) { super(message); }
    }

    public static class NegativeProductQuantityException extends ProductException {
        public NegativeProductQuantityException(String message) { super(message); }
    }

    public static class EmptyProductBranchException extends ProductException {
        public EmptyProductBranchException(String message) { super(message); }
    }
}
