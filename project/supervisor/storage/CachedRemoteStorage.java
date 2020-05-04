
package supervisor.storage;

import com.amazonaws.AmazonClientException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CachedRemoteStorage extends RemoteStorage{
    
    public static ConcurrentHashMap<String,Map<String, String>> cache = new ConcurrentHashMap<String,Map<String, String>>();
    
    public CachedRemoteStorage(String table, String key) {
        super(table, key);
    }
    
    public static void init(boolean instance) throws AmazonClientException {
        RemoteStorage.init(instance);
    }

    @Override
    public String describe() {
        return super.describe();
    }

    // TODO this will change depending on how we aggregate the values
    @Override
    public void put(String key, Map<String, String> newItem) {
        super.put(key, newItem);
        cache.put(key, newItem);
    }

    @Override
    public void destroy() {
        super.destroy();
        cache.clear();
    }

    @Override
    public Map<String, String> get(String key) {
        Map<String, String> value = cache.get(key);
        if (value==null){
            return forceGet(key);
        } else {
            System.out.println("get "+key);
            return value;
        }
    }


    public Map<String, String> forceGet(String key) {
        Map<String, String> value = super.get(key);
        aggregate(value);
        return value;
    }
    
    
    //TODO
    private void aggregate(Map<String, String> value){
        
    }
    
    // to be sure we have all the keys this needs to be remote
    @Override
    public Set<String> keys() {
        return super.keys();
    }

    @Override
    public boolean contains(String key) {
        if (!cache.contains(key))
            return super.contains(key);
        else
            return true;
    }
    
}
