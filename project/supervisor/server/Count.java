package supervisor.server;

import com.amazonaws.util.Base64;

import java.io.*;
import java.util.Queue;

public class Count extends Metric implements java.io.Serializable {

    int i_count = 0;
    int b_count = 0;
    int m_count = 0;
    int br_count = 0;
    int inc_count = 0;

    int n = 1;

    public void aggregate( Count b ){
        int k = b.n;

        this.n+=k;

        this.i_count += k*(b.i_count - this.i_count)/n;
        this.b_count += k*(b.b_count - this.b_count)/n;
        this.m_count += k*(b.m_count - this.m_count)/n;
        this.br_count += k*(b.br_count - this.br_count)/n;
        this.inc_count += k*(b.inc_count - this.inc_count)/n;
    }

    public Count( Count a ){
        this.i_count = a.i_count;
        this.b_count = a.b_count;
        this.m_count = a.m_count;
        this.br_count = a.br_count;
        this.inc_count = a.inc_count;
        n = a.n;
    }

     public boolean valid(){
         return !((i_count == b_count)&&
                    (b_count == m_count)&&
                    (m_count == br_count)&&
                    (br_count == inc_count));
     }

    public Count(){ }

    public synchronized Count counti(int incr){ i_count += incr; return this; }
    public synchronized Count countb(){ b_count++; return this; }
    public synchronized Count countm(){ m_count++; return this; }
    public synchronized Count countBranch(){ br_count++; return this; }
    public synchronized Count countinc(){ inc_count++; return this; }

    public String toString(){
        return m_count + ":" + b_count + ":" + i_count + ":" + inc_count + ":" + br_count;
    }

    public String explain(){
        return "method:basicblock:instruction:increment:branch";
    }

    public String toBinary() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream( baos );
        oos.writeObject( this );
        oos.close();
        return Base64.encodeAsString(baos.toByteArray());
    }

    public static Count fromString(String s) throws IOException, ClassNotFoundException {
        byte [] data = Base64.decode(s);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(  data ) );
        Object o  = ois.readObject();
        ois.close();
        return (Count)o;
    }
}
