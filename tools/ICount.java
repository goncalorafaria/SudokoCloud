import BIT.highBIT.ClassInfo;
import BIT.highBIT.Routine;
import BIT.highBIT.BasicBlock;
import BIT.highBIT.BasicBlockArray;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import supervisor.util.Logger;

public class ICount {

    private static Map<Long, ICount.Count> counter;
    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */

    public static void init(){
        counter = new ConcurrentSkipListMap<>();
    }

    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();
        
        for (int i = 0; i < infilenames.length; i++) {
            String infilename = infilenames[i];
            if (infilename.endsWith(".class") && !infilename.equals("SolverMain.class") && !infilename.contains("Parser") ) {
				// create class info object
				ClassInfo ci = new ClassInfo(argv[0] + System.getProperty("file.separator") + infilename);
                // loop through all the routines
                // see java.util.Enumeration for more information on Enumeration class

                //System.out.println(infilename);

                for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                    Routine routine = (Routine) e.nextElement();

                    if( infilename.equals("Solver.class") && routine.getMethodName().contains("solveSudoku") ){

                        routine.addBefore("ICount", "start", new Integer(1) );
                        routine.addAfter("ICount", "end", new Integer(1) );
                    }

                    routine.addBefore("ICount", "mcount", new Integer(1) );

                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();
                        bb.addBefore("ICount", "count", new Integer(bb.size()));
                    }
                }

                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }

    public static void count(int incr) {
        counter.get(Thread.currentThread().getId()).counti(incr).countb();

    }

    public static void end(int incr){
        Logger.log("counts: " +  Thread.currentThread().getId() + ": " + counter.get(Thread.currentThread().getId()) );
        counter.remove(Thread.currentThread().getId());
    }
    public static void start(int incr){
        counter.put(
                Thread.currentThread().getId(),
                new Count()
        );
        Logger.log("start " +  Thread.currentThread().getId());
    }

    public static void mcount(int incr) {
        counter.get(Thread.currentThread().getId()).countm();

    }

    static public class Count {
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

}

