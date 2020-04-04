import BIT.highBIT.*;
import java.io.*;
import java.util.*;

import pt.ulisboa.tecnico.cnv.storage.LocalStorage;
import pt.ulisboa.tecnico.cnv.storage.Storage;

public class CMonitor {

    static {

        try{
            CMonitor.vmstates = new LocalStorage<String>("MonitorTable");
        }catch(Exception e){
            System.out.println("error loading MonitorTable");
        }
                
    }

    private static Storage<String> vmstates; 
    /*
        vm-id -> Map<String, String> {"property": value}

        eg. 
        {"queue_size" : "4"}
    */

    public static void addNode(String vmid ){
        CMonitor.vmstates.put(vmid, new ConcurrentSkipListMap<String, String>());
    }

    public static Map<String,String> removeNode(String vmid ){
        return CMonitor.vmstates.remove(vmid);
    }

}