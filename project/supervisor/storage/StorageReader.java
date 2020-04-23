package supervisor.storage;

import supervisor.server.Count;

import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.*;

public class StorageReader {

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        LocalStorage.init("bin/Storage.db");
        Storage<String> requestTable;
        Map<String, Map<Integer, Count>> storage = new HashMap<>();
        Map<String, Map<Integer, List<Integer>>> sp = new HashMap<>();

        try {
            requestTable = new LocalStorage<String>("RequestTable");

            Count c;

            for (String s : requestTable.keys()) {
                System.out.println(s);
                c = Count.fromString(requestTable.get(s).get("Overhead"));

                String method = s.split(":")[0];

                if (!storage.containsKey(method))
                    storage.put(method, new TreeMap<Integer, Count>());

                storage.get(method).put(Integer.valueOf(s.split(":")[1]), c);

            }

        } catch (Exception e) {
            System.out.println("woops");
            System.out.println(e.toString());
        }

        for (String method : storage.keySet()) {
            sp.put(method, new HashMap<Integer, List<Integer>>());
            for (int j = 0; j < 5; j++) {
                sp.get(method).put(j, new ArrayList<Integer>());
                for (Integer i : storage.get(method).keySet()) {
                    Count c = storage.get(method).get(i);

                    sp.get(method).get(j).add(c.getV(j));
                }
                //System.out.println(sp.get(method));
            }
            System.out.println(sp.get(method).get(0).size());
        }

        //System.out.println(sp.toString());

        PrintWriter writer = new PrintWriter("reader.txt");

        int i = 0;
        writer.print("{");
        for (String method : sp.keySet()) {

            writer.print("\"" + method + "\": { ");
            for (int j = 0; j < 5; j++) {

                writer.print(j + ":" + sp.get(method).get(j));
                if (j != 4)
                    writer.print(",");
                else
                    writer.print("}");

            }
            if (i != 2)
                writer.print(",");
            else
                writer.print("}");

            i++;
        }

        writer.flush();
        writer.close();

    }
}
