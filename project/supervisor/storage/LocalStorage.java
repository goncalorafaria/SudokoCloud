package supervisor.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import supervisor.storage.Storage;

public class LocalStorage<V> implements Storage<V> {

    static{
        LocalStorage.database = new ConcurrentSkipListMap<String, Map>();
    }

    static Map<String, Map> database;

    private String tablename;

    public LocalStorage(String table) throws Exception {
        this.tablename = table;
        if( !database.containsKey(tablename) )
            LocalStorage.database.put(
                table, 
                new ConcurrentSkipListMap<String,Map>() 
            );
    }

    public String describe(){
        return database.get(this.tablename).entrySet().toString();
    }

    public void put(String key, Map<String,V> newItem){
        database.get(this.tablename).put(key, newItem);
    }

    public Map<String,V> remove(String key){
        Map<String, Map<String,V> >  table = (Map<String, Map<String,V> >)database.get(this.tablename);
     
        return table.remove(key);
    }
    
    public void destroy(){
        database.remove(this.tablename);
    }
}
