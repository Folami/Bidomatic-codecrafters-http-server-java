import java.io.BufferedReader; 
import java.io.IOException; 
import java.io.InputStream; 
import java.io.InputStreamReader; 
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
                // Accept the client connection without using try-with-resources so that
                // it remains open for the handling thread.
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted connection from " 
                        + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
                executor.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        // Use try-with-resources to ensure the socket and its streams are closed after handling.
        try (Socket socket = clientSocket;
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream()) {
            
            String request = readHttpRequest(inputStream);
            if (request == null) {
                System.err.println("Error reading request, connection closed prematurely.");
                return;
            }
            String[] requestLines = request.split("\r?\n");
            String path = extractPath(requestLines);
            System.out.println("Requested path: " + path);
            String response = createResponse(path, requestLines);
            sendResponse(outputStream, response);
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    // Reads the HTTP request line by line until an empty line is encountered,
    // signifying the end of HTTP headers.
    private static String readHttpRequest(InputStream inputStream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder requestBuilder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            requestBuilder.append(line).append("\r\n");
            if (line.trim().isEmpty()) {  // End of headers
                break;
            }
        }
        if (requestBuilder.length() == 0) {
            return null;
        }
        return requestBuilder.toString();
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
        String response;
        if (path.equals("/") || path.equals("/index.html")) {
            response = "HTTP/1.1 200 OK\r\n\r\n";
        } else if (path.startsWith("/echo/")) {
            String echoString = path.substring("/echo/".length());
            int contentLength = echoString.getBytes().length;
            response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + contentLength + "\r\n" +
                    "\r\n" +
                    echoString;
        } else if (path.equals("/user-agent")) {
            String userAgent = "";
            for (String line : requestLines) {
                if (line.isEmpty()) break;
                if (line.toLowerCase().startsWith("user-agent:")) {
                    String[] parts = line.split(":", 2);
                    if(parts.length > 1){
                        userAgent = parts[1].trim();
                    }
                    break;
                }
            }
            int contentLength = userAgent.getBytes().length;
            response = "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: text/plain\r\n" +
                    "Content-Length: " + contentLength + "\r\n" +
                    "\r\n" +
                    userAgent;
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