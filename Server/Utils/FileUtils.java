package Server.Utils;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.regex.*;
import Exceptions.CustomExceptions;
import Models.*;
import Models.Role;

public class FileUtils {

    // Generic File Save
    /**
     * Saves a list of objects to a file using a serializer function.
     * @param filePath Path to the file
     * @param items List of objects to save
     * @param serializer Function that converts object of type T to JSON string
     * @param <T> Type of objects
     */
    public static <T> void saveToFile(String filePath, List<T> items, Function<T, String> serializer) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < items.size(); i++) {
            sb.append(serializer.apply(items.get(i)));
            if (i < items.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("]\n");

        try (FileWriter fw = new FileWriter(filePath)) {
            fw.write(sb.toString());
            System.out.println("Saved " + items.size() + " items to file: " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Read JSON Objects
    /**
     * Reads a JSON array from a file and splits it into individual JSON object strings.
     * @param filePath Path to the JSON file
     * @return List of JSON strings
     */
    public static List<String> readJsonObjectsFromFile(String filePath) {
        File f = new File(filePath);
        if (!f.exists()) {
            System.out.println("File not found: " + filePath + ". Skipping load.");
            return Collections.emptyList();
        }

        String jsonContent = readWholeFile(f);
        if (jsonContent == null || jsonContent.trim().isEmpty()) {
            System.out.println("File is empty: " + filePath + ". Skipping load.");
            return Collections.emptyList();
        }

        return getStrings(jsonContent);
    }

    private static List<String> getStrings(String jsonContent) {
        List<String> result = new ArrayList<>();
        int braceCount = 0;
        StringBuilder current = new StringBuilder();
        for (char c : jsonContent.toCharArray()) {
            if (c == '{') braceCount++;
            if (c == '}') braceCount--;
            current.append(c);
            if (braceCount == 0 && !current.isEmpty()) {
                String obj = current.toString().trim();
                if (!obj.isEmpty() && !obj.equals("[") && !obj.equals("]")) {
                    result.add(obj);
                }
                current.setLength(0);
            }
        }
        return result;
    }

    // Read Whole File
    /**
     * Reads entire file content into a single String.
     * @param file File to read
     * @return File content as String
     */
    public static String readWholeFile(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    // JSON Value Extractors
    /** Extracts string value by key from a JSON object string */
    public static String extractJsonStringValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
        Matcher matcher = Pattern.compile(regex).matcher(json);
        return matcher.find() ? matcher.group(1).trim() : null;
    }

    /** Extracts int value by key from a JSON object string */
    public static int extractJsonIntValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*(\\d+)";
        Matcher matcher = Pattern.compile(regex).matcher(json);
        if (matcher.find()) try { return Integer.parseInt(matcher.group(1)); } catch(Exception ignored){}
        return 0;
    }

    /** Extracts double value by key from a JSON object string */
    public static double extractJsonDoubleValue(String json, String key) {
        String regex = "\"" + key + "\"\\s*:\\s*([\\d.]+)";
        Matcher matcher = Pattern.compile(regex).matcher(json);
        if (matcher.find()) try { return Double.parseDouble(matcher.group(1)); } catch(Exception ignored){}
        return 0.0;
    }


    // Parses for each type
    /** Parses a Branch from JSON string */
    public static Branch parseBranchFromJson(String json) throws CustomExceptions.BranchException {
        String id = extractJsonStringValue(json, "branchId");
        String name = extractJsonStringValue(json, "branchName");
        if (id == null || name == null) return null;
        return new Branch(id, name);
    }

    /** Parses an Employee from JSON string */
    public static Employee parseEmployeeFromJson(String json) throws CustomExceptions.EmployeeException {
        String fullName = extractJsonStringValue(json, "fullName");
        String id = extractJsonStringValue(json, "employeeId");
        String phone = extractJsonStringValue(json, "phoneNumber");
        String account = extractJsonStringValue(json,"accountNumber");
        int empNum = extractJsonIntValue(json, "employeeNumber");
        String branch = extractJsonStringValue(json, "branchId");
        String roleStr = extractJsonStringValue(json, "role");
        String username = extractJsonStringValue(json, "userName");
        String password = extractJsonStringValue(json, "password");

        if (fullName == null || id == null || roleStr == null) return null;

        Role role;
        try { role = Role.valueOf(roleStr.toUpperCase()); }
        catch (Exception ex) { return null; }

        Employee e = new Employee(fullName, id, phone, account, empNum, branch, role, username, password);

        e.setUserName(username);
        e.setPassword(password);
        return e;
    }

    /** Parses a Product from JSON string */
    public static Product parseProductFromJson(String json) throws CustomExceptions.ProductException {
        String id = extractJsonStringValue(json, "productId");
        String name = extractJsonStringValue(json, "productName");
        String category = extractJsonStringValue(json, "category");
        double price = extractJsonDoubleValue(json, "price");
        int quantity = extractJsonIntValue(json, "quantityInStock");
        String branch = extractJsonStringValue(json, "branchId");
        if (id == null || name == null) return null;
        return new Product(id, name, category, price, quantity, branch);
    }

    /** Parses a Customer from JSON string */
    public static Customer parseCustomerFromJson(String json) throws CustomExceptions.CustomerException {
        String name = extractJsonStringValue(json, "fullName");
        String id = extractJsonStringValue(json, "customerId");
        String phone = extractJsonStringValue(json, "phoneNumber");
        String type = extractJsonStringValue(json, "type");
        if (name == null || id == null || type == null) return null;

        return switch (type.toUpperCase()) {
            case "NEW" -> new NewCustomer(name, id, phone);
            case "RETURNING" -> new ReturningCustomer(name, id, phone);
            case "VIP" -> new VIPCustomer(name, id, phone);
            default -> null;
        };
    }
}
