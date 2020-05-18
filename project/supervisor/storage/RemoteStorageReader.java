package supervisor.storage;

import supervisor.balancer.estimation.Estimator;
import supervisor.server.Count;
import supervisor.storage.TaskStorage;
import supervisor.util.CloudStandart;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

public class RemoteStorageReader {

    public static void main(String[] args) throws IOException, ClassNotFoundException {
        CloudStandart.init();
        TaskStorage.init(false);

        TaskStorage a = new TaskStorage();

        Map<String, Map<Integer, Count>> metrics = new HashMap<>();

        for( Map<String,String> row : a.getAll() ){
            double v = Estimator.transform(
                    Count.fromString(row.get("Count")).mean(),
                    row.get("key").split(":")[0]);

            System.out.println(row.get("key") + ":" + v);
            System.out.println(row.get("key") + ":" + Count.fromString(row.get("Count")).var());



            //overhead.get(row.get("classe")).put(
            //        Integer.valueOf(row.get("un")),
            //        Count.fromString(row.get("Overhead"))
            //);
        }



    }
}
