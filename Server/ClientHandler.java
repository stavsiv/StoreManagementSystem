package Server;
import Server.Utils.FileUtils;

import Models.*;
import Models.Employee;

import Services.*;
import Services.ChatService;
import Services.LogsService;
import Services.SaleService;
import java.io.*;
import java.util.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 class to handle client sessions.
 */

public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final AuthService authService;
    private final EmployeeService employeeService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final BranchService branchService;
    private final SaleService saleService;
    private final ChatService chatService;

    // logging appended actions to "logs/actions.log"
    private static final String ACTION_LOG_FILE = "Logs/actions.log";

    private Employee loggedInEmployee;
    private String currentUsername;

    // Keep track of the current chat ID the user is in
    private String currentChatId = null;

    // Constructor: get socket and services from ServerApp
    public ClientHandler(Socket socket, AuthService authService, EmployeeService employeeService,
                         ProductService productService, CustomerService customerService, BranchService branchService, SaleService saleService, ChatService chatService) {
        this.clientSocket = socket;
        this.authService = authService;
        this.employeeService = employeeService;
        this.productService = productService;
        this.customerService = customerService;
        this.branchService = branchService;
        this.saleService = saleService;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
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
                clientSocket.close();
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
            boolean dirCreated = logsDir.mkdirs();
            if (!dirCreated) {
                System.err.println("ERROR: Failed to create logs directory.");
                return;
            }
        }

        String timestamp = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        String finalLine = "[" + timestamp + "] " + action;
        File logFile = new File(ACTION_LOG_FILE);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(finalLine + "\n");
        } catch (IOException e) {
            System.err.println("ERROR: Could not write to actions.log - " + e.getMessage());
        }
    }

    private boolean login(BufferedReader in, PrintWriter out) throws IOException {
        out.println("Welcome to the Store Management System!");
        out.println("Please enter your username:");
        String username = in.readLine().trim();

        if (username == null)
            return false;

        out.println("Please enter your password:");
        String password = in.readLine().trim();
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

    // NEW COMMAND: LOGS_TO_WORD
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

    // ADD_EMPLOYEE command (with logging)
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
            if (!nameBuilder.isEmpty()) nameBuilder.append(" ");
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
            // Save updated employees list using FileUtils
            FileUtils.saveToFile(ServerApp.EMPLOYEES_FILE, employeeService.listAllEmployees(), e -> String.format(
                    """
                              {
                                "fullName": "%s",
                                "employeeId": "%s",
                                "phoneNumber": "%s",
                                "accountNumber": "%s",
                                "employeeNumber": %d,
                                "branchId": "%s",
                                "role": "%s",
                                "userName": "%s",
                                "password": "%s"
                              }\
                            """,
                    e.getFullName(), e.getEmployeeId(), e.getPhoneNumber(), e.getAccountNumber(),
                    e.getEmployeeNumber(), e.getBranchId(), e.getRole(), e.getUserName(), e.getPassword()
            ));

            return String.format("Employee has been successfully added to the system: Name: %s,Id: %s, Branch: %s, Role: %s",
                    newEmp.getFullName(), newEmp.getEmployeeId(), newEmp.getBranchId(), newEmp.getRole());


        } catch (IllegalArgumentException ex) {
            return "ERROR: " + ex.getMessage();
        } catch (Exception ex) {
            return "ERROR: Invalid input or role.";
        }
    }

    // SELL command (with logging) (Employee sells product to a customer)
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
        FileUtils.saveToFile(ServerApp.PRODUCTS_FILE, productService.getAllProducts(), p -> String.format(
                """
                          {
                            "productId": "%s",
                            "productName": "%s",
                            "category": "%s",
                            "price": %.2f,
                            "quantityInStock": %d,
                            "branchId": "%s"
                          }\
                        """,
                p.getProductId(), p.getProductName(), p.getCategory(),
                p.getPrice(), p.getQuantityInStock(), p.getBranch()
        ));


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


    // PURCHASE_PRODUCT command (with logging) - (Employee buys new stock from supplier or adds a new product to the branch.)
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
            FileUtils.saveToFile(ServerApp.PRODUCTS_FILE, productService.getAllProducts(), p -> String.format(
                    """
                              {
                                "productId": "%s",
                                "productName": "%s",
                                "category": "%s",
                                "price": %.2f,
                                "quantityInStock": %d,
                                "branchId": "%s"
                              }\
                            """,
                    p.getProductId(), p.getProductName(), p.getCategory(),
                    p.getPrice(), p.getQuantityInStock(), p.getBranch()
            ));

            logAction(String.format("PURCHASE: Employee '%s' in branch '%s' added %d to product '%s'",
                    loggedInEmployee.getFullName(), branch, quantity, productId));

            return "Product stock successfully updated: " + productId +
                    " increased by " + quantity +
                    ". New total = " + existing.getQuantityInStock();
        } else {
            Product newProduct = new Product(productId, productName, category, price, quantity, branch);
            productService.addOrUpdateProduct(newProduct);
            FileUtils.saveToFile(ServerApp.PRODUCTS_FILE, productService.getAllProducts(), p -> String.format(
                    """
                              {
                                "productId": "%s",
                                "productName": "%s",
                                "category": "%s",
                                "price": %.2f,
                                "quantityInStock": %d,
                                "branchId": "%s"
                              }\
                            """,
                    p.getProductId(), p.getProductName(), p.getCategory(),
                    p.getPrice(), p.getQuantityInStock(), p.getBranch()
            ));

            logAction(String.format("PURCHASE: Employee '%s' in branch '%s' created new product '%s' (id=%s) qty=%d",
                    loggedInEmployee.getFullName(), branch, productName, productId, quantity));

            return "Product has been successfully created: " + productName +
                    " (ID=" + productId + ") with initial stock of " + quantity + ".";
        }
    }


    // ADD_CUSTOMER command (with logging)
    private String addCustomerCommand(String[] parts) {
        if (parts.length < 5) {
            return "Usage: ADD_CUSTOMER <fullName> <id> <phone> <type> (NEW, RETURNING, VIP)";
        }

        try {
            int currentIndex = 1;
            StringBuilder nameBuilder = new StringBuilder();
            while (currentIndex < parts.length && !parts[currentIndex].matches("\\d{9}")) {
                if (!nameBuilder.isEmpty()) {
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

            FileUtils.saveToFile(ServerApp.CUSTOMERS_FILE, customerService.listAllCustomers(), c -> String.format(
                    """
                              {
                                "fullName": "%s",
                                "customerId": "%s",
                                "phoneNumber": "%s",
                                "type": "%s"
                              }\
                            """,
                    c.getCustomerName(), c.getCustomerId(), c.getPhoneNumber(), c.getCustomerType()
            ));

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

    // showEmployees, showProducts, showCustomers
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

    // SAVE_SALES, buildJsonFromSalesMap, viewSalesLogs
    private String saveSalesLogs() {
        if (loggedInEmployee.getRole() != Role.ADMIN) {
            return "ERROR: Only ADMIN can save sales logs.";
        }
        List<SaleService.SaleRecord> allSales = SaleService.getAllSales();
        if (allSales.isEmpty()) {
            return "No sales to log.";
        }
        File logsDir = new File("logs");
        if (!logsDir.exists()) {
            boolean created = logsDir.mkdirs();
            if (!created) {
                return "ERROR: Failed to create logs directory.";
            }
        }

        Map<String, List<SaleService.SaleRecord>> salesByBranch = new HashMap<>();
        Map<String, List<SaleService.SaleRecord>> salesByType = new HashMap<>();

        for (SaleService.SaleRecord sr : allSales) {
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

    private String buildJsonFromSalesMap(Map<String, List<SaleService.SaleRecord>> grouped) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int size = grouped.size();
        int count = 0;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        for (String key : grouped.keySet()) {
            sb.append("  \"").append(key).append("\": [\n");
            List<SaleService.SaleRecord> recs = grouped.get(key);
            for (int i = 0; i < recs.size(); i++) {
                SaleService.SaleRecord sr = recs.get(i);
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
            sb.append(FileUtils.readWholeFile(branchFile)).append("\n");
        }
        if (typeFile.exists()) {
            sb.append("* Product-Type Sales Logs *\n");
            sb.append(FileUtils.readWholeFile(typeFile)).append("\n");
        }
        return sb.toString();
    }

    // Chat commands (with NEW logging in each)
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
        ChatService.ChatSession session = ChatService.getChatById(chatId);
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

        ChatService.ChatSession session = ChatService.getChatById(currentChatId);
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
        ChatService.ChatMessage msg = new ChatService.ChatMessage(loggedInEmployee.getFullName(), userBranch, messageContent);
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
        ChatService.ChatSession session = ChatService.getChatById(currentChatId);
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
        for (ChatService.ChatMessage msg : session.getMessages()) {
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
        Collection<ChatService.ChatSession> allChats = ChatService.listAllChats();
        if (allChats.isEmpty()) {
            return "No active chats at the moment.";
        }
        StringBuilder sb = new StringBuilder("Active Chats:\n");
        for (ChatService.ChatSession cs : allChats) {
            if (cs.isActive()) {
                sb.append("ChatID: ").append(cs.getChatId())
                        .append(", Branches: ").append(cs.getBranchesInvolved())
                        .append("\n");
            }
        }
        return sb.toString();
    }

}