package supervisor.server;

public class Task {

    private String key;

    public static String makeKey( String[] args ){
        return args[1] + ":" + args[3]+ ":" + args[5] + ":" + args[9] + ":" + args[11];
    }
    public Task(String key){
        this.key=key;
    }
}
