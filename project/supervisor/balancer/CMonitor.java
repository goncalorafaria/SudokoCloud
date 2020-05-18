package supervisor.balancer;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.util.Base64;
import supervisor.balancer.estimation.Estimator;
import supervisor.balancer.estimation.Oracle;
import supervisor.server.Count;
import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static java.lang.Thread.sleep;

public class CMonitor {
    /**
     *  This is the Hypervisor class for the balancer.
     * */
    private static AmazonEC2 ec2;
    private static AWSCredentials credentials;

    private static final String imageid = CloudStandart.ami;
    //private static final String collectorimageid = CloudStandart.collectorimageid;
    private static final String instancetype = CloudStandart.instancetype;
    private static final String keyname = CloudStandart.keyname;
    private static final String securitygroups = CloudStandart.securitygroups;

    private static final long idealThreashold = 200000;
    private static final long ceilingThreashold = (int)(idealThreashold * 2);
    private static long scaleUpThreashold = (int)(idealThreashold * 0.75);
    private static long scaleDownThreashold = (int)(idealThreashold * 0.25);

    /* number of virtual machines starting. */
    private static final AtomicInteger startingvms =
            new AtomicInteger(0);

    /* dictionary of requested virtual machines. */
    private static final Map<String, CMonitor.Endpoint> vmstates =
            new ConcurrentHashMap<>();

    /* Storage persistente que guarda o historico das metricas para cada pedido. */
    private static Oracle requestTable;

    /* Máquinas virtuais que estão a prontas a receber pedidos. */
    private final static Set<String> activevms =
            Collections.newSetFromMap(
                    new ConcurrentHashMap<String,Boolean>());

    /* script executed in the vm before running the webserver. */
    private static String launchscript;
    private static String elbvm;
    /**
     * Does the aws connection, dynamodb connection and
     * */
    public static void init(
            double lowerth,
            double upperth,
            int cachesize,
            String me) throws AmazonClientException {

        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
            ec2 = AmazonEC2ClientBuilder.standard()
                    .withRegion(CloudStandart.region)
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .build();

            if( credentials instanceof BasicSessionCredentials ) {
                BasicSessionCredentials bsc = (BasicSessionCredentials)credentials;
                Logger.log("BasicSessionCredentials");

                launchscript = "#!/bin/bash \n" +
                        "cd /home/ec2-user\n" +
                        "sudo echo " + bsc.getAWSAccessKeyId() + " > cred.txt\n" +
                        "sudo echo " + bsc.getAWSSecretKey() + " >> cred.txt\n" +
                        "sudo echo " + bsc.getSessionToken() + " >> cred.txt\n" +
                        //"sudo ./redo.sh \n" +
                        "./server.sh > out.txt \n";
            }else {
                launchscript = "#!/bin/bash \n" +
                        "cd /home/ec2-user\n" +
                        "sudo echo " + credentials.getAWSAccessKeyId() + " > cred.txt\n" +
                        "sudo echo " + credentials.getAWSSecretKey() + " >> cred.txt\n" +
                        //"sudo ./redo.sh \n" +
                        "./server.sh > out.txt \n";
            }
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }

        scaleUpThreashold = (int)(idealThreashold * upperth);
        scaleDownThreashold = (int)(idealThreashold * lowerth);

        TaskStorage.init(false);

        CMonitor.requestTable = new Oracle(cachesize);

