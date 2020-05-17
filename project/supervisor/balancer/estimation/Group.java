package supervisor.balancer.estimation;

import supervisor.server.Count;
import supervisor.util.Logger;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Group implements Comparable<Group>{

    ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();
    final Lock rl = rwl.readLock();
    private final Lock wl = rwl.writeLock();
    ConcurrentHashMap<String,Element> table = new ConcurrentHashMap<>();

    final AtomicInteger key = new AtomicInteger(0);
    final AtomicInteger skey = new AtomicInteger(0);

    private final UCB bscp =
            new UCB(0.1);

    static class Element implements Comparable<Element> {
        private final UCB ucb =
                new UCB(0.1);
        private Count c;

        final String un;

        Element(Map<String, String> value, String un) {
            this.un = un;
            try {
                this.c = Count.fromString(value.get("Count"));
                ucb.setUpdate(c.n);
            } catch (IOException e) {
                e.printStackTrace();
            }catch (ClassNotFoundException e){
                e.printStackTrace();
            }
        }

        Element(Count c, String un) {
            this.un = un;
            this.c = new Count(c);
        }

        Count getCount(){
            return new Count(c);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Element)) return false;
            Element element = (Element) o;
            return Objects.equals(un, element.un);
        }

        boolean shouldUpdate(){
            return ucb.shouldUpdate();
        }

        // TODO: ALTER UCB -  acording to N.
        void updateDirect(Count candidate){
            if( candidate.n > c.n ){
                ucb.setUpdate(candidate.n);
                this.c = candidate;
            }
        }

        void update(Map<String,String> value){
            try {
                Count candidate = Count.fromString(value.get("Count"));
                updateDirect(candidate);

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

            if( th == oh ){
                th = (int)this.c.n;
                oh = (int)o.c.n;
            }

            return Integer.compare(th, oh);
        }
    }

    public Group(){
    }

    public boolean contains(String key){
        try {
            rl.lock();
            return table.containsKey(key);
        }finally {
            rl.unlock();
        }
    }

    public int response(String un, Count c){
        try {
            wl.lock();

            Element e;
            if( table.containsKey(un) ){
                e = table.get(un);
                e.updateDirect(c);
                return 0;
            }else {
                e = new Element(c,un);
                table.put(un, e);

                if( table.size() > 2)
                    return 1;
                else
                    return 0;
            }


        }finally {
            wl.unlock();
        }
    }

    public int put(String un, Map<String,String> value ){
        try {
            wl.lock();
            Element e;
            if( table.containsKey(un) ){
                e = table.get(un);
                e.update(value);
                return 0;
            }else {
                e = new Element(value,un);

                table.put(un, e);

                if( table.size() > 2)
                    return 1;
                else
                    return 0;
            }

        }finally {
            wl.unlock();
        }
    }

    void revertUpdate(){
        try {
            rl.lock();
            this.bscp.revertUpdate();
        }finally {
            rl.unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Group)) return false;
        Group group = (Group) o;
        return Objects.equals(rwl, group.rwl);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rwl);
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

    public void blockKey(){
        try {
            this.wl.lock();
            this.key.set( this.size() );
            this.skey.set( bscp.getHit() );
        }finally {
            this.wl.unlock();
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
            Set<Integer> kis = new HashSet<>();

            for (String k : ks) {
                int candidate = Integer.parseInt(k);
                kis.add(candidate);
                if (candidate < target) {
                    if (before < candidate)
                        before = candidate;
                } else {
                    if (candidate < after)
                        after = candidate;
                }

            }

            l.add(new Object[2]);
            l.add(new Object[2]);
            String sbefore, safter;

            if (before == Integer.MIN_VALUE || after == Integer.MAX_VALUE) {
                sbefore = String.valueOf(
                        Collections.min(kis));
                safter= String.valueOf(
                        Collections.max(kis));
            } else {
                sbefore = String.valueOf(before);
                safter = String.valueOf(after);
            }

            Logger.log("Interpolating: " + sbefore + " " + safter);

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

        if( 0 < rl.toString().compareTo(o.rl.toString()) ){
            ll.add(rl);
        }else{
            ll.add(o.rl);
        }

        try {
            for( Lock l : ll)
                l.lock();

        int th = this.key.get();
        int oh = o.key.get();

        if( th == oh ){
            th = this.skey.get();
            oh = o.skey.get();

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
            if( this.size() > 2){
                Element e = Collections.min(this.table.values());
                this.table.remove(e.un);
                Logger.log("---  trim: " + e.un);
                return true;
            }else{
                return false;
            }
        }finally {
            wl.unlock();
        }
    }

    @Override
    public String toString() {
        return "Group{" +
                "table=" + table.keySet() +
                '}';
    }
}

