package Server;

// Standard library
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Models
import Models.Branch;
import Models.Employee;
import Models.Product;
import Models.Customer;
import Models.NewCustomer;
import Models.ReturningCustomer;
import Models.VIPCustomer;
import Models.Role;
//import Models.TransactionItem;
//import Models.Transaction;

// Services
import Services.AuthService;
import Services.BranchService;
import Services.CustomerService;
import Services.EmployeeService;
import Services.ProductService;
import Services.SaleService;
import Services.SaleService.SaleRecord;

// Chat Service
import Services.ChatService;
import Services.ChatService.ChatSession;
import Services.ChatService.ChatMessage;

// NEW SERVICE for converting logs to doc
import Services.LogsService;

public class ServerApp {
    private int port;
    private AuthService authService;
    private EmployeeService employeeService;
    private ProductService productService;
    private SaleService saleService;
    private CustomerService customerService;
    private BranchService branchService;
    private ChatService chatService;

    // File paths for reading/writing JSON on startup or updates
    private static final String BRANCHES_FILE = "Data/branches.json";
    private static final String EMPLOYEES_FILE = "Data/employees.json";
    private static final String PRODUCTS_FILE = "Data/products.json";
    private static final String CUSTOMERS_FILE = "Data/customers.json";


    // logging appended actions to "logs/actions.log"
    private static final String ACTION_LOG_FILE = "Logs/actions.log";

    public ServerApp(int port) {
        this.port = port;

        // Initialize services
        this.authService = new AuthService();
        this.employeeService = new EmployeeService();
        this.productService = new ProductService();
        this.saleService = new SaleService(productService);
        this.customerService = new CustomerService();
        this.branchService = new BranchService();
        this.chatService = new ChatService();

        // Load existing data
        loadBranchesFromFile(BRANCHES_FILE);
        loadEmployeesFromFile(EMPLOYEES_FILE);
        for (Employee e : employeeService.listAllEmployees()) {
            authService.register(e, e.getUserName(), e.getPassword());
        }
        loadProductsFromFile(PRODUCTS_FILE);
        loadCustomersFromFile(CUSTOMERS_FILE);

        System.out.println("Server initialized successfully. Data loaded from JSON files.");
    }

