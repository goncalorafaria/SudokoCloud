package supervisor.server;

import com.amazonaws.util.Base64;

import java.io.*;
import java.util.concurrent.atomic.AtomicLong;

public class Count implements java.io.Serializable {

    long i_count = 0;
    long b_count = 0;
    long m_count = 0;
    double br_count = 0;
    long inc_count = 0;
    double br_s = 0;
    AtomicLong br_count_act = new AtomicLong(0L);
    public long n = 1;

    public Count() {
    }

    public Count(Count c){
        i_count = c.i_count;
        b_count = c.b_count;
        m_count = c.m_count;
        br_count = c.br_count;
        inc_count = c.inc_count;
        br_s = c.br_s;
        long lx = c.br_count_act.get();
        br_count_act.set(lx);
        n = c.n;
    }

    public static Count fromString(String s) throws IOException, ClassNotFoundException {
        byte[] data = Base64.decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return (Count) o;
    }

    public void aggregate(Count b) {
        this.n += 1;

        double mk = this.br_count;

        this.br_count += (b.br_count - mk) / n;
        this.br_s += ( b.br_count - mk ) * ( b.br_count - this.br_count);

        //this.i_count += k * (b.i_count - this.i_count) / n;
        //this.b_count += k * (b.b_count - this.b_count) / n;
        //this.m_count += k * (b.m_count - this.m_count) / n;
        //this.inc_count += k * (b.inc_count - this.inc_count) / n;
    }

    public double mean(){
        return this.br_count;
    }

    public double var(){
        if( n > 1){
            return this.br_s/(n-1);
        }else{
            return this.br_count/4;
        }
    }

    public long getlocked(){
        return this.br_count_act.get();
    }

    public void lock(){
        this.br_count = (double)this.br_count_act.get();
    }

    public int getV(int index) {
        switch (index) {
            case 0:
                return (int)i_count;
            case 1:
                return (int)b_count;
            case 2:
                return (int)m_count;
            case 3:
                return (int)br_count;
            case 4:
                return (int)inc_count;
            default:
                return -1;
        }
    }

    public boolean valid() {
        return !((i_count == b_count) &&
                (b_count == m_count) &&
                (m_count == br_count) &&
                (br_count == inc_count));
    }

    public synchronized Count counti(int incr) {
        i_count += incr;
        return this;
    }

    public synchronized Count countb() {
        b_count++;
        return this;
    }

    public synchronized Count countm() {
        m_count++;
        return this;
    }

    public synchronized Count countBranch() {
        br_count_act.incrementAndGet();
        return this;
    }

    public synchronized Count countinc() {
        inc_count++;
        return this;
    }

    public String toString() {
        return i_count + ":" + b_count + ":" + m_count + ":" + br_count + ":" + inc_count + ":" + br_s;
    }

    public String explain() {
        return "instruction:basicblock:method:branch:increment:var";
    }

    public String toBinary() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(this);
        oos.close();
        return Base64.encodeAsString(baos.toByteArray());
    }
}


