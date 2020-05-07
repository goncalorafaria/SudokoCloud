package supervisor.storage.remote;

import supervisor.util.CloudStandart;

import java.util.*;

public class TaskStorage extends CachedRemoteStorage {

    public TaskStorage() {
        super(CloudStandart.taskStorage_tablename,
                CloudStandart.taskStorage_tablekey);//,"classe","un");
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

    public static String extractClase(String key){
        String[] sv = key.split(":");
        String classe = sv[0] + ":" + sv[2] + ":" + sv[3];
        return classe;
    }

    }


