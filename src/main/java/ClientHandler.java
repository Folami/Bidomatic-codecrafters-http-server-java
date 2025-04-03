import java.io.*;
import java.net.Socket;

/**
ClientHandler is responsible for reading 
the request from a socket, processing it,
and writing the response.
*/
class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final HttpServer server;

    public ClientHandler(Socket socket, HttpServer server) {
        this.clientSocket = socket; this.server = server;
    }


    @Override public void run() {
        try (Socket socket = clientSocket;
             InputStream inputStream = socket.getInputStream();
             OutputStream outputStream = socket.getOutputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))
            ) {
            HttpRequest request = HttpRequest.readRequest(reader);
            if (request == null) {
                System.err.println("Error reading request, connection closed prematurely.");
                return;
            }
            // For POST to /files, read the body.
            if ("POST".equals(request.getMethod()) && request.getPath().startsWith("/files/")) {
                request.readBody(reader);
            }
            HttpResponse response = new HttpResponse();
            System.out.println("Method: " + request.getMethod() + ", Path: " + request.getPath());
            server.processRequest(request, response);
            outputStream.write(response.getBytes());
            outputStream.flush();
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    } 
}
