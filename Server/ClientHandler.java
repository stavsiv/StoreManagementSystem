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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    /** NEW: persist this TCP session’s server-side sessionId (for ChatService presence & idle pool). */
    private String currentSessionId = null;
    private final Map<String, Consumer<ChatService.ChatMessage>> chatListeners = new ConcurrentHashMap<>();
    private enum RequesterJoinPolicy { BLOCK, AUTO_LEAVE, AUTO_END_BOTH }
    private static final RequesterJoinPolicy REQUESTER_JOIN_POLICY = RequesterJoinPolicy.BLOCK; // pick your default

    public ClientHandler(Socket clientSocket,
                         AuthService authService,
                         EmployeeService employeeService,
                         ProductService productService,
                         CustomerService customerService,
                         SaleService saleService,
                         ChatService chatService) throws IOException {
        this.clientSocket = clientSocket;
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
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
                if (currentSessionId != null) chatService.disconnect(currentSessionId);
                clientSocket.close();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            out.println("Client disconnected.");
        }
    }

    // Log of action
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
                this.currentSessionId = UUID.randomUUID().toString();

                out.println("Type 'Menu' to see available commands, or 'Exit' to exit.");

                String branchId = loggedInEmployee.getBranchId();
                chatService.connect(this.currentSessionId, branchId, loggedInEmployee.getEmployeeId(),
                    loggedInEmployee.getFullName() + " (" + loggedInEmployee.getRole() + ", " + branchId + ")",

                    msg -> {
                        if (msg != null && msg.startsWith("[NOTIFY] CHAT_ENDED ")) {
                            String endedId = msg.substring("[NOTIFY] CHAT_ENDED ".length()).trim();
                            if (endedId.equals(currentChatId)) {
                                currentChatId = null;
                            }
                        } else {
                            out.println("" + msg);
                        }
                    }
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

        if (line.toUpperCase().startsWith("SEND")) return handleSendMsg(line);

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

            // Chat Commands
            case "REQUEST" -> handleRequestChat(parts);
            case "ACCEPT" -> handleAcceptChatOffer(parts);
            case "BEGIN" -> handleJoinChatRequester(parts);
            case "JOIN" -> handleJoinExistingChat(parts);
            case "CHAT_HISTORY" -> handleShowChat();
            case "LEAVE_CHAT" -> handleLeaveChat(parts);
            case "LIVE_CHATS" -> handleListJoinableChats();
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
        StringBuilder menuSB = new StringBuilder("--- COMMAND MENU ---\n");

        if (loggedInEmployee.getRole() == Role.ADMIN) {
            menuSB.append("SHOW_EMPLOYEES - display all employees\n");
            menuSB.append("SHOW_CUSTOMERS - display all customers\n");
            menuSB.append("SHOW_PRODUCTS - display all products\n");
            menuSB.append("ADD_EMPLOYEE <FullName> <Id> <Phone> <BankAccount> <EmpNum> <Branch> <Role> <Username> <Password>\n");
            menuSB.append("ADD_CUSTOMER <Name> <Id> <Phone> <Type> (NEW, RETURNING, VIP)\n");
            menuSB.append("SAVE_SALES - save sales logs to JSON\n");
            menuSB.append("VIEW_SALES_LOGS - view saved logs\n");
            menuSB.append("LOGS_TO_WORD - convert logs to Word doc\n");
        } else {
            menuSB.append("SHOW_PRODUCTS - display products in your branch\n");
            menuSB.append("SHOW_CUSTOMERS - display all customers\n");
            menuSB.append("ADD_CUSTOMER <Name> <Id> <Phone> <Type> (NEW, RETURNING, VIP)\n");
            menuSB.append("SELL <ProductId> <Quantity> <CustomerId>\n");
            menuSB.append("PURCHASE_PRODUCT <ProductId> <ProductName> <Category> <Price> <Quantity> <Branch>\n");
        }

        menuSB.append("\n--- Chat Commands ---\n");
        menuSB.append("REQUEST <BranchId> [note...] - queue a 1:1 chat to another branch; offered to the next idle employee there.\n");
        menuSB.append("ACCEPT - assignee only; accept your current offer (creates the chat).\n");
        menuSB.append("BEGIN <ChatId> - requester only; join within 60s of acceptance or the reservation expires.\n");
        menuSB.append("JOIN <ChatId> - rejoin: original requester/assignee or SHIFT_MANAGER of either chat branch.\n");
        menuSB.append("LIVE_CHATS - list chats you are allowed to JOIN right now.\n");
        menuSB.append("SEND <message...> - send a message to your current chat.\n");
        menuSB.append("CHAT_HISTORY - print the current chat history.\n");
        menuSB.append("LEAVE_CHAT - leave; if one side remains, the room auto-closes after 2 minutes.\n");
        menuSB.append("END_CHAT - end the chat for everyone (you’ll be asked whether to save history).\n");
        if (loggedInEmployee.getRole() == Role.ADMIN || loggedInEmployee.getRole() == Role.SHIFT_MANAGER) {
            menuSB.append("LIST_CHATS - (ADMIN/SHIFT_MANAGER) list all active chats.\n");
        }
        menuSB.append("====================");

        return menuSB.toString();
    }

    // Reuse Functions
    // 1. Validate permissions and parameters
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

    // 2. Standardize chat message format
    private String formatChatMessage(ChatService.ChatMessage msg) {
        return String.format("[NEW MSG][%s] %s (%s): %s",
                msg.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
                msg.getSenderName(), msg.getSenderBranch(), msg.getContent());
    }

    // 3. Active chat validation
    private ChatService.ChatSession getActiveChat(String chatId) throws CustomExceptions.ChatException {
        ChatService.ChatSession session = chatService.getChatById(chatId);
        if (session == null || !session.isActive())
            throw new CustomExceptions.ChatInactiveException("Chat " + chatId + " is not active or does not exist.");
        return session;
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

            logAction(String.format("ADD EMPLOYEE: [%s, Role=%s] added new employee '%s' (ID=%s, Branch=%s, Role=%s)",
                    loggedInEmployee.getFullName(), loggedInEmployee.getRole(),
                    fullName, id, branch, newEmpRole));

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
            int quantity = Integer.parseInt(parts[2]);
            String customerId = parts[3];

            Customer customer = customerService.getCustomerById(customerId);
            if (customer == null) return "ERROR: No customer found with Id " + customerId;

            Product product = productService.getProductByIdAndBranch(productId, loggedInEmployee.getBranchId());
            if (product == null) return "ERROR: Product not found in your branch.";

            double finalPrice = saleService.sellProduct(customer, productId, loggedInEmployee.getBranchId(), quantity);
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

            logAction(String.format("SELL: Employee '%s' sold product '%s' quantity=%d to customer '%s'",
                    loggedInEmployee.getFullName(), productId, quantity, customer.getCustomerName()));

            return String.format("Transaction completed! Sold %d units of '%s' to %s. Final price: %.2f",
                    quantity, product.getProductName(), customer.getCustomerName(), finalPrice);

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
            int index = 1;
            StringBuilder nameBuilder = new StringBuilder();

            while (index < parts.length && !parts[index].matches("\\d{9}")) {
                if (!nameBuilder.isEmpty()) nameBuilder.append(" ");
                nameBuilder.append(parts[index++]);
            }

            String fullName = nameBuilder.toString();
            String customerId = parts[index++];
            String phone = parts[index++];
            String type = parts[index].toUpperCase();

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

            logAction(String.format("ADD CUSTOMER: [%s, Role=%s] added new customer '%s' (ID=%s, Type=%s)",
                    loggedInEmployee.getFullName(), loggedInEmployee.getRole(), fullName, customerId, type));
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
        if (loggedInEmployee.getRole() != Role.ADMIN)
            return "ERROR: Only ADMIN can view all employees.";

        List<Employee> allEmployees = employeeService.listAllEmployees();
        if (allEmployees.isEmpty()) return "No employees found.";

        return employeeService.formatEmployeeList(allEmployees);
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

        return customerService.formatCustomerList(allCustomers);
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

        String logMsg = String.format("ADMIN '%s' converted logs to Word doc at '%s'.",
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

        for (SaleService.SaleRecord record : allSales) {
            salesByBranch.computeIfAbsent(record.getBranch(), _ -> new ArrayList<>()).add(record);
            salesByType.computeIfAbsent(record.getProductType(), _ -> new ArrayList<>()).add(record);
        }

        String branchJson = buildJsonFromSalesMap(salesByBranch);
        String typeJson = buildJsonFromSalesMap(salesByType);

        try (FileWriter writerBranch = new FileWriter("logs/sales_by_branch.json");
             FileWriter writerProduct = new FileWriter("logs/sales_by_productType.json")) {
            writerBranch.write(branchJson);
            writerProduct.write(typeJson);
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

        StringBuilder saleSB = new StringBuilder();
        saleSB.append("{\n");
        int size = grouped.size();
        int count = 0;
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        for (String key : grouped.keySet()) {
            saleSB.append("  \"").append(key).append("\": [\n");
            List<SaleService.SaleRecord> recs = grouped.get(key);
            for (int i = 0; i < recs.size(); i++) {
                SaleService.SaleRecord sr = recs.get(i);
                saleSB.append("    {\n");
                saleSB.append("      \"productId\": \"").append(sr.getProductId()).append("\",\n");
                saleSB.append("      \"productName\": \"").append(sr.getProductName()).append("\",\n");
                saleSB.append("      \"quantity\": ").append(sr.getQuantity()).append(",\n");
                saleSB.append("      \"finalPrice\": ").append(sr.getFinalPrice()).append(",\n");
                saleSB.append("      \"saleTime\": \"").append(sr.getSaleTime().format(dtf)).append("\"\n");
                saleSB.append("    }");
                if (i < recs.size() - 1) saleSB.append(",");
                saleSB.append("\n");
            }
            saleSB.append("  ]");
            if (++count < size) saleSB.append(",");
            saleSB.append("\n");
        }
        saleSB.append("}\n");
        return saleSB.toString();
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

        StringBuilder saleSB = new StringBuilder("\n---- VIEW SALES LOGS ----\n\n");

        if (branchFile.exists()) {
            saleSB.append("* Branch-Based Sales Logs *\n");
            saleSB.append(FileUtils.readWholeFile(branchFile)).append("\n");
        }
        if (typeFile.exists()) {
            saleSB.append("* Product-Type Sales Logs *\n");
            saleSB.append(FileUtils.readWholeFile(typeFile)).append("\n");
        }

        logAction("Sales logs viewed by ADMIN " + loggedInEmployee.getFullName());
        return saleSB.toString();
    }

    // Chat Commands
    // request chat from a Branch (enqueue to target-branch queue; auto-assign if capacity exists)
    private String handleRequestChat(String[] parts) {
        String err = validateCommand(null, parts, 2);
        if (err != null) return err;

        String targetBranch = parts[1];
        String userBranch = loggedInEmployee.getBranchId();

        if (userBranch.equals(targetBranch))
            return "ERROR: Starting a chat with your own branch is not allowed. Please choose a different branch.";

        // Optional note
        String note = (parts.length > 2) ? String.join(" ", Arrays.copyOfRange(parts, 2, parts.length)) : "";

        try {
            chatService.requestChatFromBranch(
                    userBranch,
                    loggedInEmployee.getEmployeeId(), // explicit requester (employee)
                    targetBranch,
                    note,
                    // Requester-specific notify callback (for this command only)
                    msg -> out.println(" " + msg)
            );
            return "[QUEUED] Your chat request to branch " + targetBranch + " was sent.";

        } catch (Exception e) {
            e.printStackTrace();
            return "ERROR: Oops! Something went wrong while starting the chat: " + e.getMessage();
        }
    }

    // accept chat offer (assignee in target branch attaches + becomes busy)
    private String handleAcceptChatOffer(String[] parts) {
        String err = validateCommand(null, parts, 1); // no chatId needed anymore
        if (err != null) return err;

        if (currentSessionId == null) return "ERROR: Session not initialized. Please re-login.";

        try {
            Consumer<ChatService.ChatMessage> listener = m -> out.println(formatChatMessage(m));
            String chatId = chatService.acceptOfferByAssignee(currentSessionId, listener);
            chatListeners.put(chatId, listener);
            currentChatId = chatId;

            return "[WAITING] Chat session "+chatId+" created. Up to 60s for requester to join.";
        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }
    }

    // JOIN as REQUESTER (marks the requester UI as attached for the 60s rule)
    private String handleJoinChatRequester(String[] parts) {
        String err = validateCommand(null, parts, 2);
        if (err != null) return err;

        String chatId = parts[1];
        if (currentSessionId == null) return "ERROR: Session not initialized. Please re-login.";

        // Optional gate if you want to block concurrent chats for the same user
        if (currentChatId != null && !currentChatId.equals(chatId)) {
            switch (REQUESTER_JOIN_POLICY) {
                case BLOCK -> { return "[ERROR] You’re already in chat " + currentChatId + ". END_CHAT or LEAVE_CHAT first."; }
                case AUTO_LEAVE -> {
                    // Just call it; it doesn't throw a checked exception
                    chatService.leaveChatAsUser(currentChatId, loggedInEmployee.getBranchId(), currentSessionId);
                    chatListeners.remove(currentChatId);
                    out.println("[INFO] Left chat " + currentChatId + ". The other side has up to 2 minutes before auto-close.");
                    currentChatId = null;
                }
                case AUTO_END_BOTH -> {
                    chatService.endChat(currentChatId);      
                    chatListeners.remove(currentChatId);
                    out.println("[INFO] Ended previous chat " + currentChatId + " for both sides.");
                    currentChatId = null;
                }
            }
        }

        try {
            getActiveChat(chatId);
            // one terminal listener for requester
            Consumer<ChatService.ChatMessage> listener = chatListeners.computeIfAbsent(chatId, _ -> m -> out.println(formatChatMessage(m)));
            chatService.markRequesterAttached(chatId,
                loggedInEmployee.getEmployeeId(), // explicit requester (employee)
                currentSessionId,                  // requesterSessionId
                loggedInEmployee.getBranchId(),    // requesterBranch
                listener                           // attach via service (don’t add twice yourself)
            );
            ChatService.ChatSession session = getActiveChat(chatId);
            String reqName   = loggedInEmployee.getFullName();
            String reqRole   = loggedInEmployee.getRole().name();
            String reqBranch = loggedInEmployee.getBranchId();
            String requesterDisplay = reqName + " (" + reqRole + ", " + reqBranch + ")";
            
            // try to resolve the other side currently attached (assignee)
            String assigneeDisplay = chatService.displayOf(session.getAssigneeSessionId());
            session.addMessage(new ChatService.ChatMessage(
                    "SYSTEM", reqBranch, "Requester joined: " + requesterDisplay + ".\nCurrent assigned: " + assigneeDisplay + "."));

            currentChatId = chatId;
            return "use CHAT_HISTORY to see history.";

        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    // rejoining existing chat (if you know the chatId; e.g. re-join after disconnect)
    private String handleJoinExistingChat(String[] parts) {
        String err = validateCommand(null, parts, 2);
        if (err != null) return err;

        String chatId = parts[1];
        
        // Optional: block multiple simultaneous chats for the same terminal
        if (currentChatId != null && !currentChatId.equals(chatId)) {
            return "[ERROR] You’re already in chat " + currentChatId + ". LEAVE_CHAT or END_CHAT first.";
        }

        try {
            // one listener for this terminal
            Consumer<ChatService.ChatMessage> listener =
                    chatListeners.computeIfAbsent(chatId, _ -> m -> out.println(formatChatMessage(m)));
            chatService.joinExistingChatAuthorized(chatId, loggedInEmployee.getEmployeeId(), loggedInEmployee.getRole(),
                    loggedInEmployee.getBranchId(), currentSessionId, listener);
            ChatService.ChatSession session = getActiveChat(chatId);
            String name   = loggedInEmployee.getFullName();
            String role   = loggedInEmployee.getRole().name();
            String branch = loggedInEmployee.getBranchId();
            String displayUser   = name + " (" + role + ", " + branch + ")";
            session.addMessage(new ChatService.ChatMessage(
                    "SYSTEM", branch,
                    displayUser + " joined the chat."
            ));            

            currentChatId = chatId;
            return "Joined existing chat " + chatId + " , use CHAT_HISTORY to view chat history.";
        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }
    }
    
    private String handleListJoinableChats() {
        List<ChatService.ChatSession> joinables = chatService.listJoinableChatsForEmployee(
                loggedInEmployee.getEmployeeId(), loggedInEmployee.getRole(), loggedInEmployee.getBranchId(), currentSessionId);

        if (joinables.isEmpty()) return "No joinable active chats for you.";

        StringBuilder joinSB = new StringBuilder("Joinable Chats:\n");
        for (ChatService.ChatSession cs : joinables) {
            joinSB.append("ChatID: ").append(cs.getChatId())
            .append(" | Branches: ").append(cs.getBranchesInvolved())
            .append(" | Participants: ").append(cs.getParticipants().size())
            .append("\n");
        }
        joinSB.append("Use: JOIN <ChatID>");
        return joinSB.toString();
    }
    
    // SEND MSG
    private String handleSendMsg(String line) {
        String[] parts = line.split(" ", 2);
        String err = validateCommand(null, parts, 2);
        if (err != null) return "[ERROR] " + err;

        if (currentChatId == null)
            return "[ERROR] No current chat selected. Use JOIN_CHAT_REQUESTER/JOIN_CHAT_EMPLOYEE first.";

        try {
            chatService.sendMessage(currentChatId, currentSessionId, loggedInEmployee.getFullName(), parts[1]);
            return "Message sent successfully";
        } catch (CustomExceptions.ChatException e) {
            return "[ERROR] " + e.getMessage();
        }
    }
    
    // LEAVE CHAT (unregister local listener, then tell service this specific session leaves)
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

        if (!session.getBranchesInvolved().contains(userBranch)) {
            return "[ERROR] Your branch is not part of chat " + chatId;
        }
        
        String name = loggedInEmployee.getFullName();
        String role = loggedInEmployee.getRole().name();
        String branch = loggedInEmployee.getBranchId();
        String displayUser = name + " (" + role + ", " + branch + ")";

        session.addMessage(new ChatService.ChatMessage("SYSTEM", branch, displayUser + " left the chat."));
        chatListeners.remove(chatId); // local stop (service removes its own listener internally)
        chatService.leaveChatAsUser(chatId, userBranch, currentSessionId);

        if (chatId.equals(currentChatId))
            currentChatId = null;

        return "You left chat " + chatId + ". Type 'Menu' to see available commands, or 'Exit' to exit.";
    }

    // END CHAT (optional save -> unregister listener -> end room for everyone)
    private String handleEndChat() {
        if (currentChatId == null) return "[ERROR] No chat selected.";

        boolean saved = false;

        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            out.println("Do you want to save the chat history? (yes/no)");

            String response;
            while (true) {
                response = in.readLine();
                if (response == null) continue;

                if (response.equalsIgnoreCase("yes")) {
                    chatService.saveChatHistory(currentChatId);
                    out.println("Chat history saved. Chat will end now.");
                    saved = true;
                    break;
                } else if (response.equalsIgnoreCase("no")) {
                    out.println("Chat will end without saving.");
                    break;
                } else {
                    out.println("Please answer yes or no.");
                }
            }
            try{
                ChatService.ChatSession session = getActiveChat(currentChatId);
                String endedBy = loggedInEmployee.getFullName() + " (" + loggedInEmployee.getRole().name() + ", " + loggedInEmployee.getBranchId() + ")";

                session.addMessage(new ChatService.ChatMessage("SYSTEM", loggedInEmployee.getBranchId(), "Chat ended by " + endedBy + "\nType 'Menu' to see available commands, or 'Exit' to exit."));
                chatListeners.remove(currentChatId);
                chatService.endChat(currentChatId);
            } 
            catch (CustomExceptions.ChatException e) {
                    return "[ERROR] " + e.getMessage();
            } 
        }
        catch (IOException e) {
            e.printStackTrace();
            return "Error handling chat end: " + e.getMessage();
        }

        logAction(String.format("Chat %s ended by %s (Branch=%s). saved=%s",
                currentChatId, loggedInEmployee.getFullName(), loggedInEmployee.getBranchId(), saved));
        currentChatId = null;
        return "";
    }

    private String handleShowChat() {
        if (currentChatId == null) return "ERROR: No chat selected.";

        ChatService.ChatSession session;
        try {
            session = getActiveChat(currentChatId);
        } catch (CustomExceptions.ChatException e) {
            return "ERROR: " + e.getMessage();
        }

        if (session.getMessages().isEmpty()) return "No messages yet in chat " + currentChatId;

        StringBuilder showSB = new StringBuilder("Chat " + currentChatId + " messages:\n");
        for (ChatService.ChatMessage msg : session.getMessages()) {
            showSB.append(formatChatMessage(msg)).append("\n");
        }
        return showSB.toString();
    }

    private String listChatsCommand() {
        if (loggedInEmployee.getRole() != Role.ADMIN && loggedInEmployee.getRole() != Role.SHIFT_MANAGER)
            return "ERROR: Only ADMIN or SHIFT_MANAGER can list all chats.";

        Collection<ChatService.ChatSession> allChats = chatService.listAllChats();
        if (allChats.isEmpty()) return "No active chats.";

        StringBuilder list = new StringBuilder("Active Chats:\n");
        for (ChatService.ChatSession cs : allChats) {
            if (cs.isActive()) {
                list.append("ChatID: ").append(cs.getChatId()).append(" | Branches: ").append(cs.getBranchesInvolved()).append("\n");
            }
        }
        return list.toString();
    }

}
