import java.io.BufferedReader; 
import java.io.ByteArrayOutputStream; 
import java.io.File; 
import java.io.FileInputStream; 
import java.io.FileOutputStream; 
import java.io.IOException; 
import java.io.InputStream; 
import java.io.InputStreamReader; 
import java.io.OutputStream; 
import java.net.ServerSocket; 
import java.net.Socket; 
import java.nio.charset.StandardCharsets; 
import java.util.concurrent.ExecutorService; 
import java.util.concurrent.Executors;

public class Main {
    // Executor for handling clients concurrently.
    private static ExecutorService executor = Executors.newCachedThreadPool();
    // Files directory as provided by the --directory flag.
    private static File filesDirectory = null;

    // Updated container for the HTTP method, path, and (optional) request body.
    private static class RequestInfo {
        String method;
        String path;
        String[] requestLines;
        String body = "";  // default to empty

        RequestInfo(String method, String path, String[] requestLines) {
            this.method = method;
            this.path = path;
            this.requestLines = requestLines;
        }
    }


    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");
        parseCommandLineArgs(args); 
        int port = 4221;       
        plugClient(port);
    }


    private static void parseCommandLineArgs(String[] args) {
        // Parse command-line args to get the --directory flag.
        try {
            for (int i = 0; i < args.length; i++) {
                if ("--directory".equals(args[i]) && i + 1 < args.length) {
                    filesDirectory = new File(args[i + 1]);
                    if (!filesDirectory.exists() || !filesDirectory.isDirectory()) {
                        System.err.println("Error: Provided directory does not exist or is not valid.");
                        System.exit(1);
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Error parsing command-line arguments: " + e.getMessage());
            System.exit(1);
        }
    }


    private static void plugClient(int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                // Accept without auto-closing socket.
                Socket clientSocket = serverSocket.accept();
                String socketDetails = "Accepted connection from ";
                socketDetails += clientSocket.getInetAddress();
                socketDetails += clientSocket.getPort();
                System.out.println(socketDetails);
                executor.execute(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }    
    }


    private static void handleClient(Socket clientSocket) {
        // Use try-with-resources to ensure the thread manages socket closing.
        try (Socket socket = clientSocket;
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            // We use a BufferedReader for reading header lines.
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
        ) {
            // 1. Read the HTTP request headers.
            String[] requestLines = readHttpRequest(reader);
            if (requestLines == null || requestLines.length == 0) {
                System.err.println("Error reading request, connection closed prematurely.");
                return;
            }
            // 2. Extract HTTP method & path from the request-line.
            RequestInfo reqInfo = extractPath(requestLines);
            System.out.println("Method: " + reqInfo.method + " Path: " + reqInfo.path);
            handlePostMethod(requestLines, reqInfo, reader);
            // 3. Create full HTTP response as a byte array.
            byte[] response = createResponse(reqInfo);
            // 4. Send the response to the client.
            sendResponse(outputStream, response);
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }


    // Reads the HTTP request (header lines) until an empty line is found.
    private static String[] readHttpRequest(BufferedReader reader) throws IOException {
        String line;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((line = reader.readLine()) != null) {
            baos.write(line.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            if (line.trim().isEmpty()) {  // End of headers.
                break;
            }
        }
        String request = baos.toString(StandardCharsets.UTF_8.name());
        if (request.isEmpty()) {
            return new String[0];
        }
        // Split on CRLF or LF.
        return request.split("\r?\n");
    }


    // Extract the request method and path from the first line (request-line).
    private static RequestInfo extractPath(String[] requestLines) {
        String method = "";
        String path = "";
        if (requestLines.length > 0) {
            String[] parts = requestLines[0].split(" ");
            if (parts.length >= 2) {
                method = parts[0].toUpperCase();
                path = parts[1].trim();
            }
        }
        return new RequestInfo(method, path, requestLines);
    }


    private static void handlePostMethod(String[] requestLines, RequestInfo reqInfo, BufferedReader reader) {
        // 2.1 If the request is a POST to /files, read the request body.
        if ("POST".equals(reqInfo.method) && reqInfo.path.startsWith("/files/")) {
            int contentLength = 0;
            // Look for Content-Length header in the requestLines.
            for (String header : requestLines) {
                if (header.toLowerCase().startsWith("content-length:")) {
                    try {
                        contentLength = Integer.parseInt(header.split(":", 2)[1].trim());
                    } catch (NumberFormatException nfe) {
                        contentLength = 0;
                    }
                    break;
                }
            }
            // Read exactly contentLength characters from the reader.
            // (Assuming the POST body is ASCII/text and Content-Length equals the number of characters.)
            char[] bodyChars = new char[contentLength];
            int read = reader.read(bodyChars, 0, contentLength);
            if (read > 0) {
                reqInfo.body = new String(bodyChars, 0, read);
            }
        }
    }
    


    // Creates a response based on the request info and headers.
    private static byte[] createResponse(RequestInfo reqInfo) {
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        try {
            handleEndpoints(reqInfo, responseStream);
        } catch (IOException e) {
            String errorResponse = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            return errorResponse.getBytes(StandardCharsets.UTF_8);
        }
        return responseStream.toByteArray();
    }

    // Determines which endpoint to call based on the request info and writes the response to the stream.
    private static void handleEndpoints(RequestInfo reqInfo, ByteArrayOutputStream responseStream) throws IOException {
        String method = reqInfo.method;
        String path = reqInfo.path;
        if (path.equals("/") || path.equals("/index.html")) {
            handleRootEndpoint(responseStream);
        } else if (path.startsWith("/echo/")) {
            handleEchoEndpoint(path, responseStream);
        } else if (path.equals("/user-agent")) {
            handleUserAgentEndpoint(reqInfo.requestLines, responseStream);
        } else if (path.startsWith("/files/")) {
            handleFilesEndpoint(reqInfo, responseStream);
        } else {
            // Unknown resource.
            String response = "HTTP/1.1 404 Not Found\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void handleRootEndpoint(ByteArrayOutputStream responseStream) throws IOException {
        String response = "HTTP/1.1 200 OK\r\n\r\n";
        responseStream.write(response.getBytes(StandardCharsets.UTF_8));
    }

    private static void handleEchoEndpoint(String path, ByteArrayOutputStream responseStream) throws IOException {
        String echoBody = path.substring("/echo/".length());
        byte[] echoBytes = echoBody.getBytes(StandardCharsets.UTF_8);
        String responseHeader = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + echoBytes.length + "\r\n" +
                "\r\n";
        responseStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        responseStream.write(echoBytes);
    }

    private static void handleUserAgentEndpoint(String[] requestLines, ByteArrayOutputStream responseStream) throws IOException {
        String userAgent = "";
        for (int i = 1; i < requestLines.length; i++) {
            String header = requestLines[i];
            if (header.isEmpty())
                break;
            if (header.toLowerCase().startsWith("user-agent:")) {
                String[] headerParts = header.split(":", 2);
                if (headerParts.length > 1) {
                    userAgent = headerParts[1].trim();
                }
                break;
            }
        }
        byte[] uaBytes = userAgent.getBytes(StandardCharsets.UTF_8);
        String responseHeader = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: " + uaBytes.length + "\r\n" +
                "\r\n";
        responseStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
        responseStream.write(uaBytes);
    }

    // Updated files endpoint handler to support both GET and POST.
    private static void handleFilesEndpoint(RequestInfo reqInfo, ByteArrayOutputStream responseStream) throws IOException {
        String method = reqInfo.method;
        String path = reqInfo.path;
        if (filesDirectory == null) {
            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String filename = path.substring("/files/".length());
        File file = new File(filesDirectory, filename);
        if ("GET".equals(method)) {
            if (file.exists() && file.isFile()) {
                byte[] fileBytes = readFileBytes(file);
                String responseHeader = "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: application/octet-stream\r\n" +
                        "Content-Length: " + fileBytes.length + "\r\n" +
                        "\r\n";
                responseStream.write(responseHeader.getBytes(StandardCharsets.UTF_8));
                responseStream.write(fileBytes);
            } else {
                String response = "HTTP/1.1 404 Not Found\r\n\r\n";
                responseStream.write(response.getBytes(StandardCharsets.UTF_8));
            }
        } else if ("POST".equals(method)) {
            // Write the request body to the new file.
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(reqInfo.body.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ioe) {
                String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                responseStream.write(response.getBytes(StandardCharsets.UTF_8));
                return;
            }
            // Respond with 201 Created.
            String response = "HTTP/1.1 201 Created\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
        } else {
            String response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Reads the entire file into a byte array.
    private static byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, n);
            }
        }
        return baos.toByteArray();
    }


    // Writes the response to the client's output stream.
    private static void sendResponse(OutputStream outputStream, byte[] response) throws IOException {
        outputStream.write(response);
        outputStream.flush();
    }
}