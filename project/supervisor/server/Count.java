package supervisor.server;

import java.util.Queue;

public class Count extends Metric {

    int i_count = 0;
    int b_count = 0;
    int m_count = 0;

    public synchronized Count counti(int incr){ i_count += incr; return this; }
    public synchronized Count countb(){ b_count++; return this; }
    public synchronized Count countm(){ m_count++; return this; }

    public String toString(){
        return m_count + ":" + b_count + ":" + i_count;
    }
}
