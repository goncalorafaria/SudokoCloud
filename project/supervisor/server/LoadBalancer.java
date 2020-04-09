package supervisor.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.util.HashMap;
import java.util.Map;

import java.util.Set;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import supervisor.storage.LocalStorage;
import supervisor.util.HttpRedirection;
import supervisor.util.Logger;

import com.amazonaws.services.ec2.model.Instance;


public class LoadBalancer {

    private static LinkedBlockingQueue<Request> inqueue = new LinkedBlockingQueue<>();

    private static AtomicBoolean active = new AtomicBoolean(true);

    private static Balancer worker = new LoadBalancer.Balancer();

    public LoadBalancer() {
    }

    public static void main(String[] args) throws Exception {

        Logger.publish(true,false);
        Logger.log("Starting the Load balancer");

        // Load local db
        LocalStorage.init();

        // Connect to aws
        CMonitor.init();

        // start redirection thread
        worker.start();

        //CMonitor.summon();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/sudoku", new Request.Handler());
        server.setExecutor(Executors.newCachedThreadPool());

        //Thread.sleep(10000);

        //sCMonitor.terminate();
        // start http server.
        server.start();

    }

    static class Request {
        String query;
        HttpExchange tunel;

        Request(String query, HttpExchange t ){
            this.query = query;
            this.tunel = t;
        }

        public String toString(){
            return this.query + "\n" + this.tunel.toString();
        }

        static class Handler implements HttpHandler {

            public void handle(HttpExchange t) {

                String query = t.getRequestURI().getQuery();

                LoadBalancer.inqueue.add(
                        new LoadBalancer.Request(query,t)
                );
            }

        }

    }

    static class Balancer extends Thread{

        public Balancer(){
        }

        @Override
        public void run() {

            while ( LoadBalancer.active.get() ) {
                try {
                    Request r = LoadBalancer.inqueue.take();
                    String redirectPath = this.decide(r);

                    String location = "http://" + redirectPath + ":8000/sudoku?" + r.query ;

                    HttpRedirection.send(r.tunel,location);

                }catch(InterruptedException e){
                    Logger.log(e.toString());
                    return;
                }catch(IOException e){
                    Logger.log(e.toString());
                    return;
                }
            }

        }

        private String decide(Request r) throws InterruptedException{
            Logger.log("This code Delivers the task to the servers");
            Logger.log(r.toString());
            Logger.log("#####");

            int size=0;

            Set<Instance> ins = CMonitor.getActiveInstances();

            while( ins.size() == 0 ){
                Logger.log(
                        "."
                );
                Thread.sleep(100);
                ins = CMonitor.getActiveInstances();
            }

            Logger.log("Available VMs");
            for (Instance i : ins) {
                Logger.log(i.getPublicDnsName());
                Logger.log(i.getPublicIpAddress());
                Logger.log(i.getPrivateIpAddress());
            }

            Instance i = ins.iterator().next();

            String result = i.getPublicDnsName();

            return result;
        }
    }
}
