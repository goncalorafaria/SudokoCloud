package supervisor.server;

import BIT.highBIT.*;

import java.util.*;
import java.util.HashMap;

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

    private static AmazonEC2 ec2;

    private static String imageid = "ami-0cb790308f7591fa6";
    private static String instancetype = "t2.micro";
    private static String keyname = "CNV-lab-AWS";
    private static String securitygroups = "CNV-ssh+http";

    private static Storage<String> vmstates;
    private static Set<String> activevms;
    private static Map<String, Long> workload = new HashMap<>();
    /*
        vm-id -> Map<String, String> {"property": value}

        eg. 
        {"queue_size" : "4"}
    */

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

        CMonitor.activevms = CMonitor.vmstates.keys();
    }

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

        Map<String,String> properties = new HashMap<String,String>();


        properties.put(
                "queue.size",
                "0"
        );


        CMonitor.vmstates.put(
                newInstanceId,
                properties
        );

        CMonitor.workload.put(
                newInstanceId,
                0L
        );

        CMonitor.activevms.add(newInstanceId);

        return newInstanceId;
    }

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

    private static void recall(String vmid ) throws AmazonServiceException {

        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        
        termInstanceReq.withInstanceIds(vmid);
        ec2.terminateInstances(termInstanceReq);

        CMonitor.vmstates.remove(vmid);
    }

    public static Map<String,String> get(String vmid){

        return CMonitor.vmstates.get(vmid);
    }

    public static String describe(){
        return CMonitor.vmstates.describe();
    }

    public static void terminate(){

        for( String k: CMonitor.vmstates.keys())
            CMonitor.recall(k);

        CMonitor.vmstates.destroy();

    }

    public static Set<String> keys(){
        return CMonitor.vmstates.keys();
    }

    public static Set<Instance> getActiveInstances() {

        Set<Instance> instances = new HashSet<Instance>();

        DescribeInstancesRequest request = new DescribeInstancesRequest();

        DescribeInstancesResult response = ec2.describeInstances(request);

        for (Reservation reservation : response.getReservations()) {
            for (Instance instance : reservation.getInstances()) {

                if( instance.getState().getName().equals("running")
                        && CMonitor.activevms.contains(instance.getInstanceId()) )
                    instances.add( instance );
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

}