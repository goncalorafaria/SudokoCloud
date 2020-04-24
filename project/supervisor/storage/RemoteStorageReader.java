package supervisor.storage;

import supervisor.util.CloudStandart;

import java.util.HashMap;
import java.util.Map;

public class RemoteStorageReader {

    public static void main(String[] args){
        CloudStandart.init();
        TaskStorage.init(false);

        TaskStorage a = new TaskStorage();

        //System.out.println(a.queryMetrics("BFS","9","9"));

        System.out.println(a.describe());
    }
}
