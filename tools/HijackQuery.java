import supervisor.server.CNode;
import supervisor.server.Task;
import supervisor.util.Logger;
import BIT.highBIT.*;

import java.util.Enumeration;

public class HijackQuery {
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
    }

    public static void startTask(String[] args){
        CNode.registerTask( Task.makeKey(args) );
    }

}

