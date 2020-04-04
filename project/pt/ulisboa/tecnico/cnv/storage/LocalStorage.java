package pt.ulisboa.tecnico.cnv.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import pt.ulisboa.tecnico.cnv.storage.Storage;

public class LocalStorage<V> implements Storage<V> {

    static{
        LocalStorage.database = new ConcurrentSkipListMap<String, Map>();
        LocalStorage.database.put("test", new ConcurrentSkipListMap<String,String>() );
    }
    static Map<String, Map> database;

    private String tablename;

    public LocalStorage(String table) throws Exception {
        this.tablename = table;
        if( !database.containsKey(tablename) )
            throw new NullPointerException();
    }
    public String describe(){
        return database.get(this.tablename).entrySet().toString();
    }
    public void put(String key, Map<String,V> newItem){
        database.get(this.tablename).put(key, newItem);
    }
}
