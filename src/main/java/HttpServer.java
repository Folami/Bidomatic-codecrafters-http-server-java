import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**

HttpServer encapsulates the server socket, 
client dispatching, and endpoint logic. 
*/
class HttpServer {
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private File filesDirectory = null;

    public HttpServer(String[] args, int port) {
        this.port = port;
        parseCommandLineArgs(args);
    }


    private void parseCommandLineArgs(String[] args) {
        // Parse for --directory flag.
        for (int i = 0; i < args.length; i++) {
            if ("--directory".equals(args[i]) && i + 1 < args.length) {
                filesDirectory = new File(args[i + 1]);
                if (!filesDirectory.exists() || !filesDirectory.isDirectory()) {
                    System.err.println("Error: Provided directory does not exist or is not valid.");
                    System.exit(1);
                }
            }
        }
    }


    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server listening on port: " + port);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                String socketDetails = "Accepted connection from ";
                socketDetails += clientSocket.getInetAddress();
                socketDetails += ":" + clientSocket.getPort();
                System.out.println(socketDetails);
                executor.execute(new ClientHandler(clientSocket, this));
            }
        } catch (IOException e) {
            System.err.println("Error starting server: " + e.getMessage());
        }
    }


    /**
    Processes the request and writes into the response. 
    */
    public void processRequest(HttpRequest request, HttpResponse response) throws IOException {
        String path = request.getPath();
        String method = request.getMethod();
        if ("/".equals(path) || "/index.html".equals(path)) {
            handleRootEndpoint(response);
        } else if (path.startsWith("/echo/")) {
            handleEchoEndpoint(request, response);
        } else if ("/user-agent".equals(path)) {
            handleUserAgentEndpoint(request, response);
        } else if (path.startsWith("/files/")) {
            handleFilesEndpoint(request, response);
        } else {
            response.write("HTTP/1.1 404 Not Found\r\n\r\n");
        }
    }


    private void handleRootEndpoint(HttpResponse response) throws IOException {
        response.write("HTTP/1.1 200 OK\r\n\r\n");
    }


    private void handleEchoEndpoint(HttpRequest request, HttpResponse response) throws IOException {
        // Strip off the /echo/ prefix.
        String echoBody = request.getPath().substring("/echo/".length());
        byte[] bodyBytes = echoBody.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: text/plain\r\n");
        if (request.clientAcceptsGzip()) {
            sb.append("Content-Encoding: gzip\r\n");
        }
        sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
        response.write(sb.toString());
        response.write(bodyBytes);
    }


    private void handleUserAgentEndpoint(HttpRequest request, HttpResponse response) throws IOException {
        String userAgent = request.getHeader("user-agent");
        byte[] uaBytes = userAgent.getBytes(StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(); sb.append("HTTP/1.1 200 OK\r\n");
        sb.append("Content-Type: text/plain\r\n");
        if (request.clientAcceptsGzip()) {
            sb.append("Content-Encoding: gzip\r\n");
        }
        sb.append("Content-Length: ").append(uaBytes.length).append("\r\n\r\n");
        response.write(sb.toString());
        response.write(uaBytes);
    }


    private void handleFilesEndpoint(HttpRequest request, HttpResponse response) throws IOException {
        if (filesDirectory == null) {
            response.write("HTTP/1.1 500 Internal Server Error\r\n\r\n");
            return;
        }
        String filename = request.getPath().substring("/files/".length());
        File file = new File(filesDirectory, filename);
        if ("GET".equals(request.getMethod())) {
            handleGetRequest(file, response);
        } else if ("POST".equals(request.getMethod())) {
            handlePostRequest(file, request, response);
        } else {
            response.write("HTTP/1.1 405 Method Not Allowed\r\n\r\n");
        }
    }


    private void handleGetRequest(File file, HttpResponse response) throws IOException {
        if (file.exists() && file.isFile()) {
            byte[] fileBytes = readFileBytes(file);
            StringBuilder sb = new StringBuilder();
            sb.append("HTTP/1.1 200 OK\r\n");
            sb.append("Content-Type: application/octet-stream\r\n");
            sb.append("Content-Length: ").append(fileBytes.length).append("\r\n\r\n");
            response.write(sb.toString()); response.write(fileBytes);
        } else {
            response.write("HTTP/1.1 404 Not Found\r\n\r\n");
        }
    }


    private void handlePostRequest(
            File file,
            HttpRequest request,
            HttpResponse response
        ) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(request.getBody().getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            response.write("HTTP/1.1 500 Internal Server Error\r\n\r\n");
            return;
        }
        response.write("HTTP/1.1 201 Created\r\n\r\n");
    }


    private byte[] readFileBytes(File file) throws IOException {
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
}
