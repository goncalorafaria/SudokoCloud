package supervisor.server;

import supervisor.storage.Storage;
import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class CNode {
    /**
     *  This is the Server Hypervisor class.
     * */

    /* tasks que estão a decorrer, indexadas pelo thread que está a correr. */
    private static final Map<Long, Task> activetasks
            = new ConcurrentSkipListMap<>();

    /* tasks que têm de se ter as metricas publicadas para a db. */
    private static final Map<String, Task.Group> oldtasks
            = new ConcurrentSkipListMap<>();

    /* Fila de espera para publicar as metricas das tasks terminadas */
    private static final BlockingQueue<String> keyq = new LinkedBlockingQueue();

    /* storage persistente para request metrics. */
    private static Storage<String> requestTable;

    /* thread that takes care of publishing metrics to db. */
    private static Publisher worker;

    /* class that comunicates with the balancer. */
    private static CNode.EndPoint tunnel;

    /**
     * Initializes storage, workers and communication channel with balancer.
     * */
    public static void init() {

        CNode.requestTable = new TaskStorage();

        CNode.worker = new Publisher();
        CNode.worker.start();

        CNode.tunnel = new CNode.EndPoint();
    }

    /** Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey) {
        registerTask(taskkey, false);
    }

    /** Associa um novo pedido a um thread. */
    public static void registerTask(String taskkey, boolean overhead) {
        //Logger.log("Registering task:" + taskkey);


        if (!activetasks.containsKey(Thread.currentThread().getId())) {
            Task t = new Task(taskkey);
            t.addMetric("Count", new Count());
            t.addMetric("Overhead", new Count());

            activetasks.put(Thread.currentThread().getId(), t);


            if (!oldtasks.containsKey(t.getKey()))
                oldtasks.put(t.getKey(), new Task.Group());

            CNode.tunnel.increment();
        }

        //Logger.log("start " + Thread.currentThread().getId());
    }

    /** Termina um novo pedido. */
    public static void finishTask() {
        Long tid = Thread.currentThread().getId();
        Task t = activetasks.remove(tid);

        //Logger.log("Finish task:" + t.getKey());

        oldtasks.get(t.getKey()).add(t);
        keyq.add(t.getKey());/* shedule for publishing*/

        //Logger.log("(Explain):" + ((Count) t.getMetric("Count")).explain());
        //Logger.log("(Metrics):" + t.getMetric("Count"));

        CNode.tunnel.decrement();
    }

    public static Task getTask() {
        return activetasks.get(Thread.currentThread().getId());
    }

    public static void terminate() {
        Publisher.off();
        worker.interrupt();
    }

    static class Publisher extends Thread {

        private static final AtomicBoolean active = new AtomicBoolean(true);

        public static void off() {
            active.getAndSet(false);
        }

        @Override
        public void run() {

            while (active.get()) {

                try {
                    //Logger.log("Publisher:Halt");
                    String tsk = CNode.keyq.take();
                    //Logger.log("Publisher:Working");
                    Set<Task> taskr = new HashSet<>();
                    oldtasks.get(tsk).drainTo(taskr);

                    for (Task itask : taskr) {
                        Map<String, String> row;

                        if (requestTable.contains(tsk)) {
                            row = requestTable.get(tsk);
                        } else {
                            row = new HashMap<>();
                        }

                        for (String mname : itask.metricsK()) {

                            Metric m = itask.getMetric(mname);
                            //Logger.log(mname);
                            Count c = (Count) m;

                            if (c.valid()) {
                                if (row.containsKey(mname)) {
                                    Count cold = Count.fromString(row.get(mname));
                                    c.aggregate(cold);
                                }
                                row.put(mname, c.toBinary());
                            }
                        }
                        requestTable.put(tsk, row);
                    }
                    //oldtasks.remove(tsk);

                } catch (InterruptedException e) {
                    //Logger.log(e.getMessage());
                } catch (IOException e) {
                    //Logger.log(e.getMessage());
                } catch (ClassNotFoundException e) {
                    //Logger.log(e.getMessage());
                }
            }

        }
    }

    static class EndPoint extends Thread {
        private Socket sc;
        private PrintWriter out=null;
        private BlockingQueue<String> lbq = new LinkedBlockingQueue<>();

        public EndPoint() {
            this.start();
        }

        public void increment() {
            //Logger.log("Increment");

            lbq.add("1");
            this.flush();
        }

        public void decrement() {
            //Logger.log("Decrement");
            lbq.add("-1");
            this.flush();
        }

        private void flush(){
            if( out != null ){
                try {
                    this.out.println(
                            lbq.take()
                    );
                    this.out.flush();
                }catch (InterruptedException e){
                }
            }
        }
        public void run() {
            try {
                this.sc = (new ServerSocket(CloudStandart.inbound_channel_port)).accept();

                this.out = new PrintWriter(
                        sc.getOutputStream()
                );

                //Logger.log("Tunnel open");


            } catch (UnknownHostException e) {

            } catch (IOException e) {

            }
        }
    }
}
