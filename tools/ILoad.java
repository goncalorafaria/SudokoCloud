import BIT.highBIT.ClassInfo;

import supervisor.server.CNode;
import supervisor.util.Logger;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

/*
* This class instruments SolverMain and WebServer.
* Suck that when they start supervisor code is iniciated.
* */

public class ILoad {

    public static void main(String argv[]) {
        String indir = "project/pt/ulisboa/tecnico/cnv";
        String outdir ="instrumented/pt/ulisboa/tecnico/cnv";
        Set<String> infinames = new HashSet<>();
        infinames.add("solver/SolverMain.class");
        infinames.add("server/WebServer.class");

        for( String infilename : infinames){
            ClassInfo ci = new ClassInfo(indir + System.getProperty("file.separator") + infilename);

            for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
                BIT.highBIT.Routine routine = (BIT.highBIT.Routine) e.nextElement();
                if( routine.getMethodName().contains("main") )
                    routine.addBefore(
                            "ILoad",
                            "start",
                            new Integer(1));

            }

            ci.addAfter("ILoad", "termination",new Integer(1));

            ci.write(outdir + System.getProperty("file.separator") + infilename);
        }

    }

    public static void start(int incr){
        Logger.publish(true,true);
        CNode.init();
        //ICount.init();
        Logger.log("start");
    }

    public static void termination(int incr){
        Logger.log("terminated");
    }

}
