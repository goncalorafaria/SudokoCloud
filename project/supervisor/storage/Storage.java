package supervisor.storage;

import java.util.Map;
import java.util.Set;

public interface Storage<Item> {
    String describe();
    void put(String key, Map<String,Item> newItem);
    Map<String,Item> remove(String key);
    void destroy();
    Map<String,Item> get(String key);
    Set<String> keys();
}
