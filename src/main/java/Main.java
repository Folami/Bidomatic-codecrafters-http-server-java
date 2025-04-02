import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.nio.charset.StandardCharsets;

public class Main {
    private static ExecutorService executor;

    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");
        int port = 4221;
        executor = Executors.newCachedThreadPool();
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    System.out.println("Accepted connection from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                    executor.execute(() -> handleClient(clientSocket));
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream inputStream = clientSocket.getInputStream();
             OutputStream outputStream = clientSocket.getOutputStream()) {
            String request = readRequest(inputStream);
            String[] requestLines = request.split("\r?\n");
            String path = extractPath(requestLines);
            System.out.println("Requested path: " + path);
            String response = createResponse(path, requestLines);
            sendResponse(outputStream, response);
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    private static String readRequest(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead = inputStream.read(buffer);
        return new String(buffer, 0, bytesRead);
    }

    private static String extractPath(String[] requestLines) {
        String path = "";
        if (requestLines.length > 0) {
            String requestLine = requestLines[0];
            String[] parts = requestLine.split(" ");
            if (parts.length >= 2) {
                path = parts[1].trim();
            }
        }
        return path;
    }

    private static String createResponse(String path, String[] requestLines) {
        String response = "";
        if (path.equals("/") || path.equals("/index.html")) {
            response = "HTTP/1.1 200 OK\r\n\r\n";
        } else if (path.startsWith("/echo/")) {
            String echoString = path.substring("/echo/".length());
            int contentLength = echoString.getBytes().length;
            response = "HTTP/1.1 200 OK\r\n";
            response += "Content-Type: text/plain\r\n";
            response += "Content-Length: " + contentLength + "\r\n";
            response += "\r\n";
            response += echoString;
        } else if (path.equals("/user-agent")) {
            String userAgent = "";
            for (String line : requestLines) {
                if (line.isEmpty()) break;
                if (line.toLowerCase().startsWith("user-agent:")) {
                    String[] parts = line.split(":", 1);
                    userAgent = parts[1].trim();
                    break;
                }
            }
            int contentLength = userAgent.getBytes().length;
            response = "HTTP/1.1 200 OK\r\n";
            response += "Content-Type: text/plain\r\n";
            response += "Content-Length: " + contentLength + "\r\n";
            response += "\r\n";
            response += userAgent;
        } else {
            response = "HTTP/1.1 404 Not Found\r\n\r\n";
        }
        return response;
    }

    private static void sendResponse(OutputStream outputStream, String response) throws IOException {
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