    /**
     * Start listening for incoming connections.
     */
    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server listening on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getInetAddress());
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Inner class to handle client sessions.
     */
    private class ClientHandler implements Runnable {
        private Socket socket;
        private Employee loggedInEmployee;
        private String currentUsername;

        // Keep track of the current chat ID the user is in
        private String currentChatId = null;

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {
                while (true) {
                    if (!login(in, out)) {
                        break;
                    }
                    if (!handleCommands(in, out)) {
                        break;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (currentUsername != null) {
                    authService.logout(currentUsername);
                }
                try {
                    socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
                System.out.println("Client disconnected.");
            }
        }

        /**
         * Append a line to "logs/actions.log" with a timestamp.
         */
        private void logAction(String action) {
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }
            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            String finalLine = "[" + timestamp + "] " + action;
            File logFile = new File(ACTION_LOG_FILE);

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
                writer.write(finalLine + "\n");
                writer.flush();
            } catch (IOException e) {
                System.err.println("ERROR: Could not write to actions.log - " + e.getMessage());
            }
        }

        private boolean login(BufferedReader in, PrintWriter out) throws IOException {
            out.println("Welcome to the Store Management System!");
            out.println("Please enter your username:");
            String username = in.readLine();
            if (username == null)
                return false;

            out.println("Please enter your password:");
            String password = in.readLine();
            if (password == null)
                return false;

            Employee loggedInEmployee = authService.login(username, password);
            if (loggedInEmployee == null) {
                out.println("ERROR: Invalid credentials or user already logged in. Closing connection...");
                return false;
            }

            this.loggedInEmployee = loggedInEmployee;
            this.currentUsername = username;
            out.println("Login successful! Hello, " + loggedInEmployee.getFullName() + " (Role: "
                    + loggedInEmployee.getRole() + ")");
            return true;
        }

        private boolean handleCommands(BufferedReader in, PrintWriter out) throws IOException {
            out.println("Type 'Menu' to see available commands, or 'Exit' to exit.");

            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.equalsIgnoreCase("EXIT")) {
                    out.println("Goodbye!");
                    return false;
                }

                String response = handleCommand(line);
                out.println(response);

                // Handle logout separately
                if (response.contains("Returning to login screen")) {
                    return true; // back to login
                }
            }
            return false;
        }

        private String handleCommand(String line) {
            line = line.trim();
            if (line.isEmpty()) return "";

            if (line.toUpperCase().startsWith("SEND_MSG")) {
                return sendMessageCommand(line);
            }

            // Normalize command: replace spaces with underscores and uppercase
            String[] parts = line.split(" ");
            String command = parts[0].toUpperCase().replace(" ", "_");

            switch (command) {
                case "MENU":
                    return showMenu();
                case "ADD_EMPLOYEE":
                    return addEmployeeCommand(parts);
                case "SHOW_EMPLOYEES":
                    return showEmployees();
                case "SHOW_PRODUCTS":
                    return showProducts();
                case "SELL":
                    return sellProductCommand(parts);
                case "PURCHASE_PRODUCT":
                    return purchaseProductCommand(parts);
                case "SAVE_SALES":
                    return saveSalesLogs();
                case "VIEW_SALES_LOGS":
                    return viewSalesLogs();
                case "ADD_CUSTOMER":
                    return addCustomerCommand(parts);
                case "SHOW_CUSTOMERS":
                    return showCustomers();
                case "LOGS_TO_WORD":
                    return logsToWordCommand();
                case "START_CHAT":
                    return startChatCommand(parts);
                case "JOIN_CHAT":
                    return joinChatCommand(parts);
                case "SHOW_CHAT":
                    return showChatWithoutParam();
                case "LIST_CHATS":
                    return listChatsCommand();
                case "LOGOUT":
                    authService.logout(currentUsername);
                    loggedInEmployee = null;
                    currentUsername = null;
                    return "You have been logged out. Returning to login screen...";
                case "EXIT":
                    return "Goodbye!";
                default:
                    return "Unknown command: " + command + ". Type MENU for commands.";
            }
        }


        // =========================================================
        // NEW COMMAND: LOGS_TO_WORD
        // =========================================================
        private String logsToWordCommand() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can convert logs to Word.";
            }

            // We assume the 2 JSON logs exist:
            // logs/sales_by_branch.json
            // logs/sales_by_productType.json
            // We output to logs/sales_report.doc
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                return "No logs/ folder found. Please run SAVE_SALES first.";
            }
            File branchFile = new File(logsDir, "sales_by_branch.json");
            File productFile = new File(logsDir, "sales_by_productType.json");
            if (!branchFile.exists() || !productFile.exists()) {
                return "Missing JSON logs. Please run SAVE_SALES first.";
            }

            String outputDoc = "logs/sales_report.doc";
            try {
                LogsService.convertToDoc(branchFile.getAbsolutePath(), productFile.getAbsolutePath(), outputDoc);
            } catch (IOException e) {
                e.printStackTrace();
                return "ERROR converting logs to Word: " + e.getMessage();
            }

            // Log this action
            String logMsg = String.format("ADMIN '%s' converted logs to Word doc at '%s'.",
                    loggedInEmployee.getFullName(), outputDoc);
            logAction(logMsg);

            return "SUCCESS!: logs converted to " + outputDoc;
        }

        private String showMenu() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== COMMAND MENU ===\n");

            if (loggedInEmployee.getRole() == Role.ADMIN) {
                sb.append("ADD_EMPLOYEE <fullName> <id> <phone> <bankAccount> <branch> <empNum> <role> <username> <password>\n");
                sb.append("ADD_CUSTOMER <name> <id> <phone> <type> (NEW, RETURNING, VIP)\n");
                sb.append("SHOW_EMPLOYEES - display all employees\n");
                sb.append("SHOW_PRODUCTS - display all products\n");
                sb.append("SHOW_CUSTOMERS - display all customers\n");
                sb.append("SAVE_SALES - save sales logs to JSON\n");
                sb.append("VIEW_SALES_LOGS - view saved logs\n");
                sb.append("LOGS_TO_WORD - convert logs to Word doc\n");
            } else {
                sb.append("ADD_CUSTOMER <name> <id> <phone> <type> (NEW, RETURNING, VIP)\n");
                sb.append("SHOW_PRODUCTS - display products in your branch\n");
                sb.append("SELL <productId> <quantity> <customerId>\n");
                sb.append("PURCHASE_PRODUCT <productId> <productName> <category> <price> <quantity> <branch>\n");
            }

            sb.append("LOGOUT - logout\n");
            sb.append("EXIT - close connection\n");
            sb.append("--- Chat Commands ---\n");
            sb.append("START_CHAT <branchId>\n");
            sb.append("JOIN_CHAT <chatId>\n");
            sb.append("SEND_MSG <message...>\n");
            sb.append("SHOW_CHAT\n");
            sb.append("LIST_CHATS\n");
            sb.append("====================");

            return sb.toString();
        }


        // ---------------------------------------------------
        // ADD_EMPLOYEE command (with logging)
        // ---------------------------------------------------

        private String addEmployeeCommand(String[] parts) {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can add employees.";
            }

            if (parts.length < 10) {
                return "Usage: ADD_EMPLOYEE <fullName> <id> <phone> <bankAccount> <branch> <empNum> <role> <username> <password>";
            }

            int currentIndex = 1;
            StringBuilder nameBuilder = new StringBuilder();
            while (currentIndex < parts.length && !parts[currentIndex].matches("\\d+")) {
                if (nameBuilder.length() > 0) nameBuilder.append(" ");
                nameBuilder.append(parts[currentIndex]);
                currentIndex++;
            }

            try {
                String fullName = nameBuilder.toString();
                String id = parts[currentIndex++];
                String phone = parts[currentIndex++];
                String bankAcc = parts[currentIndex++];
                String branch = parts[currentIndex++];
                int empNum = Integer.parseInt(parts[currentIndex++]);
                String roleStr = parts[currentIndex++].toUpperCase();
                Role newEmpRole = Role.valueOf(roleStr);
                String newUsername = parts[currentIndex++];
                String newPassword = parts[currentIndex++];

                Employee newEmp = new Employee(fullName, id, phone, bankAcc, empNum, branch, newEmpRole, newUsername, newPassword);

                if (!employeeService.addEmployee(newEmp)) {
                    return "ERROR: Employee with same username, ID or number already exists.";
                }

                authService.register(newEmp, newUsername, newPassword);
                saveEmployeesToFile(EMPLOYEES_FILE);

                return String.format("Employee has been successfully added to the system: Name: %s,Id: %s, Branch: %s, Role: %s",
                        newEmp.getFullName(), newEmp.getEmployeeId(), newEmp.getBranchId(), newEmp.getRole());


            } catch (IllegalArgumentException ex) {
                return "ERROR: " + ex.getMessage();
            } catch (Exception ex) {
                return "ERROR: Invalid input or role.";
            }
        }

        // ---------------------------------------------------
        // SELL command (with logging)
        // ---------------------------------------------------
        // Employee sells product to a customer
