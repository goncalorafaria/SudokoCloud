import supervisor.server.CNode;
import supervisor.util.CloudStandart;
import BIT.highBIT.*;

import java.util.Enumeration;

public class HijackQuery {
    /*
    * Esta classe trata de instrumentar o servidor para intercetar a receção do pedido.
    * */
    public static void main(String argv[]) {
        
        String infilename = "project/pt/ulisboa/tecnico/cnv/util/AbstractArgumentParser.class";
        ClassInfo ci = new ClassInfo(infilename);

        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
            Routine routine = (Routine) e.nextElement();

            if( routine.getMethodName().equals("setup") ){
                Hijack.arg("HijackQuery","startTask", routine, Hijack.StringArray);
            }
        }
        ci.write("instrumented/pt/ulisboa/tecnico/cnv/util/AbstractArgumentParser.class");

        ///


        ci = new ClassInfo("project/pt/ulisboa/tecnico/cnv/solver/Solver.class");

        for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements(); ) {
            Routine routine = (Routine) e.nextElement();

            if( routine.getMethodName().contains("solveSudoku") ){

                routine.addAfter("HijackQuery","endTask","nothingburger");
            }
        }

        ci.write("instrumented/pt/ulisboa/tecnico/cnv/solver/Solver.class");

    }

    public static void startTask(String[] args){
        CNode.registerTask( CloudStandart.makeKey(args) );
    }

    public static void endTask(String garbage){
        CNode.finishTask();
    }

}

