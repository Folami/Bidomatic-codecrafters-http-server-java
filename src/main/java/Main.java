import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");
        int port = 4221;

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            while (true) {
                // Tester restarts program quite often, setting SO_REUSEADDR
                // ensures that we don't run into 'Address already in use' errors
                serverSocket.setReuseAddress(true);
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                OutputStream out = clientSocket.getOutputStream();
                String response = "HTTP/1.1 200 OK\r\n\r\n";
                out.write(response.getBytes("UTF-8"));
                out.flush();
                clientSocket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}