//        private String sellCommand(String[] parts) {
//            if (loggedInEmployee.getRole() == Role.ADMIN) {
//                return "ERROR: ADMIN cannot SELL products.";
//            }
//            if (parts.length < 4) {
//                return "Usage: SELL <productId> <quantity> <customerId>";
//            }
//            String productId = parts[1];
//            int qty;
//            try {
//                qty = Integer.parseInt(parts[2]);
//            } catch (NumberFormatException e) {
//                return "ERROR: quantity must be an integer.";
//            }
//            String customerId = parts[3];
//            Customer customer = customerService.getCustomerById(customerId);
//            if (customer == null) {
//                return "ERROR: No customer found with ID " + customerId + ". Please ADD_CUSTOMER first.";
//            }
//            double finalPrice = saleService.purchaseProduct(customer, productId, qty);
//            if (finalPrice < 0) {
//                return "ERROR: Unable to sell (invalid product or insufficient stock).";
//            }
//
//            // Log the SELL
//            String logMsg = String.format(
//                    "SELL: Employee '%s' in branch '%s' sold productId='%s' (qty=%d) to customer '%s'",
//                    loggedInEmployee.getFullName(), loggedInEmployee.getBranchId(),
//                    productId, qty, customer.getCustomerName());
//            logAction(logMsg);
//
//            return "SUCCESS: Sold " + qty + " of " + productId + " to " + customerId
//                    + " (" + customer.getCustomerName() + "). Final price: " + finalPrice;
//        }

        private String sellProductCommand(String[] parts) {
            if (loggedInEmployee.getRole() == Role.ADMIN) {
                return "ERROR: ADMIN cannot sell products.";
            }
            if (parts.length < 4) {
                return "Usage: SELL <productId> <quantity> <customerId>";
            }

            String productId = parts[1];
            int qty;
            try {
                qty = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "ERROR: quantity must be an integer.";
            }

            String customerId = parts[3];
            Customer customer = customerService.getCustomerById(customerId);
            if (customer == null) {
                return "ERROR: No customer found with ID " + customerId + ". Please ADD_CUSTOMER first.";
            }

            // Branch-aware lookup
            Product product = productService.getProductByIdAndBranch(productId, loggedInEmployee.getBranchId());
            if (product == null) {
                return "ERROR: Product not found in your branch.";
            }

            double finalPrice = saleService.sellProduct(customer, productId, loggedInEmployee.getBranchId(), qty);
            saveProductsToFile(PRODUCTS_FILE);

            // Log the SELL
            logAction(String.format(
                    "SELL: Employee '%s' in branch '%s' sold productId='%s' (qty=%d) to customer '%s'",
                    loggedInEmployee.getFullName(), loggedInEmployee.getBranchId(),
                    productId, qty, customer.getCustomerName()));

            return String.format(
                    "Transaction completed successfully! Sold %d units of product '%s' - (Id= '%s') to customer %s (Id= %s). Final price: %.2f",
                    qty, product.getProductName(), product.getProductId(), customer.getCustomerName(), customerId, finalPrice
            );

        }

        // ---------------------------------------------------
        // PURCHASE_PRODUCT command (with logging)
        // ---------------------------------------------------
        //Employee buys new stock from supplier or adds a new product to the branch.

