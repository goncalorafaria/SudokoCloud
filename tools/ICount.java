import BIT.highBIT.ClassInfo;
import BIT.highBIT.Routine;
import BIT.highBIT.BasicBlock;
import BIT.highBIT.BasicBlockArray;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

import supervisor.server.CNode;
import supervisor.server.Count;
import supervisor.server.Metric;
import supervisor.util.Logger;

public class ICount {

    private static Map<Long, Count> counter;
    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */

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
        Count c = (Count)CNode.getTask().getMetric("Count");
        c.counti(incr).countb();
    }

    public static void mcount(int incr) {
        Count c = (Count)CNode.getTask().getMetric("Count");
        c.countm();
    }

}

