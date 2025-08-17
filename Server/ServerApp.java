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
    private static final String BRANCHES_FILE = "data/branches.json";
    private static final String EMPLOYEES_FILE = "data/employees.json";
    private static final String PRODUCTS_FILE = "data/products.json";
    private static final String CUSTOMERS_FILE = "data/customers.json";

    // logging appended actions to "logs/actions.log"
    private static final String ACTION_LOG_FILE = "logs/actions.log";

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
            out.println("Login successful! Hello, " + loggedInEmployee.getEmployeeName() + " (Role: "
                    + loggedInEmployee.getRole() + ")");
            return true;
        }

        private boolean handleCommands(BufferedReader in, PrintWriter out) throws IOException {
            out.println("Type 'Menu' to see available commands, or 'Exit' to exit.");

            String line;
            while ((line = in.readLine()) != null) {
                if (line.equalsIgnoreCase("Exit")) {
                    out.println("Goodbye!");
                    return false;
                }

                if (line.equalsIgnoreCase("LOGOUT")) {
                    authService.logout(currentUsername);
                    loggedInEmployee = null;
                    currentUsername = null;
                    out.println("You have been logged out. Returning to login screen...");
                    return true;
                }

                String response = handleCommand(line);
                out.println(response);
            }
            return false;
        }

        private String handleCommand(String line) {
            if (line.toUpperCase().startsWith("SEND_MSG")) {
                return sendMessageCommand(line);
            }

            String[] parts = line.split(" ");
            String command = parts[0].toUpperCase();

            switch (command) {
                case "Menu":
                    return showMenu();

                case "Add Employee":
                    return addEmployeeCommand(parts);

                case "SHOW_EMPLOYEES":
                    return showEmployees();

                case "SHOW_PRODUCTS":
                    return showProducts();

                case "SELL":
                    return sellCommand(parts);

                case "BUY_PRODUCT":
                    return buyProductCommand(parts);

                case "SAVE_SALES":
                    return saveSalesLogs();

                case "VIEW_SALES_LOGS":
                    return viewSalesLogs();

                case "ADD_CUSTOMER":
                    return addCustomerCommand(parts);

                case "SHOW_CUSTOMERS":
                    return showCustomers();

                // ----- The NEW command to convert logs to Word ------
                case "LOGS_TO_WORD":
                    return logsToWordCommand();

                // Chat commands
                case "START_CHAT":
                    return startChatCommand(parts);

                case "JOIN_CHAT":
                    return joinChatCommand(parts);

                case "SHOW_CHAT":
                    return showChatWithoutParam();

                case "LIST_CHATS":
                    return listChatsCommand();

                default:
                    return "Unknown command: " + command + ". Type Menu for commands.";
            }
        }

        // =========================================================
        // NEW COMMAND: LOGS_TO_WORD
        // =========================================================
        private String logsToWordCommand() {
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
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
                    loggedInEmployee.getEmployeeName(), outputDoc);
            logAction(logMsg);

            return "SUCCESS: logs converted to " + outputDoc;
        }

        private String showMenu() {
            String baseMenu;
            if (loggedInEmployee.getRole() == Employee.Role.ADMIN) {
                baseMenu = "Commands for ADMIN:\n" +
                        "ADD_EMPLOYEE <fullName> <id> <phone> <bankAccount> <branch> <empNum> <role> <username> <password>\n"
                        +
                        "ADD_CUSTOMER <name> <id> <phone> <type> (NEW, RETURNING, VIP)\n" +
                        "SHOW_PRODUCTS               - show ALL products\n" +
                        "SHOW_EMPLOYEES              - show ALL employees\n" +
                        "SHOW_CUSTOMERS              - show ALL customers\n" +
                        "SAVE_SALES                  - save sales logs to JSON files\n" +
                        "VIEW_SALES_LOGS             - view saved JSON logs\n" +
                        "LOGS_TO_WORD                - convert JSON logs to Word doc\n" +
                        "LIST_CHATS                  - list all active chat sessions\n" +
                        "LOGOUT                      - Logout\n" +
                        "Exit                        - Close the connection";
            } else {
                baseMenu = "Commands:\n" +
                        "ADD_CUSTOMER <name> <id> <phone> <type> (NEW, RETURNING, VIP)\n" +
                        "SHOW_PRODUCTS                 - show products in your branch\n" +
                        "SELL <productId> <quantity> <customerId>\n" +
                        "BUY_PRODUCT <productId> <quantity> <productName> <category> <price> <branch>\n" +
                        "LOGOUT                        - logout\n" +
                        "Exit                          - close connection";
            }

            String chatMenu = "\nChat Commands (ALL roles):\n" +
                    "START_CHAT <branchId>         - open a new chat with that branch\n" +
                    "JOIN_CHAT <chatId>            - join an existing chat\n" +
                    "SEND_MSG <message...>         - send a message to the current chat\n" +
                    "SHOW_CHAT                     - show messages in the current chat\n";

            return baseMenu + "\n" + chatMenu;
        }

        // ---------------------------------------------------
        // ADD_EMPLOYEE command (with logging)
        // ---------------------------------------------------
        private String addEmployeeCommand(String[] parts) {
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
                return "ERROR: Only ADMIN can add employees.";
            }
            if (parts.length < 10) {
                return "Usage: ADD_EMPLOYEE <fullName> <id> <phone> <bankAccount> <branch> <empNum> <role> <username> <password>";
            }
            String fullName = parts[1];
            String id = parts[2];
            String phone = parts[3];
            String bankAcc = parts[4];
            String branch = parts[5];
            int empNum;
            try {
                empNum = Integer.parseInt(parts[6]);
            } catch (NumberFormatException e) {
                return "ERROR: employeeNumber must be integer.";
            }
            String roleStr = parts[7].toUpperCase();
            Employee.Role newEmpRole;
            try {
                newEmpRole = Employee.Role.valueOf(roleStr);
            } catch (Exception e) {
                return "ERROR: invalid role.";
            }
            String newUsername = parts[8];
            String newPassword = parts[9];

            Employee newEmp = new Employee(fullName, id, phone, roleStr, empNum, branch, newEmpRole, newUsername,
                    newPassword);
            newEmp.setUserName(newUsername);
            newEmp.setPassword(newPassword);
            authService.register(newEmp, newUsername, newPassword);
            employeeService.addEmployee(newEmp);

            saveEmployeesToFile(EMPLOYEES_FILE);

            // Log the hiring
            String logMsg = String.format("ADMIN '%s' hired employee '%s' (ID=%s) in branch '%s'",
                    loggedInEmployee.getEmployeeName(), newEmp.getEmployeeName(), newEmp.getEmployeeId(),
                    newEmp.getBranchId());
            logAction(logMsg);

            return "SUCCESS: added new employee: " + newEmp;
        }

        // ---------------------------------------------------
        // SELL command (with logging)
        // ---------------------------------------------------
        private String sellCommand(String[] parts) {
            if (loggedInEmployee.getRole() == Employee.Role.ADMIN) {
                return "ERROR: ADMIN cannot SELL products.";
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
            String custId = parts[3];
            Customer customer = customerService.getCustomerById(custId);
            if (customer == null) {
                return "ERROR: No customer found with ID " + custId + ". Please ADD_CUSTOMER first.";
            }
            double finalPrice = saleService.purchaseProduct(customer, productId, qty);
            if (finalPrice < 0) {
                return "ERROR: Unable to sell (invalid product or insufficient stock).";
            }

            // Log the SELL
            String logMsg = String.format(
                    "SELL: Employee '%s' in branch '%s' sold productId='%s' (qty=%d) to customer '%s'",
                    loggedInEmployee.getEmployeeName(), loggedInEmployee.getBranchId(),
                    productId, qty, customer.getCustomerName());
            logAction(logMsg);

            return "SUCCESS: Sold " + qty + " of " + productId + " to " + custId
                    + " (" + customer.getCustomerName() + "). Final price: " + finalPrice;
        }

        // ---------------------------------------------------
        // BUY_PRODUCT command (with logging)
        // ---------------------------------------------------
        private String buyProductCommand(String[] parts) {
            if (loggedInEmployee.getRole() == Employee.Role.ADMIN) {
                return "ERROR: ADMIN cannot buy products.";
            }
            if (parts.length < 7) {
                return "Usage: BUY_PRODUCT <productId> <quantity> <productName> <category> <price> <branch>";
            }
            String productId = parts[1];
            int quantity;
            try {
                quantity = Integer.parseInt(parts[2]);
            } catch (NumberFormatException e) {
                return "ERROR: quantity must be integer.";
            }

            String productName = parts[3];
            String category = parts[4];
            double price;
            try {
                price = Double.parseDouble(parts[5]);
            } catch (NumberFormatException ex) {
                return "ERROR: price must be a valid number.";
            }
            String branch = parts[6];

            String empBranch = loggedInEmployee.getBranchId();
            if (!empBranch.equalsIgnoreCase(branch)) {
                return "ERROR: You can't buy product for a different branch.";
            }

            Product existingProduct = productService.getProductById(productId);
            if (existingProduct != null) {
                int oldStock = existingProduct.getQuantityInStock();
                int newStock = oldStock + quantity;
                existingProduct.setQuantityInStock(newStock);
                saveProductsToFile(PRODUCTS_FILE);

                // Log the BUY
                String logMsg = String.format(
                        "BUY: Employee '%s' in branch '%s' purchased more of productId='%s' (qty=%d)",
                        loggedInEmployee.getEmployeeName(), empBranch, productId, quantity);
                logAction(logMsg);

                return "SUCCESS: Product " + productId + " stock increased by " + quantity
                        + ". New total = " + newStock;
            } else {
                // brand new product
                Product newProduct = new Product(productId, productName, category, price, quantity, branch);
                productService.addProduct(newProduct);
                saveProductsToFile(PRODUCTS_FILE);

                // Log the new product buy
                String logMsg = String.format(
                        "BUY: Employee '%s' in branch '%s' created new product '%s' (id=%s) with qty=%d",
                        loggedInEmployee.getEmployeeName(), empBranch, productName, productId, quantity);
                logAction(logMsg);

                return "SUCCESS: Created new product " + productId + " with stock = " + quantity;
            }
        }

        // ---------------------------------------------------
        // ADD_CUSTOMER command (with logging)
        // ---------------------------------------------------
        private String addCustomerCommand(String[] parts) {
            if (parts.length < 5) {
                return "Usage: ADD_CUSTOMER <name> <id> <phone> <type> (NEW, RETURNING, VIP)";
            }
            String name = parts[1];
            String id = parts[2];
            String phone = parts[3];
            String type = parts[4].toUpperCase();
            Customer cust;
            switch (type) {
                case "NEW":
                    cust = new NewCustomer(name, id, phone);
                    break;
                case "RETURNING":
                    cust = new ReturningCustomer(name, id, phone);
                    break;
                case "VIP":
                    cust = new VIPCustomer(name, id, phone);
                    break;
                default:
                    return "ERROR: unknown customer type: " + type;
            }
            customerService.addCustomer(cust);
            saveCustomersToFile(CUSTOMERS_FILE);

            // Log the new customer
            String logMsg = String.format("Employee '%s' (Role=%s) added new customer '%s' (ID=%s)",
                    loggedInEmployee.getEmployeeName(), loggedInEmployee.getRole(), cust.getCustomerName(),
                    cust.getCustomerId());
            logAction(logMsg);

            return "SUCCESS: Created new " + type + " customer: " + name + " (ID=" + id + ")";
        }

        // ---------------------------------------------------
        // showEmployees, showProducts, showCustomers
        // ---------------------------------------------------
        private String showEmployees() {
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
                return "ERROR: Only ADMIN can view all employees.";
            }
            List<Employee> all = employeeService.listAllEmployees();
            if (all.isEmpty()) {
                return "No employees found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s %-10s %-15s %-15s %-10s %-10s %-10s\n",
                    "Name", "ID", "Phone", "BankAccount", "Branch", "EmpNum", "Role"));
            sb.append(
                    "---------------------------------------------------------------------------------------------\n");
            for (Employee emp : all) {
                sb.append(String.format("%-20s %-10s %-15s %-15s %-10s %-10d %-10s\n",
                        emp.getEmployeeName(), emp.getEmployeeId(), emp.getPhoneNumber(), emp.getAccountNumber(),
                        emp.getBranchId(), emp.getEmployeeNumber(), emp.getRole()));
            }
            return sb.toString();
        }

        private String showProducts() {
            if (loggedInEmployee.getRole() == Employee.Role.ADMIN) {
                return productService.getFormattedAllProducts();
            } else {
                String branch = loggedInEmployee.getBranchId();
                List<Product> branchProducts = productService.getProductsByBranch(branch);
                if (branchProducts.isEmpty()) {
                    return "No products found for branch " + branch;
                }
                return productService.getFormattedProductList(branchProducts);
            }
        }

        private String showCustomers() {
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
                return "ERROR: Only ADMIN can view all customers.";
            }
            List<Customer> allCustomers = customerService.listAllCustomers();
            if (allCustomers.isEmpty()) {
                return "No customers found.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append(String.format("%-20s %-10s %-15s %-15s\n",
                    "Name", "ID", "Phone", "Type"));
            sb.append("-------------------------------------------------------------\n");
            for (Customer cust : allCustomers) {
                sb.append(String.format("%-20s %-10s %-15s %-15s\n",
                        cust.getCustomerName(), cust.getCustomerId(), cust.getPhoneNumber(), cust.getCustomerType()));
            }
            return sb.toString();
        }

        // ---------------------------------------------------
        // SAVE_SALES, buildJsonFromSalesMap, viewSalesLogs
        // ---------------------------------------------------
        private String saveSalesLogs() {
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
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
            return "SUCCESS: sales logs saved in logs/sales_by_branch.json and logs/sales_by_productType.json.";
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
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
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
                    loggedInEmployee.getEmployeeName(), myBranch, targetBranch, chatId);
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
                    loggedInEmployee.getEmployeeName(), userBranch, chatId);
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
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN
                    && !session.getBranchesInvolved().contains(userBranch)) {
                return "ERROR: Your branch is not part of this chat, and you are not admin.";
            }

            // Create chat message
            ChatMessage msg = new ChatMessage(loggedInEmployee.getEmployeeName(), userBranch, messageContent);
            session.addMessage(msg);

            // Log the SEND_MSG operation
            String logMsg = String.format("CHAT: Employee '%s' (branch='%s') SENT a message to chatId='%s': \"%s\"",
                    loggedInEmployee.getEmployeeName(), userBranch, currentChatId, messageContent);
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
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN
                    && !session.getBranchesInvolved().contains(userBranch)) {
                return "ERROR: Your branch is not part of this chat, and you are not admin.";
            }
            if (session.getMessages().isEmpty()) {
                return "No messages yet in the current chat (" + currentChatId + ")";
            }

            // Log the SHOW_CHAT action
            String logMsg = String.format("CHAT: Employee '%s' (branch='%s') VIEWED chatId='%s'",
                    loggedInEmployee.getEmployeeName(), userBranch, currentChatId);
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
            if (loggedInEmployee.getRole() != Employee.Role.ADMIN) {
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
        if (!f.exists()) {
            System.out.println("No employees file found at " + filePath + ". Skipping load.");
            return;
        }
        String jsonContent = readWholeFile(f);
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            System.out.println("Employees file is empty. Skipping.");
            return;
        }

        String trimmed = jsonContent.trim();
        if (trimmed.startsWith("["))
            trimmed = trimmed.substring(1);
        if (trimmed.endsWith("]"))
            trimmed = trimmed.substring(0, trimmed.length() - 1);

        String[] objectStrings = trimmed.split("\\},\\s*\\{");
        for (String objStr : objectStrings) {
            String empJson = objStr.trim();
            if (!empJson.startsWith("{"))
                empJson = "{" + empJson;
            if (!empJson.endsWith("}"))
                empJson += "}";
            Employee e = parseEmployeeFromJson(empJson);
            if (e != null) {
                authService.register(e, e.getUserName(), e.getPassword());
                employeeService.addEmployee(e);
            }
        }
        System.out.println("Loaded employees from file: " + filePath);
    }

    private Employee parseEmployeeFromJson(String json) {
        String fullName = extractJsonStringValue(json, "fullName");
        String id = extractJsonStringValue(json, "id");
        String phone = extractJsonStringValue(json, "phone");
        String bankAccount = extractJsonStringValue(json, "bankAccount");
        String branch = extractJsonStringValue(json, "branch");
        int empNum = extractJsonIntValue(json, "employeeNumber");
        String roleStr = extractJsonStringValue(json, "role");
        String username = extractJsonStringValue(json, "username");
        String password = extractJsonStringValue(json, "password");

        if (fullName == null || id == null || roleStr == null) {
            System.out.println("Invalid employee JSON: missing required fields.\n" + json);
            return null;
        }
        Employee.Role role;
        try {
            role = Employee.Role.valueOf(roleStr.toUpperCase());
        } catch (Exception ex) {
            System.out.println("Invalid role: " + roleStr);
            return null;
        }
        Employee e = new Employee(fullName, id, phone, roleStr, empNum, branch, role, username, password);
        e.setUserName(username);
        e.setPassword(password);
        return e;
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
                productService.addProduct(p);
            }
        }
        System.out.println("Loaded products from file: " + filePath);
    }

    private Product parseProductFromJson(String json) {
        String id = extractJsonStringValue(json, "id");
        String name = extractJsonStringValue(json, "name");
        String category = extractJsonStringValue(json, "category");
        double price = extractJsonDoubleValue(json, "price");
        int quantity = extractJsonIntValue(json, "quantityInStock");
        String branch = extractJsonStringValue(json, "branch");

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
            String custJson = objStr.trim();
            if (!custJson.startsWith("{"))
                custJson = "{" + custJson;
            if (!custJson.endsWith("}"))
                custJson += "}";
            Customer c = parseCustomerFromJson(custJson);
            if (c != null) {
                customerService.addCustomer(c);
            }
        }
        System.out.println("Loaded customers from file: " + filePath);
    }

    private Customer parseCustomerFromJson(String json) {
        String name = extractJsonStringValue(json, "name");
        String id = extractJsonStringValue(json, "id");
        String phone = extractJsonStringValue(json, "phone");
        String type = extractJsonStringValue(json, "type");

        if (name == null || id == null || type == null) {
            System.out.println("Invalid customer JSON: missing required fields.\n" + json);
            return null;
        }

        Customer cust;
        switch (type.toUpperCase()) {
            case "NEW":
                cust = new NewCustomer(name, id, phone);
                break;
            case "RETURNING":
                cust = new ReturningCustomer(name, id, phone);
                break;
            case "VIP":
                cust = new VIPCustomer(name, id, phone);
                break;
            default:
                System.out.println("Invalid customer type: " + type + " in JSON:\n" + json);
                return null;
        }
        return cust;
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
            sb.append("    \"id\": \"").append(p.getProductId()).append("\",\n");
            sb.append("    \"name\": \"").append(p.getProductName()).append("\",\n");
            sb.append("    \"category\": \"").append(p.getCategory()).append("\",\n");
            sb.append("    \"price\": ").append(p.getPrice()).append(",\n");
            sb.append("    \"quantityInStock\": ").append(p.getQuantityInStock()).append(",\n");
            sb.append("    \"branch\": \"").append(p.getBranch()).append("\"\n");
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
            sb.append("    \"fullName\": \"").append(e.getEmployeeName()).append("\",\n");
            sb.append("    \"id\": \"").append(e.getEmployeeId()).append("\",\n");
            sb.append("    \"phone\": \"").append(e.getPhoneNumber()).append("\",\n");
            sb.append("    \"bankAccount\": \"").append(e.getAccountNumber()).append("\",\n");
            sb.append("    \"branch\": \"").append(e.getBranchId()).append("\",\n");
            sb.append("    \"employeeNumber\": ").append(e.getEmployeeNumber()).append(",\n");
            sb.append("    \"role\": \"").append(e.getRole()).append("\",\n");
            sb.append("    \"username\": \"").append(e.getUserName()).append("\",\n");
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
            sb.append("    \"name\": \"").append(c.getCustomerName()).append("\",\n");
            sb.append("    \"id\": \"").append(c.getCustomerId()).append("\",\n");
            sb.append("    \"phone\": \"").append(c.getPhoneNumber()).append("\",\n");
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
        ServerApp server = new ServerApp(12345);
        server.start();
    }
}