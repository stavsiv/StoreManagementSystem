package Client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ClientApp {
    private String host;
    private int port;

    public ClientApp(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        try (Socket socket = new Socket(host, port);
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                Scanner scanner = new Scanner(System.in)) {

            System.out.println("Connected to " + host + ":" + port);

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        System.out.println("SERVER> " + line);
                    }
                } catch (IOException e) {
                    // connection closed
                }
            });
            reader.start();

            while (true) {
                String userInput = scanner.nextLine();
                if ("QUIT".equalsIgnoreCase(userInput)) {
                    out.println("QUIT");
                    break;
                }
                out.println(userInput);
            }

            reader.join();
            System.out.println("Client closed.");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        ClientApp client = new ClientApp("localhost", 12345);
        client.start();
    }
}
