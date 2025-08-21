package Server;
import Server.Utils.FileUtils;

// Standard library
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

// Models
import Models.Branch;
import Models.Employee;
import Models.Product;
import Models.Customer;

// Services
import Services.*;
//import Services.SaleService;

// Chat Service
//import Services.ChatService;
//import Services.ChatService.ChatSession;
//import Services.ChatService.ChatMessage;

// NEW SERVICE for converting logs to doc
//import Services.LogsService;

public class ServerApp {
    // Initialize services
    private final int port;
    private final AuthService authService = new AuthService();
    private final EmployeeService employeeService = new EmployeeService();
    private final ProductService productService = new ProductService();
    private final CustomerService customerService = new CustomerService();
    private final BranchService branchService = new BranchService();
    //private final SaleService saleService = new SaleService();
    SaleService saleService = new SaleService(productService);
    private final ChatService chatService = new ChatService();

    // File paths for reading/writing JSON on startup or updates
    private static final String BRANCHES_FILE = "Data/branches.json";
    public static final String EMPLOYEES_FILE = "Data/employees.json";
    public static final String PRODUCTS_FILE = "Data/products.json";
    public static final String CUSTOMERS_FILE = "Data/customers.json";

    public ServerApp(int port) {
        this.port = port;


        // Load branches
        for (String branchJson : FileUtils.readJsonObjectsFromFile(BRANCHES_FILE)) {
            Branch currBranch = FileUtils.parseBranchFromJson(branchJson);
            if (currBranch != null) branchService.addBranch(currBranch);
        }

        // Load employees
        for (String empJson : FileUtils.readJsonObjectsFromFile(EMPLOYEES_FILE)) {
            Employee currEmployee = FileUtils.parseEmployeeFromJson(empJson);
            if (currEmployee != null) {
                employeeService.addEmployee(currEmployee);
                authService.register(currEmployee, currEmployee.getUserName(), currEmployee.getPassword());
            }

        }

        // Load products
        for (String prodJson : FileUtils.readJsonObjectsFromFile(PRODUCTS_FILE)) {
            Product currProduct = FileUtils.parseProductFromJson(prodJson);
            if (currProduct != null) productService.addOrUpdateProduct(currProduct);
        }

        // Load customers
        for (String custJson : FileUtils.readJsonObjectsFromFile(CUSTOMERS_FILE)) {
            Customer currCustomer = FileUtils.parseCustomerFromJson(custJson);
            if (currCustomer != null) customerService.addCustomer(currCustomer);
        }

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
                ClientHandler handler = new ClientHandler(clientSocket, authService, employeeService, productService, customerService, branchService,saleService,chatService);

                new Thread(handler).start();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ServerApp server = new ServerApp(3000);
        server.start();
    }
}
