package supervisor.balancer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import supervisor.storage.CachedRemoteStorage;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class LoadBalancer {

    private static final long waitTime = 30 * 1000;
    /* Fila de pedidos http */
    private static final LinkedBlockingQueue<Request> inqueue = new LinkedBlockingQueue<>();
    /* thread workers state */
    private static final AtomicBoolean active = new AtomicBoolean(true);
    /* Workers : Idealmente seria um Set. */
    private static final Balancer worker = new LoadBalancer.Balancer();

    public LoadBalancer() {
    }

    public static void main(String[] args) throws Exception {

        try {
            Logger.publish(true, false);
            Logger.log("Starting the Load balancer");

            CloudStandart.init();
            // Load local db

            // Connect to aws
            CMonitor.init();

            // start redirection thread
            worker.start();

            HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
            server.createContext("/sudoku", new Request.Handler());
            server.setExecutor(Executors.newCachedThreadPool());

            // start http server.
            server.start();
        } catch (Exception e) {
            Logger.log("Terminating VMS" + e.toString());
        }

    }

    static class Request {
        String query;
        HttpExchange tunel;

        Request(String query, HttpExchange t) {
            this.query = query;
            this.tunel = t;
        }

        public String toString() {
            return this.query + "\n" + this.tunel.toString();
        }

        static class Handler implements HttpHandler {

            public void handle(HttpExchange t) {

                String query = t.getRequestURI().getQuery();

                LoadBalancer.inqueue.add(
                        new LoadBalancer.Request(query, t)
                );
            }

        }

    }

    static class Balancer extends Thread {

        public Balancer() {
        }

        @Override
        public void run() {
            Request r = null;

            while (LoadBalancer.active.get()) {
                try {
                    r = LoadBalancer.inqueue.poll(LoadBalancer.waitTime, TimeUnit.MILLISECONDS);

                    CMonitor.autoscale(LoadBalancer.inqueue.size());

                    if (r != null) {
                        String redirectPath = CMonitor.decide(CloudStandart.makeKey(r.query));

                        String location = "http://" + redirectPath + ":8000/sudoku?" + r.query;

                        HttpRedirection.send(r.tunel, location);
                    }else{
                        Logger.log("Autoscalling round ");
                    }

                } catch (InterruptedException e) {
                    Logger.log(e.toString());
                    return;
                } catch (IOException e) {
                    Logger.log(e.toString());
                    LoadBalancer.inqueue.add(r);
                    return;
                }
            }

        }

    }
}
