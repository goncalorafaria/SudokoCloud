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
import supervisor.storage.CachedRemoteStorage;
import supervisor.util.CloudStandart;
import supervisor.util.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
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
    private static final String collectorimageid = CloudStandart.collectorimageid;
    private static final String instancetype = CloudStandart.instancetype;
    private static final String keyname = CloudStandart.keyname;
    private static final String securitygroups = CloudStandart.securitygroups;

    private static final long idealThreashold = 200000;
    private static final long ceilingThreashold = (int)(idealThreashold * 2);
    private static final long scaleUpThreashold = (int)(idealThreashold * 1.5);
    private static final long scaleDownThreashold = (int)(idealThreashold * 0.5);

    /* number of virtual machines starting. */
    private static final AtomicInteger startingvms = new AtomicInteger(0);

    /* dictionary of requested virtual machines. */
    private static final Map<String, CMonitor.Endpoint> vmstates = new ConcurrentSkipListMap<>();

    /* Storage persistente que guarda o historico das metricas para cada pedido. */
    private static CachedRemoteStorage requestTable;

    /* Máquinas virtuais que estão a prontas a receber pedidos. */
    private final static Set<String> activevms = new ConcurrentSkipListSet<>();

    /* script executed in the vm before running the webserver. */
    private static String launchscript;

    /**
     * Does the aws connection, dynamodb connection and
     * */
    public static void init() throws AmazonClientException {

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
                        "./server.sh \n";
            }else {
                launchscript = "#!/bin/bash \n" +
                        "cd /home/ec2-user\n" +
                        "sudo echo " + credentials.getAWSAccessKeyId() + " > cred.txt\n" +
                        "sudo echo " + credentials.getAWSSecretKey() + " >> cred.txt\n" +
                        "./server.sh \n";
            }
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }

        CachedRemoteStorage.init(false);

        CMonitor.requestTable = new CachedRemoteStorage(
                CloudStandart.taskStorage_tablename,
                CloudStandart.taskStorage_tablekey);

        CMonitor.serverRecovery();
    }

    /**
     * Routine that decides resource allocation.
     * TODO: Replace the find min with proper priority queue.
     * TODO: Careful with deadlock generated by Endpoint compareTo.
    * */
    static void autoscale(int requests) {

        HashSet<CMonitor.Endpoint> a = new HashSet<>();
        boolean go = true;

        while(go) {
            double exp = 0;
            go = false;
            Collection<Endpoint> sep = vmstates.values();
            for (Endpoint e : sep) {
                exp += e.getLoad();
            }
            exp = exp / sep.size();
            /*
            if (exp <= scaleDownThreashold && activevms.size() > 1) {
                go = true;
                //Logger.log("discard:");
                //schedulerecall(Collections.min(sep).vm);
            }

            if (exp >= scaleUpThreashold || (startingvms.get()==0 && sep.size() == 0) ) {
                go = true;
                //Logger.log("summon:");
                //CMonitor.summon();
            }
             */
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

    static void serverRecovery(){
        for( String newInstanceId : getActiveInstances().keySet())
            CMonitor.vmstates.put(newInstanceId, new Endpoint(newInstanceId));
    }

    /**
     * Desliga uma VM assim que esta acabar de servir. */
    static void schedulerecall(String vmid) {
        CMonitor.Endpoint e = CMonitor
                .vmstates.remove(vmid);
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
    static String decide(String s) throws InterruptedException {
        Set<String> tmp = new HashSet<>(CMonitor.activevms);

        double iestimate = CMonitor.requestTable.estimate(s);
        boolean go = true;

        Logger.log("branch count estimate: " +
                iestimate);

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
            for (CMonitor.Endpoint e : vmstates.values()) {
                if (activevms.contains(e.vm))
                    hset.add(e);
            }
            minx = Collections.min(hset);

            go = ceilingThreashold <= minx.getLoad();


        }

        Logger.log(" Load of selected server :" + minx.getLoad());

        minx.scheduleLoad(iestimate);

        return minx.publicip;
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
        private String dns;
        private String privateip;
        private String publicip;
        private BufferedReader in;
        private Socket sc;

        private AtomicLong load = new AtomicLong(0L);
        private long lastLoad = 0;

        public Endpoint(String vm) {
            this.vm = vm;
            this.start();
        }

        /**
         * Unsafe, was causing deadlock.
         * */
        @Override
        public int compareTo(Endpoint o) {

            long mev = this.load.get();
            long otherv = o.load.get();

            return (int)(mev-otherv);
        }

        public long getLoad(){ return load.get();}

        public void scheduleLoad(double l) { load.addAndGet((long)l); }

        private void discountLoad(long l){
            long tmp = load.addAndGet(-l);

            if( tmp == this.lastLoad ){
                load.set(-tmp);
            }

            this.lastLoad = tmp;
        }
        public void recall() {
            CMonitor.activevms.remove(vm);
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
                    sleep(1000 * 2);
                } catch (InterruptedException e) {
                    //Logger.log(e.toString());
                }
            }

            CMonitor.activevms.add(vm);
            CMonitor.startingvms.addAndGet(-1);

            Logger.log("Fetching: " + vm);
            while (active.get() || this.qsize.get() > 0) {
                try {
                    fetching();
                } catch (IOException e) {
                    //Logger.log(e.toString());
                }
            }

            this.recalling();
        }

        private void recalling() {

            try {
                this.sc.close();
            } catch (IOException e) {
                //Logger.log(e.toString());
            }

            CMonitor.recall(this.vm);
        }

        private void fetching() throws IOException {
            String[] args = in.readLine().split(":");

            switch (args[0]){
                case "queue" :
                    this.qsize.getAndAdd(
                            Integer.parseInt(
                                    args[1]));
                    Logger.log("<" + this.vm + ">" + args[0] + ":"+ this.qsize.get());
                    break;
                case "loadreport" :
                    long tmp = Long.parseLong(
                            args[1]);
                    this.discountLoad(tmp);
                    Logger.log("<" + this.vm + ">" + args[0] + ":"+ this.load.get());
                    break;
                case "fault-key":
                    double est = CMonitor.requestTable.estimate(args[1]);
                    this.scheduleLoad(est);
                    Logger.log("fault-key" + ":" + args[1] + ":" + est);
                    break;
                default: Logger.log(args[0]);
            }

        }

        private boolean calling() {
            boolean c = true;
            try {
                this.sc = new Socket(this.publicip, CloudStandart.inbound_channel_port);
                sc.setTcpNoDelay(true);

                c = false;

                this.sc.setSoTimeout(20 * 1000);

                this.in = new BufferedReader(
                        new InputStreamReader(
                                sc.getInputStream()));

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

                this.dns = vmi.getPublicDnsName();
                this.privateip = vmi.getPrivateIpAddress();
                this.publicip = vmi.getPublicIpAddress();

                return false;
            } else {
                return true;
            }

        }

    }

}