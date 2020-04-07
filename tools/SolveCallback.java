import BIT.highBIT.*;
import java.io.*;
import java.util.*;

import supervisor.storage.LocalStorage;
import supervisor.storage.Storage;

public class SolveCallback {

    static {

        try{
            SolveCallback.cloud = new LocalStorage<Integer>("VirtualMachines");
        }catch(Exception e){
            System.out.println("error loading localstorage test");
        }
                
    }

    private static PrintStream out = null;
    private static int i_count = 0, b_count = 0, m_count = 0;
    private static Storage<Integer> cloud;

    //private static Map<Integer, Private_state >;



    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */
    public static void main(String argv[]) {
        
        String infilename = "project/pt/ulisboa/tecnico/cnv/solver/Solver.class";
        
        // create class info object
        ClassInfo ci = new ClassInfo(infilename);
        
        // loop through all the routines
        // see java.util.Enumeration for more information on Enumeration class
        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
            Routine routine = (Routine) e.nextElement();
            routine.addBefore("SolveCallback", "mcount", new Integer(1));
            
            if( routine.getMethodName().contains("solveSudoku") ){
                System.out.println(routine.getConstantPool());

                for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                    BasicBlock bb = (BasicBlock) b.nextElement();
                    bb.addBefore("SolveCallback", "count", new Integer(bb.size()));
                }
            }
            
        }
        ci.addAfter("SolveCallback", "printSolveCallback", ci.getClassName());
        ci.write("instrumented/pt/ulisboa/tecnico/cnv/solver/Solver.class");
        
    }
    
    public static synchronized void printSolveCallback(String foo) {
        //System.out.println(Runtime.getRuntime().toString());
        System.out.println(" Solution found in " + i_count + "-" + b_count + "-" + m_count );
    }
    

    public static synchronized void count(int incr) {
        i_count += incr;
        b_count++;
    }

    public static synchronized void mcount(int incr) {
        
        m_count++;

    }
}

