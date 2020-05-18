package supervisor.balancer;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import supervisor.util.Logger;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class Redirect {
    /**
     * Esta classe trata dos detalhos de mandar http redirect.
     */

    public void send( String redirectPath)
            throws IOException {

        URI path;

        try {
            path = new URI(redirectPath);

            Logger.log("Redirection path");
            Logger.log(path.toString());

            t.getResponseHeaders().set("Location", path.toString());

            t.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            //Required by firefox or js. (Super unsafe don't use in a serious project)

            t.sendResponseHeaders(307, -1);
            // 303 does not work properly. ()
            t.close();

        } catch (URISyntaxException e) {
            Logger.log(e.toString());
            throw new IOException();
        }
    }

    private byte[] buffer = null;
    private HttpURLConnection cn = null;
    HttpExchange t;

    public Redirect(HttpExchange t){
        this.t = t;
    }

    public void passRequest(String ip, int port, URI inu)
            throws URISyntaxException, IOException {

        if( buffer == null ) {
            buffer = new byte[t.getRequestBody().available()];
            t.getRequestBody().read(buffer);
        }

        URI outu = new URI(
                inu.getScheme(),
                inu.getRawUserInfo(),
                ip,
                port,
                inu.getPath(),
                inu.getRawQuery(),
                inu.getFragment());

        URL curl = new URL("http:" + outu.toString());
        Logger.log(curl.toString());

        cn = (HttpURLConnection)curl.openConnection();
        cn.setRequestMethod("GET");

        cn.setUseCaches(false);
        cn.setDoInput(true);
        cn.setDoOutput(true);

        DataOutputStream wr = new DataOutputStream (
                cn.getOutputStream());

        wr.write(buffer);
        wr.flush();
        wr.close();

    }

    public String readResponse()
            throws IOException {

        //int status = cn.getResponseCode();
        //
        InputStream is = cn.getInputStream();
        is.read(buffer);

        return new String(buffer);
    }

    public void passResponse(String solution) throws IOException {

        //Logger.log("solution");
        Headers hdrs = t.getResponseHeaders();
        hdrs.add("Content-Type", "application/json");
        hdrs.add("Access-Control-Allow-Origin", "*");
        hdrs.add("Access-Control-Allow-Credentials", "true");
        hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
        hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
        t.sendResponseHeaders(200, solution.length());

        OutputStream os = t.getResponseBody();
        OutputStreamWriter osw = new OutputStreamWriter(os, StandardCharsets.UTF_8);
        osw.write(solution);
        osw.flush();
        osw.close();
        os.close();
    }

}
