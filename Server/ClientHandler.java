package Server;

import Exceptions.CustomExceptions;
import Server.Utils.FileUtils;
import Models.*;
import Services.*;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Handles client sessions and commands in the Store Management System.
 * Improved exception handling for more robust operations.
 */
public class ClientHandler implements Runnable {
    private final PrintWriter out;
    private final Socket clientSocket ;
    private final AuthService authService;
    private final EmployeeService employeeService;
    private final ProductService productService;
    private final CustomerService customerService;
    private final SaleService saleService;
    private final ChatService chatService;

    private static final String ACTION_LOG_FILE = "Logs/actions.log";

    private Employee loggedInEmployee;
    private String currentUsername;
    private String currentChatId = null;

    public ClientHandler(Socket clientSocket,
                         AuthService authService,
                         EmployeeService employeeService,
                         ProductService productService,
                         CustomerService customerService,
                         SaleService saleService,
                         ChatService chatService) throws IOException {
        this.clientSocket = clientSocket;
        this.out = new PrintWriter(clientSocket.getOutputStream(), true); // init once
        this.authService = authService;
        this.employeeService = employeeService;
        this.productService = productService;
        this.customerService = customerService;
        this.saleService = saleService;
        this.chatService = chatService;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            while (true) {
                if (!login(in, out)) break;
                if (!handleCommands(in, out)) break;
            }
        } catch (IOException e) {
            System.err.println("IO ERROR: " + e.getMessage());
        } finally {
            if (currentUsername != null) authService.logout(currentUsername);
            try {
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            out.println("Client disconnected.");
        }
    }

