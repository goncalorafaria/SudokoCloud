package supervisor.server;

import java.util.HashMap;
import java.util.Map;

public class CloudStandart {

    public static String region = "eu-west-2";
    public static int inbound_channel_port = 8088;

    public static String makeKey(String s) {

        Map<String, String> pars = new HashMap<>();
        String[] sp = s.split("[&]");

        for (int i = 0; i < sp.length; i++) {
            String[] ss = sp[i].split("=");
            pars.put(ss[0], ss[1]);
        }

        return pars.get("s") + ":" + pars.get("un") + ":" + pars.get("n1") + ":" + pars.get("n2");
    }

    public static String makeKey(String[] args) {

        return args[1] + ":" + args[3] + ":" + args[5] + ":" + args[7];
    }

}
