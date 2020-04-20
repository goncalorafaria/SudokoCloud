package supervisor.storage;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;

import supervisor.util.Logger;

public class LocalStorage<V> implements Storage<V> {
    /**
     * Implementação de uma tabela através de um ficheiro.
     * */
    private static Map<String, Map> database;
    private static String dbfile = "bin/Storage.db";
    private String tablename;

    public static void init() {
        try {
            FileInputStream fin = new FileInputStream(LocalStorage.dbfile);
            ObjectInputStream ois = new ObjectInputStream(fin);
            LocalStorage.database = (Map<String, Map>)ois.readObject();
            ois.close();
        }
        catch (Exception e) {
            LocalStorage.database =
                    new ConcurrentSkipListMap<String,Map>();
            Logger.log("Create db file ");
        }
    }

    public static void init(String dir){
        dbfile = dir;
        init();
    }

    public LocalStorage(String table){
        this.tablename = table;

        if( !database.containsKey(tablename) ) {
            LocalStorage.database.put(
                    table,
                    new ConcurrentSkipListMap<String, Map>()
            );
        }
        //Logger.log(LocalStorage.database.toString());
        LocalStorage.save();
    }

    public String describe(){
        return database.get(this.tablename).entrySet().toString();
    }

    public void put(String key, Map<String,V> newItem){
        //Logger.log(database.toString());
        database.get(this.tablename).put(key, newItem);
        LocalStorage.save();
    }

    public Set<String> keys(){
        return (Set<String>)LocalStorage.database.get(this.tablename).keySet();
    }

    private static void save(){
        try {
            FileOutputStream fout = new FileOutputStream(LocalStorage.dbfile);
            ObjectOutputStream oos = new ObjectOutputStream(fout);
            oos.writeObject(LocalStorage.database);
            oos.close();
        } catch (Exception e) {
            Logger.log(e.toString());
        }
    }

    public Map<String,V> remove(String key){
        Map<String, Map<String,V>> table =
                (Map<String, Map<String,V>>)database.get(this.tablename);

        Map<String,V> r = table.remove(key);

        LocalStorage.save();

        return r;
    }

    public boolean contains(String key){

        return database.
                get(this.tablename).containsKey(key);
    }
    
    public void destroy(){
        database.remove(this.tablename);
        LocalStorage.save();
    }

    public Map<String,V> get(String key){

        return (Map<String,V>)database.
                get(this.tablename).get(key);
    }
}
