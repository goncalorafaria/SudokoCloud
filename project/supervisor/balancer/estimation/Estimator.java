package supervisor.balancer.estimation;

import java.util.List;
import supervisor.server.Count;

public class Estimator {

    public static double linint(
            double x0,
            double y0,
            double x1,
            double y1,
            double x) {
        return y0 + (x - x0) * (y1 - y0) / (x1 - x0);
    }

    public static double defaultEstimate(
            String solver,
            String board,
            String un){
        double est = 50000;

        switch (solver){
            case "DLX": est = 80000; break;
            case "CP" : est = 50000; break;
            case "BFS": est = 50000; break;
        }

        return est;
    }

    public static double estimateBranchesTaken(
            String un,
            Group g){

        int sz = g.size();
        if( sz == 1 ){
            Count c = g.take();
            return c.mean() + c.var();
        }else{
            List<Object[]> listc = g.getNearbyPair(un);

            String before =
                    (String)listc.get(0)[0];

            Count cbefore = (Count)listc.get(0)[1];

            String after =
                    (String)listc.get(1)[0];

            Count cafter = (Count)listc.get(1)[1];

            return Estimator.interpolate(
                    before,
                    cbefore,
                    after,
                    cafter,
                    un
            );
        }
    }

    private static double interpolate(
            String before,
            Count cbefore,
            String after,
            Count cafter,
            String target){

        double est = linint(
                Double.parseDouble(before),
                cbefore.mean(),
                Double.parseDouble(after),
                cafter.mean(),
                Double.parseDouble(target));

        est += Math.sqrt(Math.max(cafter.var(),cbefore.var()));

        return est;
    }

    public static double estimate(
            String solver,
            String board,
            String un,
            Group g){

        if (g.size() > 0 ) {
            double est = estimateBranchesTaken(un, g);

            switch (solver) {
                case "BFS":
                    est = est * 12.88852179 + 14068.78484095;
                    break;
                case "CP":
                    est = est * 14.16131419 + 19312.86569091;
                    break;
                case "DLX":
                    // est * 0.005052572765698524 + fixed*109378.21280323979
                    // (dlx branch) -> time
                    // (time) -> bfs branch
                    // bfs branch -> inst
                    est = ((est * 0.00430531 + 75100.9879752195) * 0.09105526 + 556.56763109) * 12.88852179 + 14068.78484095;
                    break;
            }
            return est;
        }else{
            return defaultEstimate(solver,board,un);
        }

    }
}
