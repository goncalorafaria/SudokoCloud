package supervisor.balancer.estimation;

import supervisor.server.Count;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


import java.util.Map;

public class Group {

    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    private final Lock rl = rwl.readLock();
    private final Lock wl = rwl.writeLock();
    ConcurrentHashMap<String,Element> table = new ConcurrentHashMap<>();
    private final BinaryStochasticBanditProblem bscp = new BinaryStochasticBanditProblem(0.1);

    static class Element {
        private final BinaryStochasticBanditProblem ucb = new BinaryStochasticBanditProblem(0.1);
        private Count c;

        Element(Map<String, String> value) {
            try {
                this.c = Count.fromString(value.get("Count"));
                ucb.setUpdate(c.n);
            } catch (IOException e) {
                e.printStackTrace();
            }catch (ClassNotFoundException e){
                e.printStackTrace();
            }
        }

        Count getCount(){
            return new Count(c);
        }

        boolean shouldUpdate(){
            return ucb.shouldUpdate();
        }

        // TODO: ALTER UCB -  acording to N.
        void update(Map<String, String> value){

            try {
                this.c = Count.fromString(value.get("Count"));
                ucb.setUpdate(c.n);
            } catch (IOException e) {
                e.printStackTrace();
            }catch (ClassNotFoundException e){
                e.printStackTrace();
            }
        }
    }

    public boolean contains(String key){
        try {
            rl.lock();
            return table.containsKey(key);
        }finally {
            rl.unlock();
        }
    }

    public void put(String key, Map<String,String> value ){
        try {
            wl.lock();

            if( table.containsKey(key) ){
                Element e = table.get(key);
                e.update(value);
            }else {
                table.put(key, new Element(value));
            }

        }finally {
            wl.unlock();
        }
    }

    public Count get(String un){
        try {
            rl.lock();
            Element e = this.table.get(un);
            if(e != null){
                return e.getCount();
            }else{
                return null;
            }
        }finally {
            rl.unlock();
        }
    }

    public boolean shouldUpdate(String un){
        try {
            rl.lock();
            if( table.size() < 2)
                return true;

            if( table.containsKey(un) ){
                boolean b = table.get(un).shouldUpdate();
                return b;
            }else{
                return bscp.shouldUpdate();
            }

        }finally {
            rl.unlock();
        }
    }

    public Count take(){
        try {
            rl.lock();
            Element e = this.table.values().iterator().next();
            if(e != null){
                return e.getCount();
            }else{
                return null;
            }
        }finally {
            rl.unlock();
        }
    }

    public int size(){
        try {
            rl.lock();
            return table.size();
        }finally {
            rl.unlock();
        }
    }

    public List<Object[]> getNearbyPair(String un){
        int target = Integer.parseInt(un);
        int before = Integer.MIN_VALUE;
        int after = Integer.MAX_VALUE;
        List<Object[]> l = new LinkedList<>();

        int fst = -1;
        int snd = -1;
        float dif;
        float fstv=Float.MAX_VALUE;
        float sndv=Float.MAX_VALUE;

        Set<String> ks = table.keySet();

        for( String k : ks ){
            int candidate = Integer.parseInt(k);
            if( candidate < target ){
                if( before < candidate )
                    before = candidate;
            }else{
                if( candidate < after )
                    after = candidate;
            }

            dif = (candidate - target);
            dif = dif*dif;

            if( fst != -1 && snd != -1){
                if (dif <= fstv) {
                    fst = candidate;
                    fstv = dif;
                } else {
                    if (dif < sndv) {
                        snd = candidate;
                        sndv = dif;
                    }
                }
            }else{
                if(fst == -1){
                    fst = candidate;
                    fstv = dif;
                }else {
                    snd = candidate;
                    sndv = dif;
                }
            }
        }

        l.add(new Object[2]);
        l.add(new Object[2]);
        String sbefore, safter;

        if(before == Integer.MIN_VALUE || after == Integer.MAX_VALUE){
            sbefore = String.valueOf(fst);
            safter = String.valueOf(snd);
        }else{
            sbefore = String.valueOf(before);
            safter = String.valueOf(after);
        }

        l.get(0)[0]=sbefore;
        l.get(0)[1]=this.get(sbefore);
        l.get(1)[0]=safter;
        l.get(1)[1]=this.get(safter);

        return l;
    }

}

