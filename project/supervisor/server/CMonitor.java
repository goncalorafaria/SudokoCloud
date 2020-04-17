package supervisor.server;

import BIT.highBIT.*;

import java.util.*;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import supervisor.storage.LocalStorage;
import supervisor.storage.Storage;
import supervisor.util.Logger;

import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;

public class CMonitor {
    /*
     *  This is the Hypervisor class for the balancer.
     * */

    private static AmazonEC2 ec2;

    private static String imageid = "ami-0cb790308f7591fa6";
    private static String collectorimageid = "ami-0cb790308f7591fa6";

    private static String instancetype = "t2.micro";
    private static String keyname = "CNV-lab-AWS";
    private static String securitygroups = "CNV-ssh+http";

    private static AtomicBoolean updating = new AtomicBoolean(false);

    private static Storage<String> vmstates;
    /* Storage persistente que guarda as máquinas virtuais e as suas propriedades(ip, queue size etc)*/

    private static Storage<String> requestTable;
    /* Storage persistente que guarda a correspondência entre pedidos e métricas. */

    private static Set<String> activevms;
    /* Storage local que guarda as máquinas virtuais que estão a prontas a receber pedidos. */

    private static Map<String, Long> workload = new HashMap<>();
    /* Storage local com a estimativa da carga de cada vm. (desde a ultima atualização) */

    public static void init() throws AmazonClientException {

        AWSCredentials credentials = null;

        try {
            credentials = new ProfileCredentialsProvider().getCredentials();
        } catch (Exception e) {
            throw new AmazonClientException(
                    "Cannot load the credentials from the credential profiles file. " +
                            "Please make sure that your credentials file is at the correct " +
                            "location (~/.aws/credentials), and is in valid format.",
                    e);
        }

        ec2 = AmazonEC2ClientBuilder.standard().withRegion( CloudStandart.region )
                .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        try{
            CMonitor.vmstates = new LocalStorage<String>("VirtualMachines");
        }catch(Exception e){
            Logger.log("error loading MonitorTable");
        }

        try{
            CMonitor.requestTable = new LocalStorage<String>("RequestTable");
        }catch(Exception e){
            Logger.log("error loading RequestTable");
        }

        CMonitor.activevms  = new ConcurrentSkipListSet<>(CMonitor.vmstates.keys());

    }

    /* Cria uma nova VM */
    public static String summon() throws AmazonServiceException {

        RunInstancesRequest runInstancesRequest =
               new RunInstancesRequest();

        runInstancesRequest.withImageId(imageid)
            .withInstanceType(instancetype)
            .withMinCount(1)
            .withMaxCount(1)
            .withKeyName(keyname)
            .withSecurityGroups(securitygroups);
        
        RunInstancesResult runInstancesResult =
            ec2.runInstances(runInstancesRequest);

        Instance newInstance = runInstancesResult
            .getReservation().getInstances()
            .get(0);

        String newInstanceId =  newInstance.getInstanceId();

        Logger.log("New Instanceid: ");
        Logger.log(newInstanceId);

        SelfExecutingQueue.add(newInstanceId);

        return newInstanceId;
    }

    static void updateCache( Set<String> readyids,  Map<String,Instance> instances ){
        Logger.log("Updating cache!");
        for( String vm: readyids ) {
            Logger.log(vm);
            Instance vmi = instances.get(vm);

            Map<String, String> properties = new HashMap<>();

            properties.put(
                    "public.ip",
                    vmi.getPublicIpAddress()
            );

            properties.put(
                    "queue.size",
                    "0"
            );

            properties.put(
                    "private.ip",
                    vmi.getPrivateIpAddress()
            );

            properties.put(
                    "dns",
                    vmi.getPublicDnsName()
            );

            CMonitor.vmstates.put(
                    vm,
                    properties
            );
            Logger.log(" vmstates ");
            CMonitor.workload.put(
                    vm,
                    0L
            );

            Logger.log("checkif:" + vmstates.describe());

            CMonitor.activevms.add(vm);

            Logger.log(" Adding as Active ");
            Logger.log( readyids.toString() );
        }
    }

    /* Desliga uma VM assim que esta acabar de servir. */
    public static Map<String,String> schedulerecall( String vmid ){

        if( CMonitor.activevms.contains(vmid) ){
            CMonitor.activevms.remove(vmid);

            Recaller worker = new Recaller(vmid);
            worker.start();
        }else{
            Logger.log("This vm is not active! ");
        }

        return CMonitor.vmstates.get(vmid);
    }

