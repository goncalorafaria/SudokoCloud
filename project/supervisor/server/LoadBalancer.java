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

import com.amazonaws.services.ec2.model.Instance;


public class LoadBalancer {

    private static LinkedBlockingQueue<Request> inqueue = new LinkedBlockingQueue<>();

    private static AtomicBoolean active = new AtomicBoolean(true);

    private static Balancer worker = new LoadBalancer.Balancer();

    private static Map<String, HttpExchange> tunels;

    private static Map<String, Set<String>> assignments = new HashMap<>();

    public LoadBalancer() {
    }

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/sudoku", new Request.Handler());
        server.setExecutor(Executors.newCachedThreadPool());

        System.out.println("Starting the Load balancer");

        // Load local db
        LocalStorage.init();

        // Connect to aws
        CMonitor.init();

        // start redirection thread
        worker.start();

        //CMonitor.summon();

        //Thread.sleep(10000);
        //System.out.println("Ready to work");
        CMonitor.terminate();
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

            public void handle(HttpExchange t) throws IOException {

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

                    HttpRedirection.send(r.tunel,redirectPath);

                }catch(InterruptedException e){
                    System.out.println(e.toString());
                    return;
                }catch(IOException e){
                    System.out.println(e.toString());
                    return;
                }
            }

        }

        private String decide(Request r) throws InterruptedException{
            System.out.println("This code Delivers the task to the servers");
            System.out.println(r.toString());
            //String vmid = CMonitor.keys().iterator().next();

            //System.out.println(vmid);
            //System.out.println(CMonitor.get(vmid).toString());
            //"PublicDnsAddress"
            System.out.println("#####");

            int size=0;

            Set<Instance> ins = CMonitor.getI();

            while( ins.size() == 0 ){
                System.out.print(
                        "."
                );
                Thread.sleep(100);
                ins = CMonitor.getI();

            }

            for (Instance i : ins) {
                System.out.println(i.getPublicDnsName());
                System.out.println(i.getPublicIpAddress());
                System.out.println(i.getPrivateIpAddress());
            }


            return ins.iterator().next().getPublicDnsName();
        }
    }
}
