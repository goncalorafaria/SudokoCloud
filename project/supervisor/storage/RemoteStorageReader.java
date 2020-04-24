package supervisor.storage;

import supervisor.util.CloudStandart;

import java.util.HashMap;
import java.util.Map;

public class RemoteStorageReader {



    public static void main(String[] args){
        CloudStandart.init();
        RemoteStorage.init(false);


        RemoteStorage a = new RemoteStorage("UUUUU","key");

        System.out.println(a.describe());

        Map<String,String> s = new HashMap<>();
        s.put("name","gooc");

        a.put("r3",s);

        System.out.println(a.get("r3"));
    }
}
