package supervisor.storage;

import supervisor.server.Count;
import supervisor.util.CloudStandart;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class RemoteStorageReader {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        CloudStandart.init();
        CachedRemoteStorage.init(false);

        CachedRemoteStorage a = new CachedRemoteStorage(
                CloudStandart.taskStorage_tablename,
                CloudStandart.taskStorage_tablekey);

        Map<String, Map<Integer, Count>> metrics = new HashMap<>();
        Map<String, Map<Integer, Count>> overhead = new HashMap<>();

        for( Map<String,String> row : a.getAll() ){
            //System.out.println(row);
            if( !metrics.containsKey(row.get("classe")) ){
                metrics.put(row.get("classe"), new TreeMap<Integer,Count>());
                //overhead.put(row.get("classe"), new TreeMap<Integer, Count>());
            }

            metrics.get(row.get("classe")).put(
                    Integer.valueOf(row.get("un")),
                    Count.fromString(row.get("Count"))
            );


            //overhead.get(row.get("classe")).put(
            //        Integer.valueOf(row.get("un")),
            //        Count.fromString(row.get("Overhead"))
            //);
        }

        System.out.println(metrics);
        //System.out.println("#####");

        //System.out.println(overhead);

        //System.out.println(a.describe());


    }
}