        elbvm = me;
        CMonitor.serverRecovery();
    }

    /**
     * Routine that decides resource allocation.
     * TODO: Replace the find min with proper priority queue.
     * TODO: Careful with deadlock generated by Endpoint compareTo.
    * */
    static void autoscale() {

        HashSet<CMonitor.Endpoint> a = new HashSet<>();
        double exp = 0;
        Collection<Endpoint> sep = vmstates.values();

        for (Endpoint e : sep) {
            Logger.log(e.toString());
            exp += e.getLoad();
        }

        if( sep.size() > 0 )
            exp = exp / sep.size();

        Logger.log("Average load/per machine:" + exp + "/ machines:" + sep.size() + " - " + startingvms.get());


        if (exp <= scaleDownThreashold && activevms.size() > 1) {

            Logger.log("discard:");
            schedulerecall(Collections.min(sep).vm);
        }

        if (exp >= scaleUpThreashold || (startingvms.get()==0 && sep.size() == 0) ) {

            Logger.log("summon:");
            CMonitor.summon();
        }

    }

    /**
     * Cria uma nova VM */
    static String summon() throws AmazonServiceException {

        RunInstancesRequest runInstancesRequest =
                new RunInstancesRequest();

        runInstancesRequest.withImageId(imageid)
                .withInstanceType(instancetype)
                .withMinCount(1)
                .withMaxCount(1)
                .withKeyName(keyname)
                .withSecurityGroups(securitygroups)
                .withUserData(
                        Base64.encodeAsString(launchscript.getBytes()));


        RunInstancesResult runInstancesResult =
                ec2.runInstances(runInstancesRequest);

        Instance newInstance = runInstancesResult
                .getReservation().getInstances()
                .get(0);

        String newInstanceId = newInstance.getInstanceId();

        Logger.log("New Instanceid: ");
        Logger.log(newInstanceId);

        //SelfExecutingQueue.add(newInstanceId);

        CMonitor.vmstates.put(newInstanceId, new Endpoint(newInstanceId));

        CMonitor.startingvms.addAndGet(1);

        return newInstanceId;
    }

    private static void forcedrecall(String vmid){
        CMonitor.vmstates.remove(vmid);
        CMonitor.activevms.remove(vmid);

        //CMonitor.recall(vmid);
        //LoadBalancer.reschedule(requests);
    }

    private static void jobresponse(String key, Count c){
        CMonitor.requestTable.response(key,c);
    }

    static void serverRecovery(){

        for( String newInstanceId : getActiveInstances().keySet())
            if( ! CMonitor.vmstates.containsKey(newInstanceId)) {
                if(elbvm != null ) {
                    if( !newInstanceId.equals(elbvm) ) {
                        CMonitor.startingvms.addAndGet(1);
                        CMonitor.vmstates.put(newInstanceId, new Endpoint(newInstanceId));
                    }
                } else {
                    CMonitor.startingvms.addAndGet(1);
                    CMonitor.vmstates.put(newInstanceId, new Endpoint(newInstanceId));
                }
            }
    }

    /**
     * Desliga uma VM assim que esta acabar de servir. */
    static void schedulerecall(String vmid) {
        CMonitor.Endpoint e = CMonitor
                .vmstates.remove(vmid);
        CMonitor.activevms.remove(vmid);

        e.recall();
    }

    /**
     * Termina imediatamente uma Máquina Virtual - (Inseguro) */
    private static void recall(String vmid) {

        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();

        termInstanceReq.withInstanceIds(vmid);
        try {
            ec2.terminateInstances(termInstanceReq);
            Logger.log("Amazon shut up:" + vmid);
        } catch (AmazonServiceException e) {
            Logger.log(e.toString());
        }
    }

    /** Decides to which machine to send the request.
     * TODO: Replace the find min with proper priority queue.
     * TODO: Careful with deadlock generated by Endpoint compareTo.
     * */
    static Endpoint decide(String s) throws InterruptedException {
        Set<String> tmp = new HashSet<>(CMonitor.activevms);

        double iestimate = CMonitor.requestTable.predict(s);
        boolean go = true;

        Logger.log("estimate:"+iestimate);

        Logger.log(CMonitor.activevms.toString());
        Logger.log(CMonitor.vmstates.toString());

        CMonitor.Endpoint minx = null;

        while( go ) {
            while (tmp.size() == 0) {
                Logger.log(".");
                sleep(5000);
                tmp.addAll(CMonitor.activevms);
            }

            Set<Endpoint> hset = new HashSet<>();
            /* find min code */
            for (CMonitor.Endpoint e : vmstates.values())
                if (activevms.contains(e.vm))
                    hset.add(e);

            minx = Collections.min(hset);

            go = ceilingThreashold <= minx.getLoad();
        }

        Logger.log("Load of selected server:" + minx.getLoad());

        minx.scheduleLoad(iestimate);

        return minx;
    }

    /**
     * Retira todas as vms imediatamente.
     * TODO: Remover as restantes estruturas. */
    public static void terminate() {

        for (String k : CMonitor.vmstates.keySet())
            CMonitor.recall(k);

        //CMonitor.vmstates.destroy();
    }

    /** Obtêm a listagem de VM ATIVAS */
    private static Map<String, Instance> getActiveInstances() {

        Map<String, Instance> instances = new HashMap<>();

        DescribeInstancesRequest request = new DescribeInstancesRequest();

        DescribeInstancesResult response = ec2.describeInstances(request);

        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                //Logger.log( "reservation: " + instance.getInstanceId());
                if (instance.getState().getName().equals("running"))
                    instances.put(instance.getInstanceId(), instance);
            }
        }

        return instances;
    }

    static class Endpoint extends Thread implements Comparable<Endpoint> {
        /**
         * Esta classe representa cada vm no load balancer.
         *      - faz comunicação por tcp.
         * */
        private final String vm;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicInteger qsize = new AtomicInteger(0);
        private final Map<String, BlockingQueue<LoadBalancer.Request>> linked =
                new ConcurrentHashMap<>();
        private String publicip;
        private BufferedReader in;
        private PrintWriter out;
        private Socket sc;
        //private final ConcurrentSkipListMap<String,LoadBalancer.Request> rcache = new ConcurrentSkipListMap<>();

        private AtomicLong load = new AtomicLong(0L);
        private long lastLoad = 0;
        private long lastLoadCount = 0;

        public Endpoint(String vm) {
            this.vm = vm;
            this.start();
        }

        @Override
        public int compareTo(Endpoint o) {

            long mev = this.load.get();
            long otherv = o.load.get();

            return (int)(mev-otherv);
        }

        public String getIp(){
            return this.publicip;
        }
        /*public void cache(String key, LoadBalancer.Request request){
            this.rcache.put(key,request);
        }*/

        public String toString(){
            return "|/"+this.vm+"| active:"+this.active.get()+":-=-:"+ this.load+"\\|";
        }

        public void listening( String key, LoadBalancer.Request r){
            BlockingQueue<LoadBalancer.Request> q = this.linked.get(key);
            if( q == null){
                q = new LinkedBlockingDeque<>();
                this.linked.put(key,q);
            }
            q.add(r);
        }

        public long getLoad(){ return load.get();}

        public void scheduleLoad(double l) { load.addAndGet((long)l); }

        private void discountLoad(long l){
            long tmp = load.addAndGet(-l);

            if( tmp == lastLoad && lastLoadCount == 0) {
                load.addAndGet(-tmp);
                lastLoadCount++;
            }

            if( l != 0 )
                lastLoadCount = 0;

            lastLoad = tmp;

        }

        public void recall() {
            this.active.getAndSet(false);
        }

        public void run() {
            boolean bcalling = true;

            Logger.log("Searching: " + vm);
            while (bcalling && active.get()) {
                try {
                    bcalling = searching();
                    sleep(500);
                } catch (InterruptedException e) {
                    //Logger.log(e.toString());
                }
            }

            Logger.log("Calling: " + vm);
            bcalling = true;
            while (bcalling && active.get()) {
                try {
                    bcalling = calling();
                    sleep(1000);
                } catch (InterruptedException e) {
                    //Logger.log(e.toString());
                }
            }

            CMonitor.activevms.add(vm);
            CMonitor.startingvms.addAndGet(-1);

            Logger.log("Fetching: " + vm);
            int offc = 0;
            while (active.get() || this.qsize.get() > 0) {
                try {
                    fetching();
                    offc = 0;
                } catch (IOException e) {
                    offc++;
                    if( offc > 4){
                        this.faultdetected();
                        this.active.set(false);
                        return;
                    }
                    Logger.log( vm + ">" + e.toString() + "|" + active.get());
                }
            }
            this.recalling();
        }

        public void faultdetected(){
            try { this.sc.close();
            }catch(IOException exp){
            }

            CMonitor.forcedrecall(this.vm);

            for( Queue<LoadBalancer.Request> t : this.linked.values())
                for( LoadBalancer.Request r: t)
                    t.notify();

                this.linked.clear();
        }

        boolean isActive(){
            return this.active.get();
        }

        private void recalling() {

            try {
                this.sc.close();
            } catch (IOException| NullPointerException e) {
                //Logger.log(e.toString());
            }

            CMonitor.recall(this.vm);
        }

        private void fetching() throws IOException{
            String[] args = in.readLine().split(":");
            Logger.log(args[0]);
            switch (args[0]){
                case "data" :
                    String key = args[1]+":"+args[2]+":"+args[3];
                    try {
                        Logger.log(key + "DATA");
                        LoadBalancer.Request r = this.linked.get(key).take();
                        synchronized (r){r.notify();}
                    }catch (InterruptedException e){
                    }
                    try {
                        Count c = Count.fromString(args[4]);
                        CMonitor.jobresponse(key,c);
                        //this.rcache.remove(key);
                        //Logger.log("endpoint ------------:"+this.rcache.toString());
                    }catch (ClassNotFoundException e){
                    }
                    break;
                case "queue" :
                    this.qsize.getAndAdd(
                            Integer.parseInt(
                                    args[1]));
                    //Logger.log("<" + this.vm + ">" + args[0] + ":"+ this.qsize.get());
                    break;
                case "loadreport" :
                    long tmp = Long.parseLong(
                            args[1]);
                    this.discountLoad(tmp);
                    this.out.println("confirmation:");
                    this.out.flush();
                    break;
                case "fault-key":
                    double est = CMonitor.requestTable.predict(args[1]);
                    this.scheduleLoad(est);
                    Logger.log("fault-key" + ":" + args[1] + ":" + est);
                    break;
                default: Logger.log("swithc default:" + args[0]);
            }
        }

        private boolean calling() {
            boolean c = true;
            try {
                this.sc = new Socket(this.publicip, CloudStandart.inbound_channel_port);
                sc.setTcpNoDelay(true);
                this.sc.setSoTimeout(20 * 1000);
                c = false;

                this.in = new BufferedReader(
                        new InputStreamReader(
                                sc.getInputStream()));

                this.out = new PrintWriter(
                        sc.getOutputStream()
                );

                Logger.log("Tunnel open");
            } catch (UnknownHostException e) {
                //Logger.log(e.toString());
            } catch (IOException e) {
                //Logger.log(e.toString());
            }
            return c;
        }

        private boolean searching() {

            Map<String, Instance> ins = CMonitor.getActiveInstances();

            if (ins.containsKey(this.vm)) {
                Instance vmi = ins.get(vm);

                this.publicip = vmi.getPublicIpAddress();

                return false;
            } else {
                return true;
            }

        }
    }

}