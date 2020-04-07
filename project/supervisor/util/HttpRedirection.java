package supervisor.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpRedirection {

    public static void send(HttpExchange ex, String redirectPath)
            throws IOException {

        URI base = getRequestUri(ex);
        URI path;

        try {
            path = new URI(redirectPath);

            URI location = base.resolve(path);

            System.out.println(base.toString());
            System.out.println(path.toString());
            System.out.println(location.toString());

            System.out.println(ex.getResponseHeaders().toString());

            ex.getResponseHeaders().set("Location", location.toString());

            ex.sendResponseHeaders(HttpURLConnection.HTTP_SEE_OTHER, -1);
            ex.close();

        }catch(URISyntaxException e){
            System.out.println(e.toString());
            throw new IOException();
        }
    }

    private static URI getRequestUri(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        if (host == null) {
            int port = ex.getHttpContext().getServer().getAddress().getPort();
            host = "localhost:" + port;
        }
        String protocol = (ex.getHttpContext().getServer() instanceof HttpsServer)
                ? "https" : "http";
        URI base;
        try {
            base = new URI(protocol, host, "/", null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
        URI requestedUri = ex.getRequestURI();
        requestedUri = base.resolve(requestedUri);
        return requestedUri;
    }

}
