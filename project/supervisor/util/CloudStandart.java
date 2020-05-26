package supervisor.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class CloudStandart {

    public static String region = "us-east-1";
    public static int inbound_channel_port = 8088;

    public static String ami = "ami-0345a950e41c49326";
    public static String collectorimageid = "ami-0cb790308f7591fa6";
    public static String instancetype = "t2.micro";
    public static String keyname = "CNV-AWS-lab";
    public static String securitygroups = "CNV-ssh+http";

    public static void init(){

        try {
            File fin = new File("dep.config");
            Scanner sc = new Scanner(fin);
            while (sc.hasNext()) {
                String line = sc.nextLine();
                if (line.contains("=")) {
                    String[] args = line.split("=");
                    switch (args[0]) {
                        case "AMI":
                            ami = args[1];
                            break;
                        case "KEYNAME":
                            keyname = args[1];
                            break;
                        case "SECURITYGROUPS":
                            securitygroups = args[1];
                            break;
                        case "INSTANCETYPE":
                            instancetype = args[1];
                            break;
                        case "REGION":
                            region = args[1];
                            break;
                        case "PORT":
                            inbound_channel_port = Integer.parseInt(args[1]);
                            break;
                        default:
                            ;
                    }
                }
            }
        }catch (FileNotFoundException e){
            System.out.println(e.toString());
        }
    }

    public static String makeKey(String s) {

        Map<String, String> pars = new HashMap<>();
        String[] sp = s.split("[&]");

        for (int i = 0; i < sp.length; i++) {
            String[] ss = sp[i].split("=");
            pars.put(ss[0], ss[1]);
        }
        return pars.get("s") + ":" + pars.get("un") + ":" + pars.get("n1") + "x" + pars.get("n2");
    }

    public static String makeKey(String[] args) {
        return args[1] + ":" + args[3] + ":" + args[5] + "x" + args[7];
    }

}