    // Logging
    private void logAction(String action) {
        File logsDir = new File("logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            System.err.println("ERROR: Failed to create logs directory.");
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        String finalLine = "[" + timestamp + "] " + action;

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(ACTION_LOG_FILE, true))) {
            writer.write(finalLine + "\n");
        } catch (IOException e) {
            System.err.println("ERROR: Could not write to actions.log - " + e.getMessage());
        }
    }

    // Login
    private boolean login(BufferedReader in, PrintWriter out) throws IOException {
        out.println("Welcome to the Store Management System!");

        int attempts = 0;
        final int MAX_ATTEMPTS = 3;

        while (attempts < MAX_ATTEMPTS) {
            out.println("Please enter your username:");
            String username = in.readLine();
            if (username == null) return false;

            out.println("Please enter your password:");
            String password = in.readLine();
            if (password == null) return false;

            try {
                Employee loggedInEmployee = authService.login(username.trim(), password.trim());
                if (loggedInEmployee == null) {
                    out.println("ERROR: Invalid credentials or user already logged in.");
                    attempts++;
                    continue;
                }

                this.loggedInEmployee = loggedInEmployee;
                this.currentUsername = username;
                out.println("Login successful! Hello, " + loggedInEmployee.getFullName() +
                        " (Role: " + loggedInEmployee.getRole() + ", Branch: " + loggedInEmployee.getBranchId() + ")");
                String currentSessionId = UUID.randomUUID().toString();
                String branchId = loggedInEmployee.getBranchId();
                chatService.addConnectedUser(
                        branchId,
                        currentSessionId,
                        msg -> out.println("[NOTIFY] " + msg)
                );

                return true;

            } catch (CustomExceptions.InvalidPasswordException | CustomExceptions.InvalidUsernameException e) {
                out.println("ERROR: " + e.getMessage());
                attempts++;
            }

        }

        out.println("ERROR: Maximum login attempts reached. Closing connection...");
        return false;
    }

    // Command Handling
    private boolean handleCommands(BufferedReader in, PrintWriter out) throws IOException {
        out.println("Type 'Menu' to see available commands, or 'Exit' to exit.");

        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (line.equalsIgnoreCase("Exit")) {
                out.println("Goodbye!");
                return false;
            }

            try {
                String response = handleCommand(line);
                out.println(response);
                if (response.contains("Returning to login screen")) return true;
            } catch (CustomExceptions.ProductException | CustomExceptions.EmployeeException |
                     CustomExceptions.CustomerException e) {
                out.println("ERROR: " + e.getMessage());
            } catch (Exception e) {
                out.println("ERROR: Unexpected error occurred - " + e.getMessage());
            }
        }
        return false;
    }

    private String handleCommand(String line) throws CustomExceptions.ProductException, CustomExceptions.EmployeeException, CustomExceptions.CustomerException {
        if (line.isEmpty()) return "";

        if (line.toUpperCase().startsWith("SEND_MSG")) return handleSendMsg(line);

        String[] parts = line.split(" ");
        String command = parts[0].toUpperCase().replace(" ", "_");

        return switch (command) {
            case "MENU" -> showMenu();
            case "ADD_EMPLOYEE" -> addEmployeeCommand(parts);
            case "SHOW_EMPLOYEES" -> showEmployees();
            case "SHOW_PRODUCTS" -> showProducts();
            case "SELL" -> sellProductCommand(parts);
            case "PURCHASE_PRODUCT" -> purchaseProductCommand(parts);
            case "SAVE_SALES" -> saveSalesLogs();
            case "VIEW_SALES_LOGS" -> viewSalesLogs();
            case "ADD_CUSTOMER" -> addCustomerCommand(parts);
            case "SHOW_CUSTOMERS" -> showCustomers();
            case "LOGS_TO_WORD" -> logsToWordCommand();
            case "CHECK_INVITES" -> handleCheckInvites();
            case "START_CHAT" -> handleStartChat(parts);
            case "JOIN_CHAT" -> handleJoinChat(parts);
            case "SHOW_CHAT" -> handleShowChat();
            case "LEAVE_CHAT" -> handleLeaveChat(parts);
            case "END_CHAT" -> handleEndChat();
            case "LIST_CHATS" -> listChatsCommand();
            case "LOGOUT" -> {
                authService.logout(currentUsername);
                loggedInEmployee = null;
                currentUsername = null;
                yield "You have been logged out. Returning to login screen...";
            }
            case "Exit" -> "Goodbye!";
            default -> "Unknown command: " + command + ". Type Menu for commands.";
        };
    }

    // Menu
    private String showMenu() {
        StringBuilder sb = new StringBuilder("=== COMMAND MENU ===\n");

        if (loggedInEmployee.getRole() == Role.ADMIN) {
            sb.append("SHOW_EMPLOYEES - display all employees\n");
            sb.append("SHOW_CUSTOMERS - display all customers\n");
            sb.append("SHOW_PRODUCTS - display all products\n");
            sb.append("ADD_EMPLOYEE <FullName> <Id> <Phone> <BankAccount> <EmpNum> <Branch> <Role> <Username> <Password>\n");
            sb.append("ADD_CUSTOMER <Name> <Id> <Phone> <Type> (NEW, RETURNING, VIP)\n");
            sb.append("SAVE_SALES - save sales logs to JSON\n");
            sb.append("VIEW_SALES_LOGS - view saved logs\n");
            sb.append("LOGS_TO_WORD - convert logs to Word doc\n");
        } else {
            sb.append("SHOW_PRODUCTS - display products in your branch\n");
            sb.append("SHOW_CUSTOMERS - display all customers\n");
            sb.append("ADD_CUSTOMER <Name> <Id> <Phone> <Type> (NEW, RETURNING, VIP)\n");
            sb.append("SELL <ProductId> <Quantity> <CustomerId>\n");
            sb.append("PURCHASE_PRODUCT <ProductId> <ProductName> <Category> <Price> <Quantity> <Branch>\n");
        }

        sb.append("LOGOUT - logout\n");
        sb.append("EXIT - close connection\n");
        sb.append("--- Chat Commands ---\n");
        sb.append("CHECK_INVITES\n");
        sb.append("START_CHAT <BranchId>\n");
        sb.append("JOIN_CHAT <ChatId>\n");
        sb.append("SEND_MSG <Message...>\n");
        sb.append("SHOW_CHAT\n");
        sb.append("LEAVE_CHAT <ChatId>\n");
        sb.append("END_CHAT\n");
        sb.append("LIST_CHATS\n");
        sb.append("====================");

        return sb.toString();
    }

    // Reuse Functions
    // 1. בדיקת הרשאות ומספר פרמטרים
    private String validateCommand(Role requiredRole, String[] parts, int minParams) {
        if (requiredRole != null && loggedInEmployee.getRole() != requiredRole) {
            return "ERROR: Only " + requiredRole + " can execute this command.";
        }
        if (parts.length < minParams) {
            return "ERROR: Not all parameters provided. Expected at least " + (minParams - 1) +
                    " parameters after the command.";
        }
        return null;
    }

    // 2. פורמט אחיד להודעות צ'אט
    private String formatChatMessage(ChatService.ChatMessage msg) {
        return String.format("[NEW MSG][%s] %s (%s): %s",
                msg.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                msg.getSenderName(),
                msg.getSenderBranch(),
                msg.getContent());
    }

    // 3. בדיקת צ'אט פעיל
    private ChatService.ChatSession getActiveChat(String chatId) throws CustomExceptions.ChatException {
        ChatService.ChatSession session = chatService.getChatById(chatId);
        if (session == null || !session.isActive())
            throw new CustomExceptions.ChatInactiveException("Chat " + chatId + " is not active or does not exist.");
        return session;
    }

    // 4. בדיקת אם Branch חלק מהצ'אט
    private boolean isBranchInChat(ChatService.ChatSession session, String branchId) {
        return !session.getBranchesInvolved().contains(branchId);
    }

    // 5. שליחת הודעת מערכת לכל המשתתפים, אפשר להחריג branch מסוים
    private void broadcastSystemMessage(ChatService.ChatSession session, String content, String excludeBranch) {
        ChatService.ChatMessage msg = new ChatService.ChatMessage("SYSTEM", "SYSTEM", content);
        for (String branch : session.getBranchesInvolved()) {
            if (!branch.equals(excludeBranch)) {
                session.notifyBranch(branch, msg);
            }
        }
        session.addMessage(msg);
    }

    // 6. הרשמה פשוטה של listener חדש
    private void registerChatListener(ChatService.ChatSession session) {
        String userBranch = loggedInEmployee.getBranchId();
        session.addBranch(userBranch, msg -> {
            try {
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println(formatChatMessage(msg));
            } catch (Exception e) {
                System.err.println("Failed to process incoming chat message: " + e.getMessage());
            }
        });
    }

    // Employee commands
    private String addEmployeeCommand(String[] parts) {
        String validationError = validateCommand(Role.ADMIN, parts, 10);
        if (validationError != null) return validationError;

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
            int empNum = Integer.parseInt(parts[currentIndex++]);
            String branch = parts[currentIndex++];
            Role newEmpRole = Role.valueOf(parts[currentIndex++].toUpperCase());
            String newUsername = parts[currentIndex++];
            String newPassword = parts[currentIndex++];

            Employee newEmp = new Employee(fullName, id, phone, bankAcc, empNum, branch, newEmpRole, newUsername, newPassword);

            employeeService.addEmployee(newEmp);
            authService.register(newEmp, newUsername, newPassword);

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
                            }
                            """,
                    e.getFullName(), e.getEmployeeId(), e.getPhoneNumber(), e.getAccountNumber(),
                    e.getEmployeeNumber(), e.getBranchId(), e.getRole(), e.getUserName(), e.getPassword()
            ));

            logAction(String.format("Employee added: %s (Id=%s, Branch=%s, Role=%s)", fullName, id, branch, newEmpRole));
            return "Employee successfully added: " + fullName;

        } catch (CustomExceptions.EmployeeException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception ex) {
            return "ERROR: Invalid input format.";
        }
    }

    // Sell & Purchase
    private String sellProductCommand(String[] parts) {
        String validationError = validateCommand(null, parts, 4);
        if (validationError != null) return validationError;

        try {
            String productId = parts[1];
            int qty = Integer.parseInt(parts[2]);
            String customerId = parts[3];

            Customer customer = customerService.getCustomerById(customerId);
            if (customer == null) return "ERROR: No customer found with Id " + customerId;

            Product product = productService.getProductByIdAndBranch(productId, loggedInEmployee.getBranchId());
            if (product == null) return "ERROR: Product not found in your branch.";

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
                            }
                            """, p.getProductId(), p.getProductName(), p.getCategory(), p.getPrice(), p.getQuantityInStock(), p.getBranch()
            ));

            logAction(String.format("SELL: Employee '%s' sold product '%s' qty=%d to customer '%s'",
                    loggedInEmployee.getFullName(), productId, qty, customer.getCustomerName()));

            return String.format("Transaction completed! Sold %d units of '%s' to %s. Final price: %.2f",
                    qty, product.getProductName(), customer.getCustomerName(), finalPrice);

        } catch (NumberFormatException e) {
            return "ERROR: Quantity must be an integer.";
        } catch (CustomExceptions.ProductException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    private String purchaseProductCommand(String[] parts) throws CustomExceptions.ProductException {
        String validationError = validateCommand(null, parts, 7);
        if (validationError != null) return validationError;

        try {
            String productId = parts[1];
            String productName = parts[2];
            String category = parts[3];
            double price = Double.parseDouble(parts[4]);
            int quantity = Integer.parseInt(parts[5]);
            String branch = parts[6];

            if (!loggedInEmployee.getBranchId().equalsIgnoreCase(branch))
                return "ERROR: You can't purchase products for a different branch.";

            Product existing = productService.getProductByIdAndBranch(productId, branch);
            if (existing != null) {
                productService.addOrUpdateProduct(existing, quantity);
                logAction(String.format("PURCHASE: Employee '%s' added %d to product '%s'",
                        loggedInEmployee.getFullName(), quantity, productId));
                return "Product stock updated. New total: " + existing.getQuantityInStock();
            } else {
                Product newProduct = new Product(productId, productName, category, price, quantity, branch);
                productService.addOrUpdateProduct(newProduct, 0);
                logAction(String.format("PURCHASE: Employee '%s' created new product '%s' (id=%s) qty=%d",
                        loggedInEmployee.getFullName(), productName, productId, quantity));
                return "Product created: " + productName + " (Id=" + productId + ") with stock " + quantity;
            }
        } catch (NumberFormatException e) {
            return "ERROR: Price or Quantity invalid format.";
        }
    }

    // Customers
    private String addCustomerCommand(String[] parts) {
        String validationError = validateCommand(null, parts, 5);
        if (validationError != null) {
            return validationError;
        }

        try {
            int idx = 1;
            StringBuilder nameBuilder = new StringBuilder();

            while (idx < parts.length && !parts[idx].matches("\\d{9}")) {
                if (!nameBuilder.isEmpty()) nameBuilder.append(" ");
                nameBuilder.append(parts[idx++]);
            }

            String fullName = nameBuilder.toString();
            String customerId = parts[idx++];
            String phone = parts[idx++];
            String type = parts[idx].toUpperCase();

            if (!customerId.matches("\\d{9}")) {
                return "ERROR: Invalid Id format. Must be exactly 9 digits.";
            }
            if (!phone.matches("\\d{10}")) {
                return "ERROR: Invalid phone format. Must be exactly 10 digits.";
            }

            Customer customer = switch (type) {
                case "NEW" -> new NewCustomer(fullName, customerId, phone);
                case "RETURNING" -> new ReturningCustomer(fullName, customerId, phone);
                case "VIP" -> new VIPCustomer(fullName, customerId, phone);
                default -> null;
            };

            if (customer == null) {
                return "ERROR: Unknown customer type: " + type + ". Allowed: NEW, RETURNING, VIP";
            }
            customerService.addCustomer(customer);

            FileUtils.saveToFile(
                    ServerApp.CUSTOMERS_FILE,
                    customerService.listAllCustomers(),
                    c -> String.format(
                            """
                                    {
                                      "fullName": "%s",
                                      "customerId": "%s",
                                      "phoneNumber": "%s",
                                      "type": "%s"
                                    }
                                    """,
                            c.getCustomerName(), c.getCustomerId(), c.getPhoneNumber(), c.getCustomerType()
                    )
            );

            logAction(String.format("Customer added: %s (Id=%s, Type=%s)", fullName, customerId, type));
            return "Customer successfully added: " + fullName;

        } catch (CustomExceptions.CustomerException e) {
            return "ERROR: " + e.getMessage() + " already exists.";
        } catch (IllegalArgumentException e) {
            return "ERROR: " + e.getMessage();
        } catch (Exception e) {
            return "ERROR: Failed to add customer - " + e.getMessage();
        }
    }

    // Show methods
    private String showEmployees() {
        if (loggedInEmployee.getRole() != Role.ADMIN) return "ERROR: Only ADMIN can view all employees.";
        List<Employee> all = employeeService.listAllEmployees();
        if (all.isEmpty()) return "No employees found.";

        StringBuilder sb = new StringBuilder(String.format("%-20s %-12s %-15s %-15s %-8s %-10s %-15s %-12s\n",
                "Name", "Id", "Phone", "BankAccount", "Branch", "EmpNum", "Role", "Username"));
        sb.append("----------------------------------------------------------------------------------------------------------------\n");

        for (Employee emp : all) {
            sb.append(String.format("%-20s %-12s %-15s %-15s %-8s %-10d %-15s %-12s\n",
                    emp.getFullName(), emp.getEmployeeId(), emp.getPhoneNumber(), emp.getAccountNumber(),
                    emp.getBranchId(), emp.getEmployeeNumber(), emp.getRole(), emp.getUserName()));
        }
        return sb.toString();
    }

    private String showProducts() {
        List<Product> productsToShow = loggedInEmployee.getRole() == Role.ADMIN
                ? productService.getAllProducts()
                : productService.getProductsByBranch(loggedInEmployee.getBranchId());

        if (productsToShow.isEmpty()) return "No products found for branch " + loggedInEmployee.getBranchId();
        return productService.formatProductList(productsToShow);
    }

    private String showCustomers() {
        List<Customer> allCustomers = customerService.listAllCustomers();
        if (allCustomers.isEmpty()) return "No customers found.";

        StringBuilder sb = new StringBuilder(String.format(
                "%-20s %-10s %-12s %-10s %-8s\n", "Name", "Id", "Phone", "Type", "Discount"));
        sb.append("---------------------------------------------------------------\n");

        for (Customer customer : allCustomers) {
            String type = customer.getCustomerType();
            String discount = switch (type.toLowerCase()) {
                case "returning" -> "10%";
                case "vip" -> "30%";
                default -> "0%";
            };

            sb.append(String.format(
                    "%-20s %-10s %-12s %-10s %-8s\n",
                    customer.getCustomerName(),
                    customer.getCustomerId(),
                    customer.getPhoneNumber(),
                    type,
                    discount
            ));
        }

        return sb.toString();
    }

    // Sales Logs
    private String logsToWordCommand() {
        if (loggedInEmployee.getRole() != Role.ADMIN) {
            return "ERROR: Only ADMIN can convert logs to Word.";
        }

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
            String msg = "ERROR converting logs to Word: " + e.getMessage();
            logAction(msg);
            return msg;
        }

        String logMsg = String.format(
                "ADMIN '%s' converted logs to Word doc at '%s'.",
                loggedInEmployee.getFullName(), outputDoc);
        logAction(logMsg);

        return "SUCCESS!: logs converted to " + outputDoc;
    }

    private String saveSalesLogs() {
        if (loggedInEmployee.getRole() != Role.ADMIN) {
            return "ERROR: Only ADMIN can save sales logs.";
        }

        List<SaleService.SaleRecord> allSales = SaleService.getAllSales();
        if (allSales.isEmpty()) {
            return "No sales to log.";
        }

        File logsDir = new File("logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            return "ERROR: Failed to create logs directory.";
        }

        Map<String, List<SaleService.SaleRecord>> salesByBranch = new HashMap<>();
        Map<String, List<SaleService.SaleRecord>> salesByType = new HashMap<>();

        for (SaleService.SaleRecord sr : allSales) {
            salesByBranch.computeIfAbsent(sr.getBranch(), _ -> new ArrayList<>()).add(sr);
            salesByType.computeIfAbsent(sr.getProductType(), _ -> new ArrayList<>()).add(sr);
        }

        String branchJson = buildJsonFromSalesMap(salesByBranch);
        String typeJson = buildJsonFromSalesMap(salesByType);

        try (FileWriter fw1 = new FileWriter("logs/sales_by_branch.json");
             FileWriter fw2 = new FileWriter("logs/sales_by_productType.json")) {
            fw1.write(branchJson);
            fw2.write(typeJson);
        } catch (IOException e) {
            String msg = "ERROR writing log files: " + e.getMessage();
            logAction(msg);
            return msg;
        }

        logAction("Sales logs saved by ADMIN " + loggedInEmployee.getFullName());
        return "SUCCESS!: sales logs saved in logs/sales_by_branch.json and logs/sales_by_productType.json.";
    }

    private String buildJsonFromSalesMap(Map<String, List<SaleService.SaleRecord>> grouped) {
        if (grouped == null || grouped.isEmpty()) return "{}";

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
                if (i < recs.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("  ]");
            if (++count < size) sb.append(",");
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

        StringBuilder sb = new StringBuilder("\n---- VIEW SALES LOGS ----\n\n");

        if (branchFile.exists()) {
            sb.append("* Branch-Based Sales Logs *\n");
            sb.append(FileUtils.readWholeFile(branchFile)).append("\n");
        }
        if (typeFile.exists()) {
            sb.append("* Product-Type Sales Logs *\n");
            sb.append(FileUtils.readWholeFile(typeFile)).append("\n");
        }

        logAction("Sales logs viewed by ADMIN " + loggedInEmployee.getFullName());
        return sb.toString();
    }

    // Chat Commands
    // START CHAT
    private String handleStartChat(String[] parts) {
        String err = validateCommand(null, parts, 2);
        if (err != null) return err;

        String targetBranch = parts[1];
        String userBranch = loggedInEmployee.getBranchId();

        if (userBranch.equals(targetBranch))
            return "ERROR: Starting a chat with your own branch is not allowed. Please choose a different branch.";

        try {
            String response = chatService.startChat(userBranch, targetBranch, null);
            if (response == null) return "[NOTIFY] Something went wrong. Invite saved if needed.";
            if (response.contains("Chat started with")) {
                String chatId = response.substring(response.indexOf("ChatID: ") + 8);
                ChatService.ChatSession session = chatService.getChatById(chatId);
                registerChatListener(session);
                currentChatId = chatId;
            }

            return response;

        } catch (CustomExceptions.ChatBranchBusyException e) {
            return "[NOTIFY] " + e.getMessage();
        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: Oops! Something went wrong while starting the chat.";
        }
    }

    // JOIN CHAT
    private String handleJoinChat(String[] parts) {
        String err = validateCommand(null, parts, 2);
        if (err != null) return err;

        String chatId = parts[1];
        String userBranch = loggedInEmployee.getBranchId();
        ChatService.ChatSession session;
        try {
            session = getActiveChat(chatId);
        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }

        boolean isNewJoin = isBranchInChat(session, userBranch);
        if (isNewJoin) {
            broadcastSystemMessage(session, "Branch " + userBranch + " joined the chat.", userBranch);
        }

        registerChatListener(session);
        currentChatId = chatId;

        return "You joined chat " + chatId + ". Branches: " + session.getBranchesInvolved()
                + ".\nTo see the chat history so far, type: SHOW_CHAT";
    }

    // SEND MSG
    private String handleSendMsg(String line) {
        String[] parts = line.split(" ", 2);
        String err = validateCommand(null, parts, 2);
        if (err != null) return "[ERROR] " + err;

        if (currentChatId == null) {
            return "[ERROR] No current chat selected. Use START_CHAT or JOIN_CHAT first.";
        }

        ChatService.ChatSession session;
        try {
            session = getActiveChat(currentChatId);
        } catch (CustomExceptions.ChatException e) {
            return "[ERROR] " + e.getMessage();
        }

        String userBranch = loggedInEmployee.getBranchId();
        ChatService.ChatMessage msg = new ChatService.ChatMessage(
                loggedInEmployee.getFullName(),
                userBranch,
                parts[1]
        );

        session.addMessage(msg);

        return "Message sent successfully";
    }

    // LEAVE CHAT
    private String handleLeaveChat(String[] parts) {
        String chatId = (parts.length > 1) ? parts[1] : currentChatId;
        if (chatId == null) return "[ERROR] Please provide a chat ID.";

        String userBranch = loggedInEmployee.getBranchId();
        ChatService.ChatSession session;
        try {
            session = getActiveChat(chatId);
        } catch (CustomExceptions.ChatException e) {
            return "[ERROR] " + e.getMessage();
        }

        if (isBranchInChat(session, userBranch))
            return "[ERROR] Your branch is not part of chat " + chatId;

        try {
            chatService.leaveChat(chatId, userBranch);

            if (chatId.equals(currentChatId)) {
                currentChatId = null;
            }

            if (session.getBranchesInvolved().size() == 1) {
                String remainingBranch = session.getBranchesInvolved().iterator().next();
                if (remainingBranch.equals(userBranch)) {
                    return "[NOTIFY] You are now alone in chat " + chatId + ". Type END_CHAT to close it.";
                }
            }

            return "[NOTIFY] You left the chat " + chatId;

        } catch (CustomExceptions.ChatException e) {
            return "[ERROR] " + e.getMessage();
        }
    }

    // END CHAT
    private String handleEndChat() {
        if (currentChatId == null) return "ERROR: No chat selected.";
        chatService.endChat(currentChatId);
        String ended = currentChatId;
        currentChatId = null;
        return "Chat " + ended + " has been ended.";
    }

    // SHOW CHAT
    private String handleShowChat() {
        if (currentChatId == null) return "ERROR: No chat selected.";

        ChatService.ChatSession session;
        try {
            session = getActiveChat(currentChatId);
        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }

        if (session.getMessages().isEmpty()) return "No messages yet in chat " + currentChatId;

        StringBuilder sb = new StringBuilder("Chat " + currentChatId + " messages:\n");
        for (ChatService.ChatMessage msg : session.getMessages()) {
            sb.append(formatChatMessage(msg)).append("\n");
        }
        return sb.toString();
    }

    // CHECK INVITES
    private String handleCheckInvites() {
        String branchId = loggedInEmployee.getBranchId();
        Queue<String> invites = chatService.getPendingInvites(branchId);
        if (invites.isEmpty()) return "No pending invites.";

        StringBuilder sb = new StringBuilder("Pending chat invites from: ");
        while (!invites.isEmpty()) sb.append(invites.poll()).append(" ");
        chatService.clearPendingInvites(branchId);
        return sb.toString().trim();
    }

    // LIST CHATS
    private String listChatsCommand() {
        if (loggedInEmployee.getRole() != Role.ADMIN && loggedInEmployee.getRole() != Role.SHIFT_MANAGER)
            return "ERROR: Only ADMIN or SHIFT_MANAGER can list all chats.";

        Collection<ChatService.ChatSession> allChats = chatService.listAllChats();
        if (allChats.isEmpty()) return "[NOTIFY] No active chats.";

        StringBuilder sb = new StringBuilder("Active Chats:\n");
        for (ChatService.ChatSession cs : allChats) {
            if (cs.isActive()) {
                sb.append("ChatID: ").append(cs.getChatId())
                        .append(" | Branches: ").append(cs.getBranchesInvolved())
                        .append("\n");
            }
        }
        return sb.toString();
    }

}


