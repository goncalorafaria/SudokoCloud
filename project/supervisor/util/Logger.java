package supervisor.util;

import java.io.PrintWriter;
import java.util.concurrent.atomic.AtomicBoolean;

public class Logger {
    /*
    * Logger for debugging.
    * */

    private static AtomicBoolean bpublish = new AtomicBoolean(false);
    private static AtomicBoolean bfile = new AtomicBoolean(false);
    private static AtomicBoolean bterminal = new AtomicBoolean(false);
    private static PrintWriter writer;

    public static void log(String message){
        if ( bpublish.get() ) {
            if(bfile.get() ){
                writer.println(message);
                writer.flush();
            }
            if( bterminal.get() ){
                System.out.println("[log]" + message);
            }
        }

    }

    public static void publish(boolean terminal, boolean file){
        try {
            if(file)
                writer = new PrintWriter("log.txt", "UTF-8");
        }catch (Exception e){
            System.out.println(e.toString()+ " publish method");
        }

        bpublish.getAndSet(true);
        bfile.getAndSet(file);
        bterminal.getAndSet(terminal);
    }

}