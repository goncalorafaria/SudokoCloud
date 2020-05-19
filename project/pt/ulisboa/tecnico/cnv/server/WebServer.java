package pt.ulisboa.tecnico.cnv.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import pt.ulisboa.tecnico.cnv.solver.Solver;
import pt.ulisboa.tecnico.cnv.solver.SolverArgumentParser;
import pt.ulisboa.tecnico.cnv.solver.SolverFactory;

public class WebServer {
    public WebServer() {
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/sudoku", new WebServer.MyHandler());
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        System.out.println(server.getAddress().toString());
    }

    public static String parseRequestBody(InputStream is) throws IOException {
        InputStreamReader isr = new InputStreamReader(is, "utf-8");
        BufferedReader br = new BufferedReader(isr);
        StringBuilder buf = new StringBuilder(512);

        int b;
        while((b = br.read()) != -1) {
            buf.append((char)b);
        }

        br.close();
        isr.close();
        return buf.toString();
    }

    static class MyHandler implements HttpHandler {
        MyHandler() {
        }

        public void handle(HttpExchange t) throws IOException {
            boolean done = false;
            try {
                String query = t.getRequestURI().getQuery();
                System.out.println("> Query:\t" + query);
                String[] params = query.split("&");
                ArrayList<String> newArgs = new ArrayList();
                String[] args = params;
                int i = params.length;

                String arg;
                for (int var7 = 0; var7 < i; ++var7) {
                    arg = args[var7];
                    String[] splitParam = arg.split("=");
                    newArgs.add("-" + splitParam[0]);
                    newArgs.add(splitParam[1]);
                }

                newArgs.add("-b");
                newArgs.add(WebServer.parseRequestBody(t.getRequestBody()));
                newArgs.add("-d");
                args = new String[newArgs.size()];
                i = 0;

                for (Iterator var13 = newArgs.iterator(); var13.hasNext(); ++i) {
                    arg = (String) var13.next();
                    args[i] = arg;
                }

                SolverArgumentParser ap = new SolverArgumentParser(args);
                Solver s = SolverFactory.getInstance().makeSolver(ap);
                JSONArray solution = s.solveSudoku();
                Headers hdrs = t.getResponseHeaders();
                hdrs.add("Content-Type", "application/json");
                hdrs.add("Access-Control-Allow-Origin", "*");
                hdrs.add("Access-Control-Allow-Credentials", "true");
                hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
                hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
                t.sendResponseHeaders(200, (long) solution.toString().length());
                OutputStream os = t.getResponseBody();
                OutputStreamWriter osw = new OutputStreamWriter(os, "UTF-8");
                osw.write(solution.toString());
                osw.flush();
                osw.close();
                os.close();
                System.out.println("> Sent response to " + t.getRemoteAddress().toString());
                done=true;
            }finally {
                if( !done )
                    t.close();
            }
        }
    }
}
