/**
HttpRequest encapsulates an HTTP request header and optional body.
*/
class HttpRequest {
    private final String method;
    private final String path;
    private final String[] requestLines;
    private String body = "";

    public HttpRequest(String method, String path, String[] requestLines) {
        this.method = method;
        this.path = path;
        this.requestLines = requestLines;
    }


    public String getMethod() {
        return method;
    }


    public String getPath() {
        return path;
    }


    public String[] getRequestLines() {
        return requestLines;
    }


    public String getBody() {
        return body;
    }


    public void readBody(BufferedReader reader) throws IOException {
        int contentLength = 0;
        for (String header : requestLines) {
            if (header.toLowerCase().startsWith("content-length:")) {
                try {
                    String lengthStr = header.split(":", 2)[1].trim();
                    contentLength = Integer.parseInt(lengthStr);
                } catch (NumberFormatException nfe) {
                    contentLength = 0;
                }
                break;
            }
        }
        if (contentLength > 0) {
            char[] bodyChars = new char[contentLength];
            int read = reader.read(bodyChars, 0, contentLength);
            if (read > 0) {
                body = new String(bodyChars, 0, read);
            }
        }
    }


    /**
    Reads HTTP header lines until an empty line is encountered.
    Returns an HttpRequest instance or null if nothing was read. 
    */
    public static HttpRequest readRequest(BufferedReader reader) throws IOException {
        String line; ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while ((line = reader.readLine()) != null) {
            baos.write(line.getBytes(StandardCharsets.UTF_8));
            baos.write("\r\n".getBytes(StandardCharsets.UTF_8));
            if (line.trim().isEmpty()) {
                // End of headers.
                break;
            }
        }
        String requestStr = baos.toString(StandardCharsets.UTF_8.name());
        if (requestStr.isEmpty()) {
            return null;
        }
        // Split on CRLF (or LF)
        String[] lines = requestStr.split("\r?\n");
        // Extract method and path from first line.
        String method = "";
        String path = "";
        if (lines.length > 0) {
            String[] parts = lines[0].split(" ");
            if (parts.length >= 2) {
                method = parts[0].toUpperCase();
                path = parts[1].trim();
            }
        }
        return new HttpRequest(method, path, lines);
    }

    /**
    Returns the value of the header with the given name 
    (case-insensitive), or "" if not found.
    */
    public String getHeader(String name) {
        String lowerName = name.toLowerCase();
        for (String header : requestLines) {
            if (header.toLowerCase().startsWith(lowerName + ":")) { 
                String[] parts = header.split(":", 2);
                if (parts.length > 1) {
                    return parts[1].trim();
                }
            }
        }
        return "";
    }

    /**

    Checks if the Accept-Encoding header mentions gzip.
    */
    public boolean clientAcceptsGzip() {
        for (String header : requestLines) {
            if (header.toLowerCase().startsWith("accept-encoding:")) {
                String encodings = header.substring("accept-encoding:".length()).trim().toLowerCase();
                if (encodings.contains("gzip")) {
                    return true;
                }
            }
        }
        return false;
    }
}