    /* Termina imediatamente uma Máquina Virtual - Inseguro */
    private static void recall(String vmid ) {

        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        
        termInstanceReq.withInstanceIds(vmid);
        try{
            ec2.terminateInstances(termInstanceReq);
        }catch (AmazonServiceException e){
            Logger.log(e.toString());
        }


        if( CMonitor.activevms.contains(vmid) )
            CMonitor.activevms.remove(vmid);

        CMonitor.vmstates.remove(vmid);
        CMonitor.workload.remove(vmid);

        Logger.log("Recalling: "+ vmid);
    }

    public static Map<String,String> get(String vmid){

        return CMonitor.vmstates.get(vmid);
    }

    public static String describe(){
        return CMonitor.vmstates.describe();
    }

    /* TODO: Remover as restantes estruturas. */
    public static void terminate(){

        for( String k: CMonitor.vmstates.keys())
            CMonitor.recall(k);

        CMonitor.vmstates.destroy();

    }

    public static Set<String> keys(){
        return new HashSet<>(CMonitor.activevms);
    }

    /* Obtêm a listagem de VM ATIVAS */
    private static Map<String,Instance> getActiveInstances() {

        Map<String,Instance> instances = new HashMap<>();

        DescribeInstancesRequest request = new DescribeInstancesRequest();

        DescribeInstancesResult response = ec2.describeInstances(request);

        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {
                //Logger.log( "reservation: " + instance.getInstanceId());
                if( instance.getState().getName().equals("running") )
                    instances.put(instance.getInstanceId(), instance);
            }
        }

        return instances;
    }

    static class Recaller extends Thread{
        // waits for the server to stop serving to issue a recall
        private String vmid;

        Recaller(String vmid){
            this.vmid = vmid;
        }

        @Override
        public void run() {
            boolean b = true;

            while( b ){
                b = ! CMonitor.vmstates.get(vmid).get("queue.size").equals("0");

                try {
                    Thread.sleep(1000); // 1 sec
                }catch (InterruptedException e){
                    Logger.log("Recalled Interrupted");
                }

            }

            CMonitor.recall(vmid);
        }
    }

    static class SelfExecutingQueue extends Thread{
        private static ReentrantLock rl;
        private static Condition c;
        private static Queue<String> q = new ArrayDeque<>();
        private static boolean updating = false;

        static{
            SelfExecutingQueue.rl = new ReentrantLock();
            SelfExecutingQueue.c = rl.newCondition();
        }

        public static void add(String vmid){
            try{
                rl.lock();

                q.add(vmid);
                c.signal();

                if( updating == false ){
                    updating = true;
                    (new SelfExecutingQueue()).start();
                }

            }finally {
                rl.unlock();
            }
        }




        @Override
        public void run() {

            Map<String,Instance> ins ;
            int cycles = 3;
            Set<String> workload = new HashSet<>();
            Set<String> iset;
            Queue<String> tmpq;
            boolean halt = true;

            // loop;

            while( cycles > 0 || workload.size() != 0) { // enquanto ha ciclos ou elementos à espera.

                ins = CMonitor.getActiveInstances();

                try {
                    rl.lock();
                    tmpq = SelfExecutingQueue.q;
                    SelfExecutingQueue.q = new ArrayDeque<>();
                } finally {
                    rl.unlock();
                }

                workload.addAll(tmpq);
                iset = new TreeSet<>(workload);
                iset.retainAll(ins.keySet()); // iset contains intersection.

                if (iset.size() > 0) {
                    // do the updating
                    CMonitor.updateCache(iset, ins);
                    workload.removeAll(iset);
                    Logger.log("Terminated update Cache.");
                }

                if (workload.size() == 0) {// (Se não tiver pedidos por fazer)
                    cycles -= ((tmpq.size() == 0) ? 1 : 0);
                    if( cycles == 0 ){
                        Logger.log("DynamicQ:about to leave");
                        try{
                            rl.lock();
                            updating = false;
                            halt=false;
                        }finally {
                            rl.unlock();
                        }
                    }
                }

                if( halt ){
                    try {
                        rl.lock();
                            c.await(100, TimeUnit.MILLISECONDS);
                    }catch (InterruptedException e){
                        Logger.log(e.getMessage());
                    }finally {
                        rl.unlock();
                    }
                }
            }
        }
    }
}