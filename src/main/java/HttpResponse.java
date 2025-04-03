import java.io.*;

/**
HttpResponse collects content and headers into a byte array.
*/
class HttpResponse {
    private final ByteArrayOutputStream baos = new ByteArrayOutputStream();

    public void write(String str) throws IOException {
        baos.write(str.getBytes(StandardCharsets.UTF_8));
    }


    public void write(byte[] bytes) throws IOException {
        baos.write(bytes);
    }


    public byte[] getBytes() {
        return baos.toByteArray();
    }
}