//        private String purchaseProductCommand(String[] parts) {
//            if (loggedInEmployee.getRole() == Role.ADMIN) {
//                return "ERROR: ADMIN cannot purchase products.";
//            }
//            if (parts.length < 7) {
//                return "Usage: PURCHASE_PRODUCT <productId> <productName> <category> <price> <quantity> <branch>";
//            }
//
//            String productId = parts[1];
//            String productName = parts[2];
//            String category = parts[3];
//            double price;
//            int quantity;
//            String branch = parts[6];
//
//            try {
//                price = Double.parseDouble(parts[4]);
//            } catch (NumberFormatException e) {
//                return "ERROR: price must be a valid number.";
//            }
//
//            try {
//                quantity = Integer.parseInt(parts[5]);
//            } catch (NumberFormatException e) {
//                return "ERROR: quantity must be an integer.";
//            }
//
//            if (!loggedInEmployee.getBranchId().equalsIgnoreCase(branch)) {
//                return "ERROR: You can't purchase products for a different branch.";
//            }
//
//            Product existing = productService.getProductByIdAndBranch(productId, branch);
//            if (existing != null) {
//                existing.setQuantityInStock(existing.getQuantityInStock() + quantity);
//                saveProductsToFile(PRODUCTS_FILE);
//
//                logAction(String.format("PURCHASE: Employee '%s' in branch '%s' added %d to product '%s'",
//                        loggedInEmployee.getFullName(), branch, quantity, productId));
//
//                return "Success!: Increased stock of product [" + productId + "] by " + quantity +
//                        ". New total in stock = " + existing.getQuantityInStock();
//
//            } else {
//                Product newProduct = new Product(productId, productName, category, price, quantity, branch);
//                productService.addOrUpdateProduct(newProduct);
//                saveProductsToFile(PRODUCTS_FILE);
//
//                logAction(String.format("PURCHASE: Employee '%s' in branch '%s' created new product '%s' (id=%s) qty=%d",
//                        loggedInEmployee.getFullName(), branch, productName, productId, quantity));
//
//                return "Product has been successfully created with ID: " + productId + " and stock: " + quantity + ".";
//            }
//        }
private String purchaseProductCommand(String[] parts) {
    if (loggedInEmployee.getRole() == Role.ADMIN) {
        return "ERROR: ADMIN cannot purchase products.";
    }
    if (parts.length < 7) {
        return "Usage: PURCHASE_PRODUCT <productId> <productName> <category> <price> <quantity> <branch>";
    }

    String productId = parts[1];
    String productName = parts[2];
    String category = parts[3];
    double price;
    int quantity;
    String branch = parts[6];

    try {
        price = Double.parseDouble(parts[4]);
    } catch (NumberFormatException e) {
        return "ERROR: price must be a valid number.";
    }

    try {
        quantity = Integer.parseInt(parts[5]);
    } catch (NumberFormatException e) {
        return "ERROR: quantity must be an integer.";
    }

    if (!loggedInEmployee.getBranchId().equalsIgnoreCase(branch)) {
        return "ERROR: You can't purchase products for a different branch.";
    }

    Product existing = productService.getProductByIdAndBranch(productId, branch);
    if (existing != null) {
        existing.setQuantityInStock(existing.getQuantityInStock() + quantity);
        saveProductsToFile(PRODUCTS_FILE);

        logAction(String.format("PURCHASE: Employee '%s' in branch '%s' added %d to product '%s'",
                loggedInEmployee.getFullName(), branch, quantity, productId));

        return "Product stock successfully updated: " + productId +
                " increased by " + quantity +
                ". New total = " + existing.getQuantityInStock();
    } else {
        Product newProduct = new Product(productId, productName, category, price, quantity, branch);
        productService.addOrUpdateProduct(newProduct);
        saveProductsToFile(PRODUCTS_FILE);

        logAction(String.format("PURCHASE: Employee '%s' in branch '%s' created new product '%s' (id=%s) qty=%d",
                loggedInEmployee.getFullName(), branch, productName, productId, quantity));

        return "Product has been successfully created: " + productName +
                " (ID=" + productId + ") with initial stock of " + quantity + ".";
    }
}


        // ---------------------------------------------------
        // ADD_CUSTOMER command (with logging)
        // ---------------------------------------------------

        private String addCustomerCommand(String[] parts) {
            if (parts.length < 5) {
                return "Usage: ADD_CUSTOMER <fullName> <id> <phone> <type> (NEW, RETURNING, VIP)";
            }

            try {
                int currentIndex = 1;
                StringBuilder nameBuilder = new StringBuilder();
                while (currentIndex < parts.length && !parts[currentIndex].matches("\\d{9}")) {
                    if (nameBuilder.length() > 0) {
                        nameBuilder.append(" ");
                    }
                    nameBuilder.append(parts[currentIndex]);
                    currentIndex++;
                }

                String fullName = nameBuilder.toString();

                String customerId = parts[currentIndex++];
                String phoneNumber = parts[currentIndex++];
                String type = parts[currentIndex++].toUpperCase();

                Customer customer;
                switch (type) {
                    case "NEW":
                        customer = new NewCustomer(fullName, customerId, phoneNumber);
                        break;
                    case "RETURNING":
                        customer = new ReturningCustomer(fullName, customerId, phoneNumber);
                        break;
                    case "VIP":
                        customer = new VIPCustomer(fullName, customerId, phoneNumber);
                        break;
                    default:
                        return "ERROR: unknown customer type: " + type;
                }

                customerService.addCustomer(customer);

                saveCustomersToFile(CUSTOMERS_FILE);

                String logMsg = String.format(
                        "Employee '%s' (Role=%s) added new customer '%s' (ID=%s)",
                        loggedInEmployee.getFullName(),
                        loggedInEmployee.getRole(),
                        customer.getCustomerName(),
                        customer.getCustomerId()
                );
                logAction(logMsg);

                return "Customer has been successfully added to the system with ID: " + customerId + ".";

            } catch (IllegalArgumentException ex) {
                return "ERROR: " + ex.getMessage();
            } catch (Exception ex) {
                return "ERROR: Failed to add customer - " + ex.getMessage();
            }
        }



        // ---------------------------------------------------
        // showEmployees, showProducts, showCustomers
        // ---------------------------------------------------

        private String showEmployees() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can view all employees.";
            }
            List<Employee> all = employeeService.listAllEmployees();
            if (all.isEmpty()) {
                return "No employees found.";
            }
            StringBuilder sb = new StringBuilder();

            sb.append(String.format("%-20s %-12s %-15s %-15s %-8s %-10s %-15s %-12s\n",
                    "Name", "Id", "Phone", "BankAccount", "Branch", "EmpNum", "Role", "Username"));
            sb.append("---------------------------------------------------------------------------------------------------------------\n");

            for (Employee emp : all) {
                sb.append(String.format("%-20s %-12s %-15s %-15s %-8s %-10d %-15s %-12s\n",
                        emp.getFullName(),
                        emp.getEmployeeId(),
                        emp.getPhoneNumber(),
                        emp.getAccountNumber(),
                        emp.getBranchId(),
                        emp.getEmployeeNumber(),
                        emp.getRole(),
                        emp.getUserName()));
            }
            return sb.toString();
        }


        private String showProducts() {
            List<Product> productsToShow;

            if (loggedInEmployee.getRole() == Role.ADMIN) {
                productsToShow = productService.getAllProducts();
            } else {
                productsToShow = productService.getProductsByBranch(loggedInEmployee.getBranchId());
                if (productsToShow.isEmpty())
                    return "No products found for branch " + loggedInEmployee.getBranchId();
            }

            return productService.formatProductList(productsToShow);
        }



        private String showCustomers() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can view all customers.";
            }
            List<Customer> allCustomers = customerService.listAllCustomers();
            if (allCustomers.isEmpty()) {
                return "No customers found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s %-10s %-15s %-15s\n",
                    "Name", "Id", "Phone", "Type"));
            sb.append("-------------------------------------------------------------\n");
            for (Customer customer : allCustomers) {
                sb.append(String.format("%-20s %-10s %-15s %-15s\n",
                        customer.getCustomerName(), customer.getCustomerId(), customer.getPhoneNumber(), customer.getCustomerType()));
            }
            return sb.toString();
        }

        // ---------------------------------------------------
        // SAVE_SALES, buildJsonFromSalesMap, viewSalesLogs
        // ---------------------------------------------------
        private String saveSalesLogs() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can save sales logs.";
            }
            List<SaleRecord> allSales = SaleService.getAllSales();
            if (allSales.isEmpty()) {
                return "No sales to log.";
            }
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            Map<String, List<SaleRecord>> salesByBranch = new HashMap<>();
            Map<String, List<SaleRecord>> salesByType = new HashMap<>();

            for (SaleRecord sr : allSales) {
                salesByBranch.computeIfAbsent(sr.getBranch(), k -> new ArrayList<>()).add(sr);
                salesByType.computeIfAbsent(sr.getProductType(), k -> new ArrayList<>()).add(sr);
            }

            String branchJson = buildJsonFromSalesMap(salesByBranch);
            String typeJson = buildJsonFromSalesMap(salesByType);

            try (FileWriter fw1 = new FileWriter("logs/sales_by_branch.json");
                    FileWriter fw2 = new FileWriter("logs/sales_by_productType.json")) {
                fw1.write(branchJson);
                fw2.write(typeJson);
            } catch (IOException e) {
                e.printStackTrace();
                return "ERROR writing log files: " + e.getMessage();
            }
            return "SUCCESS!: sales logs saved in logs/sales_by_branch.json and logs/sales_by_productType.json.";
        }

        private String buildJsonFromSalesMap(Map<String, List<SaleRecord>> grouped) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            int size = grouped.size();
            int count = 0;
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

            for (String key : grouped.keySet()) {
                sb.append("  \"").append(key).append("\": [\n");
                List<SaleRecord> recs = grouped.get(key);
                for (int i = 0; i < recs.size(); i++) {
                    SaleRecord sr = recs.get(i);
                    sb.append("    {\n");
                    sb.append("      \"productId\": \"").append(sr.getProductId()).append("\",\n");
                    sb.append("      \"productName\": \"").append(sr.getProductName()).append("\",\n");
                    sb.append("      \"quantity\": ").append(sr.getQuantity()).append(",\n");
                    sb.append("      \"finalPrice\": ").append(sr.getFinalPrice()).append(",\n");
                    sb.append("      \"saleTime\": \"").append(sr.getSaleTime().format(dtf)).append("\"\n");
                    sb.append("    }");
                    if (i < recs.size() - 1) {
                        sb.append(",");
                    }
                    sb.append("\n");
                }
                sb.append("  ]");
                if (++count < size) {
                    sb.append(",");
                }
                sb.append("\n");
            }
            sb.append("}\n");
            return sb.toString();
        }

        private String viewSalesLogs() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can view sales logs.";
            }
            File logsDir = new File("logs");
            if (!logsDir.exists() || !logsDir.isDirectory()) {
                return "No logs/ folder found. Try SAVE_SALES first.";
            }
            File branchFile = new File(logsDir, "sales_by_branch.json");
            File typeFile = new File(logsDir, "sales_by_productType.json");
            if (!branchFile.exists() && !typeFile.exists()) {
                return "No sales log files found. Try SAVE_SALES first.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("\n---- VIEW SALES LOGS ----\n\n");

            if (branchFile.exists()) {
                sb.append("* Branch-Based Sales Logs *\n");
                sb.append(readWholeFile(branchFile)).append("\n");
            }
            if (typeFile.exists()) {
                sb.append("* Product-Type Sales Logs *\n");
                sb.append(readWholeFile(typeFile)).append("\n");
            }
            return sb.toString();
        }

        // ---------------------------------------------------
        // Chat commands (with NEW logging in each)
        // ---------------------------------------------------
        private String startChatCommand(String[] parts) {
            if (parts.length < 2) {
                return "Usage: START_CHAT <branchId>";
            }
            String myBranch = loggedInEmployee.getBranchId();
            String targetBranch = parts[1];
            if (myBranch.equals(targetBranch)) {
                return "ERROR: You cannot start a chat with your own branch.";
            }
            String chatId = chatService.startChat(myBranch, targetBranch);
            currentChatId = chatId;

            // Log that this employee started a chat
            String logMsg = String.format(
                    "CHAT: Employee '%s' (branch='%s') STARTED a chat with branch='%s'; chatId='%s'",
                    loggedInEmployee.getFullName(), myBranch, targetBranch, chatId);
            logAction(logMsg);

            return "Chat started with " + targetBranch + ". Current chat = " + chatId;
        }

        private String joinChatCommand(String[] parts) {
            if (parts.length < 2) {
                return "Usage: JOIN_CHAT <chatId>";
            }
            String chatId = parts[1];
            ChatSession session = ChatService.getChatById(chatId);
            if (session == null) {
                return "ERROR: No chat found with ID " + chatId;
            }
            if (!session.isActive()) {
                return "ERROR: That chat is no longer active.";
            }
            String userBranch = loggedInEmployee.getBranchId();
            if (!session.getBranchesInvolved().contains(userBranch)) {
                session.addBranch(userBranch);
            }
            currentChatId = chatId;

            // Log that this employee joined an existing chat
            String logMsg = String.format("CHAT: Employee '%s' (branch='%s') JOINED chatId='%s'",
                    loggedInEmployee.getFullName(), userBranch, chatId);
            logAction(logMsg);

            return "You joined chat " + chatId + ". Current chat set. Branches in chat: "
                    + session.getBranchesInvolved();
        }

        private String sendMessageCommand(String line) {
            String[] parts = line.split(" ", 2);
            if (parts.length < 2) {
                return "Usage: SEND_MSG <message...>";
            }
            if (currentChatId == null) {
                return "ERROR: No current chat set. Please START_CHAT or JOIN_CHAT first.";
            }
            String messageContent = parts[1];

            ChatSession session = ChatService.getChatById(currentChatId);
            if (session == null) {
                return "ERROR: Current chat not found or invalid. Re-join or start a new chat.";
            }
            if (!session.isActive()) {
                return "ERROR: Current chat is no longer active.";
            }
            String userBranch = loggedInEmployee.getBranchId();
            if (loggedInEmployee.getRole() != Role.ADMIN
                    && !session.getBranchesInvolved().contains(userBranch)) {
                return "ERROR: Your branch is not part of this chat, and you are not admin.";
            }

            // Create chat message
            ChatMessage msg = new ChatMessage(loggedInEmployee.getFullName(), userBranch, messageContent);
            session.addMessage(msg);

            // Log the SEND_MSG operation
            String logMsg = String.format("CHAT: Employee '%s' (branch='%s') SENT a message to chatId='%s': \"%s\"",
                    loggedInEmployee.getFullName(), userBranch, currentChatId, messageContent);
            logAction(logMsg);

            return "MESSAGE SENT to chat " + currentChatId + ": " + messageContent;
        }

        private String showChatWithoutParam() {
            if (currentChatId == null) {
                return "ERROR: No current chat set. Please START_CHAT or JOIN_CHAT first.";
            }
            ChatSession session = ChatService.getChatById(currentChatId);
            if (session == null) {
                return "ERROR: Current chat not found or invalid. Re-join or start a new chat.";
            }
            if (!session.isActive()) {
                return "ERROR: That chat is no longer active.";
            }
            String userBranch = loggedInEmployee.getBranchId();
            if (loggedInEmployee.getRole() != Role.ADMIN
                    && !session.getBranchesInvolved().contains(userBranch)) {
                return "ERROR: Your branch is not part of this chat, and you are not admin.";
            }
            if (session.getMessages().isEmpty()) {
                return "No messages yet in the current chat (" + currentChatId + ")";
            }

            // Log the SHOW_CHAT action
            String logMsg = String.format("CHAT: Employee '%s' (branch='%s') VIEWED chatId='%s'",
                    loggedInEmployee.getFullName(), userBranch, currentChatId);
            logAction(logMsg);

            StringBuilder sb = new StringBuilder();
            sb.append("Chat ").append(currentChatId).append(" messages:\n");
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            for (ChatMessage msg : session.getMessages()) {
                sb.append("[").append(msg.getTimestamp().format(dtf)).append("] ")
                        .append(msg.getSenderName()).append(" (").append(msg.getSenderBranch())
                        .append("): ").append(msg.getContent()).append("\n");
            }
            return sb.toString();
        }

        private String listChatsCommand() {
            if (loggedInEmployee.getRole() != Role.ADMIN) {
                return "ERROR: Only ADMIN can list all chats.";
            }
            Collection<ChatSession> allChats = ChatService.listAllChats();
            if (allChats.isEmpty()) {
                return "No active chats at the moment.";
            }
            StringBuilder sb = new StringBuilder("Active Chats:\n");
            for (ChatSession cs : allChats) {
                if (cs.isActive()) {
                    sb.append("ChatID: ").append(cs.getChatId())
                            .append(", Branches: ").append(cs.getBranchesInvolved())
                            .append("\n");
                }
            }
            return sb.toString();
        }

        // ... optional method: ACCESS_CHAT <chatId> (ADMIN only) ...
        // (not fully used in your code, but you might implement similarly)

    }

    // ===========================
    // FILE-LOADING METHODS
    // ===========================
    private void loadBranchesFromFile(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.println("No branches file found at " + filePath + ". Skipping branch load.");
            return;
        }
        String jsonContent = readWholeFile(f);
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            System.out.println("Branches file is empty. Skipping.");
            return;
        }
        String trimmed = jsonContent.trim();
        if (trimmed.startsWith("["))
            trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] objects = trimmed.split("\\},\\s*\\{");
        for (String objStr : objects) {
            String branchJson = objStr.trim();
            if (!branchJson.startsWith("{"))
                branchJson = "{" + branchJson;
            if (!branchJson.endsWith("}"))
                branchJson += "}";
            Branch b = parseBranchFromJson(branchJson);
            if (b != null) {
                branchService.addBranch(b);
            }
        }
        System.out.println("Loaded branches from file: " + filePath);
    }

    private Branch parseBranchFromJson(String json) {
        String branchId = extractJsonStringValue(json, "branchId");
        String branchName = extractJsonStringValue(json, "branchName");
        if (branchId == null || branchName == null) {
            System.out.println("Invalid Branch JSON: " + json);
            return null;
        }
        return new Branch(branchId, branchName);
    }

