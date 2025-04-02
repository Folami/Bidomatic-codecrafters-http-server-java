import java.io.*; 
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

    // Container for the HTTP method, path, headers (as requestLines) and (optional) request body.
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
            for (int i=0; i < args.length; i++) {
                if ("--directory".equals(args[i]) && i+1 < args.length) {
                    filesDirectory = new File(args[i+1]);
                    if (!filesDirectory.exists() || !filesDirectory.isDirectory()) {
                        System.err.println("Error: Provided directory does not exist or is not valid.");
                        System.exit(1);
                    }
                }
            }
        } catch(Exception e) {
            System.err.println("Error parsing command-line arguments: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void plugClient(int port) {
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            while(true) {
                // Accept without auto-closing socket.
                Socket clientSocket = serverSocket.accept();
                String socketDetails = "Accepted connection from " 
                        + clientSocket.getInetAddress() + ":" + clientSocket.getPort();
                System.out.println(socketDetails);
                executor.execute(() -> handleClient(clientSocket));
            }
        } catch(IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        // Use try-with-resources so the client socket is closed automatically.
        try (Socket socket = clientSocket;
            InputStream inputStream = socket.getInputStream();
            OutputStream outputStream = socket.getOutputStream();
            // Use a BufferedReader for reading header lines.
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
            System.out.println("Method: " + reqInfo.method + ", Path: " + reqInfo.path);
            // 2.1 If the request is a POST to /files, read the body.
            if (reqInfo.method.equals("POST") && reqInfo.path.startsWith("/files/")) {
                handlePostMethod(requestLines, reqInfo, reader);
            }
            // 3. Create full HTTP response as a byte array.
            byte[] response = createResponse(reqInfo);
            // 4. Send the response to the client.
            sendResponse(outputStream, response);
        } catch(IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }

    // Reads HTTP header lines until an empty line and returns an array of header lines.
    private static String[] readHttpRequest(BufferedReader reader) throws IOException {
        String line;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((line = reader.readLine()) != null) {
            baos.write(line.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            if(line.trim().isEmpty()) {  // End of headers.
                break;
            }
        }
        String request = baos.toString(StandardCharsets.UTF_8.name());
        if(request.isEmpty()){
            return new String[0];
        }
        // Split on CRLF or LF.
        return request.split("\r?\n");
    }

    // Extracts the HTTP method and path from the first request line.
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

    // Reads the POST body for /files endpoints.
    private static void handlePostMethod(String[] requestLines, RequestInfo reqInfo, BufferedReader reader) throws IOException {
        int contentLength = 0;
        // Find Content-Length header.
        for(String header : requestLines) {
            if(header.toLowerCase().startsWith("content-length:")) {
                try {
                    contentLength = Integer.parseInt(header.split(":", 2)[1].trim());
                } catch(NumberFormatException nfe) {
                    contentLength = 0;
                }
                break;
            }
        }
        // Read exactly contentLength characters from the reader.
        char[] bodyChars = new char[contentLength];
        int read = reader.read(bodyChars, 0, contentLength);
        if(read > 0) {
            reqInfo.body = new String(bodyChars, 0, read);
        }
    }

    // Creates the full HTTP response.
    private static byte[] createResponse(RequestInfo reqInfo) {
        ByteArrayOutputStream responseStream = new ByteArrayOutputStream();
        try {
            handleEndpoints(reqInfo, responseStream);
        } catch(IOException e) {
            String errorResponse = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            return errorResponse.getBytes(StandardCharsets.UTF_8);
        }
        return responseStream.toByteArray();
    }

    // Dispatches the request to the appropriate endpoint handler.
    private static void handleEndpoints(RequestInfo reqInfo, ByteArrayOutputStream responseStream) throws IOException {
        String method = reqInfo.method;
        String path = reqInfo.path;
        if(path.equals("/") || path.equals("/index.html")) {
            handleRootEndpoint(responseStream);
        } else if(path.startsWith("/echo/")) {
            // For /echo, pass the full requestLines to check for Accept-Encoding.
            handleEchoEndpoint(reqInfo.requestLines, path, responseStream);
        } else if(path.equals("/user-agent")) {
            handleUserAgentEndpoint(reqInfo.requestLines, responseStream);
        } else if(path.startsWith("/files/")) {
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

    // Handles the /echo endpoint.
    private static void handleEchoEndpoint(String[] requestLines, String path, ByteArrayOutputStream responseStream) throws IOException {
        String echoBody = path.substring("/echo/".length());
        byte[] echoBytes = echoBody.getBytes(StandardCharsets.UTF_8);
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 200 OK\r\n");
        headerBuilder.append("Content-Type: text/plain\r\n");
        // If the client accepts gzip, add the Content-Encoding header.
        if(clientAcceptsGzip(requestLines)) {
            headerBuilder.append("Content-Encoding: gzip\r\n");
        }
        headerBuilder.append("Content-Length: " + echoBytes.length + "\r\n");
        headerBuilder.append("\r\n");
        responseStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
        responseStream.write(echoBytes);
    }

    // Handles the /user-agent endpoint.
    private static void handleUserAgentEndpoint(String[] requestLines, ByteArrayOutputStream responseStream) throws IOException {
        String userAgent = "";
        for (int i = 1; i < requestLines.length; i++) {
            String header = requestLines[i];
            if(header.isEmpty())
                break;
            if(header.toLowerCase().startsWith("user-agent:")) {
                String[] headerParts = header.split(":", 2);
                if(headerParts.length > 1) {
                    userAgent = headerParts[1].trim();
                }
                break;
            }
        }
        byte[] uaBytes = userAgent.getBytes(StandardCharsets.UTF_8);
        StringBuilder headerBuilder = new StringBuilder();
        headerBuilder.append("HTTP/1.1 200 OK\r\n");
        headerBuilder.append("Content-Type: text/plain\r\n");
        if(clientAcceptsGzip(requestLines)) {
            headerBuilder.append("Content-Encoding: gzip\r\n");
        }
        headerBuilder.append("Content-Length: " + uaBytes.length + "\r\n");
        headerBuilder.append("\r\n");
        responseStream.write(headerBuilder.toString().getBytes(StandardCharsets.UTF_8));
        responseStream.write(uaBytes);
    }

    // Handles the /files endpoint for both GET and POST.
    private static void handleFilesEndpoint(RequestInfo reqInfo, ByteArrayOutputStream responseStream) throws IOException {
        String httpMethod = reqInfo.method;
        String httpPath = reqInfo.path;
        if(filesDirectory == null) {
            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String filename = httpPath.substring("/files/".length());
        File file = new File(filesDirectory, filename);
        if("GET".equals(httpMethod)) {
            handleHttpGetMethod(file, responseStream);
        } else if("POST".equals(httpMethod)) {
            handleHttpPostMethod(file, reqInfo, responseStream);
        } else {
            String response = "HTTP/1.1 405 Method Not Allowed\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
        }
    }

    // Handles the HTTP GET for a file.
    private static void handleHttpGetMethod(File file, ByteArrayOutputStream responseStream) throws IOException {
        if(file.exists() && file.isFile()) {
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
    }

    // Handles the HTTP POST for a file.
    private static void handleHttpPostMethod(File file, RequestInfo reqInfo, ByteArrayOutputStream responseStream) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(reqInfo.body.getBytes(StandardCharsets.UTF_8));
        } catch(IOException ioe) {
            String response = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
            responseStream.write(response.getBytes(StandardCharsets.UTF_8));
            return;
        }
        String response = "HTTP/1.1 201 Created\r\n\r\n";
        responseStream.write(response.getBytes(StandardCharsets.UTF_8));
    }

    // Helper to check whether the client Accept-Encoding header mentions gzip.
    private static boolean clientAcceptsGzip(String[] requestLines) {
        for (String header : requestLines) {
            if(header.toLowerCase().startsWith("accept-encoding:")) {
                String encodings = header.substring("accept-encoding:".length()).trim().toLowerCase();
                if(encodings.contains("gzip")) {
                    return true;
                }
            }
        }
        return false;
    }

    // Reads a file fully and returns its contents as a byte array.
    private static byte[] readFileBytes(File file) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try(FileInputStream fis = new FileInputStream(file)){
            byte[] buffer = new byte[4096];
            int n;
            while((n = fis.read(buffer)) != -1) {
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