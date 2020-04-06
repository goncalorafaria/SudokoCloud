package supervisor.server;

import BIT.highBIT.*;
import java.io.*;
import java.util.*;
import java.util.HashMap;

import supervisor.storage.LocalStorage;
import supervisor.storage.Storage;

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


public class CMonitor {

    private static AmazonEC2 ec2;
    
    private static String region = "eu-west-2";
    private static String imageid = "ami-0cb790308f7591fa6";
    private static String instancetype = "t2.micro";
    private static String keyname = "CNV-lab-AWS";
    private static String securitygroups = "CNV-ssh+http";

    private static Storage<String> vmstates; 
    /*
        vm-id -> Map<String, String> {"property": value}

        eg. 
        {"queue_size" : "4"}
    */

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


        Map<String,String> properties = new HashMap<String,String>();

        properties.put(
                "PublicIpAddress",
                newInstance.getPublicIpAddress()
        );

        properties.put(
                "PrivateIpAddress",
                newInstance.getPrivateIpAddress()
        );

        CMonitor.vmstates.put(
                newInstanceId,
                properties
        );

        return newInstanceId;
    }

    public static Map<String,String> recall(String vmid ) throws AmazonServiceException {

        TerminateInstancesRequest termInstanceReq = new TerminateInstancesRequest();
        
        termInstanceReq.withInstanceIds(vmid);
        ec2.terminateInstances(termInstanceReq);

        return CMonitor.vmstates.remove(vmid);
    }

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

        ec2 = AmazonEC2ClientBuilder.standard().withRegion( CMonitor.region )
        .withCredentials(new AWSStaticCredentialsProvider(credentials)).build();

        try{
            CMonitor.vmstates = new LocalStorage<String>("MonitorTable");
        }catch(Exception e){
            System.out.println("error loading MonitorTable");
        }
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
}