private void loadEmployeesFromFile(String filePath) {
    File f = new File(filePath);
    if (!f.exists()) return;

    String jsonContent = readWholeFile(f);
    if (jsonContent == null || jsonContent.trim().isEmpty()) return;

    String trimmed = jsonContent.trim();
    if (trimmed.startsWith("[")) trimmed = trimmed.substring(1);
    if (trimmed.endsWith("]")) trimmed = trimmed.substring(0, trimmed.length() - 1);

    String[] objectStrings = trimmed.split("\\},\\s*\\{");
    for (String objStr : objectStrings) {
        String empJson = objStr.trim();
        if (!empJson.startsWith("{")) empJson = "{" + empJson;
        if (!empJson.endsWith("}")) empJson += "}";

        Employee e = parseEmployeeFromJson(empJson);
        if (e == null) continue;

        // Add to services
        employeeService.addEmployee(e);
        authService.register(e, e.getUserName(), e.getPassword());
    }

    System.out.println("Loaded employees from file: " + filePath);
}

    private Employee parseEmployeeFromJson(String json) {
        String fullName = extractJsonStringValue(json, "fullName");
        String employeeId = extractJsonStringValue(json, "employeeId");
        String phoneNumber = extractJsonStringValue(json, "phoneNumber");
        String accountNumber = extractJsonStringValue(json, "accountNumber");
        int employeeNumber = extractJsonIntValue(json, "employeeNumber");
        String branchId = extractJsonStringValue(json, "branchId");
        String roleStr = extractJsonStringValue(json, "role");
        String userName = extractJsonStringValue(json, "userName");
        String password = extractJsonStringValue(json, "password");

        if (fullName == null || employeeId == null || roleStr == null) {
            System.out.println("Invalid employee JSON: missing required fields.\n" + json);
            return null;
        }

        Role role;
        try {
            role = Role.valueOf(roleStr.toUpperCase());
        } catch (Exception ex) {
            System.out.println("Invalid role: " + roleStr);
            return null;
        }
        Employee newEmp = new Employee(fullName, employeeId, phoneNumber, accountNumber, employeeNumber, branchId, role, userName, password);
        return newEmp;
    }


    private void loadProductsFromFile(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.println("No products file found at " + filePath + ". Skipping load.");
            return;
        }
        String jsonContent = readWholeFile(f);
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            System.out.println("Products file is empty. Skipping.");
            return;
        }

        String trimmed = jsonContent.trim();
        if (trimmed.startsWith("["))
            trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] objectStrings = trimmed.split("\\},\\s*\\{");
        for (String objStr : objectStrings) {
            String prodJson = objStr.trim();
            if (!prodJson.startsWith("{"))
                prodJson = "{" + prodJson;
            if (!prodJson.endsWith("}"))
                prodJson += "}";
            Product p = parseProductFromJson(prodJson);
            if (p != null) {
                productService.addOrUpdateProduct(p);
            }
        }
        System.out.println("Loaded products from file: " + filePath);
    }

    private Product parseProductFromJson(String json) {
        String id = extractJsonStringValue(json, "productId");
        String name = extractJsonStringValue(json, "productName");
        String category = extractJsonStringValue(json, "category");
        double price = extractJsonDoubleValue(json, "price");
        int quantity = extractJsonIntValue(json, "quantityInStock");
        String branch = extractJsonStringValue(json, "branchId");

        if (id == null || name == null) {
            System.out.println("Invalid product JSON: missing id or name.\n" + json);
            return null;
        }
        return new Product(id, name, category, price, quantity, branch);
    }

    private void loadCustomersFromFile(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.println("No customers file found at " + filePath + ". Skipping customer load.");
            return;
        }
        String jsonContent = readWholeFile(f);
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            System.out.println("Customers file is empty. Skipping.");
            return;
        }

        String trimmed = jsonContent.trim();
        if (trimmed.startsWith("["))
            trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] objectStrings = trimmed.split("\\},\\s*\\{");
        for (String objStr : objectStrings) {
            String customerJson = objStr.trim();
            if (!customerJson.startsWith("{"))
                customerJson = "{" + customerJson;
            if (!customerJson.endsWith("}"))
                customerJson += "}";
            Customer c = parseCustomerFromJson(customerJson);
            if (c != null) {
                customerService.addCustomer(c);
            }
        }
        System.out.println("Loaded customers from file: " + filePath);
    }

    private Customer parseCustomerFromJson(String json) {
        String name = extractJsonStringValue(json, "fullName");
        String id = extractJsonStringValue(json, "customerId");
        String phone = extractJsonStringValue(json, "phoneNumber");
        String type = extractJsonStringValue(json, "type");

        if (name == null || id == null || type == null) {
            System.out.println("Invalid customer JSON: missing required fields.\n" + json);
            return null;
        }

        Customer customer;
        switch (type.toUpperCase()) {
            case "NEW":
                customer = new NewCustomer(name, id, phone);
                break;
            case "RETURNING":
                customer = new ReturningCustomer(name, id, phone);
                break;
            case "VIP":
                customer = new VIPCustomer(name, id, phone);
                break;
            default:
                System.out.println("Invalid customer type: " + type + " in JSON:\n" + json);
                return null;
        }
        return customer;
    }

    /**
     * Reads an entire file into a single String.
     */
    private String readWholeFile(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Extracts a string value from Json.
     */
    private String extractJsonStringValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * Extracts an int value from Json.
     */
    private int extractJsonIntValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*(\\d+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * Extracts a double value from Json.
     */
    private double extractJsonDoubleValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*([\\d.]+)";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }

    // ===========================
    // SAVE PRODUCTS TO FILE
    // ===========================
    private void saveProductsToFile(String filePath) {
        List<Product> allProducts = productService.getAllProducts();
        String jsonArray = buildProductsJson(allProducts);

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(jsonArray);
            System.out.println("Products saved to file: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ERROR: Could not save products to file " + filePath);
        }
    }

    private String buildProductsJson(List<Product> products) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < products.size(); i++) {
            Product p = products.get(i);
            sb.append("  {\n");
            sb.append("    \"productId\": \"").append(p.getProductId()).append("\",\n");
            sb.append("    \"productName\": \"").append(p.getProductName()).append("\",\n");
            sb.append("    \"category\": \"").append(p.getCategory()).append("\",\n");
            sb.append("    \"price\": ").append(p.getPrice()).append(",\n");
            sb.append("    \"quantityInStock\": ").append(p.getQuantityInStock()).append(",\n");
            sb.append("    \"branchId\": \"").append(p.getBranch()).append("\"\n");
            sb.append("  }");
            if (i < products.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    // ===========================
    // SAVE EMPLOYEES TO FILE
    // ===========================
    private void saveEmployeesToFile(String filePath) {
        List<Employee> allEmployees = employeeService.listAllEmployees();
        String jsonArray = buildEmployeesJson(allEmployees);

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(jsonArray);
            System.out.println("Employees saved to file: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ERROR: Could not save employees to file " + filePath);
        }
    }

    private String buildEmployeesJson(List<Employee> employees) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < employees.size(); i++) {
            Employee e = employees.get(i);
            sb.append("  {\n");
            sb.append("    \"fullName\": \"").append(e.getFullName()).append("\",\n");
            sb.append("    \"employeeId\": \"").append(e.getEmployeeId()).append("\",\n");
            sb.append("    \"phoneNumber\": \"").append(e.getPhoneNumber()).append("\",\n");
            sb.append("    \"accountNumber\": \"").append(e.getAccountNumber()).append("\",\n");
            sb.append("    \"employeeNumber\": ").append(e.getEmployeeNumber()).append(",\n");
            sb.append("    \"branchId\": \"").append(e.getBranchId()).append("\",\n");
            sb.append("    \"role\": \"").append(e.getRole()).append("\",\n");
            sb.append("    \"userName\": \"").append(e.getUserName()).append("\",\n");
            sb.append("    \"password\": \"").append(e.getPassword()).append("\"\n");
            sb.append("  }");
            if (i < employees.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    // ===========================
    // SAVE CUSTOMERS TO FILE
    // ===========================
    private void saveCustomersToFile(String filePath) {
        List<Customer> allCustomers = customerService.listAllCustomers();
        String jsonArray = buildCustomersJson(allCustomers);

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(jsonArray);
            System.out.println("Customers saved to file: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("ERROR: Could not save customers to file " + filePath);
        }
    }

    private String buildCustomersJson(List<Customer> customers) {
        StringBuilder sb = new StringBuilder();
        sb.append("[\n");
        for (int i = 0; i < customers.size(); i++) {
            Customer c = customers.get(i);
            sb.append("  {\n");
            sb.append("    \"fullName\": \"").append(c.getCustomerName()).append("\",\n");
            sb.append("    \"customerId\": \"").append(c.getCustomerId()).append("\",\n");
            sb.append("    \"phoneNumber\": \"").append(c.getPhoneNumber()).append("\",\n");
            sb.append("    \"type\": \"").append(c.getCustomerType()).append("\"\n");
            sb.append("  }");
            if (i < customers.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    public static void main(String[] args) {
        ServerApp server = new ServerApp(3000);
        server.start();
    }
}