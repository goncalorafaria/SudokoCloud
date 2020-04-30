import BIT.highBIT.*;

import java.io.*;
import java.util.*;

import supervisor.server.CNode;
import supervisor.server.Count;

public class ICount {
    /*
     * Instruments metric collection.
     * */

    /* main reads in all the files class files present in the input directory,
     * instruments them, and outputs them to the specified output directory.
     */

    private static boolean overhead = false;
    // TODO: Fix issue that counts methods and instructions while the compilation says explicitly to not do that.
    public static void main(String argv[]) {
        File file_in = new File(argv[0]);
        String infilenames[] = file_in.list();
        boolean[] tr = new boolean[4];

        if( argv.length > 2 ){

            if( argv.length == 3 )
                overhead = Boolean.parseBoolean(argv[2]);

            if( overhead ){
                tr[0]=false;
                tr[1]=false;
                tr[2]=false;
                tr[3]=false;
            }else{
                tr[0] = Integer.parseInt(argv[2])==1;// instructions
                tr[1] = Integer.parseInt(argv[3])==1;// methods
                tr[2] = Integer.parseInt(argv[4])==1;// loops
                tr[3] = Integer.parseInt(argv[5])==1;// inc

            }

        }else{
            tr[0]=true;
            tr[1]=true;
            tr[2]=true;
            tr[3]=true;
        }

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
                    if(tr[1])
                        routine.addBefore("ICount", "mcount", new Integer(1) );

                    for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements(); ) {
                        BasicBlock bb = (BasicBlock) b.nextElement();

                        if(overhead)
                            bb.addBefore("ICount", "countover", new Integer(bb.size()));

                        if(tr[0])
                            bb.addBefore("ICount", "count", new Integer(bb.size()));

                        if( tr[2] ) {
                            Instruction[] instructions = routine.getInstructions();
                            Instruction instr = instructions[bb.getEndAddress()];
                            short instr_type = InstructionTable.InstructionTypeTable[instr.getOpcode()];

                            if (instr_type == InstructionTable.CONDITIONAL_INSTRUCTION)
                                instr.addBefore("ICount", "CheckIncrement", "BranchOutcome");
                        }

                        if( tr[3] ){
                            //search for iinc.
                            Instruction[] instructions = routine.getInstructions();
                            for( int j = 0; j < instructions.length; j++){
                                if( instructions[j].getOpcode() == InstructionTable.iinc ){
                                    instructions[j].addBefore("ICount", "countinc", 1);
                                }

                                short instr_type = InstructionTable.InstructionTypeTable[instructions[j].getOpcode()];

                                //if( instr_type == InstructionTable.STORE_INSTRUCTION )
                                 //   instructions[j].addBefore("ICount","counts",1);
                            }
                        }
                    }
                }

                ci.write(argv[1] + System.getProperty("file.separator") + infilename);
            }
        }
    }

    public static void countinc(int incr) {
        Count c = (Count)CNode.getTask().getMetric("Count");
        c.countinc();
    }

    public static void count(int incr) {
        Count c = (Count)CNode.getTask().getMetric("Count");
        c.counti(incr).countb();
    }

    public static void countover(int incr) {
        Count c = (Count)CNode.getTask().getMetric("Overhead");
        c.counti(incr);
    }

    public static void mcount(int incr) {
        Count c = (Count)CNode.getTask().getMetric("Count");
        c.countm();
    }

    public static void CheckIncrement(int brOutcome) {
        if (brOutcome == 0) {// nova iteracao
            Count c = (Count)CNode.getTask().getMetric("Count");
            c.countBranch();
        }
    }

}

