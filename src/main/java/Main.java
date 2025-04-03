

public class Main {
    public static void main(String[] args) {
        int port = 4221;
        // Instantiate and start the HTTP server.
        HttpServer server = new HttpServer(args, port);
        server.start();
    }
}


