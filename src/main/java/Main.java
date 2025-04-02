import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


public class Main {
    public static void main(String[] args) {
        // You can use print statements as follows for debugging, they'll be visible when running tests.
        System.out.println("Logs from your program will appear here!");
        int port = 4221;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            // Set SO_REUSEADDR to avoid 'Address already in use' errors when Tester restarts program
            serverSocket.setReuseAddress(true);
            while (true) {
                try (Socket clientSocket = serverSocket.accept()) {
                    handleClient(clientSocket);
                } catch (IOException e) {
                    System.err.println("Error handling client: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }


    private static void handleClient(Socket clientSocket) throws IOException {
        System.out.println("Accepted connection from " + clientSocket.getInetAddress() + ":" + clientSocket.getPort());
        String request = readRequest(clientSocket.getInputStream());
        String path = extractPath(request);
        System.out.println("Requested path: " + path);
        String response = createResponse(path);
        sendResponse(clientSocket.getOutputStream(), response);
    }


    private static String readRequest(InputStream inputStream) throws IOException {
        byte[] buffer = new byte[1024];
        int bytesRead = inputStream.read(buffer);
        return new String(buffer, 0, bytesRead);
    }


    private static String extractPath(String request) {
        // Split the request into lines (using CRLF as the separator).
        String[] requestLines = request.split("\r\n");
        if (requestLines.length > 0) {
            // The first line should be something like "GET /index.html HTTP/1.1"
            String[] firstLine = requestLines[0].split(" ");
            if (firstLine.length > 1) {
                // Strip any extraneous whitespace.
                return firstLine[1].trim();
            }
        }
        return "";
    }


    private static String createResponse(String path) {
        String response = "";
        // Return 200 OK if the path is "/" or "/index.html"; 404 otherwise.
        if (path.equals("/") || path.equals("/index.html")) {
            return "HTTP/1.1 200 OK\r\n\r\n";
        } else if (path.startsWith("/echo/")) {
            // Extract the string following "/echo/"
            String echoString = path.substring("/echo/".length());
            // Determine byte length of body (assumes UTF-8 encoding).
            int contentLength = echoString.getBytes().length;
            // Build response with required headers.
            response = "HTTP/1.1 200 OK\r\n";
            response += "Content-Type: text/plain\r\n";
            response += "Content-Length: " + contentLength + "\r\n";
            response += "\r\n";
            response += echoString;
            return response;
        } else {
            response = "HTTP/1.1 404 Not Found\r\n\r\n";
            return response;
        }
    }


    private static void sendResponse(OutputStream outputStream, String response) throws IOException {
        outputStream.write(response.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }
}
