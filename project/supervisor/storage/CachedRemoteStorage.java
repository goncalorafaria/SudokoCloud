
package supervisor.storage;

import com.amazonaws.AmazonClientException;
import java.util.Map;
import java.util.Set;

public class CachedRemoteStorage extends RemoteStorage{
    
    public CachedRemoteStorage(String table, String key) {
        super(table, key);
    }
    
    public static void init(boolean instance) throws AmazonClientException {
        RemoteStorage.init(instance);
    }

    private void setup() {
        
    }

    @Override
    public String describe() {
        return super.describe();
    }

    @Override
    public void put(String key, Map<String, String> newItem) {
        super.put(key, newItem);
    }

    @Override
    public void destroy() {
        super.destroy();
    }

    @Override
    public Map<String, String> get(String key) {
        return super.get(key);
    }

    @Override
    public Set<String> keys() {
        return super.keys();
    }

    @Override
    public boolean contains(String key) {
        // TODO
        return super.contains(key);
    }
    
}
