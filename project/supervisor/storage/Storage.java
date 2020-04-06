package supervisor.storage;

import java.util.Map;

public interface Storage<Item> {
    String describe();
    void put(String key, Map<String,Item> newItem);
    Map<String,Item> remove(String key);
    void destroy();
}
