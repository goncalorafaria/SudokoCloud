package supervisor.storage;

import java.util.Map;

public class TaskStorage extends RemoteStorage {

    public TaskStorage() {
        super("RequestTable", "task");
    }

    @Override
    public void put(String key, Map<String, String> newItem) {

        String[] sv = key.split(":");
        String classe = sv[0] + ":" + sv[2] + ":" + sv[3];
        String un = sv[1];

        newItem.put("classe", classe);
        newItem.put("un", un);

        super.put(key, newItem);
    }
}
