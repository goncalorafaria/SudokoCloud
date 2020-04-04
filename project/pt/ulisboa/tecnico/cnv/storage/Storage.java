package pt.ulisboa.tecnico.cnv.storage;

import java.util.Map;

public interface Storage<Item> {
    String describe();
    void put(String key, Map<String,Item> newItem);
}
