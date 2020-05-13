package supervisor.balancer.estimation;

import supervisor.server.Count;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Group implements Comparable<Group>{

    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final Lock rl = rwl.readLock();
    private final Lock wl = rwl.writeLock();
    ConcurrentHashMap<String,Element> table = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<Element> rcvl = new ConcurrentSkipListSet<>();

    private final BinaryStochasticBanditProblem bscp =
            new BinaryStochasticBanditProblem(0.05);

    static class Element implements Comparable<Element> {
        private final BinaryStochasticBanditProblem ucb =
                new BinaryStochasticBanditProblem(0.1);
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

        @Override
        public int compareTo(Element o) {
            int th = this.ucb.getHit();
            int oh = o.ucb.getHit();

            return Integer.compare(th, oh);
        }
    }

    public Group(){
    }

    public int getHit(){
        try {
            rl.lock();
            return bscp.getHit();
        }finally {
            rl.unlock();
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

    public int put(String key, Map<String,String> value ){
        try {
            wl.lock();
            Element e;
            if( table.containsKey(key) ){
                e = table.get(key);
                rcvl.remove(e);
                e.update(value);
                rcvl.add(e);
                return 0;
            }else {
                e = new Element(value);
                rcvl.add(e);
                table.put(key, e);
                if( table.size() > 2)
                    return 1;
                else
                    return 0;
            }

        }finally {
            wl.unlock();
        }
    }

    float getScore(int m, int hm){
        try {
            rl.lock();
            float pm  = this.rcvl.size()/(float)m;
            float phm = this.bscp.getHit()/(float)hm;
            return hm-pm;
        }finally {
            rl.unlock();
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
                return table.get(un).shouldUpdate();
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
        try {
            rl.lock();

            int target = Integer.parseInt(un);
            int before = Integer.MIN_VALUE;
            int after = Integer.MAX_VALUE;
            List<Object[]> l = new LinkedList<>();

            int fst = -1;
            int snd = -1;
            float dif;
            float fstv = Float.MAX_VALUE;
            float sndv = Float.MAX_VALUE;

            Set<String> ks = table.keySet();

            for (String k : ks) {
                int candidate = Integer.parseInt(k);
                if (candidate < target) {
                    if (before < candidate)
                        before = candidate;
                } else {
                    if (candidate < after)
                        after = candidate;
                }

                dif = (candidate - target);
                dif = dif * dif;

                if (fst != -1 && snd != -1) {
                    if (dif <= fstv) {
                        fst = candidate;
                        fstv = dif;
                    } else {
                        if (dif < sndv) {
                            snd = candidate;
                            sndv = dif;
                        }
                    }
                } else {
                    if (fst == -1) {
                        fst = candidate;
                        fstv = dif;
                    } else {
                        snd = candidate;
                        sndv = dif;
                    }
                }
            }

            l.add(new Object[2]);
            l.add(new Object[2]);
            String sbefore, safter;

            if (before == Integer.MIN_VALUE || after == Integer.MAX_VALUE) {
                sbefore = String.valueOf(fst);
                safter = String.valueOf(snd);
            } else {
                sbefore = String.valueOf(before);
                safter = String.valueOf(after);
            }

            l.get(0)[0] = sbefore;
            l.get(0)[1] = this.get(sbefore);
            l.get(1)[0] = safter;
            l.get(1)[1] = this.get(safter);

            return l;
        }finally {
            rl.unlock();
        }
    }

    @Override
    public int compareTo(Group o) {
        List<Lock> ll = new LinkedList<>();
        ll.add(rl);
        ll.add(o.rl);
        Arrays.sort(ll.toArray());

        try {
            for( Lock l : ll)
                l.lock();

        int th = this.size();
        int oh = o.size();

        if( th == oh ){
            th = this.getHit();
            oh = o.getHit();

            return Integer.compare(th,oh);
        }

        return Integer.compare(oh, th);

        }finally {
            for( Lock l : ll)
                l.unlock();
        }
    }

    public boolean trim(){
        try {
            wl.lock();
            if( rcvl.size() > 2){
                Element e = rcvl.first();
                rcvl.remove(e);
                return true;
            }else{
                return false;
            }
        }finally {
            wl.unlock();
        }
    }
}

