package supervisor.storage;

import supervisor.util.Logger;

import java.util.HashMap;

public class RStorageTest {

    public static void main(String[] argv) {
        RemoteStorage.init(false);
        Logger.publish(true, false);

        RemoteStorage s = new RemoteStorage("TsTable", "key");
        Logger.log(s.describe());

        HashMap<String, String> item = new HashMap<>();
        item.put("year", "1980");
        item.put("month", "oo");

        s.put("r2", item);

        Logger.log(s.get("r1").toString());
        Logger.log(String.valueOf(s.contains("r1")));

        s.destroy();
    }
}
